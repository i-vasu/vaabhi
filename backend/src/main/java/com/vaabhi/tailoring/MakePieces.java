package com.vaabhi.tailoring;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** "Make products": cut a stock item into pieces, each becoming a draft product on the workshop board. */
public record MakePieces(
        @Positive long stockItemId,
        @NotNull @Size(min = 1, max = 20) List<@Valid @NotNull PieceForm> pieces) {

    /** One piece: the product it becomes and how much stock it takes. */
    public record PieceForm(
            @NotBlank @Size(max = 160) String name,
            @NotBlank String categorySlug,

            @NotNull @DecimalMin("0.01") @DecimalMax("100000") @Digits(integer = 6, fraction = 2)
            BigDecimal quantity,

            @Nullable @Size(max = 200) String note) {}

    /** A piece that was made, with its new product. */
    public record Made(long pieceId, long productId, String productName, String productSlug) {}

    /** Work done in the stage a piece is leaving: who did it and what it cost. */
    public record StageWork(
            @Nullable Long staffId,
            @NotBlank @Size(max = 120) String workerName,

            @NotNull @DecimalMin("0") @DecimalMax("1000000") @Digits(integer = 7, fraction = 2)
            BigDecimal costRupees) {}

    /** Design instructions for the tailor. */
    public record PieceNote(@Nullable @Size(max = 200) String note) {}

    /** The key of an uploaded design photo. */
    public record PhotoForm(@NotBlank @Size(max = 120) String key) {}
}
