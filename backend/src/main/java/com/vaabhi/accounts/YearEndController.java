package com.vaabhi.accounts;

import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Closing stock, the close checks, close and reopen of a financial year (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/accounts/year-end")
class YearEndController {

    private final YearEndService yearEnd;

    YearEndController(YearEndService yearEnd) {
        this.yearEnd = yearEnd;
    }

    private static @Nullable String actor(@Nullable Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("name");
    }

    @GetMapping("/{fy}")
    YearEnd get(@PathVariable String fy) {
        return yearEnd.get(FiscalYears.parse(fy));
    }

    @PutMapping("/{fy}/stock")
    YearEnd saveStock(
            @PathVariable String fy,
            @Valid @RequestBody YearEndStockForm form,
            @AuthenticationPrincipal @Nullable Jwt jwt) {
        return yearEnd.saveStock(FiscalYears.parse(fy), form, actor(jwt));
    }

    @PostMapping("/{fy}/close")
    YearEnd close(@PathVariable String fy, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return yearEnd.close(FiscalYears.parse(fy), actor(jwt));
    }

    @PostMapping("/{fy}/reopen")
    YearEnd reopen(@PathVariable String fy, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return yearEnd.reopen(FiscalYears.parse(fy), actor(jwt));
    }
}
