package com.vaabhi.checkout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The bag and a coupon code, to price the code against the bag before checkout. */
public record CouponQuoteRequest(
        @NotNull @Size(min = 1, max = 100) List<CheckoutRequest.@Valid Line> lines,
        @NotBlank @Size(max = 40) String code) {}
