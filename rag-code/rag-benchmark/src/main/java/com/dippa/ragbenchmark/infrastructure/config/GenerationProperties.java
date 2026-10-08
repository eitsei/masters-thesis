package com.dippa.ragbenchmark.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for RAG answer generation, bound from {@code rag.generation.*}.
 *
 * <p>Supports controlling provider-specific generation behaviors such as reasoning / thinking
 * token budgets in frontier models (e.g. Gemini 2.5 / 3.x Flash and Pro).</p>
 */
@ConfigurationProperties(prefix = "rag.generation")
public record GenerationProperties(Integer thinkingBudget, Double temperature) {

    public static final int DEFAULT_THINKING_BUDGET = 0;
    public static final double DEFAULT_TEMPERATURE = 0.0;

    public GenerationProperties {
        if (thinkingBudget == null) {
            thinkingBudget = DEFAULT_THINKING_BUDGET;
        }
        if (temperature == null || temperature < 0.0) {
            temperature = DEFAULT_TEMPERATURE;
        }
    }

    public static GenerationProperties defaultProperties() {
        return new GenerationProperties(DEFAULT_THINKING_BUDGET, DEFAULT_TEMPERATURE);
    }
}
