package com.dippa.ragbenchmark.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly typed and validated configuration properties for the M4 Hybrid Model Routing module.
 */
@Validated
@ConfigurationProperties(prefix = "rag.router")
public record RouterProperties(
        @NotNull Provider provider,
        @NotBlank String classifierModel,
        @NotBlank String fallbackModel,
        double temperature,
        int timeoutMs) {

    public enum Provider {
        NOOP,
        CLASSIFIER,
        ORACLE;

        public static Provider parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return NOOP;
            }
            try {
                return Provider.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Unknown router provider '" + raw + "'. Supported: noop, classifier, oracle");
            }
        }
    }

    public RouterProperties {
        if (provider == null) {
            provider = Provider.NOOP;
        }
        if (classifierModel == null || classifierModel.isBlank()) {
            classifierModel = "gemini-3.5-flash-lite";
        }
        if (fallbackModel == null || fallbackModel.isBlank()) {
            fallbackModel = "gemini-3.8-flash";
        }
        if (temperature < 0.0) {
            temperature = 0.0;
        }
        if (timeoutMs <= 0) {
            timeoutMs = 5000;
        }
    }

    public static RouterProperties defaultProperties() {
        return new RouterProperties(
                Provider.NOOP,
                "gemini-3.5-flash-lite",
                "gemini-3.8-flash",
                0.0,
                5000);
    }
}
