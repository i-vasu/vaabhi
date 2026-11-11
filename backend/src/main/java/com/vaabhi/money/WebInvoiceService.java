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

    WebInvoiceService(JdbcClient jdbc, InvoiceService invoices) {
        this.jdbc = jdbc;
        this.invoices = invoices;
    }

    /** The web order's live invoice, issuing one first if it has none. */
    @Transactional
    public Invoice issueFor(WebInvoiceRequest request) {
        // Locking the order row serialises concurrent issues, so the one-live-invoice index is never hit.
        jdbc.sql("SELECT id FROM web_order WHERE id = :id FOR UPDATE")
                .param(ID, request.webOrderId())
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        var live = liveId(request.webOrderId());
        if (live.isPresent()) {
            return invoices.get(live.get());
        }
        var lines = new ArrayList<InvoiceLine>();
        for (var line : request.lines()) {
            var description = line.description();
            lines.add(new InvoiceLine(
                    description.length() > 160 ? description.substring(0, 160) : description,
                    line.quantity(),
                    line.unitRupees(),
                    Rupees.times(line.unitRupees(), line.quantity())));
        }
        if (request.shippingRupees().signum() > 0) {
            lines.add(new InvoiceLine("Delivery", 1, request.shippingRupees(), request.shippingRupees()));
        }
        long id = invoices.insertInvoice(new InvoiceService.NewInvoice(
                null,
                request.webOrderId(),
                null,
                request.partyId(),
                request.buyer(),
                null,
                Rupees.sum(lines, InvoiceLine::amountRupees),
                lines,
                null));
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

    /** Cancels the web order's live invoice; nothing to do when it has none. */
    @Transactional
    public void cancelFor(long webOrderId, @Nullable String actor) {
        liveId(webOrderId).ifPresent(id -> invoices.cancel(id, actor));
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
