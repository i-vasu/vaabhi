package com.vaabhi.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A step every order item moves through.
 *
 * @param id null for a stage added in this save
 * @param kind WORK stages are done by tailors; READY and DELIVERED close the flow and always come last
 */
public record ProductionStage(
        @Nullable Long id,
        @NotBlank @Size(max = 60) String name,
        @NotNull Kind kind) {

    public enum Kind {
        WORK,
        READY,
        DELIVERED
    }
}
