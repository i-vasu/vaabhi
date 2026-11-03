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
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final JdbcClient jdbc;
    private final OrderService orders;
    private final ClientService clients;
    private final SettingsService settings;
    private final NumberingService numbering;
    private final Clock clock;

    InvoiceService(
            JdbcClient jdbc,
            OrderService orders,
            ClientService clients,
            SettingsService settings,
            NumberingService numbering,
            Clock clock) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.clients = clients;
        this.settings = settings;
        this.numbering = numbering;
        this.clock = clock;
    }

    // --- GST ----------------------------------------------------------------------------------------------------

    /** Taxable value, CGST, SGST and what the customer pays, for an amount at a GST rate (percent). */
    record Tax(BigDecimal taxableRupees, BigDecimal cgstRupees, BigDecimal sgstRupees, BigDecimal totalRupees) {}

    /**
     * Splits {@code amountRupees} into taxable value and GST. When prices include tax the amount stays what the
     * customer pays; otherwise GST is added on top. The tax is halved into CGST and SGST (SGST takes the odd paisa).
     */
    static Tax tax(BigDecimal amountRupees, BigDecimal ratePercent, boolean pricesIncludeTax) {
        var amount = Rupees.of(amountRupees);
        if (ratePercent.signum() == 0) {
            return new Tax(amount, Rupees.ZERO, Rupees.ZERO, amount);
        }
        BigDecimal taxable;
        BigDecimal gst;
        if (pricesIncludeTax) {
            taxable = amount.multiply(HUNDRED).divide(HUNDRED.add(ratePercent), Rupees.SCALE, RoundingMode.HALF_UP);
            gst = amount.subtract(taxable);
        } else {
            taxable = amount;
            gst = Rupees.percentOf(amount, ratePercent);
        }
        var cgst = gst.divide(BigDecimal.valueOf(2), Rupees.SCALE, RoundingMode.DOWN);
        return new Tax(taxable, cgst, gst.subtract(cgst), taxable.add(gst));
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
        var billing = settings.billing();
        var rate = billing.gstApplies() ? billing.defaultGstRate() : BigDecimal.ZERO;
        var tax = tax(order.totalRupees(), rate, billing.pricesIncludeTax());
        var number = numbering.next(NumberSequence.Kind.INVOICE);
        long id = jdbc.sql("""
                        INSERT INTO invoice (number, order_id, client_id, issued_on, order_total_rupees, gst_rate,
                            prices_include_tax, taxable_rupees, cgst_rupees, sgst_rupees, total_rupees, shop_gstin,
                            client_gstin, created_by)
                        VALUES (:number, :order, :client, :issuedOn, :orderTotal, :rate, :inclusive, :taxable, :cgst,
                            :sgst, :total, :shopGstin, :clientGstin, :by)
                        RETURNING id
                        """)
                .param("number", number)
                .param("order", orderId)
                .param("client", order.client().id())
                .param("issuedOn", LocalDate.now(clock))
                .param("orderTotal", order.totalRupees())
                .param("rate", rate)
                .param("inclusive", billing.pricesIncludeTax())
                .param("taxable", tax.taxableRupees())
                .param("cgst", tax.cgstRupees())
                .param("sgst", tax.sgstRupees())
                .param("total", tax.totalRupees())
                .param("shopGstin", billing.gstApplies() ? billing.gstin() : null)
                .param("clientGstin", clients.get(order.client().id()).gstin())
                .param("by", actor)
                .query(Long.class)
                .single();
        int position = 0;
        for (var line : lines(order)) {
            jdbc.sql("""
                            INSERT INTO invoice_line (invoice_id, position, description, quantity, unit_rupees,
                                amount_rupees)
                            VALUES (:invoice, :position, :description, :quantity, :unit, :amount)
                            """)
                    .param("invoice", id)
                    .param("position", ++position)
                    .param("description", line.description())
                    .param("quantity", line.quantity())
                    .param("unit", line.unitRupees())
                    .param("amount", line.amountRupees())
                    .update();
        }
        return get(id);
    }

    private static List<InvoiceLine> lines(Order order) {
        var lines = new ArrayList<InvoiceLine>();
        for (var item : order.items()) {
            var description =
                    item.colour() == null ? item.description() : item.description() + " (" + item.colour() + ")";
            lines.add(new InvoiceLine(
                    description.length() > 160 ? description.substring(0, 160) : description,
                    item.quantity(),
                    item.priceRupees(),
                    Rupees.times(item.priceRupees(), item.quantity())));
        }
        if (order.deliveryChargeRupees().signum() > 0) {
            lines.add(new InvoiceLine("Delivery", 1, order.deliveryChargeRupees(), order.deliveryChargeRupees()));
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
            liveInvoiceId(event.orderId()).ifPresent(id -> cancel(id, event.actor()));
        } else if (event.type() == OrderEvent.Type.CHANGED
                && liveInvoiceId(event.orderId()).isPresent()) {
            // An issued invoice is a tax record: the order cannot change under it (this rolls the change back).
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This order has an invoice; cancel the invoice before changing the order");
        }
    }

    @Transactional
    public Invoice cancel(long id, @Nullable String actor) {
        var invoice = get(id);
        if (invoice.cancelledAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This invoice was already cancelled");
        }
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
            long orderId,
            String orderNumber,
            long clientId,
            LocalDate issuedOn,
            BigDecimal orderTotalRupees,
            BigDecimal gstRate,
            boolean pricesIncludeTax,
            BigDecimal taxableRupees,
            BigDecimal cgstRupees,
            BigDecimal sgstRupees,
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
                        SELECT v.id, v.number, v.order_id, o.number AS order_number, v.client_id, v.issued_on,
                               v.order_total_rupees, v.gst_rate, v.prices_include_tax, v.taxable_rupees, v.cgst_rupees,
                               v.sgst_rupees, v.total_rupees, o.paid_rupees, v.shop_gstin, v.client_gstin, v.created_by,
                               v.created_at, v.cancelled_at, v.cancelled_by
                        FROM invoice v JOIN shop_order o ON o.id = v.order_id
                        WHERE v.id = :id
                        """)
                .param(ID, id)
                .query(Row.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        var lines = jdbc.sql("""
                        SELECT description, quantity, unit_rupees, amount_rupees FROM invoice_line
                        WHERE invoice_id = :id ORDER BY position
                        """).param(ID, id).query(InvoiceLine.class).list();
        var linesRupees = Rupees.sum(lines, InvoiceLine::amountRupees);
        return new Invoice(
                row.id(),
                row.number(),
                row.orderId(),
                row.orderNumber(),
                clients.summary(row.clientId()),
                row.issuedOn(),
                lines,
                linesRupees.subtract(row.orderTotalRupees()),
                row.orderTotalRupees(),
                row.gstRate(),
                row.pricesIncludeTax(),
                row.taxableRupees(),
                row.cgstRupees(),
                row.sgstRupees(),
                row.totalRupees(),
                row.paidRupees(),
                row.totalRupees().subtract(row.paidRupees()),
                row.shopGstin(),
                row.clientGstin(),
                row.createdBy(),
                row.createdAt(),
                row.cancelledAt(),
                row.cancelledBy());
    }

    private record SummaryRow(
            long id,
            String number,
            long orderId,
            String orderNumber,
            long clientId,
            String firstName,
            @Nullable String lastName,
            LocalDate issuedOn,
            BigDecimal taxableRupees,
            BigDecimal taxRupees,
            BigDecimal totalRupees,
            BigDecimal paidRupees,
            boolean cancelled) {}

    private record RegisterTotals(long count, BigDecimal total, BigDecimal tax) {}

    /** The invoice register, newest first, with totals of the live invoices that match. */
    @Transactional(readOnly = true)
    public InvoicePage list(
            @Nullable String query, @Nullable LocalDate from, @Nullable LocalDate to, int page, int pageSize) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", like(query));
        params.put("from", from);
        params.put("to", to);
        var where = """
                FROM invoice v JOIN shop_order o ON o.id = v.order_id JOIN client c ON c.id = v.client_id
                WHERE (CAST(:from AS date) IS NULL OR v.issued_on >= :from)
                  AND (CAST(:to AS date) IS NULL OR v.issued_on <= :to)
                  AND (CAST(:q AS varchar) IS NULL OR lower(v.number) LIKE :q OR lower(o.number) LIKE :q
                       OR lower(c.first_name || ' ' || coalesce(c.last_name, '')) LIKE :q)
                """;
        var totals = jdbc.sql("""
                        SELECT COUNT(*) AS n,
                               COALESCE(SUM(v.total_rupees) FILTER (WHERE v.cancelled_at IS NULL), 0) AS total,
                               COALESCE(SUM(v.cgst_rupees + v.sgst_rupees) FILTER (WHERE v.cancelled_at IS NULL), 0) AS tax
                        """ + where)
                .params(params)
                .query((rs, n) ->
                        new RegisterTotals(rs.getLong("n"), rs.getBigDecimal("total"), rs.getBigDecimal("tax")))
                .single();
        params.put("limit", pageSize);
        params.put("offset", (page - 1) * pageSize);
        var items = jdbc
                .sql("""
                        SELECT v.id, v.number, v.order_id, o.number AS order_number, v.client_id, c.first_name,
                               c.last_name, v.issued_on, v.taxable_rupees, v.cgst_rupees + v.sgst_rupees AS tax_rupees,
                               v.total_rupees, o.paid_rupees, v.cancelled_at IS NOT NULL AS cancelled
                        """ + where + " ORDER BY v.issued_on DESC, v.id DESC LIMIT :limit OFFSET :offset")
                .params(params)
                .query(SummaryRow.class)
                .list()
                .stream()
                .map(row -> new InvoiceSummary(
                        row.id(),
                        row.number(),
                        row.orderId(),
                        row.orderNumber(),
                        row.clientId(),
                        row.lastName() == null || row.lastName().isBlank()
                                ? row.firstName()
                                : row.firstName() + " " + row.lastName(),
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
