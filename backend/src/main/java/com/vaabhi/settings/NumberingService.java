package com.vaabhi.settings;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ID engine: every human-facing number (orders, invoices, credit notes, web orders, stock and product codes)
 * comes from here. A sequence can restart each financial year; {FY} in its prefix or suffix prints the year.
 */
@Service
public class NumberingService {

    /**
     * @param period the financial year next_value belongs to ("" for sequences that never restart)
     */
    private record Row(
            NumberSequence.Kind kind,
            String prefix,
            long nextValue,
            String suffix,
            int pad,
            NumberSequence.Reset reset,
            String period) {

        /** The value the next number gets in {@code year}: a new financial year restarts a yearly series. */
        long valueIn(String year) {
            return reset == NumberSequence.Reset.FY && !period.equals(year) ? 1 : nextValue;
        }

        NumberSequence toSequence(String year) {
            long value = valueIn(year);
            return new NumberSequence(
                    kind, prefix, value, suffix, pad, reset, NumberSequence.format(prefix, value, suffix, pad, year));
        }
    }

    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Kolkata");

    private static final String COLUMNS = "kind, prefix, next_value, suffix, pad, reset, period";

    private final JdbcClient jdbc;
    private final Clock clock;

    NumberingService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private String year() {
        // The shop's date, not the server's: 1 April starts at midnight IST, five and a half hours before UTC.
        return NumberSequence.financialYear(LocalDate.now(clock.withZone(SHOP_ZONE)));
    }

    @Transactional(readOnly = true)
    public List<NumberSequence> list() {
        var year = year();
        return jdbc.sql("SELECT " + COLUMNS + " FROM number_sequence ORDER BY kind").query(Row.class).list().stream()
                .map(row -> row.toSequence(year))
                .toList();
    }

    /**
     * Takes the next number for a new document. Runs in the caller's transaction, so a failed create does not
     * use up a number (tax documents stay gapless); the row lock serialises concurrent creates.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(NumberSequence.Kind kind) {
        return take(kind, year());
    }

    /**
     * Like {@link #next(NumberSequence.Kind)}, but prints the financial year of {@code on} (an entry dated in another
     * year). The value still comes from the one shared counter, which follows today's financial year, so posting into a
     * past year never disturbs the current year's count.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(NumberSequence.Kind kind, LocalDate on) {
        return take(kind, NumberSequence.financialYear(on));
    }

    private String take(NumberSequence.Kind kind, String printedYear) {
        var year = year();
        var row = jdbc.sql("SELECT " + COLUMNS + " FROM number_sequence WHERE kind = :kind FOR UPDATE")
                .param("kind", kind.name())
                .query(Row.class)
                .single();
        long value = row.valueIn(year);
        jdbc.sql("UPDATE number_sequence SET next_value = :next, period = :period WHERE kind = :kind")
                .param("next", value + 1)
                .param("period", row.reset() == NumberSequence.Reset.FY ? year : "")
                .param("kind", kind.name())
                .update();
        return NumberSequence.format(row.prefix(), value, row.suffix(), row.pad(), printedYear);
    }

    @Transactional
    List<NumberSequence> update(List<NumberSequence> sequences) {
        var year = year();
        for (var sequence : sequences) {
            var reset = sequence.reset() == null ? NumberSequence.Reset.NEVER : sequence.reset();
            jdbc.sql("""
                            UPDATE number_sequence SET prefix = :prefix, next_value = :next, suffix = :suffix,
                                pad = :pad, reset = :reset, period = :period
                            WHERE kind = :kind
                            """)
                    .param("prefix", sequence.prefix())
                    .param("next", sequence.nextValue())
                    .param("suffix", sequence.suffix())
                    .param("pad", sequence.pad())
                    .param("reset", reset.name())
                    // The value typed is for this year.
                    .param("period", reset == NumberSequence.Reset.FY ? year : "")
                    .param("kind", sequence.kind().name())
                    .update();
        }
        return list();
    }
}
