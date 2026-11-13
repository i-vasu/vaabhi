package com.vaabhi.purchases;

import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Payments to suppliers: one amount spread over their open bills, oldest due first unless the admin picks the bills.
 * What is left unallocated is an advance. A payment can also be made from an unmatched money-out bank line. Nothing
 * here posts to the journal.
 */
@Service
public class SupplierPaymentService {

    private static final Set<String> MODES = Set.of("CASH", "UPI", "CARD", "BANK");

    private static final String PAYMENT = """
            SELECT p.id, p.party_id, s.name AS supplier_name, p.paid_on, p.mode, p.amount_rupees,
                   COALESCE(a.allocated_rupees, 0) AS allocated_rupees, p.reference, p.bank_line_id
            FROM supplier_payment p
            JOIN party s ON s.id = p.party_id
            LEFT JOIN (SELECT payment_id, SUM(amount_rupees) AS allocated_rupees
                       FROM supplier_payment_allocation GROUP BY payment_id) a ON a.payment_id = p.id
            """;

    /** A bill with what its allocations still leave to pay. */
    private static final String BILL = """
            SELECT b.id, b.party_id, b.status, b.bill_date, b.payable_rupees,
                   COALESCE((SELECT SUM(al.amount_rupees)
                             FROM supplier_payment_allocation al WHERE al.bill_id = b.id), 0) AS allocated_rupees
            FROM supplier_bill b
            WHERE b.id = :id
            """;

    private final JdbcClient jdbc;
    private final BooksPostedCheck posted;

    SupplierPaymentService(JdbcClient jdbc, BooksPostedCheck posted) {
        this.jdbc = jdbc;
        this.posted = posted;
    }

    private record PaymentRow(
            long id,
            long partyId,
            String supplierName,
            LocalDate paidOn,
            String mode,
            BigDecimal amountRupees,
            BigDecimal allocatedRupees,
            @Nullable String reference,
            @Nullable Long bankLineId) {}

    private record PayableBill(
            long id,
            long partyId,
            String status,
            LocalDate billDate,
            BigDecimal payableRupees,
            BigDecimal allocatedRupees) {}

    private record OutstandingBill(long id, BigDecimal outstandingRupees) {}

    private record BankLineRow(
            long id,
            LocalDate lineDate,
            BigDecimal debitRupees,
            @Nullable String reference,
            String status) {}

    private record Allocation(long billId, BigDecimal amountRupees) {}

    /** Records a payment and spreads it over bills, oldest due first when the admin names no bills. */
    @Transactional
    public SupplierPaymentView create(SupplierPaymentForm form, @Nullable String actor) {
        var partyId = requireSupplier(form.partyId());
        var mode = mode(form.mode());
        var amount = Rupees.of(form.amountRupees());
        var allocations = allocations(partyId, amount, form.allocations());
        return insert(
                partyId,
                form.paidOn(),
                mode,
                amount,
                blankToNull(form.reference()),
                blankToNull(form.notes()),
                null,
                allocations,
                actor);
    }

    /** Pays whatever is left on one bill, dated the bill's date. */
    @Transactional
    public SupplierPaymentView payBill(long billId, String mode, @Nullable String reference, @Nullable String actor) {
        var bill = payableBill(billId);
        var paidMode = mode(mode);
        if (!"OPEN".equals(bill.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That bill is cancelled");
        }
        var outstanding = Rupees.of(bill.payableRupees().subtract(bill.allocatedRupees()));
        if (outstanding.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nothing left to pay on this bill");
        }
        return insert(
                bill.partyId(),
                bill.billDate(),
                paidMode,
                outstanding,
                blankToNull(reference),
                null,
                null,
                List.of(new Allocation(billId, outstanding)),
                actor);
    }

    /** Turns an unmatched money-out bank line into a payment for its amount, dated the line. */
    @Transactional
    public SupplierPaymentView fromBankLine(BankLinePaymentForm form, @Nullable String actor) {
        var line = jdbc.sql("SELECT id, line_date, debit_rupees, reference, status FROM bank_line WHERE id = :id")
                .param("id", form.bankLineId())
                .query(BankLineRow.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bank line not found"));
        boolean used = jdbc.sql("SELECT EXISTS (SELECT 1 FROM supplier_payment WHERE bank_line_id = :id)")
                .param("id", line.id())
                .query(Boolean.class)
                .single();
        if (used || !"UNMATCHED".equals(line.status()) || line.debitRupees().signum() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only an unmatched money-out line can become a supplier payment");
        }
        var partyId = requireSupplier(form.partyId());
        var amount = Rupees.of(line.debitRupees());
        var allocations = allocations(partyId, amount, form.allocations());
        return insert(
                partyId,
                line.lineDate(),
                "BANK",
                amount,
                blankToNull(line.reference()),
                null,
                line.id(),
                allocations,
                actor);
    }

    /** Payments newest first, optionally of one supplier. */
    @Transactional(readOnly = true)
    public List<SupplierPaymentView> list(@Nullable Long partyId) {
        return jdbc.sql(PAYMENT + """
                        WHERE (CAST(:party AS bigint) IS NULL OR p.party_id = :party)
                        ORDER BY p.paid_on DESC, p.id DESC
                        """)
                .param("party", partyId)
                .query(PaymentRow.class)
                .list()
                .stream()
                .map(this::toView)
                .toList();
    }

    /** Removes a payment the books do not hold yet; allocations go with it. */
    @Transactional
    public void delete(long id) {
        boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM supplier_payment WHERE id = :id)")
                .param("id", id)
                .query(Boolean.class)
                .single();
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
        }
        if (posted.posted("SUPPLIER_PAYMENT", id, "PAYMENT")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This payment is in the books; reverse it instead");
        }
        jdbc.sql("DELETE FROM supplier_payment WHERE id = :id").param("id", id).update();
    }

    private SupplierPaymentView view(long id) {
        var row = jdbc.sql(PAYMENT + " WHERE p.id = :id")
                .param("id", id)
                .query(PaymentRow.class)
                .single();
        return toView(row);
    }

    private SupplierPaymentView toView(PaymentRow row) {
        var allocations = jdbc.sql("""
                        SELECT al.bill_id, b.bill_no, al.amount_rupees
                        FROM supplier_payment_allocation al
                        JOIN supplier_bill b ON b.id = al.bill_id
                        WHERE al.payment_id = :payment
                        ORDER BY b.bill_date, b.id
                        """)
                .param("payment", row.id())
                .query(SupplierPaymentView.PaymentAllocationView.class)
                .list();
        return new SupplierPaymentView(
                row.id(),
                row.partyId(),
                row.supplierName(),
                row.paidOn(),
                row.mode(),
                Rupees.of(row.amountRupees()),
                Rupees.of(row.allocatedRupees()),
                row.reference(),
                row.bankLineId(),
                posted.posted("SUPPLIER_PAYMENT", row.id(), "PAYMENT"),
                allocations);
    }

    private SupplierPaymentView insert(
            long partyId,
            LocalDate paidOn,
            String mode,
            BigDecimal amount,
            @Nullable String reference,
            @Nullable String notes,
            @Nullable Long bankLineId,
            List<Allocation> allocations,
            @Nullable String actor) {
        long id = jdbc.sql("""
                        INSERT INTO supplier_payment (party_id, paid_on, mode, amount_rupees, reference, notes,
                                                      bank_line_id, created_by)
                        VALUES (:party, :paidOn, :mode, :amount, :reference, :notes, :bankLine, :by)
                        RETURNING id
                        """)
                .param("party", partyId)
                .param("paidOn", paidOn)
                .param("mode", mode)
                .param("amount", amount)
                .param("reference", reference)
                .param("notes", notes)
                .param("bankLine", bankLineId)
                .param("by", actor)
                .query(Long.class)
                .single();
        for (var allocation : allocations) {
            jdbc.sql("""
                            INSERT INTO supplier_payment_allocation (payment_id, bill_id, amount_rupees)
                            VALUES (:payment, :bill, :amount)
                            """)
                    .param("payment", id)
                    .param("bill", allocation.billId())
                    .param("amount", allocation.amountRupees())
                    .update();
        }
        return view(id);
    }

    private PayableBill payableBill(long id) {
        return jdbc.sql(BILL)
                .param("id", id)
                .query(PayableBill.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bill not found"));
    }

    private List<Allocation> allocations(
            long partyId, BigDecimal amount, @Nullable List<SupplierPaymentForm.PaymentAllocationInput> input) {
        return input == null ? autoAllocate(partyId, amount) : explicit(partyId, amount, input);
    }

    /** Bills the admin named, checked so the payment cannot save a paisa more than it has or than a bill owes. */
    private List<Allocation> explicit(
            long partyId, BigDecimal amount, List<SupplierPaymentForm.PaymentAllocationInput> input) {
        var seen = new HashSet<Long>();
        var allocations = new ArrayList<Allocation>(input.size());
        var total = Rupees.ZERO;
        for (var item : input) {
            if (!seen.add(item.billId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each bill once");
            }
            var bill = payableBill(item.billId());
            if (bill.partyId() != partyId) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That bill is for another supplier");
            }
            if (!"OPEN".equals(bill.status())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That bill is cancelled");
            }
            var outstanding = Rupees.of(bill.payableRupees().subtract(bill.allocatedRupees()));
            var allocated = Rupees.of(item.amountRupees());
            if (allocated.compareTo(outstanding) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "More than this bill's balance");
            }
            total = total.add(allocated);
            allocations.add(new Allocation(item.billId(), allocated));
        }
        if (total.compareTo(amount) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Allocated more than paid");
        }
        return allocations;
    }

    /** The supplier's open bills with money left, oldest due first, each up to its outstanding. */
    private List<Allocation> autoAllocate(long partyId, BigDecimal amount) {
        var bills = jdbc.sql("""
                        SELECT b.id, b.payable_rupees - COALESCE(a.allocated_rupees, 0) AS outstanding_rupees
                        FROM supplier_bill b
                        LEFT JOIN (SELECT bill_id, SUM(amount_rupees) AS allocated_rupees
                                   FROM supplier_payment_allocation GROUP BY bill_id) a ON a.bill_id = b.id
                        WHERE b.party_id = :party AND b.status = 'OPEN'
                          AND b.payable_rupees - COALESCE(a.allocated_rupees, 0) > 0
                        ORDER BY COALESCE(b.due_date, b.bill_date), b.bill_date, b.id
                        """)
                .param("party", partyId)
                .query(OutstandingBill.class)
                .list();
        var allocations = new ArrayList<Allocation>(bills.size());
        var remaining = amount;
        for (var bill : bills) {
            if (remaining.signum() <= 0) {
                break;
            }
            var take = Rupees.of(Rupees.min(bill.outstandingRupees(), remaining));
            allocations.add(new Allocation(bill.id(), take));
            remaining = remaining.subtract(take);
        }
        return allocations;
    }

    private long requireSupplier(@Nullable Long partyId) {
        boolean supplier = partyId != null
                && jdbc.sql("SELECT EXISTS (SELECT 1 FROM party WHERE id = :id AND is_supplier)")
                        .param("id", partyId)
                        .query(Boolean.class)
                        .single();
        if (!supplier) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a supplier");
        }
        return partyId;
    }

    private static String mode(@Nullable String mode) {
        var value = blankToNull(mode);
        if (value == null || !MODES.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick how it was paid");
        }
        return value;
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
