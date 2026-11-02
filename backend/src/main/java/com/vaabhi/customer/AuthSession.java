package com.vaabhi.customer;

/**
 * A signed-in session. The storefront keeps both tokens in httpOnly cookies; the browser never sees them.
 *
 * @param accessToken short-lived bearer token for {@code /api/v1/customer/**}
 * @param accessTokenExpiresIn seconds until the access token expires
 * @param refreshToken single-use token that buys a new session; rotated on every refresh
 * @param refreshTokenExpiresIn seconds until the customer must sign in again (renewed on every refresh)
 * @param newCustomer true when this sign-in created the account (e.g. ask for a name)
 */
public record AuthSession(
        String accessToken,
        long accessTokenExpiresIn,
        String refreshToken,
        long refreshTokenExpiresIn,
        CustomerProfile customer,
        boolean newCustomer) {}
