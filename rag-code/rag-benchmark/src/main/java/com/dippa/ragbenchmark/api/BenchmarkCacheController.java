package com.dippa.ragbenchmark.api;

import com.dippa.ragbenchmark.application.RagPromptRenderer;
import com.dippa.ragbenchmark.domain.CacheLookupResult;
import com.dippa.ragbenchmark.domain.CacheRequest;
import com.dippa.ragbenchmark.domain.CacheStatus;
import com.dippa.ragbenchmark.domain.PromptCache;
import com.dippa.ragbenchmark.domain.PromptCacheUnavailableException;
import com.dippa.ragbenchmark.infrastructure.config.ModelCatalogProperties;
import com.dippa.ragbenchmark.infrastructure.config.PromptCacheProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for prompt cache lifecycle management, benchmark cache clearing, and prewarming.
 */
@RestController
@RequestMapping("/api/benchmark/cache")
public class BenchmarkCacheController {

    private static final Logger logger = LoggerFactory.getLogger(BenchmarkCacheController.class);

    public record PrewarmRequest(List<String> models) {}

    public record PrewarmModelResult(
            String model,
            String cacheId,
            CacheStatus status,
            long tokenCount
    ) {}

    private final PromptCache promptCache;
    private final ObjectProvider<RagPromptRenderer> promptRendererProvider;
    private final ObjectProvider<PromptCacheProperties> promptCachePropertiesProvider;
    private final ObjectProvider<ModelCatalogProperties> modelCatalogPropertiesProvider;

    public BenchmarkCacheController(
            PromptCache promptCache,
            ObjectProvider<RagPromptRenderer> promptRendererProvider,
            ObjectProvider<PromptCacheProperties> promptCachePropertiesProvider,
            ObjectProvider<ModelCatalogProperties> modelCatalogPropertiesProvider) {
        this.promptCache = promptCache;
        this.promptRendererProvider = promptRendererProvider;
        this.promptCachePropertiesProvider = promptCachePropertiesProvider;
        this.modelCatalogPropertiesProvider = modelCatalogPropertiesProvider;
    }

    @PostMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearCache() {
        promptCache.clearOwned();
        return ResponseEntity.ok(Map.of(
                "status", "cleared",
                "configured", promptCache.isConfigured(),
                "message", "Successfully cleared owned prompt cache resources."
        ));
    }

    @PostMapping("/prewarm")
    public ResponseEntity<Map<String, Object>> prewarmCache(@RequestBody(required = false) PrewarmRequest body) {
        if (!promptCache.isConfigured()) {
            return ResponseEntity.ok(Map.of(
                    "status", "noop",
                    "configured", false,
                    "message", "Prompt cache provider is not configured or inactive."
            ));
        }

        try {
            List<String> models = resolveModels(body);
            RagPromptRenderer promptRenderer = promptRendererProvider.getIfAvailable(RagPromptRenderer::new);
            PromptCacheProperties promptCacheProperties = promptCachePropertiesProvider.getIfAvailable(
                    PromptCacheProperties::defaultProperties);

            String systemInstruction = promptRenderer.getSystemInstruction();
            String staticDemonstrations = promptRenderer.getStaticDemonstrations();
            String fullStaticPrefix = promptRenderer.getFullStaticPrefix();

            long estimatedTokens = promptRenderer.estimateTokens(
                    fullStaticPrefix, promptCacheProperties.charsPerTokenEstimate());

            String providerName = promptCacheProperties.provider() != null
                    ? promptCacheProperties.provider().name().toLowerCase(Locale.ROOT)
                    : "google_genai";

            logger.info("Prewarming prompt cache for models: {}", models);
            List<PrewarmModelResult> results = new ArrayList<>();
            for (String model : models) {
                String cacheKey = promptRenderer.computeCanonicalCacheKey(
                        providerName,
                        model,
                        fullStaticPrefix);

                CacheRequest cacheRequest = new CacheRequest(
                        cacheKey,
                        model,
                        systemInstruction,
                        staticDemonstrations,
                        estimatedTokens);

                CacheLookupResult cacheResult = promptCache.getOrCreate(cacheRequest);
                logger.info("Prewarmed prompt cache for model '{}': cacheId={}, status={}, tokenCount={}",
                        model, cacheResult.cacheId(), cacheResult.status(), cacheResult.tokenCount());
                results.add(new PrewarmModelResult(
                        model,
                        cacheResult.cacheId(),
                        cacheResult.status(),
                        cacheResult.tokenCount()));
            }

            return ResponseEntity.ok(Map.of(
                    "status", "prewarmed",
                    "configured", true,
                    "models", results
            ));
        } catch (PromptCacheUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "prompt_cache_unavailable",
                            "message", e.getMessage() != null ? e.getMessage() : "Prompt cache provider unavailable."
                    ));
        }
    }

    private List<String> resolveModels(PrewarmRequest body) {
        if (body != null && body.models() != null && !body.models().isEmpty()) {
            List<String> filtered = body.models().stream()
                    .filter(m -> m != null && !m.isBlank())
                    .distinct()
                    .toList();
            if (!filtered.isEmpty()) {
                return filtered;
            }
        }
        ModelCatalogProperties catalogProps = modelCatalogPropertiesProvider.getIfAvailable();
        if (catalogProps != null && catalogProps.googleGenai() != null) {
            ModelCatalogProperties.GoogleGenAiTiers tiers = catalogProps.googleGenai();
            List<String> defaultModels = Stream.of(tiers.flashLite(), tiers.flash(), tiers.pro())
                    .filter(m -> m != null && !m.isBlank())
                    .toList();
            if (!defaultModels.isEmpty()) {
                return defaultModels;
            }
        }
        return List.of("gemini-3.5-flash-lite", "gemini-3.5-flash", "gemini-3.1-pro");
    }
}
