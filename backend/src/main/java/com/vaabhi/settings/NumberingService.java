package com.vaabhi.settings;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Document numbers (orders, invoices, alterations, item codes). */
@Service
public class NumberingService {

    private record Row(NumberSequence.Kind kind, String prefix, long nextValue, String suffix, int pad) {

        NumberSequence toSequence() {
            return new NumberSequence(
                    kind, prefix, nextValue, suffix, pad, NumberSequence.format(prefix, nextValue, suffix, pad));
        }
    }

    private final JdbcClient jdbc;

    NumberingService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<NumberSequence> list() {
        return jdbc
                .sql("SELECT kind, prefix, next_value, suffix, pad FROM number_sequence ORDER BY kind")
                .query(Row.class)
                .list()
                .stream()
                .map(Row::toSequence)
                .toList();
    }

    /**
     * Takes the next number for a new document. Runs in the caller's transaction, so a failed create does not
     * use up a number; the row lock serialises concurrent creates.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(NumberSequence.Kind kind) {
        return jdbc.sql("""
                        UPDATE number_sequence SET next_value = next_value + 1 WHERE kind = :kind
                        RETURNING kind, prefix, next_value - 1 AS next_value, suffix, pad
                        """)
                .param("kind", kind.name())
                .query(Row.class)
                .single()
                .toSequence()
                .preview();
    }

    @Transactional
    List<NumberSequence> update(List<NumberSequence> sequences) {
        for (var sequence : sequences) {
            jdbc.sql("""
                            UPDATE number_sequence SET prefix = :prefix, next_value = :next, suffix = :suffix, pad = :pad
                            WHERE kind = :kind
                            """)
                    .param("prefix", sequence.prefix())
                    .param("next", sequence.nextValue())
                    .param("suffix", sequence.suffix())
                    .param("pad", sequence.pad())
                    .param("kind", sequence.kind().name())
                    .update();
        }
        return list();
    }
}
