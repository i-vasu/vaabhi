package com.vaabhi.accounts;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** TDS sections, challans and the month summary. Reads need MONEY; writes the owner (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/accounts/tds")
class TdsController {

    private final TdsService tds;

    TdsController(TdsService tds) {
        this.tds = tds;
    }

    private static @Nullable String actor(@Nullable Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("name");
    }

    @GetMapping("/sections")
    List<TdsSectionView> sections() {
        return tds.sections();
    }

    @PostMapping("/sections")
    @ResponseStatus(HttpStatus.CREATED)
    TdsSectionView createSection(@Valid @RequestBody TdsSectionForm form) {
        return tds.createSection(form);
    }

    @PutMapping("/sections/{id}")
    TdsSectionView updateSection(@PathVariable long id, @Valid @RequestBody TdsSectionForm form) {
        return tds.updateSection(id, form);
    }

    @GetMapping("/summary")
    List<TdsMonthSummary> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return tds.summary(from, to);
    }

    @GetMapping("/challans")
    List<TdsChallanView> challans(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return tds.challans(from, to);
    }

    @PostMapping("/challans")
    @ResponseStatus(HttpStatus.CREATED)
    TdsChallanView createChallan(@Valid @RequestBody TdsChallanForm form, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return tds.createChallan(form, actor(jwt));
    }
}
