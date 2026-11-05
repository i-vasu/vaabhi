package com.vaabhi.shared.email;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SMTP settings under {@code vaabhi.mail}. Any SMTP service works (MSG91 Email, Google Workspace, ZeptoMail, SES).
 *
 * @param host the SMTP server; without it no email is sent
 * @param port 587 (STARTTLS, the default) or 465 (SSL)
 * @param from the sender, e.g. {@code Vaabhi <orders@vaabhi.com>}; its domain must be verified with the service
 * @param replyTo where customers' replies go, e.g. {@code hello@vaabhi.com}; null to reply to {@code from}
 */
@ConfigurationProperties("vaabhi.mail")
public record EmailProperties(
        @Nullable String host,
        int port,
        @Nullable String username,
        @Nullable String password,
        @Nullable String from,
        @Nullable String replyTo) {

    public EmailProperties {
        port = port <= 0 ? 587 : port;
    }

    boolean configured() {
        return host != null && !host.isBlank() && from != null && !from.isBlank();
    }
}
