package com.vaabhi.settings;

/** Which alerts reach the shop's devices. Applies to the whole shop, the owner included. */
public record NotificationSettings(
        boolean newOrder,
        boolean orderReady,
        boolean orderDelivered,
        boolean paymentReceived,
        boolean dataExported,
        boolean morningSchedule,
        boolean dailySummary) {

    static NotificationSettings defaults() {
        return new NotificationSettings(true, true, true, true, false, false, false);
    }
}
