package com.vaabhi.purchases;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** What the admin types for a supplier. */
public record SupplierForm(
        @NotBlank @Size(max = 160) String name,
        @Nullable @Size(max = 20) String phone,
        @Nullable @Email @Size(max = 254) String email,
        @Nullable @Pattern(regexp = "[0-9A-Z]{15}") String gstin,
        @Nullable @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]") String pan,
        @Nullable @Size(max = 300) String address,
        @Nullable @Size(max = 80) String city,
        @Nullable @Size(max = 80) String state,
        @Nullable @Pattern(regexp = "[0-9]{6}") String pinCode,
        @Nullable Long tdsSectionId) {}
