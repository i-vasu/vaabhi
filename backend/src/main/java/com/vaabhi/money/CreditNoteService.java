package com.vaabhi.money;

import com.vaabhi.settings.NumberSequence;
import com.vaabhi.settings.NumberingService;
import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Credit notes: reverse an invoice, whole or in part, for a return, a cancelled web order or a correction. Each note
 * freezes its own copy of the parties and takes the next number of its series. A line can be credited again and again
 * until its quantity is used up; the last credit of a line takes the exact remainder so the notes add up to the
 * invoice to the paisa.
 */
@Service
public class CreditNoteService {

    private static final String ID = "id";
    private static final String FULLY_CREDITED = "This invoice is already fully credited";

    private final JdbcClient jdbc;
    private final NumberingService numbering;
    private final Clock clock;

    CreditNoteService(JdbcClient jdbc, NumberingService numbering, Clock clock) {
        this.jdbc = jdbc;
        this.numbering = numbering;
        this.clock = clock;
    }

    // --- issuing ------------------------------------------------------------------------------------------------

    /** Staff's credit note from the admin form: the chosen lines, or the whole invoice when none are chosen. */
    @Transactional
    public CreditNote issue(long invoiceId, CreditNoteForm form, @Nullable String actor) {
        if (form.reason() == CreditNote.Reason.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose Return or Correction");
        }
        if (form.lines() == null || form.lines().isEmpty()) {
            return issueFull(invoiceId, form.reason(), form.note(), actor);
        }
        return issuePartial(invoiceId, form.lines(), form.reason(), form.note(), actor);
    }

    /** Credits everything on the invoice not credited yet; 409 when nothing is left. */
    @Transactional
    public CreditNote issueFull(
            long invoiceId, CreditNote.Reason reason, @Nullable String note, @Nullable String actor) {
        var issued = issueRemaining(invoiceId, reason, note, actor);
        if (issued == null) {
            throw conflict(FULLY_CREDITED);
        }
        return issued;
    }

    /** Like {@link #issueFull} but returns null instead of failing when there is nothing left to credit. */
    @Transactional
    public @Nullable CreditNote issueRemaining(
            long invoiceId, CreditNote.Reason reason, @Nullable String note, @Nullable String actor) {
        return create(invoiceId, reason, note, actor, null, null, false);
    }

    @Transactional
    public CreditNote issuePartial(
            long invoiceId,
            List<CreditNoteForm.Line> lines,
            CreditNote.Reason reason,
            @Nullable String note,
            @Nullable String actor) {
        if (lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose at least one line to credit");
        }
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        lines.forEach(line -> wanted.merge(line.invoiceLineId(), line.quantity(), Integer::sum));
        var issued = create(invoiceId, reason, note, actor, null, wanted, false);
        if (issued == null) {
            throw conflict(FULLY_CREDITED);
        }
        return issued;
    }

    /**
     * The credit note for a refunded web order return. The same return never gets two notes. Null when the order has
     * no live invoice (it never shipped) or there is nothing left to credit on it.
     *
     * @param qtyByWebOrderLineId the returned quantity of each web order line; add-on invoice lines on the same web
     *     order line are credited in the same proportion
     */
    @Transactional
    public @Nullable CreditNote issueForReturn(
            long returnId, long webOrderId, Map<Long, Integer> qtyByWebOrderLineId, @Nullable String actor) {
        var existing = jdbc.sql("SELECT id FROM credit_note WHERE web_order_return_id = :return")
                .param("return", returnId)
                .query(Long.class)
                .optional();
        if (existing.isPresent()) {
            return get(existing.get());
        }
        var invoiceId = jdbc.sql("SELECT id FROM invoice WHERE web_order_id = :order AND cancelled_at IS NULL")
                .param("order", webOrderId)
                .query(Long.class)
                .optional();
        if (invoiceId.isEmpty()) {
            return null;
        }
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT id, web_order_line_id FROM invoice_line
                        WHERE invoice_id = :invoice AND web_order_line_id IS NOT NULL
                        """)
                .param("invoice", invoiceId.get())
                .query((rs, n) -> {
                    var quantity = qtyByWebOrderLineId.get(rs.getLong("web_order_line_id"));
                    if (quantity != null) {
                        wanted.put(rs.getLong(ID), quantity);
                    }
                    return n;
                })
                .list();
        if (wanted.isEmpty()) {
            return null;
        }
        return create(invoiceId.get(), CreditNote.Reason.RETURN, null, actor, returnId, wanted, true);
    }

    private record Locked(long id, boolean cancelled) {}

    private record Source(
            long id,
            int position,
            String description,
            @Nullable String hsnCode,
            int quantity,
            BigDecimal unitRupees,
            BigDecimal gstRate,
            BigDecimal taxableRupees,
            BigDecimal cgstRupees,
            BigDecimal sgstRupees,
            BigDecimal igstRupees) {}

    private record Credited(
            long invoiceLineId,
            long quantity,
            BigDecimal taxableRupees,
            BigDecimal cgstRupees,
            BigDecimal sgstRupees,
            BigDecimal igstRupees) {}

    private record Part(
            Source line,
            int quantity,
            BigDecimal taxableRupees,
            BigDecimal cgstRupees,
            BigDecimal sgstRupees,
            BigDecimal igstRupees) {}

    /**
     * Credits {@code wanted} (invoice line id to quantity) or, when null, everything not credited yet. Returns null
     * when there is nothing to credit and the caller asked for "whatever remains" ({@code lenient}, or no lines).
     * A lenient call clamps quantities to what remains instead of failing.
     */
    private @Nullable CreditNote create(
            long invoiceId,
            CreditNote.Reason reason,
            @Nullable String note,
            @Nullable String actor,
            @Nullable Long returnId,
            @Nullable Map<Long, Integer> wanted,
            boolean lenient) {
        var locked = jdbc.sql("SELECT id, cancelled_at IS NOT NULL AS cancelled FROM invoice WHERE id = :id FOR UPDATE")
                .param(ID, invoiceId)
                .query(Locked.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        if (locked.cancelled()) {
            throw conflict("A cancelled invoice cannot be credited");
        }
        var sources = jdbc.sql("""
                        SELECT id, position, description, hsn_code, quantity, unit_rupees, gst_rate, taxable_rupees,
                               cgst_rupees, sgst_rupees, igst_rupees
                        FROM invoice_line WHERE invoice_id = :id ORDER BY position
                        """).param(ID, invoiceId).query(Source.class).list();
        Map<Long, Credited> credited = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT cl.invoice_line_id, SUM(cl.quantity) AS quantity, SUM(cl.taxable_rupees) AS taxable_rupees,
                               SUM(cl.cgst_rupees) AS cgst_rupees, SUM(cl.sgst_rupees) AS sgst_rupees,
                               SUM(cl.igst_rupees) AS igst_rupees
                        FROM credit_note_line cl JOIN credit_note c ON c.id = cl.credit_note_id
                        WHERE c.invoice_id = :id GROUP BY cl.invoice_line_id
                        """)
                .param(ID, invoiceId)
                .query(Credited.class)
                .list()
                .forEach(c -> credited.put(c.invoiceLineId(), c));
        if (wanted != null && !lenient) {
            var known = new HashSet<Long>();
            sources.forEach(s -> known.add(s.id()));
            for (var lineId : wanted.keySet()) {
                if (!known.contains(lineId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown invoice line");
                }
            }
        }
        var parts = new ArrayList<Part>();
        for (var source : sources) {
            var done = credited.get(source.id());
            int remaining = source.quantity() - (done == null ? 0 : (int) done.quantity());
            int quantity;
            if (wanted == null) {
                quantity = remaining;
            } else {
                var asked = wanted.get(source.id());
                if (asked == null) {
                    continue;
                }
                if (asked > remaining && !lenient) {
                    throw conflict(
                            remaining <= 0
                                    ? FULLY_CREDITED
                                    : "At most " + remaining + " of " + source.description()
                                            + " can still be credited");
                }
                quantity = Math.min(asked, remaining);
            }
            if (quantity > 0) {
                parts.add(part(source, done, quantity, remaining));
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        return insert(invoiceId, reason, note, actor, returnId, parts);
    }

    /** The credited share of a line: pro rata, or the exact remainder when it takes the last of the quantity. */
    private static Part part(Source line, @Nullable Credited done, int quantity, int remaining) {
        if (quantity == remaining) {
            return new Part(
                    line,
                    quantity,
                    line.taxableRupees().subtract(done == null ? Rupees.ZERO : done.taxableRupees()),
                    line.cgstRupees().subtract(done == null ? Rupees.ZERO : done.cgstRupees()),
                    line.sgstRupees().subtract(done == null ? Rupees.ZERO : done.sgstRupees()),
                    line.igstRupees().subtract(done == null ? Rupees.ZERO : done.igstRupees()));
        }
        return new Part(
                line,
                quantity,
                share(line.taxableRupees(), quantity, line.quantity()),
                share(line.cgstRupees(), quantity, line.quantity()),
                share(line.sgstRupees(), quantity, line.quantity()),
                share(line.igstRupees(), quantity, line.quantity()));
    }

    private static BigDecimal share(BigDecimal value, int quantity, int of) {
        return Rupees.divide(Rupees.times(value, quantity), BigDecimal.valueOf(of));
    }

    private CreditNote insert(
            long invoiceId,
            CreditNote.Reason reason,
            @Nullable String note,
            @Nullable String actor,
            @Nullable Long returnId,
            List<Part> parts) {
        var taxable = Rupees.sum(parts, Part::taxableRupees);
        var cgst = Rupees.sum(parts, Part::cgstRupees);
        var sgst = Rupees.sum(parts, Part::sgstRupees);
        var igst = Rupees.sum(parts, Part::igstRupees);
        var number = numbering.next(NumberSequence.Kind.CREDIT_NOTE);
        long id = jdbc.sql("""
                        INSERT INTO credit_note (number, invoice_id, reason, note, issued_on, web_order_return_id, title,
                            buyer_name, buyer_phone, buyer_address, buyer_state, seller_name, seller_address, seller_cin,
                            seller_state, seller_state_code, place_of_supply, supply_type, taxable_rupees, cgst_rupees,
                            sgst_rupees, igst_rupees, total_rupees, created_by)
                        SELECT :number, v.id, :reason, CAST(:note AS varchar), CAST(:issuedOn AS date),
                            CAST(:return AS bigint), 'Credit Note', v.buyer_name, v.buyer_phone, v.buyer_address,
                            v.buyer_state, v.seller_name, v.seller_address, v.seller_cin, v.seller_state,
                            v.seller_state_code, v.place_of_supply, v.supply_type, :taxable, :cgst, :sgst, :igst,
                            :total, CAST(:by AS varchar)
                        FROM invoice v WHERE v.id = :invoice
                        RETURNING id
                        """)
                .param("number", number)
                .param("reason", reason.name())
                .param("note", note == null || note.isBlank() ? null : note.trim())
                .param("issuedOn", InvoiceParties.today(clock))
                .param("return", returnId)
                .param("taxable", taxable)
                .param("cgst", cgst)
                .param("sgst", sgst)
                .param("igst", igst)
                .param("total", taxable.add(cgst).add(sgst).add(igst))
                .param("by", actor)
                .param("invoice", invoiceId)
                .query(Long.class)
                .single();
        int position = 0;
        for (var part : parts) {
            var line = part.line();
            jdbc.sql("""
                            INSERT INTO credit_note_line (credit_note_id, position, invoice_line_id, description,
                                hsn_code, quantity, unit_rupees, gst_rate, taxable_rupees, cgst_rupees, sgst_rupees,
                                igst_rupees, amount_rupees)
                            VALUES (:note, :position, :line, :description, :hsn, :quantity, :unit, :rate, :taxable,
                                :cgst, :sgst, :igst, :amount)
                            """)
                    .param("note", id)
                    .param("position", ++position)
                    .param("line", line.id())
                    .param("description", line.description())
                    .param("hsn", line.hsnCode())
                    .param("quantity", part.quantity())
                    .param("unit", line.unitRupees())
                    .param("rate", line.gstRate())
                    .param("taxable", part.taxableRupees())
                    .param("cgst", part.cgstRupees())
                    .param("sgst", part.sgstRupees())
                    .param("igst", part.igstRupees())
                    .param(
                            "amount",
                            part.taxableRupees()
                                    .add(part.cgstRupees())
                                    .add(part.sgstRupees())
                                    .add(part.igstRupees()))
                    .update();
        }
        return get(id);
    }

    // --- reading ------------------------------------------------------------------------------------------------

    private record Row(
            long id,
            String number,
            long invoiceId,
            String invoiceNumber,
            String orderNumber,
            CreditNote.Reason reason,
            @Nullable String note,
            LocalDate issuedOn,
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
            @Nullable String placeOfSupply,
            String supplyType,
            BigDecimal taxableRupees,
            BigDecimal cgstRupees,
            BigDecimal sgstRupees,
            BigDecimal igstRupees,
            BigDecimal totalRupees,
            @Nullable String createdBy,
            OffsetDateTime createdAt) {}

    @Transactional(readOnly = true)
    public CreditNote get(long id) {
        var row = jdbc.sql("""
                        SELECT c.id, c.number, c.invoice_id, v.number AS invoice_number,
                               COALESCE(o.number, w.number) AS order_number, c.reason, c.note, c.issued_on, c.title,
                               c.buyer_name, c.buyer_phone, c.buyer_address, c.buyer_state, c.seller_name,
                               c.seller_address, c.seller_cin, c.seller_state, c.seller_state_code, c.place_of_supply,
                               c.supply_type, c.taxable_rupees, c.cgst_rupees, c.sgst_rupees, c.igst_rupees,
                               c.total_rupees, c.created_by, c.created_at
                        FROM credit_note c
                        JOIN invoice v ON v.id = c.invoice_id
                        LEFT JOIN shop_order o ON o.id = v.order_id
                        LEFT JOIN web_order w ON w.id = v.web_order_id
                        WHERE c.id = :id
                        """)
                .param(ID, id)
                .query(Row.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Credit note not found"));
        var lines = jdbc.sql("""
                        SELECT id, invoice_line_id, description, hsn_code, quantity, unit_rupees, gst_rate,
                               taxable_rupees, cgst_rupees, sgst_rupees, igst_rupees, amount_rupees
                        FROM credit_note_line WHERE credit_note_id = :id ORDER BY position
                        """).param(ID, id).query(CreditNoteLine.class).list();
        return new CreditNote(
                row.id(),
                row.number(),
                row.invoiceId(),
                row.invoiceNumber(),
                row.orderNumber(),
                row.reason(),
                row.note(),
                row.issuedOn(),
                row.title(),
                new InvoiceParties.Seller(
                        row.sellerName(),
                        row.sellerAddress(),
                        row.sellerCin(),
                        row.sellerState(),
                        row.sellerStateCode()),
                new InvoiceParties.Buyer(row.buyerName(), row.buyerPhone(), row.buyerAddress(), row.buyerState()),
                row.placeOfSupply(),
                row.supplyType(),
                lines,
                row.taxableRupees(),
                row.cgstRupees(),
                row.sgstRupees(),
                row.igstRupees(),
                row.totalRupees(),
                row.createdBy(),
                row.createdAt());
    }

    /** The credit note register, newest first. */
    @Transactional(readOnly = true)
    public CreditNotePage list(@Nullable String q, int page, int size) {
        var like = InvoiceService.like(q);
        long total = jdbc.sql("""
                        SELECT COUNT(*) FROM credit_note c JOIN invoice v ON v.id = c.invoice_id
                        WHERE CAST(:q AS varchar) IS NULL OR lower(c.number) LIKE :q OR lower(v.number) LIKE :q
                           OR lower(c.buyer_name) LIKE :q
                        """).param("q", like).query(Long.class).single();
        var items = jdbc.sql("""
                        SELECT c.id, c.number, c.invoice_id, v.number AS invoice_number, c.buyer_name, c.issued_on,
                               c.total_rupees, c.reason
                        FROM credit_note c JOIN invoice v ON v.id = c.invoice_id
                        WHERE CAST(:q AS varchar) IS NULL OR lower(c.number) LIKE :q OR lower(v.number) LIKE :q
                           OR lower(c.buyer_name) LIKE :q
                        ORDER BY c.issued_on DESC, c.id DESC LIMIT :limit OFFSET :offset
                        """)
                .param("q", like)
                .param("limit", size)
                .param("offset", (page - 1) * size)
                .query(CreditNotePage.Item.class)
                .list();
        return new CreditNotePage(items, total, page, size);
    }

    @Transactional(readOnly = true)
    public List<CreditNoteSummary> forInvoice(long invoiceId) {
        return jdbc.sql("""
                        SELECT id, number, issued_on, total_rupees, reason FROM credit_note
                        WHERE invoice_id = :invoice ORDER BY id
                        """)
                .param("invoice", invoiceId)
                .query(CreditNoteSummary.class)
                .list();
    }

    /** The credit notes on one of this customer's own web orders, by order number. */
    @Transactional(readOnly = true)
    public List<StoreCreditNote> forCustomer(String orderNumber, long customerId) {
        return jdbc
                .sql("""
                        SELECT c.id FROM credit_note c
                        JOIN invoice v ON v.id = c.invoice_id
                        JOIN web_order w ON w.id = v.web_order_id
                        WHERE w.number = :number AND w.customer_id = :customer
                        ORDER BY c.id
                        """)
                .param("number", orderNumber)
                .param("customer", customerId)
                .query(Long.class)
                .list()
                .stream()
                .map(this::get)
                .map(note -> new StoreCreditNote(
                        note.number(),
                        note.title(),
                        note.reason(),
                        note.issuedOn(),
                        note.invoiceNumber(),
                        note.orderNumber(),
                        note.seller(),
                        note.buyer(),
                        note.lines(),
                        note.taxableRupees(),
                        note.cgstRupees(),
                        note.sgstRupees(),
                        note.igstRupees(),
                        note.placeOfSupply(),
                        note.supplyType(),
                        note.totalRupees()))
                .toList();
    }

    /** True when the invoice has at least one credit note. */
    @Transactional(readOnly = true)
    public boolean hasNotes(long invoiceId) {
        return Boolean.TRUE.equals(jdbc.sql("SELECT EXISTS (SELECT 1 FROM credit_note WHERE invoice_id = :invoice)")
                .param("invoice", invoiceId)
                .query(Boolean.class)
                .single());
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
