package com.vaabhi.shared.whatsapp;

/** The WhatsApp provider refused the message or could not be reached. */
public class WhatsAppException extends RuntimeException {

    public WhatsAppException(String message) {
        super(message);
    }

    public WhatsAppException(String message, Throwable cause) {
        super(message, cause);
    }
}
