package com.vaabhi.settings;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;

/**
 * Who the shop is: printed on bills and shared with customers.
 *
 * @param businessType filters garment templates on a new order
 * @param logoKey object key of the uploaded logo (see the settings image upload)
 * @param country ISO 3166 code; decides the tax system (GST for IN)
 * @param opensAt alerts are held outside shop hours and delivered when the shop next opens
 */
public record ShopProfile(
        @NotBlank @Size(max = 120) String ownerName,
        @NotBlank @Size(max = 120) String shopName,
        @NotNull BusinessType businessType,

        @Nullable @Pattern(regexp = PHONE, message = "must be a phone number")
        String phone,

        @Nullable @Email @Size(max = 254) String publicEmail,

        @Nullable @Size(max = 300) @Pattern(regexp = HTTPS, message = HTTPS_MESSAGE)
        String googleReviewLink,

        @Nullable @Size(max = 300) @Pattern(regexp = "^https?://.*", message = "must be a web address")
        String website,

        @Nullable @Size(max = 300) @Pattern(regexp = HTTPS, message = HTTPS_MESSAGE)
        String mapsLink,

        @Nullable @Size(max = 300) @Pattern(regexp = HTTPS, message = HTTPS_MESSAGE)
        String instagram,

        @Nullable @Size(max = 300) @Pattern(regexp = HTTPS, message = HTTPS_MESSAGE)
        String facebook,

        @Nullable @Size(max = 300) @Pattern(regexp = HTTPS, message = HTTPS_MESSAGE)
        String youtube,

        @Nullable @Size(max = 300) @Pattern(regexp = HTTPS, message = HTTPS_MESSAGE)
        String threads,

        @Nullable @Size(max = 300) String address,
        @Nullable @Size(max = 40) String doorNo,
        @Nullable @Size(max = 120) String street,
        @Nullable @Size(max = 120) String area,
        @Nullable @Size(max = 80) String city,
        @Nullable @Size(max = 80) String district,
        @Nullable @Size(max = 80) String state,

        @NotBlank @Pattern(regexp = "^[A-Z]{2}$", message = "must be a two-letter country code")
        String country,

        @Nullable @Pattern(regexp = "^[1-9][0-9]{5}$", message = "must be a 6-digit PIN code")
        String pinCode,

        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be a currency code")
        String currency,

        @NotNull LocalTime opensAt,
        @NotNull LocalTime closesAt,
        @Nullable @Size(max = 200) String logoKey) {

    static final String PHONE = "^\\+?[0-9 ]{7,16}$";
    static final String HTTPS = "^https://.*";
    static final String HTTPS_MESSAGE = "must start with https://";

    public enum BusinessType {
        LADIES_BOUTIQUE,
        GENTS_TAILORING,
        UNISEX_TAILORING,
        KIDS_WEAR,
        DESIGNER_STUDIO
    }

    static ShopProfile defaults() {
        return new ShopProfile(
                "Owner",
                "vaabhi",
                BusinessType.LADIES_BOUTIQUE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "IN",
                null,
                "INR",
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                null);
    }
}
