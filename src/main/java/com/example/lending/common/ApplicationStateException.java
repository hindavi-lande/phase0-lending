package com.example.lending.common;

/**
 * Raised when an operation is not allowed for the current state of an application, for example an
 * invalid status transition or an edit of an application that has already been decided.
 */
public class ApplicationStateException extends RuntimeException {

    public ApplicationStateException(String message) {
        super(message);
    }
}
