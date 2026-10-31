package com.vaabhi.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** One photo in the new order, with its alt text and colour. */
public record MediaUpdate(
        long id,
        @NotBlank @Size(max = 200) String alt,
        @Nullable @Size(max = 60) String colourName) {}
