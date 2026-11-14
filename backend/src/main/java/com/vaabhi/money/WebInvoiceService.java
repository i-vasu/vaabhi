package com.vaabhi.money;

import com.vaabhi.shared.rupees.Rupees;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Invoices for web orders: issued by checkout when an order ships, read back for the shop and the customer. */
@Service
public class WebInvoiceService {

    private static final String ID = "id";

    private final JdbcClient jdbc;
    private final InvoiceService invoices;
    private final CreditNoteService creditNotes;

    WebInvoiceService(JdbcClient jdbc, InvoiceService invoices, CreditNoteService creditNotes) {
        this.jdbc = jdbc;
        this.invoices = invoices;
        this.creditNotes = creditNotes;
    }

    /** The web order's live invoice, issuing one first if it has none. */
    @Transactional
    public Invoice issueFor(WebInvoiceRequest request) {
        // Locking the order row serialises concurrent issues, so the one-live-invoice index is never hit.
        var locked = jdbc.sql("SELECT id FROM web_order WHERE id = :id AND status IN ('PLACED','SHIPPED') FOR UPDATE")
                .param(ID, request.webOrderId())
                .query(Long.class)
                .optional();
        if (locked.isEmpty()) {
            boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM web_order WHERE id = :id)")
                    .param(ID, request.webOrderId())
                    .query(Boolean.class)
                    .single();
            throw exists
                    ? new ResponseStatusException(HttpStatus.CONFLICT, "Only a placed or shipped order can be invoiced")
                    : new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        long webOrderId = locked.get();
        var live = liveId(webOrderId);
        if (live.isPresent()) {
            return invoices.get(live.get());
        }
        var lines = new ArrayList<TaxEngine.TaxInput>();
        for (var line : request.lines()) {
            var description = line.description();
            lines.add(new TaxEngine.TaxInput(
                    description.length() > 160 ? description.substring(0, 160) : description,
                    line.quantity(),
                    line.unitRupees(),
                    line.taxClassId(),
                    line.webOrderLineId(),
                    false));
        }
        if (request.shippingRupees().signum() > 0) {
            lines.add(new TaxEngine.TaxInput("Delivery", 1, request.shippingRupees(), null, null, true));
        }
        long id = invoices.insertInvoice(new InvoiceService.NewInvoice(
                null,
                webOrderId,
                null,
                request.partyId(),
                request.buyer(),
                null,
                request.buyerStateCode(),
                // What the buyer was charged; anything below the lines is spread as a discount over the goods.
                Rupees.sum(lines, line -> Rupees.times(line.unitRupees(), line.quantity()))
                        .subtract(request.discountRupees()),
                lines,
                null,
                true));
        return invoices.get(id);
    }

    /** Web order id to the number of its live invoice, for the orders that have one. */
    @Transactional(readOnly = true)
    public Map<Long, String> liveNumbers(Collection<Long> webOrderIds) {
        Map<Long, String> numbers = new LinkedHashMap<>();
        if (webOrderIds.isEmpty()) {
            return numbers;
        }
        jdbc.sql("""
                        SELECT web_order_id, number FROM invoice
                        WHERE web_order_id IN (:ids) AND cancelled_at IS NULL
                        """)
                .param("ids", webOrderIds)
                .query((rs, n) -> {
                    numbers.put(rs.getLong("web_order_id"), rs.getString("number"));
                    return n;
                })
                .list();
        return numbers;
    }

    /**
     * A cancelled web order's live invoice is reversed by a full credit note for whatever is not credited yet;
     * nothing to do when it has no invoice or is already fully credited.
     */
    @Transactional
    public void creditFor(long webOrderId, @Nullable String actor) {
        liveId(webOrderId).ifPresent(id -> creditNotes.issueRemaining(id, CreditNote.Reason.CANCELLED, null, actor));
    }

    /** The live invoice of one of this customer's own orders, by order number. */
    @Transactional(readOnly = true)
    public Optional<StoreInvoice> forCustomer(String orderNumber, long customerId) {
        return jdbc.sql("""
                        SELECT v.id FROM invoice v
                        JOIN web_order w ON w.id = v.web_order_id
                        WHERE w.number = :number AND w.customer_id = :customer AND v.cancelled_at IS NULL
                        """)
                .param("number", orderNumber)
                .param("customer", customerId)
                .query(Long.class)
                .optional()
                .map(invoices::get)
                .map(invoice -> new StoreInvoice(
                        invoice.number(),
                        invoice.title(),
                        invoice.issuedOn(),
                        invoice.seller(),
                        invoice.buyer(),
                        invoice.orderNumber(),
                        invoice.lines(),
                        invoice.taxableRupees(),
                        invoice.cgstRupees(),
                        invoice.sgstRupees(),
                        invoice.igstRupees(),
                        invoice.placeOfSupply(),
                        invoice.supplyType(),
                        invoice.totalRupees(),
                        invoice.shopGstin(),
                        invoice.cancelledAt() != null));
    }

    private Optional<Long> liveId(long webOrderId) {
        return jdbc.sql("SELECT id FROM invoice WHERE web_order_id = :id AND cancelled_at IS NULL")
                .param(ID, webOrderId)
                .query(Long.class)
                .optional();
    }
}
