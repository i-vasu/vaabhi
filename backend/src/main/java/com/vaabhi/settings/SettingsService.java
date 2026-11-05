package com.vaabhi.settings;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and saves the JSON settings sections. Other modules read settings through the typed getters, e.g.
 * {@code settings.billing().gstApplies()}; unsaved sections return their defaults.
 */
@Service
public class SettingsService {

    /** A section: its storage key, wording for the activity log and defaults. */
    record Section<T>(String key, String title, Class<T> type, Supplier<T> defaults) {}

    static final Section<ShopProfile> SHOP_PROFILE =
            new Section<>("shop-profile", "Updated the shop profile", ShopProfile.class, ShopProfile::defaults);
    static final Section<BillingSettings> BILLING =
            new Section<>("billing", "Updated billing and tax", BillingSettings.class, BillingSettings::defaults);
    static final Section<DocumentSettings> DOCUMENTS = new Section<>(
            "documents", "Updated bank and document details", DocumentSettings.class, DocumentSettings::defaults);
    static final Section<OrderSettings> ORDERS =
            new Section<>("orders", "Updated order settings", OrderSettings.class, OrderSettings::defaults);
    static final Section<CalendarSettings> CALENDAR = new Section<>(
            "calendar", "Updated holidays and weekly off", CalendarSettings.class, CalendarSettings::defaults);
    static final Section<NotificationSettings> NOTIFICATIONS = new Section<>(
            "notifications",
            "Updated notification settings",
            NotificationSettings.class,
            NotificationSettings::defaults);
    static final Section<PrintSettings> PRINTING =
            new Section<>("printing", "Updated print settings", PrintSettings.class, PrintSettings::defaults);
    static final Section<MessageTemplates> MESSAGES =
            new Section<>("messages", "Updated message templates", MessageTemplates.class, MessageTemplates::defaults);
    static final Section<WatermarkSettings> WATERMARK = new Section<>(
            "watermark", "Updated the gallery watermark", WatermarkSettings.class, WatermarkSettings::defaults);

    private final SettingsStore store;
    private final ApplicationEventPublisher events;

    SettingsService(SettingsStore store, ApplicationEventPublisher events) {
        this.store = store;
        this.events = events;
    }

    @Transactional(readOnly = true)
    <T> T get(Section<T> section) {
        return store.find(section.key(), section.type()).orElseGet(section.defaults());
    }

    @Transactional
    <T> T save(Section<T> section, T value, @Nullable String actor) {
        store.save(section.key(), value, actor);
        events.publishEvent(new SettingsChanged(section.key(), section.title(), actor));
        return value;
    }

    public ShopProfile shopProfile() {
        return get(SHOP_PROFILE);
    }

    public BillingSettings billing() {
        return get(BILLING);
    }

    public DocumentSettings documents() {
        return get(DOCUMENTS);
    }

    public OrderSettings orders() {
        return get(ORDERS);
    }

    public CalendarSettings calendar() {
        return get(CALENDAR);
    }

    public NotificationSettings notifications() {
        return get(NOTIFICATIONS);
    }

    public PrintSettings printing() {
        return get(PRINTING);
    }

    public MessageTemplates messages() {
        return get(MESSAGES);
    }

    public WatermarkSettings watermark() {
        return get(WATERMARK);
    }
}
