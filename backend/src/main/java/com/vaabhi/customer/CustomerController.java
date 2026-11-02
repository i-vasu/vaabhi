package com.vaabhi.customer;

import com.vaabhi.customer.AuthRequests.ProfileUpdate;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in customer (role CUSTOMER; the token's subject is the customer id). */
@RestController
@RequestMapping("/api/v1/customer")
class CustomerController {

    private final CustomerService service;

    CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping("/me")
    CustomerProfile getProfile(@AuthenticationPrincipal Jwt jwt) {
        return service.profile(customerId(jwt));
    }

    @PutMapping("/me")
    CustomerProfile updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileUpdate update) {
        return service.updateName(customerId(jwt), update.name());
    }

    private static long customerId(Jwt jwt) {
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw AuthException.unauthorized("Please sign in again");
        }
    }
}
