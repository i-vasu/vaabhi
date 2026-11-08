package com.vaabhi.shared.email;

import jakarta.mail.MessagingException;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/** Sends through an SMTP server as {@code from}; replies go to {@code replyTo} when set. */
class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mail;
    private final EmailProperties properties;

    SmtpEmailSender(JavaMailSender mail, EmailProperties properties) {
        this.mail = mail;
        this.properties = properties;
    }

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            var mime = mail.createMimeMessage();
            var helper = new MimeMessageHelper(mime, message.html() != null, StandardCharsets.UTF_8.name());
            helper.setFrom(java.util.Objects.requireNonNull(properties.from()));
            if (properties.replyTo() != null && !properties.replyTo().isBlank()) {
                helper.setReplyTo(properties.replyTo());
            }
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            if (message.html() == null) {
                helper.setText(message.text());
            } else {
                helper.setText(message.text(), message.html());
            }
            mail.send(mime);
        } catch (MessagingException | MailException e) {
            throw new EmailException("The email \"" + message.subject() + "\" was not sent", e);
        }
    }
}
