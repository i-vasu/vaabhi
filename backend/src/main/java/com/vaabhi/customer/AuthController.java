package com.vaabhi.customer;

import com.vaabhi.customer.AuthRequests.GoogleSignIn;
import com.vaabhi.customer.AuthRequests.OtpRequest;
import com.vaabhi.customer.AuthRequests.OtpVerification;
import com.vaabhi.customer.AuthRequests.RefreshRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public: customer sign-in (mobile number + OTP, or Google), session refresh and logout. Called by the
 * storefront's server, which keeps the returned tokens in httpOnly cookies.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthService service;

    AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/otp/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    OtpSent requestOtp(@Valid @RequestBody OtpRequest request) {
        return service.requestOtp(request.phone());
    }

    @PostMapping("/otp/verify")
    AuthSession verifyOtp(@Valid @RequestBody OtpVerification request) {
        return service.verifyOtp(request.phone(), request.code());
    }

    @PostMapping("/google")
    AuthSession signInWithGoogle(@Valid @RequestBody GoogleSignIn request) {
        return service.signInWithGoogle(request.credential());
    }

    @PostMapping("/refresh")
    AuthSession refreshSession(@Valid @RequestBody RefreshRequest request) {
        return service.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshRequest request) {
        service.logout(request.refreshToken());
    }
}
