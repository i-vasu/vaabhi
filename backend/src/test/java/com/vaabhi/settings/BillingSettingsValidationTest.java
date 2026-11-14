package com.vaabhi.settings;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BillingSettingsValidationTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    private BillingSettings withCin(String cin) {
        return new BillingSettings(
                false,
                null,
                new BigDecimal("5"),
                true,
                false,
                BillingSettings.DiscountBase.ORDER_TOTAL,
                true,
                new BillingSettings.Company(null, cin, null, null),
                null);
    }

    @Test
    void acceptsA21CharacterCin() {
        assertThat(validator.validate(withCin("U74999TG2026OPC123456"))).isEmpty();
    }

    @Test
    void rejectsAShortCin() {
        assertThat(validator.validate(withCin("U74999TG26OPC123456"))).isNotEmpty();
    }

    @Test
    void missingCompanyBecomesAnEmptyOne() {
        assertThat(BillingSettings.defaults().company()).isEqualTo(new BillingSettings.Company(null, null, null, null));
    }
}
