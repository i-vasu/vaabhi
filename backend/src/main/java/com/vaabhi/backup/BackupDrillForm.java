package com.vaabhi.backup;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** An owner's manual restore drill: the date it was done and an optional note. */
public record BackupDrillForm(@NotNull LocalDate drilledOn, @Nullable @Size(max = 500) String note) {}
