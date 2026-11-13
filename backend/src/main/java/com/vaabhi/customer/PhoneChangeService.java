package com.vaabhi.customer;

import com.vaabhi.shared.security.SecurityProperties;
import com.vaabhi.shared.whatsapp.WhatsAppException;
import com.vaabhi.shared.whatsapp.WhatsAppSender;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adding or changing the signed-in customer's mobile number, verified with the same WhatsApp OTP as sign-in.
 * Change codes live under their own key and hash domain, so a sign-in code cannot change a number and vice
 * versa.
 */
@Service
class PhoneChangeService {

    private static final Logger log = LoggerFactory.getLogger(PhoneChangeService.class);
    private static final String PHONE = "phone";
    private static final String CODE = "code";
    private static final String CHANGE = "change";
    private static final String CHANGE_KEY_PREFIX = "chg";

    private final AuthProperties properties;
    private final SecurityProperties security;
    private final OtpStore otps;
    private final CustomerStore customers;
    private final WhatsAppSender whatsApp;
    private final Clock clock;

    PhoneChangeService(
            AuthProperties properties,
            SecurityProperties security,
            OtpStore otps,
            CustomerStore customers,
            WhatsAppSender whatsApp,
            Clock clock) {
        this.properties = properties;
        this.security = security;
        this.otps = otps;
        this.customers = customers;
        this.whatsApp = whatsApp;
        this.clock = clock;
    }

    private boolean codesCanBeSent() {
        return whatsApp.configured(WhatsAppSender.OTP_TEMPLATE);
    }

    /** Sends a 6-digit code to the new number. Rolls back (so it does not count) if WhatsApp cannot send it. */
    @Transactional
    public OtpSent request(long customerId, String rawPhone) {
        var phone = phone(rawPhone);
        if (!codesCanBeSent()) {
            throw AuthException.unavailable("Changing your number is not available right now");
        }
        var profile = customers.find(customerId).orElseThrow(() -> AuthException.unauthorized("Please sign in again"));
        if (phone.equals(profile.phone())) {
            throw AuthException.invalid(PHONE, "This is already your number");
        }
        if (customers.idByPhone(phone).filter(id -> id != customerId).isPresent()) {
            throw AuthException.conflict("This number is already on another account");
        }
        var otp = properties.otp();
        var now = now();
        var key = changeKey(phone);
        var recent = otps.recentFor(key, now.minusHours(1), now.minus(otp.resendAfter()));
        if (recent.tooSoon()) {
            throw AuthException.tooManyRequests("Please wait before asking for another code.", otp.resendAfter());
        }
        if (recent.count() >= otp.maxPerPhonePerHour()) {
            throw AuthException.tooManyRequests(
                    "Too many codes sent to this number. Try again in an hour.", java.time.Duration.ofHours(1));
        }
        if (otps.countSince(now.minusHours(1)) >= otp.maxPerHour()) {
            log.warn("OTP hourly limit ({}) reached; refusing new codes", otp.maxPerHour());
            throw AuthException.unavailable("Changing your number is not available right now");
        }
        var code = Secrets.otpCode();
        otps.replace(key, hash(customerId, phone, code), now, now.plus(otp.ttl()));
        try {
            whatsApp.send(phone, WhatsAppSender.OTP_TEMPLATE, Map.of("otp", code));
        } catch (WhatsAppException e) {
            log.warn("Could not send phone-change OTP to {}: {}", PhoneNumbers.masked(phone), e.getMessage());
            throw AuthException.badGateway("We could not send the code. Please try again.");
        }
        return new OtpSent(phone, otp.ttl().toSeconds(), otp.resendAfter().toSeconds());
    }

    /** Wrong guesses are counted even though the call fails, hence no rollback for {@link AuthException}. */
    @Transactional(noRollbackFor = AuthException.class)
    public CustomerProfile verify(long customerId, String rawPhone, String code) {
        var phone = phone(rawPhone);
        var now = now();
        var live = otps.lockLive(changeKey(phone), now)
                .orElseThrow(() -> AuthException.invalid(CODE, "This code has expired. Send a new one."));
        var maxAttempts = properties.otp().maxAttempts();
        if (live.attempts() >= maxAttempts) {
            throw AuthException.invalid(CODE, "Too many wrong tries. Send a new code.");
        }
        otps.countAttempt(live.id());
        if (!Secrets.same(hash(customerId, phone, code), live.codeHash())) {
            var left = maxAttempts - live.attempts() - 1;
            throw AuthException.invalid(
                    CODE,
                    left > 0
                            ? "Incorrect code. %d %s left.".formatted(left, left == 1 ? "try" : "tries")
                            : "Incorrect code. Send a new one.");
        }
        otps.consume(live.id(), now);
        // Re-check uniqueness; the DB's unique index is the final guard against a race for the same number.
        if (customers.idByPhone(phone).filter(id -> id != customerId).isPresent()) {
            throw AuthException.conflict("This number is already on another account");
        }
        try {
            customers.updatePhone(customerId, phone);
        } catch (DataIntegrityViolationException e) {
            throw AuthException.conflict("This number is already on another account");
        }
        return customers.find(customerId).orElseThrow(() -> AuthException.unauthorized("Please sign in again"));
    }

    private String phone(String raw) {
        return PhoneNumbers.normalize(raw)
                .orElseThrow(() -> AuthException.invalid(PHONE, "Enter a valid 10-digit Indian mobile number"));
    }

    /** Change codes sit under a different key than sign-in codes, so one can never be verified as the other. */
    private static String changeKey(String phone) {
        return CHANGE_KEY_PREFIX + phone;
    }

    private String hash(long customerId, String phone, String code) {
        return Secrets.hmac(security.jwtSecret(), CHANGE + ":" + customerId + ":" + phone + ":" + code);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
