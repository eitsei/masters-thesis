package com.dippa.ragbenchmark.domain;

/**
 * Raised when module M4 (Hybrid Model Routing) is requested in a query pipeline, but the
 * configured router provider is 'noop' or inactive.
 */
public class RouterConfigurationException extends RuntimeException {

    public RouterConfigurationException(String message) {
        super(message);
    }
}
