package com.dippa.ragbenchmark.infrastructure.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Reference catalog of model identifiers from the thesis's tiered model design (Chapter 4,
 * System Implementation), bound from {@code rag.models.*} in application.yml.
 *
 * <p>Not yet consumed by any bean: M2 (re-ranking) and M4 (model routing/cascading) are
 * unimplemented in v1 (see TODO.md). This exists so those modules, once built, have a single
 * typed place to read model ids from instead of hardcoding strings at that point. The raw
 * {@code flash} value is already the source of truth for
 * {@code spring.ai.google.genai.chat.model}'s default via a YAML placeholder (see
 * application.yml) -- that link is at the property level, not through this record.</p>
 */
@ConfigurationProperties(prefix = "rag.models")
public record ModelCatalogProperties(GoogleGenAiTiers googleGenai, Reranker reranker) {

    /**
     * The three Gemini tiers M4 (model routing/cascading) will choose between: an ultra-fast/
     * low-cost tier for routing and single-hop queries, a workhorse tier for medium-complexity
     * generation, and a flagship reasoning tier for complex multi-hop synthesis and fallback.
     */
    public record GoogleGenAiTiers(String flashLite, String flash, String pro) {
    }

    /**
     * M2's two candidate re-ranking backends: the GCP Vertex AI Ranking API (production) and a
     * local, offline-capable cross-encoder fallback (either option can be selected — the thesis
     * names both {@code deberta-v3-large} and {@code jina-reranker-v2} as candidates).
     */
    public record Reranker(String vertexDefault, String vertexFast, List<String> localFallbackOptions) {
    }
}
