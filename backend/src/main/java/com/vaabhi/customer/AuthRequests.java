package com.vaabhi.customer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request bodies of the sign-in API. */
public final class AuthRequests {

    private AuthRequests() {}

    /** Sends a sign-in code to an Indian mobile number. */
    @Schema(name = "OtpRequest")
    public record OtpRequest(
            @Schema(example = "98765 43210") @NotBlank @Size(max = 20)
            String phone) {}

    @Schema(name = "OtpVerification")
    public record OtpVerification(
            @NotBlank @Size(max = 20) String phone,

            @NotBlank @Pattern(regexp = "\\d{6}", message = "Enter the 6-digit code")
            String code) {}

    /** The ID token ("credential") from Google's Sign in with Google button. */
    @Schema(name = "GoogleSignIn")
    public record GoogleSignIn(@NotBlank @Size(max = 4096) String credential) {}

    @Schema(name = "RefreshRequest")
    public record RefreshRequest(@NotBlank @Size(max = 128) String refreshToken) {}

    @Schema(name = "ProfileUpdate")
    public record ProfileUpdate(@NotBlank @Size(max = 80) String name) {}
}
