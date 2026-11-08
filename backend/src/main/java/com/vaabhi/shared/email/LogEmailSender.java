package com.vaabhi.shared.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Without a mail server. In development (`log`) each email is written to the log so it can be read; in production
 * (`off`) emails are skipped with a debug line, so customers' messages do not end up in the logs.
 */
class LogEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

    private final boolean writeBody;

    LogEmailSender(boolean writeBody) {
        this.writeBody = writeBody;
    }

    @Override
    public boolean configured() {
        return false;
    }

    @Override
    public void send(EmailMessage message) {
        if (writeBody) {
            log.info("Email (not sent, no MAIL_HOST) to {}: {}\n{}", message.to(), message.subject(), message.text());
        } else {
            log.debug("Email not sent (MAIL_HOST is not set): {}", message.subject());
        }
    }
}
