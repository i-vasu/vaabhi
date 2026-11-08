package com.vaabhi.settings;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Text and payment details printed on documents.
 *
 * @param slogan tagline under the shop name on the invoice
 * @param upiId UPI ID for the payment QR shown at the till and printed on bills
 * @param upiQrKey object key of an uploaded QR image, used instead of one generated from {@code upiId}
 * @param alterationUndertaking wording printed on an alteration slip; blank uses {@link #DEFAULT_UNDERTAKING}
 * @param terms terms and conditions printed at the foot of the invoice
 */
public record DocumentSettings(
        @Nullable @Size(max = 120) String slogan,
        @Nullable @Size(max = 120) String bankName,
        @Nullable @Size(max = 120) String branch,

        @Nullable @Pattern(regexp = "^[0-9]{6,18}$", message = "must be 6 to 18 digits")
        String accountNumber,

        @Nullable @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "must be an IFSC like SBIN0001234")
        String ifsc,

        @Nullable
        @Pattern(regexp = "^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$", message = "must be a UPI ID like shop@okbank")
        String upiId,

        @Nullable @Size(max = 200) String upiQrKey,
        @Nullable @Size(max = 1000) String alterationUndertaking,
        @Nullable @Size(max = 2000) String terms) {

    /** Used on alteration slips when the shop has not written its own. */
    public static final String DEFAULT_UNDERTAKING =
            "I understand that alterations are best-effort and the final fit is not guaranteed.";

    public String undertaking() {
        return alterationUndertaking == null || alterationUndertaking.isBlank()
                ? DEFAULT_UNDERTAKING
                : alterationUndertaking;
    }

    static DocumentSettings defaults() {
        return new DocumentSettings(null, null, null, null, null, null, null, null, null);
    }
}
