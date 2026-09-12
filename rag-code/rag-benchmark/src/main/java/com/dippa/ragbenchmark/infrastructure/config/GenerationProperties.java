package com.dippa.ragbenchmark.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for RAG answer generation, bound from {@code rag.generation.*}.
 *
 * <p>Supports controlling provider-specific generation behaviors such as reasoning / thinking
 * token budgets in frontier models (e.g. Gemini 2.5 / 3.x Flash and Pro).</p>
 */
@ConfigurationProperties(prefix = "rag.generation")
public record GenerationProperties(Integer thinkingBudget) {

    public static final int DEFAULT_THINKING_BUDGET = 0;

    public GenerationProperties {
        if (thinkingBudget == null) {
            thinkingBudget = DEFAULT_THINKING_BUDGET;
        }
    }

    public static GenerationProperties defaultProperties() {
        return new GenerationProperties(DEFAULT_THINKING_BUDGET);
    }
}
