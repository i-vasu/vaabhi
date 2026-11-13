package com.vaabhi.purchases;

import com.vaabhi.settings.IndianStates;
import com.vaabhi.settings.SettingsService;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * The company's GST state code: the billing company's state when it is filled in, otherwise the shop profile's,
 * looked up in the Indian states list. Null when neither names a state the list knows.
 */
@Component
public class CompanyState {

    private final SettingsService settings;

    CompanyState(SettingsService settings) {
        this.settings = settings;
    }

    public @Nullable String code() {
        var state = blankToNull(settings.billing().company().state());
        if (state == null) {
            state = blankToNull(settings.shopProfile().state());
        }
        return state == null ? null : IndianStates.code(state).orElse(null);
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
