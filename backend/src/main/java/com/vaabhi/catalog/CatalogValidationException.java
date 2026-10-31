package com.vaabhi.catalog;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * A 400 (or 409) problem detail with field errors, in the same {@code errors: [{field, message}]} shape as bean
 * validation failures, so the admin form can show each message next to its field.
 */
class CatalogValidationException extends ErrorResponseException {

    record FieldError(String field, String message) {}

    CatalogValidationException(HttpStatus status, String field, String message) {
        super(status, problem(status, List.of(new FieldError(field, message))), null);
    }

    CatalogValidationException(List<FieldError> errors) {
        super(HttpStatus.BAD_REQUEST, problem(HttpStatus.BAD_REQUEST, errors), null);
    }

    private static ProblemDetail problem(HttpStatus status, List<FieldError> errors) {
        var problem = ProblemDetail.forStatusAndDetail(status, errors.getFirst().message());
        problem.setProperty("errors", errors);
        return problem;
    }
}
