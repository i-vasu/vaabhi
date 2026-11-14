package com.vaabhi.accounts;

import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Closing stock, the close checks, close and reopen of a financial year (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/accounts/year-end")
class YearEndController {

    private static final String AUDITOR_PACK_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final YearEndService yearEnd;
    private final AuditorPackService pack;

    YearEndController(YearEndService yearEnd, AuditorPackService pack) {
        this.yearEnd = yearEnd;
        this.pack = pack;
    }

    private static @Nullable String actor(@Nullable Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("name");
    }

    @GetMapping("/{fy}")
    YearEnd get(@PathVariable String fy) {
        return yearEnd.get(FiscalYears.parse(fy));
    }

    @GetMapping("/{fy}/auditor-pack.xlsx")
    ResponseEntity<StreamingResponseBody> auditorPack(@PathVariable String fy) {
        var fyStart = FiscalYears.parse(fy);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(AUDITOR_PACK_TYPE))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"vaabhi-books-" + fy + ".xlsx\"")
                .body(out -> pack.write(fyStart, out));
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
