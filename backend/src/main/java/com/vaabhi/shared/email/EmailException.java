package com.vaabhi.shared.email;

/** The mail server refused a message or could not be reached. */
public class EmailException extends RuntimeException {

    public EmailException(String message, Throwable cause) {
        super(message, cause);
    }
}
