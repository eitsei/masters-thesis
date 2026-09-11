package com.dippa.ragbenchmark.api;

import com.dippa.ragbenchmark.domain.PromptCache;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for prompt cache lifecycle management and benchmark cache clearing.
 */
@RestController
@RequestMapping("/api/benchmark/cache")
public class BenchmarkCacheController {

    private final PromptCache promptCache;

    public BenchmarkCacheController(PromptCache promptCache) {
        this.promptCache = promptCache;
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
}
