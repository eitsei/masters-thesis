package com.dippa.ragbenchmark.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly typed and validated configuration properties for the M2 semantic re-ranking module.
 */
@Validated
@ConfigurationProperties(prefix = "rag.reranker")
public record RerankerProperties(
        @NotNull Provider provider,
        @Min(1) int candidatePoolSize,
        @NotBlank String model,
        @Valid @NotNull VertexProperties vertex) {

    public enum Provider {
        NOOP,
        VERTEX,
        LOCAL;

        public static Provider parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return NOOP;
            }
            try {
                return Provider.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Unknown reranker provider '" + raw + "'. Supported: noop, vertex, local");
            }
        }
    }

    public RerankerProperties {
        if (provider == null) {
            provider = Provider.NOOP;
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

    public static RerankerProperties defaultProperties() {
        return new RerankerProperties(
                Provider.NOOP,
                50,
                "semantic-ranker-default-004",
                new VertexProperties("", "global", "default_ranking_config"));
    }

    public record VertexProperties(
            String projectId,
            @NotBlank String location,
            @NotBlank String rankingConfig) {

        public VertexProperties {
            if (projectId == null) {
                projectId = "";
            }
            if (location == null || location.isBlank()) {
                location = "global";
            }
            if (rankingConfig == null || rankingConfig.isBlank()) {
                rankingConfig = "default_ranking_config";
            }
        }
    }
}
