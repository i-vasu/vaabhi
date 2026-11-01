package com.vaabhi.shared.sms;

/** The SMS provider refused the message or could not be reached. */
public class SmsException extends RuntimeException {

    public SmsException(String message) {
        super(message);
    }

    public SmsException(String message, Throwable cause) {
        super(message, cause);
    }
}
