package com.vaabhi.purchases;

import com.vaabhi.party.PartyService;
import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Suppliers: the parties the shop buys from, what their open bills owe and what has been paid, and each supplier's
 * statement. Reads the bill and payment tables by SQL; nothing here posts to the journal.
 */
@Service
public class SupplierService {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    /** Every supplier with their open bills and payments rolled up; callers add their own filter and order. */
    private static final String BALANCES = """
            SELECT p.id AS party_id, p.name, p.phone, p.gstin, p.state,
                   COALESCE(b.billed_rupees, 0) AS billed_rupees,
                   COALESCE(y.paid_rupees, 0) AS paid_rupees,
                   COALESCE(b.tds_rupees, 0) AS tds_rupees,
                   COALESCE(b.payable_rupees, 0) - COALESCE(y.paid_rupees, 0) AS balance_rupees,
                   b.oldest_unpaid_due
            FROM party p
            LEFT JOIN (
                SELECT sb.party_id,
                       SUM(sb.total_rupees) AS billed_rupees,
                       SUM(sb.tds_rupees) AS tds_rupees,
                       SUM(sb.payable_rupees) AS payable_rupees,
                       MIN(CASE WHEN COALESCE(al.allocated_rupees, 0) < sb.payable_rupees
                                THEN COALESCE(sb.due_date, sb.bill_date) END) AS oldest_unpaid_due
                FROM supplier_bill sb
                LEFT JOIN (SELECT bill_id, SUM(amount_rupees) AS allocated_rupees
                           FROM supplier_payment_allocation GROUP BY bill_id) al ON al.bill_id = sb.id
                WHERE sb.status = 'OPEN'
                GROUP BY sb.party_id
            ) b ON b.party_id = p.id
            LEFT JOIN (SELECT party_id, SUM(amount_rupees) AS paid_rupees
                       FROM supplier_payment GROUP BY party_id) y ON y.party_id = p.id
            WHERE p.is_supplier
            """;

    private final JdbcClient jdbc;
    private final PartyService parties;

    SupplierService(JdbcClient jdbc, PartyService parties) {
        this.jdbc = jdbc;
        this.parties = parties;
    }

    private record BalanceRow(
            long partyId,
            String name,
            @Nullable String phone,
            @Nullable String gstin,
            @Nullable String state,
            BigDecimal billedRupees,
            BigDecimal paidRupees,
            BigDecimal tdsRupees,
            BigDecimal balanceRupees,
            @Nullable LocalDate oldestUnpaidDue) {

        SupplierBalance toBalance(LocalDate today) {
            return new SupplierBalance(
                    partyId,
                    name,
                    phone,
                    gstin,
                    state,
                    Rupees.of(billedRupees),
                    Rupees.of(paidRupees),
                    Rupees.of(tdsRupees),
                    Rupees.of(balanceRupees),
                    oldestUnpaidDue,
                    oldestUnpaidDue != null && oldestUnpaidDue.isBefore(today));
        }
    }

    private record PartyRow(
            @Nullable String email,
            @Nullable String pan,
            @Nullable String address,
            @Nullable String city,
            @Nullable String pinCode,
            @Nullable Long tdsSectionId) {}

    private record EntryRow(
            LocalDate date,
            String kind,
            long id,
            String reference,
            BigDecimal billedRupees,
            BigDecimal paidRupees) {}

    /** Suppliers whose name, phone or GSTIN contains {@code query} (all when it is null), largest balance first. */
    @Transactional(readOnly = true)
    public List<SupplierBalance> list(@Nullable String query) {
        var needle = query == null || query.isBlank() ? null : query.trim();
        var today = LocalDate.now(INDIA);
        return jdbc.sql(BALANCES + """
                        AND (CAST(:q AS text) IS NULL
                             OR p.name ILIKE '%' || CAST(:q AS text) || '%'
                             OR p.phone ILIKE '%' || CAST(:q AS text) || '%'
                             OR p.gstin ILIKE '%' || CAST(:q AS text) || '%')
                        ORDER BY balance_rupees DESC, p.name
                        """)
                .param("q", needle)
                .query(BalanceRow.class)
                .list()
                .stream()
                .map(row -> row.toBalance(today))
                .toList();
    }

    /** A supplier's details and their open bills and payments, oldest first. */
    @Transactional(readOnly = true)
    public SupplierStatement statement(long partyId) {
        var party = jdbc.sql("""
                        SELECT email, pan, address, city, pin_code, tds_section_id
                        FROM party WHERE id = :id AND is_supplier
                        """)
                .param("id", partyId)
                .query(PartyRow.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such supplier"));
        var today = LocalDate.now(INDIA);
        var balance = jdbc.sql(BALANCES + " AND p.id = :id")
                .param("id", partyId)
                .query(BalanceRow.class)
                .single()
                .toBalance(today);
        var rows = jdbc.sql("""
                        SELECT bill_date AS date, 'BILL' AS kind, id, bill_no AS reference,
                               payable_rupees AS billed_rupees, CAST(0 AS numeric) AS paid_rupees
                        FROM supplier_bill WHERE party_id = :party AND status = 'OPEN'
                        UNION ALL
                        SELECT paid_on, 'PAYMENT', id, COALESCE(reference, 'Payment #' || id),
                               CAST(0 AS numeric), amount_rupees
                        FROM supplier_payment WHERE party_id = :party
                        ORDER BY date, kind, id
                        """)
                .param("party", partyId)
                .query(EntryRow.class)
                .list();
        var running = Rupees.ZERO;
        var entries = new ArrayList<SupplierStatementEntry>(rows.size());
        for (var row : rows) {
            var billed = Rupees.of(row.billedRupees());
            var paid = Rupees.of(row.paidRupees());
            running = running.add(billed).subtract(paid);
            entries.add(new SupplierStatementEntry(
                    row.date(), row.kind(), row.id(), row.reference(), billed, paid, Rupees.of(running)));
        }
        return new SupplierStatement(
                balance,
                party.email(),
                party.pan(),
                party.address(),
                party.city(),
                party.pinCode(),
                party.tdsSectionId(),
                entries);
    }

    /**
     * Creates the supplier or turns an existing party with the same phone (or, without a phone, the same supplier
     * name) into one, then stores the PAN and TDS section the party record has no form fields for.
     */
    @Transactional
    public long create(SupplierForm form) {
        checkTdsSection(form.tdsSectionId());
        long id = parties.save(
                null,
                new PartyService.Details(
                        form.name(),
                        form.phone(),
                        form.email(),
                        form.gstin(),
                        form.address(),
                        form.city(),
                        form.state(),
                        form.pinCode()),
                PartyService.Role.SUPPLIER);
        jdbc.sql("UPDATE party SET pan = :pan, tds_section_id = :tds WHERE id = :id")
                .param("pan", blankToNull(form.pan()))
                .param("tds", form.tdsSectionId())
                .param("id", id)
                .update();
        return id;
    }

    /** Overwrites the supplier's details as typed; blanks clear the stored value. */
    @Transactional
    public void update(long partyId, SupplierForm form) {
        requireSupplier(partyId);
        checkTdsSection(form.tdsSectionId());
        try {
            jdbc.sql("""
                            UPDATE party SET name = :name, phone = :phone, email = :email, gstin = :gstin,
                                             pan = :pan, address = :address, city = :city, state = :state,
                                             pin_code = :pinCode, tds_section_id = :tds
                            WHERE id = :id
                            """)
                    .param("name", form.name().trim())
                    .param("phone", PartyService.phone(form.phone()))
                    .param("email", blankToNull(form.email()))
                    .param("gstin", blankToNull(form.gstin()))
                    .param("pan", blankToNull(form.pan()))
                    .param("address", blankToNull(form.address()))
                    .param("city", blankToNull(form.city()))
                    .param("state", blankToNull(form.state()))
                    .param("pinCode", blankToNull(form.pinCode()))
                    .param("tds", form.tdsSectionId())
                    .param("id", partyId)
                    .update();
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another party already has this phone");
        }
    }

    private void requireSupplier(long partyId) {
        boolean supplier = jdbc.sql("SELECT EXISTS (SELECT 1 FROM party WHERE id = :id AND is_supplier)")
                .param("id", partyId)
                .query(Boolean.class)
                .single();
        if (!supplier) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such supplier");
        }
    }

    private void checkTdsSection(@Nullable Long tdsSectionId) {
        if (tdsSectionId == null) {
            return;
        }
        boolean active = jdbc.sql("SELECT EXISTS (SELECT 1 FROM tds_section WHERE id = :id AND active)")
                .param("id", tdsSectionId)
                .query(Boolean.class)
                .single();
        if (!active) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a TDS section");
        }
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
