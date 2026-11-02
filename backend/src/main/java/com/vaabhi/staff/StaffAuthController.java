package com.vaabhi.staff;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Staff sign-in for the admin dashboard. */
@RestController
class StaffAuthController {

    private final StaffAuthService service;

    StaffAuthController(StaffAuthService service) {
        this.service = service;
    }

    /** Public (see SecurityConfig): the caller has no token yet. */
    @PostMapping("/api/v1/auth/staff/login")
    StaffSession staffLogin(@Valid @RequestBody StaffLoginRequest request) {
        return service.login(request);
    }

    @GetMapping("/api/v1/admin/me")
    StaffProfile currentStaff(@AuthenticationPrincipal Jwt jwt) {
        return service.me(jwt.getSubject());
    }
}
