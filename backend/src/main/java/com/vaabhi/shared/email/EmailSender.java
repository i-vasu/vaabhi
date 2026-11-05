package com.vaabhi.shared.email;

/** Sends one email. */
public interface EmailSender {

    /** False when no mail server is set up; such senders skip messages instead of sending them. */
    boolean configured();

    /** @throws EmailException when the mail server refuses the message or cannot be reached */
    void send(EmailMessage message);
}
