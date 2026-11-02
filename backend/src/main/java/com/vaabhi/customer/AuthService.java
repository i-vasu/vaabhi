package com.vaabhi.customer;

import com.vaabhi.shared.security.SecurityProperties;
import com.vaabhi.shared.sms.SmsException;
import com.vaabhi.shared.sms.SmsSender;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer sign-in. Codes are generated and checked here; the SMS provider only delivers them, so switching
 * providers needs a new {@link SmsSender}, nothing else.
 */
@Service
class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String PHONE = "phone";
    private static final String CODE = "code";

    private final AuthProperties properties;
    private final SecurityProperties security;
    private final OtpStore otps;
    private final RefreshTokenStore refreshTokens;
    private final CustomerStore customers;
    private final SessionTokens sessions;
    private final GoogleIdTokens google;
    private final SmsSender sms;
    private final Clock clock;

    AuthService(
            AuthProperties properties,
            SecurityProperties security,
            OtpStore otps,
            RefreshTokenStore refreshTokens,
            CustomerStore customers,
            SessionTokens sessions,
            GoogleIdTokens google,
            SmsSender sms,
            Clock clock) {
        this.properties = properties;
        this.security = security;
        this.otps = otps;
        this.refreshTokens = refreshTokens;
        this.customers = customers;
        this.sessions = sessions;
        this.google = google;
        this.sms = sms;
        this.clock = clock;
    }

    /** Sends a 6-digit code. Rolls back (so it does not count) if the SMS cannot be sent. */
    @Transactional
    public OtpSent requestOtp(String rawPhone) {
        var phone = phone(rawPhone);
        if (!sms.configured()) {
            throw AuthException.unavailable("Sign-in with mobile number is not available right now. Use Google.");
        }
        var otp = properties.otp();
        var now = now();
        var recent = otps.recentFor(phone, now.minusHours(1), now.minus(otp.resendAfter()));
        if (recent.tooSoon()) {
            throw AuthException.tooManyRequests("Please wait before asking for another code.", otp.resendAfter());
        }
        if (recent.count() >= otp.maxPerPhonePerHour()) {
            throw AuthException.tooManyRequests(
                    "Too many codes sent to this number. Try again in an hour.", java.time.Duration.ofHours(1));
        }
        if (otps.countSince(now.minusHours(1)) >= otp.maxPerHour()) {
            log.warn("OTP hourly limit ({}) reached; refusing new codes", otp.maxPerHour());
            throw AuthException.unavailable("Sign-in with mobile number is busy. Try again shortly or use Google.");
        }
        var code = Secrets.otpCode();
        otps.replace(phone, hash(phone, code), now, now.plus(otp.ttl()));
        try {
            sms.send(phone, SmsSender.OTP_TEMPLATE, Map.of("otp", code));
        } catch (SmsException e) {
            log.warn("Could not send OTP to {}: {}", PhoneNumbers.masked(phone), e.getMessage());
            throw AuthException.badGateway("We could not send the code. Please try again.");
        }
        return new OtpSent(phone, otp.ttl().toSeconds(), otp.resendAfter().toSeconds());
    }

    /** Wrong guesses are counted even though the call fails, hence no rollback for {@link AuthException}. */
    @Transactional(noRollbackFor = AuthException.class)
    public AuthSession verifyOtp(String rawPhone, String code) {
        var phone = phone(rawPhone);
        var now = now();
        var live = otps.lockLive(phone, now)
                .orElseThrow(() -> AuthException.invalid(CODE, "This code has expired. Send a new one."));
        var maxAttempts = properties.otp().maxAttempts();
        if (live.attempts() >= maxAttempts) {
            throw AuthException.invalid(CODE, "Too many wrong tries. Send a new code.");
        }
        otps.countAttempt(live.id());
        if (!Secrets.same(hash(phone, code), live.codeHash())) {
            var left = maxAttempts - live.attempts() - 1;
            throw AuthException.invalid(
                    CODE,
                    left > 0
                            ? "Incorrect code. %d %s left.".formatted(left, left == 1 ? "try" : "tries")
                            : "Incorrect code. Send a new one.");
        }
        otps.consume(live.id(), now);
        var signIn = customers.signInByPhone(phone, now);
        return sessions.issue(signIn.id(), signIn.created());
    }

    @Transactional
    public AuthSession signInWithGoogle(String credential) {
        if (!google.configured()) {
            throw AuthException.unavailable("Google sign-in is not available right now. Use your mobile number.");
        }
        var account = google.verify(credential);
        var email = account.emailVerified() ? account.email() : null;
        var now = now();
        var existing = customers
                .idByGoogleSub(account.sub())
                .or(() -> email == null ? java.util.Optional.empty() : customers.idByEmail(email));
        if (existing.isPresent()) {
            customers.linkGoogle(existing.get(), account.sub(), email, account.name(), now);
            return sessions.issue(existing.get(), false);
        }
        return sessions.issue(customers.insertGoogle(account.sub(), email, account.name(), now), true);
    }

    /**
     * Swaps a refresh token for a new session (the old token stops working). A token that was rotated earlier
     * than the grace period is a sign it was copied, so every session of that customer is ended.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public AuthSession refresh(String refreshToken) {
        var now = now();
        var stored = refreshTokens
                .lock(Secrets.sha256(refreshToken), now, now.minus(properties.refreshReuseGrace()))
                .orElseThrow(() -> AuthException.unauthorized("Please sign in again"));
        if (stored.expired()) {
            throw AuthException.unauthorized("Your session has expired. Please sign in again.");
        }
        if (stored.revoked()) {
            if (stored.recentlyRotated()) {
                return sessions.issue(stored.customerId(), false);
            }
            var ended = refreshTokens.revokeAll(stored.customerId(), now);
            log.warn("Reused refresh token for customer {}; ended {} sessions", stored.customerId(), ended);
            throw AuthException.unauthorized("Please sign in again");
        }
        refreshTokens.rotate(stored.id(), now);
        return sessions.issue(stored.customerId(), false);
    }

    /** Ends this session. Unknown or already-ended tokens are fine: the result is the same. */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.revokeByHash(Secrets.sha256(refreshToken), now());
    }

    private String phone(String raw) {
        return PhoneNumbers.normalize(raw)
                .orElseThrow(() -> AuthException.invalid(PHONE, "Enter a valid 10-digit Indian mobile number"));
    }

    private String hash(String phone, String code) {
        return Secrets.hmac(security.jwtSecret(), phone + ":" + code);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
