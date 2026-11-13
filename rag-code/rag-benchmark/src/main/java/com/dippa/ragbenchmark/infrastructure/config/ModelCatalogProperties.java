package com.dippa.ragbenchmark.infrastructure.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Reference catalog of model identifiers from the thesis's tiered model design (Chapter 4,
 * System Implementation), bound from {@code rag.models.*} in application.yml.
 *
 * <p>Consumed across modules: M2 (semantic re-ranking) reads candidate model names for Vertex AI
 * and local cross-encoders, and M4 (model routing/cascading) uses the three Gemini tiers
 * ({@code flashLite}, {@code flash}, {@code pro}) for dynamic query routing, classifier execution,
 * and empirical oracle mapping. The raw {@code flash} value also serves as
 * {@code spring.ai.google.genai.chat.model}'s default via a YAML placeholder.</p>
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
