package com.vaabhi.customer;

/**
 * @param phone the number the code went to, normalised (E.164)
 * @param expiresInSeconds how long the code is valid
 * @param resendAfterSeconds when a new code may be requested
 */
public record OtpSent(String phone, long expiresInSeconds, long resendAfterSeconds) {}
