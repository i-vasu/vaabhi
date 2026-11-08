package com.vaabhi.tailoring;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Move items (from any orders) to a stage, e.g. everything selected on the work board. */
public record BulkMove(
        @NotNull Long stageId,
        @NotNull @Size(min = 1, max = 100) List<@NotNull Long> itemIds) {}
