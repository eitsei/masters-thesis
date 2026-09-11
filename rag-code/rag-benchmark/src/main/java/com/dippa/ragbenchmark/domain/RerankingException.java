package com.dippa.ragbenchmark.domain;

/**
 * Raised when an M2 semantic re-ranking operation fails due to API errors, communication
 * timeouts, or invalid ranking responses.
 *
 * <p>A failed re-ranking must not silently fall back to unranked vector order during benchmarks:
 * doing so would distort latency and quality evaluation metrics.</p>
 */
public class RerankingException extends RuntimeException {

    public RerankingException(String message) {
        super(message);
    }

    public RerankingException(String message, Throwable cause) {
        super(message, cause);
    }
}
