package com.vaabhi.staff;

/**
 * Result of a successful staff sign-in.
 *
 * @param accessToken HS256 JWT for {@code Authorization: Bearer}; carries {@code roles: ["ADMIN"]} (plus "OWNER"
 *     for the owner) and the role's {@code permissions}
 * @param expiresIn seconds until the token expires
 */
public record StaffSession(String accessToken, long expiresIn, SignedInStaff staff) {}
