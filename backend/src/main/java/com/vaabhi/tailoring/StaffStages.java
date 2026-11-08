package com.vaabhi.tailoring;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The stages a staff member may work; empty means every stage. */
public record StaffStages(@NotNull @Size(max = 20) List<@NotNull Long> stageIds) {}
