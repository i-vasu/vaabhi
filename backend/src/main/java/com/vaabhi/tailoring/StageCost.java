package com.vaabhi.tailoring;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** What one production stage of a product costs the shop (the tailor's piece rate). */
public record StageCost(
        @NotNull Long stageId, @Min(0) @Max(100_000_000) long paise) {}
