package com.vaabhi.staff;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Email + password sign-in for shop staff; issues the access token the admin dashboard sends to the API. */
@Service
class StaffAuthService {

    static final String SUBJECT_PREFIX = "staff:";
    private static final String WRONG_CREDENTIALS = "Email or password is wrong";
    private static final String NOT_STAFF = "Not a staff sign-in";

    private final StaffAccountStore store;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final StaffProperties properties;
    private final Clock clock;
    /** Checked against when the email is unknown, so unknown and known emails take about the same time. */
    private final String dummyHash;

    StaffAuthService(
            StaffAccountStore store,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            StaffProperties properties,
            Clock clock) {
        this.store = store;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    /** Wrong-password counts must be saved even though the request fails, hence noRollbackFor. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public StaffSession login(StaffLoginRequest request) {
        var now = OffsetDateTime.now(clock);
        var account = store.findByEmail(request.email()).orElse(null);
        if (account == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, WRONG_CREDENTIALS);
        }
        var lockedUntil = account.lockedUntil();
        if (lockedUntil != null && lockedUntil.isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Too many wrong passwords. Try again in a few minutes.");
        }
        if (!passwordEncoder.matches(request.password(), account.passwordHash())) {
            store.recordFailure(account.id(), properties.maxFailedLogins(), now.plus(properties.lockout()));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, WRONG_CREDENTIALS);
        }
        if (!account.active()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account has been switched off");
        }
        store.recordSuccess(account.id(), now);
        return new StaffSession(issueToken(account, now), properties.tokenTtl().toSeconds(), account.profile());
    }

    @Transactional(readOnly = true)
    public StaffProfile me(String subject) {
        return account(subject).profile();
    }

    @Transactional
    public void changePassword(String subject, PasswordChange change) {
        var account = account(subject);
        if (!passwordEncoder.matches(change.currentPassword(), account.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is wrong");
        }
        store.setPasswordHash(account.id(), passwordEncoder.encode(change.newPassword()));
    }

    /** The active staff account behind a token subject ({@code staff:<id>}). */
    private StaffAccountStore.Account account(String subject) {
        if (!subject.startsWith(SUBJECT_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, NOT_STAFF);
        }
        long id;
        try {
            id = Long.parseLong(subject.substring(SUBJECT_PREFIX.length()));
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, NOT_STAFF, e);
        }
        return store.findById(id)
                .filter(StaffAccountStore.Account::active)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
    }

    private String issueToken(StaffAccountStore.Account account, OffsetDateTime now) {
        var claims = JwtClaimsSet.builder()
                .issuer("vaabhi")
                .subject(SUBJECT_PREFIX + account.id())
                .issuedAt(now.toInstant())
                .expiresAt(now.plus(properties.tokenTtl()).toInstant())
                .claim("roles", List.of("ADMIN"))
                .claim("staffRole", account.role().name())
                .claim("name", account.name())
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
