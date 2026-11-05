package com.vaabhi.shared.email;

import org.jspecify.annotations.Nullable;

/**
 * One email to one person.
 *
 * @param text the plain-text body (always sent; mail apps without HTML show it)
 * @param html the same message as HTML, or null for text only
 */
public record EmailMessage(
        String to, String subject, String text, @Nullable String html) {}
