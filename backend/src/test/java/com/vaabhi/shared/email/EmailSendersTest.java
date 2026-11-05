package com.vaabhi.shared.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;

/** SMTP sending, the choice of sender and the SMTP security settings. */
class EmailSendersTest {

    private static final EmailProperties SMTP =
            new EmailProperties("smtp.example.com", 0, "user", "secret", "Vaabhi <orders@vaabhi.com>", "hi@vaabhi.com");

    private final JavaMailSender mail = mock(JavaMailSender.class);

    {
        when(mail.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void sendsTextAndHtmlFromTheShopWithRepliesToItsInbox() throws Exception {
        var sender = new SmtpEmailSender(mail, SMTP);
        assertThat(sender.configured()).isTrue();

        sender.send(new EmailMessage("meera@example.com", "Your order", "Hello", "<p>Hello</p>"));

        var sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mail).send(sent.capture());
        var message = sent.getValue();
        assertThat(message.getFrom()[0].toString()).isEqualTo("Vaabhi <orders@vaabhi.com>");
        assertThat(message.getReplyTo()[0].toString()).isEqualTo("hi@vaabhi.com");
        assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString())
                .isEqualTo("meera@example.com");
        assertThat(message.getSubject()).isEqualTo("Your order");
        assertThat(message.getContent()).isInstanceOf(Multipart.class);
    }

    @Test
    void sendsPlainTextWhenThereIsNoHtmlAndRepliesToTheSenderWithoutAReplyTo() throws Exception {
        var sender = new SmtpEmailSender(
                mail, new EmailProperties("smtp.example.com", 465, null, null, "orders@vaabhi.com", " "));
        sender.send(new EmailMessage("meera@example.com", "Plain", "Just text", null));

        var sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mail).send(sent.capture());
        assertThat(sent.getValue().getContent()).isEqualTo("Just text");
        assertThat(sent.getValue().getReplyTo()[0].toString()).isEqualTo("orders@vaabhi.com");
    }

    @Test
    void reportsARefusedMessageWithoutTheRecipient() {
        doThrow(new MailSendException("554 rejected")).when(mail).send(any(MimeMessage.class));
        var sender = new SmtpEmailSender(mail, SMTP);

        assertThatThrownBy(() -> sender.send(new EmailMessage("meera@example.com", "Your order", "Hi", null)))
                .isInstanceOf(EmailException.class)
                .hasMessage("The email \"Your order\" was not sent")
                .hasMessageNotContaining("meera");
    }

    @Test
    void usesSmtpOnlyWithAHostAndASender() {
        var config = new EmailConfig();
        var dev = new MockEnvironment();
        var prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThat(config.emailSender(SMTP, dev)).isInstanceOf(SmtpEmailSender.class);
        var none = new EmailProperties(null, 0, null, null, null, null);
        assertThat(none.port()).isEqualTo(587);
        var devSender = config.emailSender(none, dev);
        assertThat(devSender).isInstanceOf(LogEmailSender.class);
        assertThat(devSender.configured()).isFalse();
        devSender.send(new EmailMessage("a@example.com", "Logged", "Body", null));
        config.emailSender(none, prod).send(new EmailMessage("a@example.com", "Skipped", "Body", null));
        assertThat(config.emailSender(new EmailProperties("smtp.example.com", 587, null, null, " ", null), dev))
                .as("a host without a sender is not enough")
                .isInstanceOf(LogEmailSender.class);
    }

    @Test
    void neverSendsInPlainText() {
        var starttls = EmailConfig.smtp(587);
        assertThat(starttls.getProperty("mail.smtp.starttls.required")).isEqualTo("true");
        assertThat(starttls.getProperty("mail.smtp.ssl.enable")).isNull();
        var ssl = EmailConfig.smtp(465);
        assertThat(ssl.getProperty("mail.smtp.ssl.enable")).isEqualTo("true");
        assertThat(ssl.getProperty("mail.smtp.timeout")).isEqualTo("10000");
    }
}
