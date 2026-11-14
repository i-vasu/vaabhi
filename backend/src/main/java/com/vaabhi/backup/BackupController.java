package com.vaabhi.backup;

import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Backup health and owner actions. Reads need MONEY; writes are the owner's (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/accounts/backups")
class BackupController {

    private final BackupService backups;
    private final BackupStatusService statuses;

    BackupController(BackupService backups, BackupStatusService statuses) {
        this.backups = backups;
        this.statuses = statuses;
    }

    private static @Nullable String actor(@Nullable Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("name");
    }

    @GetMapping("/status")
    BackupStatus status() {
        return statuses.status();
    }

    @PostMapping("/run")
    BackupStatus run(@AuthenticationPrincipal @Nullable Jwt jwt) {
        backups.runNow(actor(jwt));
        return statuses.status();
    }

    @PostMapping("/drills")
    BackupStatus drill(@Valid @RequestBody BackupDrillForm form, @AuthenticationPrincipal @Nullable Jwt jwt) {
        backups.recordDrill(form.drilledOn(), form.note(), actor(jwt));
        return statuses.status();
    }
}
