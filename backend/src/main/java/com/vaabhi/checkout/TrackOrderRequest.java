package com.vaabhi.checkout;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A guest looking up an order.
 *
 * @param number the order number from the confirmation, e.g. {@code WEB-1042} (the {@code WEB-} part is optional)
 * @param phone the mobile number the order was placed with, with or without {@code +91}
 */
public record TrackOrderRequest(
        @NotBlank(message = "Enter your order number") @Size(max = 32) String number,
        @NotBlank(message = "Enter your mobile number") @Size(max = 20) String phone) {}
