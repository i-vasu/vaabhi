package com.vaabhi.backup;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/** One row of {@code backup_run}, as the admin shows it. */
public record BackupRunView(
        long id,
        String kind,
        String status,
        OffsetDateTime startedAt,
        @Nullable OffsetDateTime finishedAt,
        @Nullable Long sizeBytes,
        @Nullable String objectKey,
        @Nullable String message,
        @Nullable String notes,
        int attachmentsCopied,
        @Nullable LocalDate drilledOn,
        @Nullable String createdBy) {}
