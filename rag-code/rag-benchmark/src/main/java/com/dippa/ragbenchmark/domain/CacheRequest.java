package com.dippa.ragbenchmark.domain;

import java.util.Objects;

/**
 * Encapsulates the context payload and metadata required to evaluate, lookup,
 * or create an explicit prompt cache resource.
 */
public record CacheRequest(
        String cacheKey,
        String model,
        String systemInstruction,
        String contextContent,
        long estimatedTokens
) {
    public CacheRequest {
        Objects.requireNonNull(cacheKey, "cacheKey must not be null");
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(systemInstruction, "systemInstruction must not be null");
        Objects.requireNonNull(contextContent, "contextContent must not be null");
    }
}
