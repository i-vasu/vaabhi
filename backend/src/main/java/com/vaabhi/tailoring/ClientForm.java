package com.vaabhi.tailoring;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Add or edit a shop customer. Only the mobile number and first name are required. */
public record ClientForm(
        @NotBlank @Size(max = 20) String phone,
        @NotBlank @Size(max = 60) String firstName,
        @Nullable @Size(max = 60) String lastName,
        @NotNull Gender gender,
        @Nullable @Size(max = 200) String photoKey,
        @Nullable @Email @Size(max = 254) String email,
        @Nullable @Size(max = 20) String altPhone,
        @Nullable @Size(max = 20) String whatsapp,
        @Nullable @Size(max = 300) String address,
        @Nullable @Size(max = 80) String city,
        @Nullable @Size(max = 80) String state,

        @Nullable @Pattern(regexp = "^[1-9][0-9]{5}$", message = "must be a 6-digit PIN code")
        String pinCode,

        @NotNull ClientType clientType,

        @Nullable
        @Pattern(regexp = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$", message = "must be a 15-character GSTIN")
        String gstin,

        @Nullable @Size(max = 1000) String preferredStyle,
        @Nullable LocalDate birthDate,
        @Nullable @Size(max = 60) String occasionName,
        @Nullable LocalDate occasionDate) {

    public enum Gender {
        FEMALE,
        MALE,
        OTHER
    }

    public enum ClientType {
        RETAIL,
        WHOLESALE,
        VIP
    }
}
