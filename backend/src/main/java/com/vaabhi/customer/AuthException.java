package com.vaabhi.customer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * A sign-in problem as an RFC 9457 problem detail. With a field, it also carries {@code errors: [{field,
 * message}]} like bean validation, so the form shows the message under that input. Too-many-requests answers
 * carry {@code Retry-After} and {@code retryAfterSeconds}.
 */
class AuthException extends ErrorResponseException {

    private final HttpHeaders headers = new HttpHeaders();

    private AuthException(HttpStatus status, String message, @Nullable String field, @Nullable Duration retryAfter) {
        super(status, problem(status, message, field, retryAfter), null);
        if (retryAfter != null) {
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter.toSeconds()));
        }
    }

    static AuthException invalid(String field, String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, message, field, null);
    }

    static AuthException unauthorized(String message) {
        return new AuthException(HttpStatus.UNAUTHORIZED, message, null, null);
    }

    static AuthException tooManyRequests(String message, Duration retryAfter) {
        return new AuthException(HttpStatus.TOO_MANY_REQUESTS, message, null, retryAfter);
    }

    static AuthException unavailable(String message) {
        return new AuthException(HttpStatus.SERVICE_UNAVAILABLE, message, null, null);
    }

    static AuthException badGateway(String message) {
        return new AuthException(HttpStatus.BAD_GATEWAY, message, null, null);
    }

    @Override
    public HttpHeaders getHeaders() {
        return headers;
    }

    private static ProblemDetail problem(
            HttpStatus status, String message, @Nullable String field, @Nullable Duration retryAfter) {
        var problem = ProblemDetail.forStatusAndDetail(status, message);
        if (field != null) {
            problem.setProperty("errors", List.of(Map.of("field", field, "message", message)));
        }
        if (retryAfter != null) {
            problem.setProperty("retryAfterSeconds", retryAfter.toSeconds());
        }
        return problem;
    }
}
