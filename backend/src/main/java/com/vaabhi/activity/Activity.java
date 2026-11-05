package com.vaabhi.activity;

import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Activity and notification records. */
public final class Activity {

    private Activity() {}

    /**
     * Something that happened.
     *
     * @param kind e.g. ORDER_PLACED, PAYMENT_RECEIVED, ALTERATION_READY, SETTINGS_CHANGED
     * @param refType what it is about (ORDER, ALTERATION, SETTINGS), for a link
     */
    public record ActivityEntry(
            long id,
            OffsetDateTime at,
            String kind,
            String title,
            @Nullable String actor,
            @Nullable String refType,
            @Nullable Long refId) {}

    /** A page of the activity log, newest first. */
    public record ActivityPage(List<ActivityEntry> items, long total, int page, int pageSize) {}

    /**
     * Something needing attention now.
     *
     * @param href the dashboard page that lists them
     */
    public record Alert(String kind, String title, int count, String href) {}

    /**
     * What the bell shows.
     *
     * @param unread recent entries newer than when the person last looked
     */
    public record Notifications(List<Alert> alerts, List<ActivityEntry> recent, int unread) {}
}
