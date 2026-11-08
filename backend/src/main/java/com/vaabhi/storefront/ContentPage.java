package com.vaabhi.storefront;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/**
 * A storefront page written in the dashboard (shipping policy, size guide, about…).
 *
 * @param body plain text: blank lines separate paragraphs, {@code ## } starts a heading, {@code - } a bullet
 * @param published only published pages are served to the storefront and listed in its footer
 */
public record ContentPage(
        long id,
        String slug,
        Section section,
        int position,
        String title,
        @Nullable String summary,
        String body,
        boolean published,
        OffsetDateTime updatedAt) {

    /** The footer column it is listed under; POLICY pages live at /policies/&lt;slug&gt;, the others at /&lt;slug&gt;. */
    public enum Section {
        HELP,
        POLICY,
        ABOUT
    }

    /** Add or edit a page. */
    public record ContentPageForm(
            @NotBlank
            @Size(max = 60)
            @Pattern(
                    regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                    message = "use lowercase letters, numbers and dashes, e.g. size-guide")
            String slug,
            @NotNull Section section,
            @NotBlank @Size(max = 120) String title,
            @Nullable @Size(max = 300) String summary,
            @NotBlank @Size(max = 50_000) String body,
            boolean published) {}

    /** A published page as the footer links to it. */
    public record ContentPageLink(String slug, Section section, String title) {}
}
