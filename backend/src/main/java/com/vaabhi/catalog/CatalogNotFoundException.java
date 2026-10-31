package com.vaabhi.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Rendered as a 404 problem detail. Drafts and archived products are reported as not found too. */
class CatalogNotFoundException extends ErrorResponseException {

    CatalogNotFoundException(String what, String slug) {
        super(
                HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, what + " '" + slug + "' not found"),
                null);
    }
}
