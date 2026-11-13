package com.dippa.ragbenchmark.domain;

/**
 * Raised when module M2 is requested in a query pipeline, but the configured reranker provider
 * is 'noop' or inactive.
 */
public class RerankingConfigurationException extends RuntimeException {

    public RerankingConfigurationException(String message) {
        super(message);
    }
}
