package com.dippa.ragbenchmark.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly typed and validated configuration properties for the M3 prompt caching module.
 */
@Validated
@ConfigurationProperties(prefix = "rag.prompt-cache")
public record PromptCacheProperties(
        @NotNull Provider provider,
        @Min(1) int minTokens,
        @NotNull Duration ttl,
        double charsPerTokenEstimate,
        @NotBlank String displayNamePrefix) {

    public enum Provider {
        NOOP,
        GOOGLE_GENAI;

        public static Provider parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return NOOP;
            }
            String normalized = raw.trim().replace('-', '_').toUpperCase(Locale.ROOT);
            try {
                return Provider.valueOf(normalized);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Unknown prompt cache provider '" + raw + "'. Supported: noop, google-genai");
            }
        }
    }

    public PromptCacheProperties {
        if (provider == null) {
            provider = Provider.NOOP;
        }
        if (minTokens <= 0) {
            minTokens = 4096;
        }
        if (ttl == null) {
            ttl = Duration.ofHours(1);
        }
        if (charsPerTokenEstimate <= 0.0) {
            charsPerTokenEstimate = 3.5;
        }
        if (displayNamePrefix == null || displayNamePrefix.isBlank()) {
            displayNamePrefix = "rag-benchmark-";
        }
    }

    public static PromptCacheProperties defaultProperties() {
        return new PromptCacheProperties(
                Provider.NOOP,
                4096,
                Duration.ofMinutes(5),
                3.5,
                "rag-benchmark-");
    }
}
