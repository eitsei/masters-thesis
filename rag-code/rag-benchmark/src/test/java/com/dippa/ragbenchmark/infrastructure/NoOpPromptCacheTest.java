package com.dippa.ragbenchmark.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.dippa.ragbenchmark.domain.CacheLookupResult;
import com.dippa.ragbenchmark.domain.CacheRequest;
import com.dippa.ragbenchmark.domain.CacheStatus;
import org.junit.jupiter.api.Test;

class NoOpPromptCacheTest {

    private final NoOpPromptCache noOpPromptCache = new NoOpPromptCache();

    @Test
    void isConfigured_returnsFalse() {
        assertThat(noOpPromptCache.isConfigured()).isFalse();
    }

    @Test
    void getOrCreate_alwaysReturnsDisabled() {
        CacheRequest request = new CacheRequest(
                "key-123", "model", "instruction", "content", 5000L);

        CacheLookupResult result = noOpPromptCache.getOrCreate(request);

        assertThat(result.status()).isEqualTo(CacheStatus.DISABLED);
        assertThat(result.cacheId()).isNull();
        assertThat(result.isExplicitlyCached()).isFalse();
    }

    @Test
    void clearOwned_completesWithoutError() {
        noOpPromptCache.clearOwned();
    }
}
