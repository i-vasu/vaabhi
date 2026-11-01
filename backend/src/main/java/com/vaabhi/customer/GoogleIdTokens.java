package com.vaabhi.customer;

import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Verifies the ID token from Google's "Sign in with Google" button: signed by Google (keys fetched from Google
 * and cached), not expired, issued by Google and meant for our client id.
 */
@Component
class GoogleIdTokens {

    static final String CERTS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    private static final String NOT_VERIFIED = "Google sign-in could not be verified. Please try again.";

    private final AuthProperties properties;
    private final JwtDecoder decoder;

    @Autowired
    GoogleIdTokens(AuthProperties properties) {
        this(
                properties,
                NimbusJwtDecoder.withJwkSetUri(CERTS_URL)
                        .jwsAlgorithm(SignatureAlgorithm.RS256)
                        .build());
    }

    GoogleIdTokens(AuthProperties properties, JwtDecoder decoder) {
        this.properties = properties;
        this.decoder = decoder;
    }

    /** @param email lower-cased; only trust it when {@code emailVerified} */
    record Account(
            String sub,
            @Nullable String email,
            boolean emailVerified,
            @Nullable String name) {}

    boolean configured() {
        return properties.googleConfigured();
    }

    Account verify(String credential) {
        Jwt jwt;
        try {
            jwt = decoder.decode(credential);
        } catch (JwtException e) {
            throw AuthException.unauthorized(NOT_VERIFIED);
        }
        var audience = jwt.getAudience();
        if (!ISSUERS.contains(String.valueOf(jwt.getClaimAsString("iss")))
                || audience == null
                || !audience.contains(properties.googleClientId())
                || jwt.getSubject() == null) {
            throw AuthException.unauthorized(NOT_VERIFIED);
        }
        var email = jwt.getClaimAsString("email");
        return new Account(
                jwt.getSubject(),
                email == null ? null : email.strip().toLowerCase(Locale.ROOT),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")),
                truncate(jwt.getClaimAsString("name")));
    }

    private static @Nullable String truncate(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        var trimmed = name.strip();
        return trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed;
    }
}
