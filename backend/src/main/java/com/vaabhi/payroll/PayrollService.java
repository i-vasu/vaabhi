package com.vaabhi.payroll;

import com.vaabhi.money.ExpenseCategory;
import com.vaabhi.money.ExpenseForm;
import com.vaabhi.money.ExpenseService;
import com.vaabhi.shared.rupees.Rupees;
import com.vaabhi.staff.AttendanceService;
import com.vaabhi.staff.MonthAttendance;
import com.vaabhi.staff.PayType;
import com.vaabhi.staff.RequestService;
import com.vaabhi.staff.StaffMember;
import com.vaabhi.staff.StaffRequest;
import com.vaabhi.staff.StaffService;
import com.vaabhi.tailoring.PaymentMode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Monthly pay. Until a month is finalised its payslips are a preview worked out from attendance, the production
 * ledger and approved advances; finalising freezes them and takes the advances back. Paying a payslip records a
 * salary expense in the ledger.
 *
 * <p>Monthly salary is reduced only by ABSENT and LEAVE days (and half of HALF_DAY): days nobody marked are paid,
 * so a shop that does not keep attendance still pays full salaries. Daily wages are paid for days marked worked.
 * Piece workers are paid the price list's current piece rate for each work stage they finished.
 */
@Service
public class PayrollService {

    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final DateTimeFormatter MONTH_NAME = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final JdbcClient jdbc;
    private final StaffService staff;
    private final AttendanceService attendance;
    private final RequestService requests;
    private final ExpenseService expenses;
    private final Clock clock;

    PayrollService(
            JdbcClient jdbc,
            StaffService staff,
            AttendanceService attendance,
            RequestService requests,
            ExpenseService expenses,
            Clock clock) {
        this.jdbc = jdbc;
        this.staff = staff;
        this.attendance = attendance;
        this.requests = requests;
        this.expenses = expenses;
        this.clock = clock;
    }

    // --- the arithmetic -----------------------------------------------------------------------------------------

    /** What one person earned in a month, from their attendance row (null: nothing marked), pieces and advances. */
    static Payslip compute(
            StaffMember member,
            YearMonth month,
            MonthAttendance.@Nullable Row days,
            int pieces,
            BigDecimal pieceRupees,
            BigDecimal advanceRupees) {
        int length = month.lengthOfMonth();
        var half = BigDecimal.valueOf(days == null ? 0 : days.halfDays()).multiply(HALF);
        var unpaid = BigDecimal.valueOf(days == null ? 0 : days.absent() + days.leave())
                .add(half);
        var worked = BigDecimal.valueOf(days == null ? 0 : days.present()).add(half);
        BigDecimal paidDays;
        BigDecimal base;
        var rate = Rupees.of(member.salaryRupees());
        switch (member.payType()) {
            case MONTHLY -> {
                paidDays = BigDecimal.valueOf(length).subtract(unpaid).max(BigDecimal.ZERO);
                base = rate.multiply(paidDays).divide(BigDecimal.valueOf(length), Rupees.SCALE, RoundingMode.HALF_UP);
            }
            case DAILY -> {
                paidDays = worked;
                base = Rupees.times(rate, paidDays);
            }
            default -> {
                paidDays = worked;
                base = Rupees.ZERO;
                rate = Rupees.ZERO;
            }
        }
        var piece = member.payType() == PayType.PIECE ? Rupees.of(pieceRupees) : Rupees.ZERO;
        int overtimeMinutes = days == null ? 0 : days.overtimeMinutes();
        int lateMinutes = days == null ? 0 : days.lateMinutes();
        var overtime = perMinute(overtimeMinutes, member.hourlyRupees());
        var late = perMinute(lateMinutes, member.hourlyRupees());
        return new Payslip(
                null,
                member.id(),
                member.name(),
                member.designation(),
                month.atDay(1),
                member.payType(),
                rate,
                paidDays.setScale(1, RoundingMode.UNNECESSARY),
                unpaid.setScale(1, RoundingMode.UNNECESSARY),
                base,
                piece,
                pieces,
                overtimeMinutes,
                overtime,
                lateMinutes,
                late,
                advanceRupees,
                base.add(piece).add(overtime).subtract(late).subtract(advanceRupees),
                null,
                null);
    }

    private static BigDecimal perMinute(int minutes, BigDecimal hourlyRupees) {
        return hourlyRupees
                .multiply(BigDecimal.valueOf(minutes))
                .divide(BigDecimal.valueOf(60), Rupees.SCALE, RoundingMode.HALF_UP);
    }

    // --- production ledger --------------------------------------------------------------------------------------

    /**
     * Work stages finished, priced at the price list's current piece rates. An item moved back and finished again
     * counts once per stage (the last time).
     */
    @Transactional(readOnly = true)
    public ProductionLedger production(LocalDate from, LocalDate to, @Nullable Long staffId) {
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The end date is before the start date");
        }
        var zone = clock.getZone();
        var entries = jdbc.sql("""
                        SELECT * FROM (
                            SELECT DISTINCT ON (l.item_id, l.from_stage_id)
                                   l.moved_at AS at, l.staff_id, s.name AS staff_name, o.id AS order_id,
                                   o.number AS order_number, i.id AS item_id, i.description, i.quantity,
                                   st.id AS stage_id, st.name AS stage_name,
                                   COALESCE((SELECT (c ->> 'rupees')::numeric FROM jsonb_array_elements(p.stage_costs) c
                                             WHERE (c ->> 'stageId')::bigint = l.from_stage_id), 0) AS rate_rupees
                            FROM order_item_stage_log l
                            JOIN order_item i ON i.id = l.item_id
                            JOIN shop_order o ON o.id = i.order_id
                            JOIN staff_account s ON s.id = l.staff_id
                            JOIN production_stage st ON st.id = l.from_stage_id
                            LEFT JOIN service_product p ON p.id = i.service_product_id
                            WHERE l.staff_id IS NOT NULL AND st.kind = 'WORK' AND o.status <> 'CANCELLED'
                            ORDER BY l.item_id, l.from_stage_id, l.moved_at DESC
                        ) e
                        WHERE e.at >= :start AND e.at < :end AND (CAST(:staff AS bigint) IS NULL OR e.staff_id = :staff)
                        ORDER BY e.at DESC
                        """)
                .param("start", from.atStartOfDay(zone).toOffsetDateTime())
                .param("end", to.plusDays(1).atStartOfDay(zone).toOffsetDateTime())
                .param("staff", staffId)
                .query((rs, n) -> new ProductionEntry(
                        rs.getObject("at", OffsetDateTime.class),
                        rs.getLong("staff_id"),
                        rs.getString("staff_name"),
                        rs.getLong("order_id"),
                        rs.getString("order_number"),
                        rs.getLong("item_id"),
                        rs.getString("description"),
                        rs.getInt("quantity"),
                        rs.getLong("stage_id"),
                        rs.getString("stage_name"),
                        Rupees.of(rs.getBigDecimal("rate_rupees")),
                        Rupees.times(rs.getBigDecimal("rate_rupees"), rs.getInt("quantity"))))
                .list();
        Map<Long, Integer> counts = new LinkedHashMap<>();
        Map<Long, BigDecimal> amounts = new HashMap<>();
        Map<Long, String> names = new HashMap<>();
        for (var e : entries) {
            counts.merge(e.staffId(), e.quantity(), Integer::sum);
            amounts.merge(e.staffId(), e.amountRupees(), BigDecimal::add);
            names.put(e.staffId(), e.staffName());
        }
        var byStaff = counts.entrySet().stream()
                .map(t -> new ProductionLedger.StaffPieces(
                        t.getKey(), names.get(t.getKey()), t.getValue(), Rupees.of(amounts.get(t.getKey()))))
                .sorted(Comparator.comparing(ProductionLedger.StaffPieces::amountRupees)
                        .reversed()
                        .thenComparing(ProductionLedger.StaffPieces::name))
                .toList();
        return new ProductionLedger(from, to, entries, byStaff);
    }

    // --- payroll ------------------------------------------------------------------------------------------------

    private List<Payslip> preview(YearMonth month) {
        Map<Long, MonthAttendance.Row> days = new HashMap<>();
        attendance.month(month).rows().forEach(row -> days.put(row.staffId(), row));
        Map<Long, ProductionLedger.StaffPieces> pieces = new HashMap<>();
        production(month.atDay(1), month.atEndOfMonth(), null).byStaff().forEach(p -> pieces.put(p.staffId(), p));
        var advances = requests.unrecoveredAdvances(month.atEndOfMonth());
        return staff.list(false).stream()
                .map(member -> {
                    var p = pieces.get(member.id());
                    return compute(
                            member,
                            month,
                            days.get(member.id()),
                            p == null ? 0 : p.pieces(),
                            p == null ? Rupees.ZERO : p.amountRupees(),
                            advances.getOrDefault(member.id(), Rupees.ZERO));
                })
                .toList();
    }

    private static final String SLIPS = """
            SELECT p.id, p.staff_id, s.name, s.designation, p.month, p.pay_type, p.rate_rupees, p.days_paid,
                   p.days_unpaid, p.base_rupees, p.piece_rupees, p.pieces, p.overtime_minutes, p.overtime_rupees,
                   p.late_minutes, p.late_rupees, p.advance_rupees, p.net_rupees, p.paid_at, p.paid_mode
            FROM payslip p JOIN staff_account s ON s.id = p.staff_id
            """;

    private List<Payslip> stored(LocalDate month) {
        return jdbc.sql(SLIPS + " WHERE p.month = :month ORDER BY lower(s.name), s.id")
                .param("month", month)
                .query(Payslip.class)
                .list();
    }

    @Transactional(readOnly = true)
    public PayrollMonth month(YearMonth month) {
        var slips = stored(month.atDay(1));
        boolean finalized = !slips.isEmpty();
        if (!finalized) {
            slips = preview(month);
        }
        var total = Rupees.sum(slips, Payslip::netRupees);
        var paid = Rupees.sum(slips.stream().filter(s -> s.paidAt() != null).toList(), Payslip::netRupees);
        return new PayrollMonth(
                month.atDay(1), finalized, slips, total, paid, finalized ? total.subtract(paid) : Rupees.ZERO);
    }

    /** Freezes the month's payslips and takes back the advances in them. Months still to come cannot be closed. */
    @Transactional
    public PayrollMonth finalizeMonth(YearMonth month, @Nullable String actor) {
        if (month.isAfter(YearMonth.now(clock))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That month has not started yet");
        }
        if (!stored(month.atDay(1)).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This month is already finalised");
        }
        var slips = preview(month);
        for (var s : slips) {
            jdbc.sql("""
                            INSERT INTO payslip (staff_id, month, pay_type, rate_rupees, days_paid, days_unpaid,
                                base_rupees, piece_rupees, pieces, overtime_minutes, overtime_rupees, late_minutes,
                                late_rupees, advance_rupees, net_rupees, finalized_by)
                            VALUES (:staff, :month, :payType, :rate, :daysPaid, :daysUnpaid, :base, :piece, :pieces,
                                :otMinutes, :ot, :lateMinutes, :late, :advance, :net, :by)
                            """)
                    .param("staff", s.staffId())
                    .param("month", s.month())
                    .param("payType", s.payType().name())
                    .param("rate", s.rateRupees())
                    .param("daysPaid", s.daysPaid())
                    .param("daysUnpaid", s.daysUnpaid())
                    .param("base", s.baseRupees())
                    .param("piece", s.pieceRupees())
                    .param("pieces", s.pieces())
                    .param("otMinutes", s.overtimeMinutes())
                    .param("ot", s.overtimeRupees())
                    .param("lateMinutes", s.lateMinutes())
                    .param("late", s.lateRupees())
                    .param("advance", s.advanceRupees())
                    .param("net", s.netRupees())
                    .param("by", actor)
                    .update();
        }
        requests.markAdvancesRecovered(
                slips.stream()
                        .filter(s -> s.advanceRupees().signum() > 0)
                        .map(Payslip::staffId)
                        .toList(),
                month.atEndOfMonth(),
                month.atDay(1));
        return month(month);
    }

    /** Throws the month's payslips away so it can be worked out again; not once any of them was paid. */
    @Transactional
    public PayrollMonth reopen(YearMonth month) {
        var slips = stored(month.atDay(1));
        if (slips.stream().anyMatch(s -> s.paidAt() != null)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Some payslips of this month were already paid");
        }
        jdbc.sql("DELETE FROM payslip WHERE month = :month")
                .param("month", month.atDay(1))
                .update();
        requests.releaseAdvances(month.atDay(1));
        return month(month);
    }

    /** Marks a finalised payslip paid and records it as a salary expense. */
    @Transactional
    public Payslip pay(long payslipId, PaymentMode mode, @Nullable String actor) {
        var slip = jdbc.sql(SLIPS + " WHERE p.id = :id")
                .param("id", payslipId)
                .query(Payslip.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payslip not found"));
        if (slip.paidAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This payslip was already paid");
        }
        Long expenseId = null;
        if (slip.netRupees().signum() > 0) {
            var description = "Salary — " + slip.name() + ", " + slip.month().format(MONTH_NAME);
            expenseId = expenses.create(
                            new ExpenseForm(
                                    LocalDate.now(clock),
                                    ExpenseCategory.SALARY,
                                    description.length() > 200 ? description.substring(0, 200) : description,
                                    slip.netRupees(),
                                    mode,
                                    slip.name()),
                            actor)
                    .id();
        }
        jdbc.sql("UPDATE payslip SET paid_at = now(), paid_mode = :mode, expense_id = :expense WHERE id = :id")
                .param("mode", mode.name())
                .param("expense", expenseId)
                .param("id", payslipId)
                .update();
        return jdbc.sql(SLIPS + " WHERE p.id = :id")
                .param("id", payslipId)
                .query(Payslip.class)
                .single();
    }

    // --- statement ----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public StaffStatement statement(long staffId) {
        var member = staff.get(staffId);
        var entries = new ArrayList<StaffStatement.Entry>();
        record Movement(LocalDate date, StaffStatement.Kind kind, String description, BigDecimal amount) {}
        var moves = new ArrayList<Movement>();
        var slips = jdbc.sql(SLIPS + " WHERE p.staff_id = :staff ORDER BY p.month")
                .param("staff", staffId)
                .query(Payslip.class)
                .list();
        for (var s : slips) {
            var month = YearMonth.from(s.month());
            moves.add(new Movement(
                    month.atEndOfMonth(),
                    StaffStatement.Kind.EARNED,
                    "Pay for " + s.month().format(MONTH_NAME),
                    s.netRupees().add(s.advanceRupees())));
            if (s.paidAt() != null && s.netRupees().signum() > 0) {
                moves.add(new Movement(
                        s.paidAt().atZoneSameInstant(clock.getZone()).toLocalDate(),
                        StaffStatement.Kind.PAID,
                        "Paid for " + s.month().format(MONTH_NAME),
                        s.netRupees().negate()));
            }
        }
        for (var r : requests.list(StaffRequest.Status.APPROVED, staffId)) {
            if (r.kind() == StaffRequest.Kind.ADVANCE && r.amountRupees() != null) {
                moves.add(new Movement(
                        r.fromDate(),
                        StaffStatement.Kind.ADVANCE,
                        r.reason() == null ? "Advance" : "Advance: " + r.reason(),
                        r.amountRupees().negate()));
            }
        }
        moves.sort(
                Comparator.comparing(Movement::date).thenComparing(m -> m.kind().ordinal()));
        var balance = Rupees.ZERO;
        for (var m : moves) {
            balance = balance.add(m.amount());
            entries.add(new StaffStatement.Entry(m.date(), m.kind(), m.description(), m.amount(), balance));
        }
        return new StaffStatement(member.id(), member.name(), entries, balance);
    }
}
