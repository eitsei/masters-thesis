package com.dippa.ragbenchmark.domain;

/**
 * Raised when module M3 is requested in a query pipeline, but the prompt caching provider
 * is unconfigured, inactive, or missing required credentials.
 */
public class PromptCacheConfigurationException extends RuntimeException {

    public PromptCacheConfigurationException(String message) {
        super(message);
    }

    public PromptCacheConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
