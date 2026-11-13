package com.vaabhi.checkout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The bag as the browser keeps it, where to deliver, and how the customer pays (ONLINE when not given). Prices
 * are never taken from the browser.
 */
public record CheckoutRequest(
        @NotNull @Size(min = 1, max = 20) List<@Valid @NotNull Line> lines,
        @Valid @NotNull Address address,
        WebOrder.@Nullable PaymentMethod paymentMethod,
        @Nullable @Size(max = 40) String couponCode) {

    WebOrder.PaymentMethod method() {
        return paymentMethod == null ? WebOrder.PaymentMethod.ONLINE : paymentMethod;
    }

    public record Line(
            @NotBlank @Size(max = 64) String sku,
            @Nullable @Size(max = 40) String addonCode,
            @Nullable @Size(max = 10) String blouseSize,
            @Min(1) @Max(3) int quantity) {}

    public record Address(
            @NotBlank @Size(max = 80) String name,

            @NotBlank @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Enter a 10-digit mobile number")
            String phone,

            @NotBlank @Size(max = 160) String line1,
            @Nullable @Size(max = 160) String line2,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 80) String state,

            @NotBlank @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Enter a 6-digit pincode")
            String pincode) {}
}
