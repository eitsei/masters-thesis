package com.dippa.ragbenchmark.domain;

/**
 * Raised when an unexpected failure occurs during model routing evaluation.
 */
public class RouterException extends RuntimeException {

    public RouterException(String message) {
        super(message);
    }

    public RouterException(String message, Throwable cause) {
        super(message, cause);
    }
}
