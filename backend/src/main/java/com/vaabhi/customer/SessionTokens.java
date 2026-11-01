package com.vaabhi.customer;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Issues a session: a short-lived access token (JWT) plus a long-lived refresh token stored as a hash. */
@Component
class SessionTokens {

    static final String ROLE = "CUSTOMER";

    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final RefreshTokenStore refreshTokens;
    private final CustomerStore customers;
    private final Clock clock;

    SessionTokens(
            JwtEncoder encoder,
            AuthProperties properties,
            RefreshTokenStore refreshTokens,
            CustomerStore customers,
            Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.refreshTokens = refreshTokens;
        this.customers = customers;
        this.clock = clock;
    }

    AuthSession issue(long customerId, boolean newCustomer) {
        var profile = customers.find(customerId).orElseThrow(() -> AuthException.unauthorized("Please sign in again"));
        var now = clock.instant();
        var claims = JwtClaimsSet.builder()
                .issuer("vaabhi")
                .subject(Long.toString(customerId))
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .claim("roles", List.of(ROLE))
                .build();
        var accessToken = encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        var refreshToken = Secrets.refreshToken();
        refreshTokens.insert(
                customerId,
                Secrets.sha256(refreshToken),
                OffsetDateTime.ofInstant(now, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(now.plus(properties.refreshTokenTtl()), ZoneOffset.UTC));
        return new AuthSession(
                accessToken,
                properties.accessTokenTtl().toSeconds(),
                refreshToken,
                properties.refreshTokenTtl().toSeconds(),
                profile,
                newCustomer);
    }
}
