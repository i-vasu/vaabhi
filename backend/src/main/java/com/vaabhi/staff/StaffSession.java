package com.vaabhi.staff;

/**
 * Result of a successful staff sign-in.
 *
 * @param accessToken HS256 JWT for {@code Authorization: Bearer}; carries {@code roles: ["ADMIN"]}
 * @param expiresIn seconds until the token expires
 */
public record StaffSession(String accessToken, long expiresIn, StaffProfile staff) {}
