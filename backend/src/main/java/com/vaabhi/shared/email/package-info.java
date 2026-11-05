/**
 * Vendor-neutral email sending over SMTP (order updates to customers). Works with any SMTP service: MSG91 Email,
 * Google Workspace, ZeptoMail, Amazon SES. Without {@code MAIL_HOST} emails are written to the log in development
 * and skipped in production.
 */
@org.springframework.modulith.NamedInterface("email")
package com.vaabhi.shared.email;
