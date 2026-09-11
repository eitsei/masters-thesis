package com.dippa.ragbenchmark.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the M2 semantic re-ranking module.
 */
@ConfigurationProperties(prefix = "rag.reranker")
public record RerankerProperties(
        String provider,
        int candidatePoolSize,
        String model,
        VertexProperties vertex) {

    public RerankerProperties {
        if (provider == null || provider.isBlank()) {
            provider = "noop";
        }
        if (candidatePoolSize <= 0) {
            candidatePoolSize = 50;
        }
        if (model == null || model.isBlank()) {
            model = "semantic-ranker-default-004";
        }
        if (vertex == null) {
            vertex = new VertexProperties("", "global", "default_ranking_config");
        }
    }

    public record VertexProperties(String projectId, String location, String rankingConfig) {
        public VertexProperties {
            if (location == null || location.isBlank()) {
                location = "global";
            }
            if (rankingConfig == null || rankingConfig.isBlank()) {
                rankingConfig = "default_ranking_config";
            }
        }
    }
}
