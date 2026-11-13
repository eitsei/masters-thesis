package com.dippa.ragbenchmark.infrastructure;

import com.dippa.ragbenchmark.domain.CacheLookupResult;
import com.dippa.ragbenchmark.domain.CacheRequest;
import com.dippa.ragbenchmark.domain.PromptCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default no-op prompt cache adapter used when prompt caching is disabled or unconfigured.
 */
@Component
@ConditionalOnProperty(name = "rag.prompt-cache.provider", havingValue = "noop", matchIfMissing = true)
public class NoOpPromptCache implements PromptCache {

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public CacheLookupResult getOrCreate(CacheRequest request) {
        return CacheLookupResult.disabled();
    }

    @Override
    public void clearOwned() {
        // No-op: no resources are tracked or managed.
    }
}
