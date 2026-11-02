package com.vaabhi.staff;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Change your own dashboard password; the current one is required. */
public record PasswordChange(
        @NotBlank @Size(max = 200) String currentPassword,

        @NotBlank @Size(min = 10, max = 200, message = "must be at least 10 characters")
        String newPassword) {}
