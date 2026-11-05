package com.vaabhi.tailoring;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;

/** Dates and notes that can change after an order is booked. */
public record OrderSchedule(
        @NotNull LocalDate deliveryDate,
        @Nullable LocalTime dueTime,
        @Nullable LocalDate trialDate,
        @Nullable @Size(max = 2000) String notes) {}
