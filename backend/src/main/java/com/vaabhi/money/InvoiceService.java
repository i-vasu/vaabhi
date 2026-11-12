package com.vaabhi.money;

import com.vaabhi.settings.NumberSequence;
import com.vaabhi.settings.NumberingService;
import com.vaabhi.settings.SettingsService;
import com.vaabhi.shared.rupees.Rupees;
import com.vaabhi.tailoring.ClientService;
import com.vaabhi.tailoring.Order;
import com.vaabhi.tailoring.OrderEvent;
import com.vaabhi.tailoring.OrderService;
import com.vaabhi.tailoring.OrderStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tax invoices. With auto-invoice on (Billing settings) one is issued in the same transaction that delivers an
 * order, so a delivered order always has its invoice; staff can also issue one by hand. Amounts are frozen at
 * issue; cancelling keeps the number and lets a new invoice be issued.
 */
@Service
public class InvoiceService {

    private static final String ID = "id";

    private final JdbcClient jdbc;
    private final OrderService orders;
    private final ClientService clients;
    private final SettingsService settings;
    private final NumberingService numbering;
    private final TaxClassService taxClasses;
    private final CreditNoteService creditNotes;
    private final Clock clock;

    InvoiceService(
            JdbcClient jdbc,
            OrderService orders,
            ClientService clients,
            SettingsService settings,
            NumberingService numbering,
            TaxClassService taxClasses,
            CreditNoteService creditNotes,
            Clock clock) {
        this.taxClasses = taxClasses;
        this.creditNotes = creditNotes;
        this.jdbc = jdbc;
        this.orders = orders;
        this.clients = clients;
        this.settings = settings;
        this.numbering = numbering;
        this.clock = clock;
    }

    // --- issuing ------------------------------------------------------------------------------------------------

    @Transactional
    public Invoice issue(long orderId, @Nullable String actor) {
        var order = orders.get(orderId);
        if (order.status() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A cancelled order cannot be invoiced");
        }
        if (liveInvoiceId(orderId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This order already has an invoice");
        }
        var client = clients.get(order.client().id());
        var buyer = new InvoiceParties.Buyer(
                buyerName(client.firstName(), client.lastName()),
                client.phone(),
                buyerAddress(client.address(), client.city(), client.pinCode()),
                client.state());
        var sellerStateCode = InvoiceParties.seller(settings.shopProfile(), settings.billing())
                .stateCode();
        long id = insertInvoice(new NewInvoice(
                orderId,
                null,
                client.id(),
                null,
                buyer,
                client.gstin(),
                sellerStateCode,
                order.totalRupees(),
                lines(order),
                actor,
                false));
        return get(id);
    }

    /**
     * What a new invoice needs besides the shop settings. The party comes from the client when {@code partyId} is
     * null; {@code orderTotalRupees} is what the order charged, which GST is worked out on. {@code buyerStateCode}
     * is the buyer's GST state code (the seller's own for a shop order); null when unknown.
     */
    record NewInvoice(
            @Nullable Long orderId,
            @Nullable Long webOrderId,
            @Nullable Long clientId,
            @Nullable Long partyId,
            InvoiceParties.Buyer buyer,
            @Nullable String clientGstin,
            @Nullable String buyerStateCode,
            BigDecimal orderTotalRupees,
            List<TaxEngine.TaxInput> lines,
            @Nullable String actor,
            boolean alwaysInclusive) {}

    /** Works out the GST, takes the next number and inserts the invoice with its lines; returns the new id. */
    long insertInvoice(NewInvoice invoice) {
        var billing = settings.billing();
        boolean inclusive = invoice.alwaysInclusive() || billing.pricesIncludeTax();
        var seller = InvoiceParties.seller(settings.shopProfile(), billing);
        var tax = TaxEngine.compute(
                invoice.lines(),
                invoice.orderTotalRupees(),
                seller.stateCode(),
                invoice.buyerStateCode(),
                billing.gstApplies(),
                inclusive,
                taxClasses::resolve);
        var number = numbering.next(NumberSequence.Kind.INVOICE);
        var buyer = invoice.buyer();
        var placeOfSupply = invoice.buyerStateCode() != null ? invoice.buyerStateCode() : seller.stateCode();
        long id = jdbc.sql("""
                        INSERT INTO invoice (number, order_id, web_order_id, client_id, party_id, issued_on,
                            order_total_rupees, gst_rate, prices_include_tax, taxable_rupees, cgst_rupees, sgst_rupees,
                            igst_rupees, place_of_supply, supply_type, total_rupees, shop_gstin, client_gstin,
                            created_by, title, buyer_name, buyer_phone, buyer_address, buyer_state, seller_name,
                            seller_address, seller_cin, seller_state, seller_state_code)
                        VALUES (:number, :order, :webOrder, :client,
                            COALESCE(CAST(:party AS bigint), (SELECT party_id FROM client WHERE id = :client)),
                            :issuedOn, :orderTotal, :rate, :inclusive, :taxable, :cgst, :sgst, :igst, :placeOfSupply,
                            :supplyType, :total, :shopGstin, :clientGstin, :by, :title, :buyerName, :buyerPhone,
                            :buyerAddress, :buyerState, :sellerName, :sellerAddress, :sellerCin, :sellerState,
                            :sellerStateCode)
                        RETURNING id
                        """)
                .param("number", number)
                .param("order", invoice.orderId())
                .param("webOrder", invoice.webOrderId())
                .param("client", invoice.clientId())
                .param("party", invoice.partyId())
                .param("issuedOn", InvoiceParties.today(clock))
                .param("title", InvoiceParties.title(billing))
                .param("buyerName", buyer.name())
                .param("buyerPhone", buyer.phone())
                .param("buyerAddress", buyer.address())
                .param("buyerState", buyer.state())
                .param("sellerName", seller.name())
                .param("sellerAddress", seller.address())
                .param("sellerCin", seller.cin())
                .param("sellerState", seller.state())
                .param("sellerStateCode", seller.stateCode())
                .param("orderTotal", invoice.orderTotalRupees())
                .param("rate", tax.highestRate())
                .param("inclusive", inclusive)
                .param("taxable", tax.taxableRupees())
                .param("cgst", tax.cgstRupees())
                .param("sgst", tax.sgstRupees())
                .param("igst", tax.igstRupees())
                .param("placeOfSupply", placeOfSupply)
                .param("supplyType", tax.supplyType().name())
                .param("total", tax.totalRupees())
                .param("shopGstin", billing.gstApplies() ? billing.gstin() : null)
                .param("clientGstin", invoice.clientGstin())
                .param("by", invoice.actor())
                .query(Long.class)
                .single();
        int position = 0;
        for (var line : tax.lines()) {
            jdbc.sql("""
                            INSERT INTO invoice_line (invoice_id, position, description, quantity, unit_rupees,
                                amount_rupees, discount_rupees, hsn_code, gst_rate, taxable_rupees, cgst_rupees,
                                sgst_rupees, igst_rupees, web_order_line_id)
                            VALUES (:invoice, :position, :description, :quantity, :unit, :amount, :discount, :hsn,
                                :rate, :taxable, :cgst, :sgst, :igst, :webOrderLine)
                            """)
                    .param("invoice", id)
                    .param("position", ++position)
                    .param("description", line.input().description())
                    .param("quantity", line.input().quantity())
                    .param("unit", line.input().unitRupees())
                    .param("amount", line.amountRupees())
                    .param("discount", line.discountRupees())
                    .param("hsn", line.hsnCode())
                    .param("rate", line.gstRate())
                    .param("taxable", line.taxableRupees())
                    .param("cgst", line.cgstRupees())
                    .param("sgst", line.sgstRupees())
                    .param("igst", line.igstRupees())
                    .param("webOrderLine", line.input().webOrderLineId())
                    .update();
        }
        return id;
    }

    private static String buyerName(String firstName, @Nullable String lastName) {
        return lastName == null || lastName.isBlank() ? firstName.trim() : (firstName + " " + lastName).trim();
    }

    private static @Nullable String buyerAddress(
            @Nullable String address, @Nullable String city, @Nullable String pin) {
        var parts = Stream.of(address, city, pin)
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .toList();
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private List<TaxEngine.TaxInput> lines(Order order) {
        var lines = new ArrayList<TaxEngine.TaxInput>();
        for (var item : order.items()) {
            var description =
                    item.colour() == null ? item.description() : item.description() + " (" + item.colour() + ")";
            lines.add(new TaxEngine.TaxInput(
                    description.length() > 160 ? description.substring(0, 160) : description,
                    item.quantity(),
                    item.priceRupees(),
                    taxClasses.shopItemClass(item.templateId(), item.serviceProductId()),
                    null,
                    false));
        }
        if (order.deliveryChargeRupees().signum() > 0) {
            lines.add(new TaxEngine.TaxInput("Delivery", 1, order.deliveryChargeRupees(), null, null, true));
        }
        return lines;
    }

    /** Issues the invoice when an order is delivered (auto-invoice on), and voids it when an order is cancelled. */
    @EventListener
    void on(OrderEvent event) {
        if (event.type() == OrderEvent.Type.DELIVERED
                && settings.billing().autoInvoice()
                && liveInvoiceId(event.orderId()).isEmpty()) {
            issue(event.orderId(), event.actor());
        } else if (event.type() == OrderEvent.Type.CANCELLED) {
            liveInvoiceId(event.orderId()).ifPresent(id -> voidOrCredit(id, event.actor()));
        } else if (event.type() == OrderEvent.Type.CHANGED
                && liveInvoiceId(event.orderId()).isPresent()) {
            // An issued invoice is a tax record: the order cannot change under it (this rolls the change back).
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This order has an invoice; issue a credit note or cancel it on the day it was issued before changing the order");
        }
    }

    @Transactional
    public Invoice cancel(long id, @Nullable String actor) {
        lock(id);
        var invoice = get(id);
        if (invoice.cancelledAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This invoice was already cancelled");
        }
        if (!invoice.issuedOn().equals(InvoiceParties.today(clock))
                || !invoice.creditNotes().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Issue a credit note instead");
        }
        return voidInvoice(id, actor);
    }

    /**
     * Cancels the invoice when that is still allowed (issued today, no credit notes), otherwise credits whatever is
     * left of it in full; so cancelling an order is never blocked by the same-day rule.
     */
    @Transactional
    public void voidOrCredit(long id, @Nullable String actor) {
        lock(id);
        var invoice = get(id);
        if (invoice.cancelledAt() != null) {
            return;
        }
        if (invoice.issuedOn().equals(InvoiceParties.today(clock))
                && invoice.creditNotes().isEmpty()) {
            voidInvoice(id, actor);
        } else {
            creditNotes.issueRemaining(id, CreditNote.Reason.CANCELLED, null, actor);
        }
    }

    /** Locks the invoice row so the day and credit note checks cannot race a credit note being issued. */
    private void lock(long id) {
        var found = jdbc.sql("SELECT id FROM invoice WHERE id = :id FOR UPDATE")
                .param(ID, id)
                .query(Long.class)
                .optional();
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found");
        }
    }

    private Invoice voidInvoice(long id, @Nullable String actor) {
        jdbc.sql("UPDATE invoice SET cancelled_at = now(), cancelled_by = :by WHERE id = :id")
                .param("by", actor)
                .param(ID, id)
                .update();
        return get(id);
    }

    // --- reading ------------------------------------------------------------------------------------------------

    private Optional<Long> liveInvoiceId(long orderId) {
        return jdbc.sql("SELECT id FROM invoice WHERE order_id = :order AND cancelled_at IS NULL")
                .param("order", orderId)
                .query(Long.class)
                .optional();
    }

    /** The order's live invoice, if it has one. */
    @Transactional(readOnly = true)
    public Optional<Invoice> forOrder(long orderId) {
        return liveInvoiceId(orderId).map(this::get);
    }

    private record Row(
            long id,
            String number,
            @Nullable Long orderId,
            @Nullable Long webOrderId,
            String orderNumber,
            @Nullable Long clientId,
            String title,
            String buyerName,
            @Nullable String buyerPhone,
            @Nullable String buyerAddress,
            @Nullable String buyerState,
            String sellerName,
            @Nullable String sellerAddress,
            @Nullable String sellerCin,
            @Nullable String sellerState,
            @Nullable String sellerStateCode,
            LocalDate issuedOn,
            BigDecimal orderTotalRupees,
            BigDecimal gstRate,
            boolean pricesIncludeTax,
            BigDecimal taxableRupees,
            BigDecimal cgstRupees,
            BigDecimal sgstRupees,
            BigDecimal igstRupees,
            @Nullable String placeOfSupply,
            String supplyType,
            BigDecimal totalRupees,
            BigDecimal paidRupees,
            @Nullable String shopGstin,
            @Nullable String clientGstin,
            @Nullable String createdBy,
            OffsetDateTime createdAt,
            @Nullable OffsetDateTime cancelledAt,
            @Nullable String cancelledBy) {}

    @Transactional(readOnly = true)
    public Invoice get(long id) {
        var row = jdbc.sql("""
                        SELECT v.id, v.number, v.order_id, v.web_order_id, COALESCE(o.number, w.number) AS order_number,
                               v.client_id, v.title, v.buyer_name, v.buyer_phone, v.buyer_address, v.buyer_state,
                               v.seller_name, v.seller_address, v.seller_cin, v.seller_state, v.seller_state_code,
                               v.issued_on, v.order_total_rupees, v.gst_rate, v.prices_include_tax, v.taxable_rupees,
                               v.cgst_rupees, v.sgst_rupees, v.igst_rupees, v.place_of_supply, v.supply_type,
                               v.total_rupees,
                               CASE WHEN v.web_order_id IS NULL THEN o.paid_rupees
                                    WHEN w.paid_at IS NOT NULL THEN v.total_rupees ELSE 0 END AS paid_rupees,
                               v.shop_gstin, v.client_gstin, v.created_by, v.created_at, v.cancelled_at, v.cancelled_by
                        FROM invoice v
                        LEFT JOIN shop_order o ON o.id = v.order_id
                        LEFT JOIN web_order w ON w.id = v.web_order_id
                        WHERE v.id = :id
                        """)
                .param(ID, id)
                .query(Row.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        var lines = jdbc.sql("""
                        SELECT id, description, quantity, unit_rupees, amount_rupees, discount_rupees, hsn_code,
                               gst_rate, taxable_rupees, cgst_rupees, sgst_rupees, igst_rupees
                        FROM invoice_line
                        WHERE invoice_id = :id ORDER BY position
                        """).param(ID, id).query(InvoiceLine.class).list();
        var linesRupees = Rupees.sum(lines, InvoiceLine::amountRupees);
        return new Invoice(
                row.id(),
                row.number(),
                row.webOrderId() == null ? Invoice.Source.SHOP : Invoice.Source.WEB,
                row.orderId(),
                row.orderNumber(),
                row.clientId() == null ? null : clients.summary(row.clientId()),
                row.title(),
                new InvoiceParties.Seller(
                        row.sellerName(),
                        row.sellerAddress(),
                        row.sellerCin(),
                        row.sellerState(),
                        row.sellerStateCode()),
                new InvoiceParties.Buyer(row.buyerName(), row.buyerPhone(), row.buyerAddress(), row.buyerState()),
                row.issuedOn(),
                lines,
                linesRupees.subtract(row.orderTotalRupees()),
                row.orderTotalRupees(),
                row.gstRate(),
                row.pricesIncludeTax(),
                row.taxableRupees(),
                row.cgstRupees(),
                row.sgstRupees(),
                row.igstRupees(),
                row.placeOfSupply(),
                row.supplyType(),
                row.totalRupees(),
                row.paidRupees(),
                row.totalRupees().subtract(row.paidRupees()),
                row.shopGstin(),
                row.clientGstin(),
                row.createdBy(),
                row.createdAt(),
                row.cancelledAt(),
                row.cancelledBy(),
                creditNotes.forInvoice(id));
    }

    private record SummaryRow(
            long id,
            String number,
            @Nullable Long orderId,
            @Nullable Long webOrderId,
            String orderNumber,
            @Nullable Long clientId,
            String buyerName,
            LocalDate issuedOn,
            BigDecimal taxableRupees,
            BigDecimal taxRupees,
            BigDecimal totalRupees,
            BigDecimal paidRupees,
            boolean cancelled) {}

    private record RegisterTotals(long count, BigDecimal total, BigDecimal tax) {}

    /** The invoice register, newest first, with totals of the live invoices that match. */
    // Only constant SQL fragments (and fixed table names / enum-chosen ORDER BY) are joined; values are bound.
    @SuppressWarnings("java:S2077")
    @Transactional(readOnly = true)
    public InvoicePage list(
            @Nullable String query, @Nullable LocalDate from, @Nullable LocalDate to, int page, int pageSize) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", like(query));
        params.put("from", from);
        params.put("to", to);
        var where = """
                FROM invoice v
                LEFT JOIN shop_order o ON o.id = v.order_id
                LEFT JOIN web_order w ON w.id = v.web_order_id
                WHERE (CAST(:from AS date) IS NULL OR v.issued_on >= :from)
                  AND (CAST(:to AS date) IS NULL OR v.issued_on <= :to)
                  AND (CAST(:q AS varchar) IS NULL OR lower(v.number) LIKE :q
                       OR lower(COALESCE(o.number, w.number)) LIKE :q OR lower(v.buyer_name) LIKE :q)
                """;
        var totals = jdbc.sql("""
                        SELECT COUNT(*) AS n,
                               COALESCE(SUM(v.total_rupees) FILTER (WHERE v.cancelled_at IS NULL), 0) AS total,
                               COALESCE(SUM(v.cgst_rupees + v.sgst_rupees + v.igst_rupees) FILTER (WHERE v.cancelled_at IS NULL), 0) AS tax
                        """ + where)
                .params(params)
                .query((rs, n) ->
                        new RegisterTotals(rs.getLong("n"), rs.getBigDecimal("total"), rs.getBigDecimal("tax")))
                .single();
        params.put("limit", pageSize);
        params.put("offset", (page - 1) * pageSize);
        var items = jdbc
                .sql("""
                        SELECT v.id, v.number, v.order_id, v.web_order_id, COALESCE(o.number, w.number) AS order_number,
                               v.client_id, v.buyer_name, v.issued_on, v.taxable_rupees,
                               v.cgst_rupees + v.sgst_rupees + v.igst_rupees AS tax_rupees, v.total_rupees,
                               CASE WHEN v.web_order_id IS NULL THEN o.paid_rupees
                                    WHEN w.paid_at IS NOT NULL THEN v.total_rupees ELSE 0 END AS paid_rupees,
                               v.cancelled_at IS NOT NULL AS cancelled
                        """ + where + " ORDER BY v.issued_on DESC, v.id DESC LIMIT :limit OFFSET :offset")
                .params(params)
                .query(SummaryRow.class)
                .list()
                .stream()
                .map(row -> new InvoiceSummary(
                        row.id(),
                        row.number(),
                        row.webOrderId() == null ? Invoice.Source.SHOP : Invoice.Source.WEB,
                        row.orderId(),
                        row.orderNumber(),
                        row.clientId(),
                        row.buyerName(),
                        row.issuedOn(),
                        row.taxableRupees(),
                        row.taxRupees(),
                        row.totalRupees(),
                        row.cancelled() ? Rupees.ZERO : row.totalRupees().subtract(row.paidRupees()),
                        row.cancelled()))
                .toList();
        return new InvoicePage(
                items, totals.count(), page, pageSize, Rupees.of(totals.total()), Rupees.of(totals.tax()));
    }

    /** Case-insensitive "contains" pattern with LIKE wildcards escaped; null for no filter. */
    static @Nullable String like(@Nullable String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return "%"
                + query.trim()
                        .toLowerCase()
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_") + "%";
    }
}
