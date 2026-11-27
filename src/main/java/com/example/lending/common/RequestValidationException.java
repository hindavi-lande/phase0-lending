package com.example.lending.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Business-level validation failure that is not expressible with bean-validation annotations. The
 * uniform error handler turns it into a 400 response with the same fieldErrors shape used by
 * {@code MethodArgumentNotValidException}.
 */
public class RequestValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public RequestValidationException(Map<String, String> fieldErrors) {
        super("Validation failed");
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public RequestValidationException(String field, String message) {
        this(Map.of(field, message));
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
