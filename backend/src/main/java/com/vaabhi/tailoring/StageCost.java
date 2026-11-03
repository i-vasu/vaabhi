package com.vaabhi.tailoring;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** What one production stage of a product costs the shop (the tailor's piece rate). */
public record StageCost(
        @NotNull Long stageId,

        @DecimalMin("0") @DecimalMax("1000000") @Digits(integer = 7, fraction = 2)
        BigDecimal rupees) {}
