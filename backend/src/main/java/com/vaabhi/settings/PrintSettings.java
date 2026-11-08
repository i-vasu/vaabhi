package com.vaabhi.settings;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

/**
 * What the Print button offers and which sections each printed form shows.
 *
 * @param formats paper formats offered when printing
 * @param defaultFormat preselected format; must be one of {@code formats}
 */
public record PrintSettings(
        @NotEmpty Set<PaperFormat> formats,
        @NotNull PaperFormat defaultFormat,
        boolean showLogo,
        boolean showMeasurements,
        boolean showClothPhotos,
        boolean showBankDetails,
        boolean showUpiQr,
        boolean showTerms,
        boolean showSignature,
        boolean showStaffName) {

    public enum PaperFormat {
        A4,
        A5,
        THERMAL_80MM,
        THERMAL_58MM
    }

    @AssertTrue(message = "must be one of the offered formats")
    boolean isDefaultFormatOffered() {
        return formats == null || defaultFormat == null || formats.contains(defaultFormat);
    }

    static PrintSettings defaults() {
        return new PrintSettings(
                Set.of(PaperFormat.A4, PaperFormat.THERMAL_80MM),
                PaperFormat.A4,
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                false);
    }
}
