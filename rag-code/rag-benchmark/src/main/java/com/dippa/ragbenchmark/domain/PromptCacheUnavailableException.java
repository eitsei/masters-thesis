package com.dippa.ragbenchmark.domain;

/**
 * Raised when an explicit prompt cache operation fails due to downstream provider errors
 * or communication failure.
 */
public class PromptCacheUnavailableException extends RuntimeException {

    public PromptCacheUnavailableException(String message) {
        super(message);
    }

    public PromptCacheUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
