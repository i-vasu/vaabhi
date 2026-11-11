package com.vaabhi.money;

import com.vaabhi.settings.BillingSettings;
import com.vaabhi.settings.IndianStates;
import com.vaabhi.settings.ShopProfile;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.jspecify.annotations.Nullable;

/** Builds the frozen seller, buyer and heading an invoice carries, so later settings changes never rewrite it. */
final class InvoiceParties {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private InvoiceParties() {}

    /** Who sold: the company when its details are filled in, otherwise the shop. */
    public record Seller(
            String name,
            @Nullable String address,
            @Nullable String cin,
            @Nullable String state,
            @Nullable String stateCode) {}

    /** Who bought, as known when the invoice was issued. */
    public record Buyer(
            String name, @Nullable String phone, @Nullable String address, @Nullable String state) {}

    static Seller seller(ShopProfile profile, BillingSettings billing) {
        var company = billing.company();
        var name = blankToNull(company.legalName());
        var address = blankToNull(company.registeredOffice());
        var state = blankToNull(company.state());
        if (state == null) {
            state = blankToNull(profile.state());
        }
        return new Seller(
                name != null ? name : profile.shopName(),
                address != null ? address : blankToNull(profile.address()),
                blankToNull(company.cin()),
                state,
                state == null ? null : IndianStates.code(state).orElse(null));
    }

    static String title(BillingSettings billing) {
        return billing.gstApplies() ? "Tax Invoice" : "Invoice";
    }

    /** Today's date in India, which is what an invoice is dated by. */
    static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(INDIA));
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
