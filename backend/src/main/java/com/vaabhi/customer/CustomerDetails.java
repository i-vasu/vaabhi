package com.vaabhi.customer;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What the customer keeps on their account page besides name, mobile and email.
 *
 * @param measurements garment template id → field key → value as typed (e.g. {@code "36"}); the fields come from
 *     the shop's garment templates (Settings → Garments), served to the storefront as measurement forms
 */
public record CustomerDetails(
        @Nullable @Past(message = "must be in the past") LocalDate dateOfBirth,
        @Nullable @Size(max = 200) String addressLine1,
        @Nullable @Size(max = 200) String addressLine2,
        @Nullable @Size(max = 80) String city,
        @Nullable @Size(max = 80) String state,

        @Nullable @Pattern(regexp = "^[1-9][0-9]{5}$", message = "must be a 6-digit PIN code")
        String pincode,

        @Size(max = 20, message = "too many garments")
        Map<
                        @Pattern(regexp = "^[0-9]{1,9}$") String,
                        @Size(max = 40) Map<@Pattern(regexp = "^[a-z][a-z0-9_]{0,39}$") String, @Size(max = 20) String>>
                measurements) {

    public CustomerDetails {
        measurements = measurements == null ? Map.of() : measurements;
    }
}
