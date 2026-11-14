package com.vaabhi.backup;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Backup health for the admin card, the year-end checklist and the auditor pack. */
public record BackupStatus(
        boolean configured,
        String locationLabel,
        @Nullable String bucket,
        @Nullable BackupRunView lastBackup,
        @Nullable BackupRunView lastCheck,
        @Nullable BackupRunView lastDrill,
        boolean missedLastNight,
        int missedDaysLast7,
        List<BackupRunView> recent) {}
