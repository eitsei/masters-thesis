package com.dippa.ragbenchmark.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for RAG answer generation, bound from {@code rag.generation.*}.
 *
 * <p>Supports controlling provider-specific generation behaviors such as reasoning / thinking
 * token budgets in frontier models (e.g. Gemini 2.5 / 3.x Flash and Pro).</p>
 */
@ConfigurationProperties(prefix = "rag.generation")
public record GenerationProperties(
        Integer thinkingBudget,
        Double temperature,
        Integer retryMaxAttempts,
        Long retryBackoffMs) {

    public static final int DEFAULT_THINKING_BUDGET = 0;
    public static final double DEFAULT_TEMPERATURE = 0.0;
    public static final int DEFAULT_RETRY_MAX_ATTEMPTS = 3;
    public static final long DEFAULT_RETRY_BACKOFF_MS = 5000L;

    public GenerationProperties {
        if (thinkingBudget == null) {
            thinkingBudget = DEFAULT_THINKING_BUDGET;
        }
        if (temperature == null || temperature < 0.0) {
            temperature = DEFAULT_TEMPERATURE;
        }
        if (retryMaxAttempts == null || retryMaxAttempts < 1) {
            retryMaxAttempts = DEFAULT_RETRY_MAX_ATTEMPTS;
        }
        if (retryBackoffMs == null || retryBackoffMs < 0L) {
            retryBackoffMs = DEFAULT_RETRY_BACKOFF_MS;
        }
    }

    public static GenerationProperties defaultProperties() {
        return new GenerationProperties(
                DEFAULT_THINKING_BUDGET,
                DEFAULT_TEMPERATURE,
                DEFAULT_RETRY_MAX_ATTEMPTS,
                DEFAULT_RETRY_BACKOFF_MS);
    }
}
