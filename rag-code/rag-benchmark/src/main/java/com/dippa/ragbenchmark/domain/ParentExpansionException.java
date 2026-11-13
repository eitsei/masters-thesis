package com.dippa.ragbenchmark.domain;

/**
 * Raised when an M1 child-to-parent expansion cannot produce a complete parent context.
 *
 * <p>A failed expansion must not be reported as a successful query using the smaller child
 * chunks: doing so makes M0/M1 benchmark results indistinguishable.</p>
 */
public class ParentExpansionException extends RuntimeException {

    public ParentExpansionException(String message) {
        super(message);
    }

    public ParentExpansionException(String message, Throwable cause) {
        super(message, cause);
    }
}
