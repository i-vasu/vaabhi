package com.vaabhi.customer;

import org.jspecify.annotations.Nullable;

/**
 * Which sign-in methods work right now, so the storefront only offers those.
 *
 * @param phoneSignIn a code can be sent to a mobile number (by WhatsApp or SMS)
 * @param googleSignIn Google sign-in is set up (the backend knows the OAuth client id)
 * @param otpChannel how the code is sent: WhatsApp when its sign-in template is set up, else SMS; null without either
 */
public record AuthOptions(boolean phoneSignIn, boolean googleSignIn, @Nullable OtpChannel otpChannel) {

    /** Where a sign-in code goes. */
    public enum OtpChannel {
        WHATSAPP,
        SMS
    }
}
