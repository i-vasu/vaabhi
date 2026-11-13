package com.vaabhi.accounts;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** The books start date and the payment mode to account map. */
@Service
public class BooksSettingsService {

    /** The payment modes whose money lands in an account, in display order. */
    static final List<String> MODES = List.of("CASH", "UPI", "CARD", "BANK", "RAZORPAY", "COD");

    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;
    private final DepreciationScheduler depreciation;

    BooksSettingsService(
            JdbcClient jdbc, ApplicationEventPublisher events, @Lazy DepreciationScheduler depreciation) {
        this.jdbc = jdbc;
        this.events = events;
        this.depreciation = depreciation;
    }

    record SettingsRow(
            @Nullable LocalDate startDate,
            @Nullable OffsetDateTime lastSweepAt,
            @Nullable Long shiprocketPartyId) {}

    record ModeRow(String mode, long accountId) {}

    @Transactional(readOnly = true)
    public BooksSettings get() {
        var row = jdbc.sql("SELECT start_date, last_sweep_at, shiprocket_party_id FROM books_setting WHERE id = 1")
                .query(SettingsRow.class)
                .single();
        var stored = jdbc.sql("""
                        SELECT key AS mode, value::bigint AS account_id
                        FROM books_setting, jsonb_each_text(mode_accounts) WHERE id = 1
                        """).query(ModeRow.class).list().stream()
                .collect(Collectors.toMap(ModeRow::mode, ModeRow::accountId));
        var modeAccounts = new LinkedHashMap<String, Long>();
        for (var mode : MODES) {
            var accountId = stored.get(mode);
            if (accountId != null) {
                modeAccounts.put(mode, accountId);
            }
        }
        return new BooksSettings(row.startDate(), modeAccounts, row.lastSweepAt(), row.shiprocketPartyId());
    }

    /** Saves the mode map and start date. A missing start date keeps the current one. */
    @Transactional
    public BooksSettings save(BooksSettings settings, @Nullable String actor) {
        var before = get();
        var submitted = settings.modeAccounts();
        for (var key : submitted.keySet()) {
            if (!MODES.contains(key)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown payment mode " + key);
            }
        }
        var json = new StringBuilder("{");
        for (var mode : MODES) {
            var accountId = submitted.get(mode);
            if (accountId == null || !isActiveAsset(accountId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick an asset account for " + mode);
            }
            if (json.length() > 1) {
                json.append(',');
            }
            json.append('"').append(mode).append("\":").append(accountId);
        }
        json.append('}');
        var startDate = settings.startDate() == null ? before.startDate() : settings.startDate();
        if (before.startDate() != null && !before.startDate().equals(startDate) && hasPostedEntries()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "The books start date can't change once entries are posted");
        }
        var shiprocketPartyId = settings.shiprocketPartyId();
        if (shiprocketPartyId != null && !isSupplier(shiprocketPartyId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a supplier for Shiprocket");
        }
        jdbc.sql("""
                        UPDATE books_setting
                        SET start_date = :startDate, mode_accounts = CAST(:modes AS jsonb),
                            shiprocket_party_id = :shiprocket
                        WHERE id = 1
                        """)
                .param("startDate", startDate)
                .param("modes", json.toString())
                .param("shiprocket", shiprocketPartyId)
                .update();
        if (before.startDate() == null && startDate != null) {
            events.publishEvent(new BooksStarted(startDate));
        }
        if (!Objects.equals(before.startDate(), startDate)) {
            depreciation.refreshAll();
        }
        return get();
    }

    /** The account a payment mode's money lands in. */
    @Transactional(readOnly = true)
    public long accountFor(String mode) {
        return jdbc.sql("SELECT CAST(mode_accounts ->> :mode AS bigint) FROM books_setting WHERE id = 1")
                .param("mode", mode)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No account set for " + mode));
    }

    /** The party Shiprocket is a supplier under, or null when it is not set. */
    @Transactional(readOnly = true)
    public @Nullable Long shiprocketPartyId() {
        // Through the settings row: a single-column query of a NULL value does not map cleanly to Optional.
        return get().shiprocketPartyId();
    }

    @Transactional
    public void markSwept(OffsetDateTime at) {
        jdbc.sql("UPDATE books_setting SET last_sweep_at = :at WHERE id = 1")
                .param("at", at)
                .update();
    }

    private boolean hasPostedEntries() {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM journal_entry WHERE source_type <> 'OPENING')")
                .query(Boolean.class)
                .single();
    }

    private boolean isActiveAsset(long accountId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM account WHERE id = :id AND type = 'ASSET' AND active)")
                .param("id", accountId)
                .query(Boolean.class)
                .single();
    }

    private boolean isSupplier(long partyId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM party WHERE id = :id AND is_supplier)")
                .param("id", partyId)
                .query(Boolean.class)
                .single();
    }
}
