package com.vaabhi.accounts;

import java.time.LocalDate;
import java.util.List;

/** Turns one kind of source record into a journal entry. The sweeper runs the rules in {@link #order()}. */
public interface PostingRule {

    String sourceType();

    String step();

    /** Lower runs first within one sweep. */
    int order();

    /** Ids of source rows dated on or after {@code start} that have no entry for this type and step yet. */
    List<Long> pending(LocalDate start, int limit);

    NewEntry build(long sourceId);

    /** Called in the posting transaction after the entry is saved; for side effects such as linking a bank line. */
    default void posted(long sourceId, long entryId) {}
}
