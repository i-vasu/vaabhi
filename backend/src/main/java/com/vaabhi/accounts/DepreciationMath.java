package com.vaabhi.accounts;

import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The straight-line depreciation schedule of one asset. The first and the disposal month are charged pro rata by day;
 * the total never exceeds cost less the residual value, and opening accumulated depreciation counts toward that cap.
 */
public final class DepreciationMath {

    public record MonthCharge(YearMonth month, BigDecimal amount) {}

    private DepreciationMath() {}

    /** The charge for each month from {@code max(putToUse, booksStart)} up to {@code throughMonth}. */
    public static List<MonthCharge> schedule(
            BigDecimal cost,
            BigDecimal residualPercent,
            int lifeYears,
            BigDecimal openingAccdep,
            LocalDate putToUse,
            LocalDate booksStart,
            YearMonth throughMonth,
            @Nullable LocalDate disposedOn) {
        var from = putToUse.isAfter(booksStart) ? putToUse : booksStart;
        var firstMonth = YearMonth.from(from);
        var lastMonth = throughMonth;
        if (disposedOn != null && YearMonth.from(disposedOn).isBefore(lastMonth)) {
            lastMonth = YearMonth.from(disposedOn);
        }
        if (lastMonth.isBefore(firstMonth) || (disposedOn != null && disposedOn.isBefore(from))) {
            return List.of();
        }
        var residual = Rupees.percentOf(cost, residualPercent);
        var depreciable = cost.subtract(residual);
        var fullMonth = Rupees.divide(depreciable, BigDecimal.valueOf((long) lifeYears * 12));
        var running = Rupees.of(openingAccdep);
        var charges = new ArrayList<MonthCharge>();
        for (var month = firstMonth; !month.isAfter(lastMonth); month = month.plusMonths(1)) {
            var start = month.equals(firstMonth) ? from : month.atDay(1);
            var end = month.atEndOfMonth();
            if (disposedOn != null && month.equals(YearMonth.from(disposedOn))) {
                end = disposedOn;
            }
            var days = end.toEpochDay() - start.toEpochDay() + 1;
            var charge = days == month.lengthOfMonth()
                    ? fullMonth
                    : Rupees.of(
                            fullMonth.multiply(BigDecimal.valueOf(days))
                                    .divide(
                                            BigDecimal.valueOf(month.lengthOfMonth()),
                                            Rupees.SCALE,
                                            RoundingMode.HALF_UP));
            var remaining = depreciable.subtract(running);
            if (remaining.signum() <= 0) {
                break;
            }
            if (charge.compareTo(remaining) > 0) {
                charge = Rupees.of(remaining);
            }
            if (charge.signum() <= 0) {
                break;
            }
            charges.add(new MonthCharge(month, charge));
            running = running.add(charge);
        }
        return List.copyOf(charges);
    }
}
