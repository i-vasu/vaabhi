package com.vaabhi.checkout;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Checkout and order history for the signed-in customer (CUSTOMER role). */
@RestController
@RequestMapping("/api/v1/customer")
class CheckoutController {

    private final CheckoutService service;

    CheckoutController(CheckoutService service) {
        this.service = service;
    }

    /** Places the order and returns what Razorpay Checkout needs to take the payment. */
    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    CheckoutStart startCheckout(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CheckoutRequest request) {
        return service.start(customerId(jwt), request);
    }

    /** Confirms the payment Razorpay Checkout returned (signature checked on the server). */
    @PostMapping("/orders/{number}/payment")
    WebOrder confirmPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("number") String number,
            @Valid @RequestBody PaymentConfirmation payment) {
        return service.confirm(customerId(jwt), number, payment);
    }

    @GetMapping("/orders")
    List<WebOrder> listMyOrders(@AuthenticationPrincipal Jwt jwt) {
        return service.orders(customerId(jwt));
    }

    @GetMapping("/orders/{number}")
    WebOrder getMyOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable("number") String number) {
        return service.order(customerId(jwt), number);
    }

    private static long customerId(Jwt jwt) {
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again");
        }
    }
}
