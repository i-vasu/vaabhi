package com.vaabhi.staff;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StaffLoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 200) String password) {}
