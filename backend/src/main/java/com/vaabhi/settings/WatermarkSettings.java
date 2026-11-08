package com.vaabhi.settings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Brands design photos shared from the gallery. A blank text uses the shop name. */
public record WatermarkSettings(
        boolean enabled,
        @Nullable @Size(max = 60) String text,
        @NotNull Position position,
        @Min(10) @Max(100) int opacityPercent) {

    public enum Position {
        BOTTOM_RIGHT,
        BOTTOM_LEFT,
        CENTER,
        TOP_RIGHT
    }

    static WatermarkSettings defaults() {
        return new WatermarkSettings(false, null, Position.BOTTOM_RIGHT, 60);
    }
}
