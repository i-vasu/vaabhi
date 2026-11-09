package com.vaabhi.tailoring;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Move order items (from any orders) and pieces to a stage, e.g. everything selected on the work board.
 *
 * @param work who did the stage one piece is leaving and what it cost, logged on its product
 */
public record BulkMove(
        @NotNull Long stageId,
        @Nullable @Size(max = 100) List<@NotNull Long> itemIds,
        @Nullable @Size(max = 100) List<@NotNull Long> pieceIds,
        @Valid MakePieces.@Nullable StageWork work) {

    public BulkMove {
        itemIds = itemIds == null ? List.of() : List.copyOf(itemIds);
        pieceIds = pieceIds == null ? List.of() : List.copyOf(pieceIds);
    }

    @AssertTrue(message = "Pick something to move")
    boolean isNotEmpty() {
        return !itemIds.isEmpty() || !pieceIds.isEmpty();
    }
}
