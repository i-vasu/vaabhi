package com.vaabhi.customer;

/**
 * Which sign-in methods work right now, so the storefront only offers those.
 *
 * @param phoneSignIn a code can be sent to a mobile number on WhatsApp (its sign-in template is set up)
 * @param googleSignIn Google sign-in is set up (the backend knows the OAuth client id)
 */
public record AuthOptions(boolean phoneSignIn, boolean googleSignIn) {}
