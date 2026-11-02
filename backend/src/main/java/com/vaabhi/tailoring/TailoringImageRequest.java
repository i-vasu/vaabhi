package com.vaabhi.tailoring;

import jakarta.validation.constraints.NotNull;

/** Which kind of photo is being uploaded. */
public record TailoringImageRequest(@NotNull TailoringImage.Purpose purpose) {}
