package com.dippa.ragbenchmark.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dippa.ragbenchmark.domain.CacheLookupResult;
import com.dippa.ragbenchmark.domain.CacheRequest;
import com.dippa.ragbenchmark.domain.CacheStatus;
import com.dippa.ragbenchmark.domain.PromptCacheUnavailableException;
import com.dippa.ragbenchmark.infrastructure.config.PromptCacheProperties;
import com.google.genai.types.CachedContentUsageMetadata;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.google.genai.cache.CachedContentRequest;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContent;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContentService;

@ExtendWith(MockitoExtension.class)
class GoogleGenAiPromptCacheTest {

    @Mock
    private GoogleGenAiCachedContentService cachedContentService;

    private PromptCacheProperties properties;
    private GoogleGenAiPromptCache promptCache;

    @BeforeEach
    void setUp() {
        properties = new PromptCacheProperties(
                PromptCacheProperties.Provider.GOOGLE_GENAI,
                4096,
                Duration.ofMinutes(5),
                3.5,
                "test-benchmark-");
        promptCache = new GoogleGenAiPromptCache(cachedContentService, properties, "inst-1");
    }

    private GoogleGenAiCachedContent mockCachedContent(String name, int tokens, Instant expiresAt) {
        GoogleGenAiCachedContent cachedContent = mock(GoogleGenAiCachedContent.class);
        when(cachedContent.getName()).thenReturn(name);
        when(cachedContent.getExpireTime()).thenReturn(expiresAt);

        CachedContentUsageMetadata usage = mock(CachedContentUsageMetadata.class);
        when(usage.totalTokenCount()).thenReturn(Optional.of(tokens));
        when(cachedContent.getUsageMetadata()).thenReturn(usage);

        return cachedContent;
    }

    @Test
    void isConfigured_returnsTrueWhenServiceAvailable() {
        assertThat(promptCache.isConfigured()).isTrue();
        GoogleGenAiPromptCache unconfigured = new GoogleGenAiPromptCache((GoogleGenAiCachedContentService) null, properties);
        assertThat(unconfigured.isConfigured()).isFalse();
    }

    @Test
    void subThresholdRequest_returnsNotEligibleWithoutCallingRemoteService() {
        CacheRequest request = new CacheRequest(
                "key-small", "gemini-3.8-flash", "system prompt", "small context", 2500L);

        CacheLookupResult result = promptCache.getOrCreate(request);

        assertThat(result.status()).isEqualTo(CacheStatus.NOT_ELIGIBLE);
        assertThat(result.tokenCount()).isEqualTo(2500L);
        assertThat(result.isExplicitlyCached()).isFalse();
        verifyNoInteractions(cachedContentService);
    }

    @Test
    void qualifyingRequest_coldMiss_createsRemoteCacheAndTracksOwnership() {
        Instant expiry = Instant.now().plusSeconds(300);
        GoogleGenAiCachedContent mockContent = mockCachedContent("cachedContents/created-1", 5120, expiry);
        when(cachedContentService.create(any(CachedContentRequest.class))).thenReturn(mockContent);

        CacheRequest request = new CacheRequest(
                "key-large-1", "gemini-3.8-flash", "system instruction", "large context text", 5120L);

        CacheLookupResult result = promptCache.getOrCreate(request);

        assertThat(result.status()).isEqualTo(CacheStatus.EXPLICIT_MISS);
        assertThat(result.cacheId()).isEqualTo("cachedContents/created-1");
        assertThat(result.tokenCount()).isEqualTo(5120L);
        assertThat(result.expiresAt()).isEqualTo(expiry);
        assertThat(result.isExplicitlyCached()).isTrue();

        assertThat(promptCache.getOwnedResourceNames()).containsExactly("cachedContents/created-1");

        ArgumentCaptor<CachedContentRequest> captor = ArgumentCaptor.forClass(CachedContentRequest.class);
        verify(cachedContentService).create(captor.capture());
        CachedContentRequest capturedReq = captor.getValue();
        assertThat(capturedReq.getModel()).isEqualTo("gemini-3.8-flash");
        assertThat(capturedReq.getDisplayName()).startsWith("test-benchmark-inst-1-");
    }

    @Test
    void qualifyingRequest_warmHit_returnsCachedResultWithoutCallingRemoteCreateAgain() {
        Instant expiry = Instant.now().plusSeconds(300);
        GoogleGenAiCachedContent mockContent = mockCachedContent("cachedContents/reused-1", 5120, expiry);
        when(cachedContentService.create(any(CachedContentRequest.class))).thenReturn(mockContent);

        CacheRequest request = new CacheRequest(
                "key-repeat", "gemini-3.8-flash", "system instruction", "large context text", 5120L);

        // First call: cold miss
        CacheLookupResult firstResult = promptCache.getOrCreate(request);
        assertThat(firstResult.status()).isEqualTo(CacheStatus.EXPLICIT_MISS);

        // Second call: warm hit
        CacheLookupResult secondResult = promptCache.getOrCreate(request);
        assertThat(secondResult.status()).isEqualTo(CacheStatus.EXPLICIT_HIT);
        assertThat(secondResult.cacheId()).isEqualTo("cachedContents/reused-1");
        assertThat(secondResult.tokenCount()).isEqualTo(5120L);

        verify(cachedContentService, times(1)).create(any(CachedContentRequest.class));
    }

    @Test
    void concurrentRequests_withSameKey_executesSingleFlightCreation() throws Exception {
        Instant expiry = Instant.now().plusSeconds(300);
        GoogleGenAiCachedContent mockContent = mockCachedContent("cachedContents/concurrent-1", 5120, expiry);
        when(cachedContentService.create(any(CachedContentRequest.class))).thenAnswer(inv -> {
            // Introduce a short artificial delay to encourage concurrency race
            Thread.sleep(50);
            return mockContent;
        });

        CacheRequest request = new CacheRequest(
                "key-concurrent", "gemini-3.8-flash", "instruction", "content", 5000L);

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<CacheLookupResult>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                return promptCache.getOrCreate(request);
            }));
        }

        startLatch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        int missCount = 0;
        int hitCount = 0;
        for (Future<CacheLookupResult> f : futures) {
            CacheLookupResult res = f.get();
            assertThat(res.cacheId()).isEqualTo("cachedContents/concurrent-1");
            if (res.status() == CacheStatus.EXPLICIT_MISS) {
                missCount++;
            } else if (res.status() == CacheStatus.EXPLICIT_HIT) {
                hitCount++;
            }
        }

        // Exactly one thread created the remote cache, the remaining threads reused it
        assertThat(missCount).isEqualTo(1);
        assertThat(hitCount).isEqualTo(threadCount - 1);
        verify(cachedContentService, times(1)).create(any(CachedContentRequest.class));
    }

    @Test
    void expiredCacheEntry_isEvictedAndRecreated() {
        Instant pastExpiry = Instant.now().minusSeconds(10);
        GoogleGenAiCachedContent expiredContent = mockCachedContent("cachedContents/old-1", 5120, pastExpiry);

        Instant newExpiry = Instant.now().plusSeconds(300);
        GoogleGenAiCachedContent newContent = mockCachedContent("cachedContents/new-2", 5120, newExpiry);

        when(cachedContentService.create(any(CachedContentRequest.class)))
                .thenReturn(expiredContent)
                .thenReturn(newContent);

        CacheRequest request = new CacheRequest(
                "key-expiry", "gemini-3.8-flash", "instruction", "content", 5000L);

        // First creation with past expiry
        CacheLookupResult first = promptCache.getOrCreate(request);
        assertThat(first.cacheId()).isEqualTo("cachedContents/old-1");

        // Next lookup detects expired entry and recreates
        CacheLookupResult second = promptCache.getOrCreate(request);
        assertThat(second.status()).isEqualTo(CacheStatus.EXPLICIT_MISS);
        assertThat(second.cacheId()).isEqualTo("cachedContents/new-2");

        verify(cachedContentService, times(2)).create(any(CachedContentRequest.class));
    }

    @Test
    void remoteCreationFailure_throwsPromptCacheUnavailableException() {
        when(cachedContentService.create(any(CachedContentRequest.class)))
                .thenThrow(new RuntimeException("GCP 503 Quota exceeded"));

        CacheRequest request = new CacheRequest(
                "key-fail", "gemini-3.8-flash", "instruction", "content", 5000L);

        assertThatThrownBy(() -> promptCache.getOrCreate(request))
                .isInstanceOf(PromptCacheUnavailableException.class)
                .hasMessageContaining("Quota exceeded");
    }

    @Test
    void clearOwned_deletesAllOwnedRemoteCachesAndClearsTracking() {
        GoogleGenAiCachedContent c1 = mockCachedContent("cachedContents/res-1", 5120, Instant.now().plusSeconds(300));
        GoogleGenAiCachedContent c2 = mockCachedContent("cachedContents/res-2", 5120, Instant.now().plusSeconds(300));
        when(cachedContentService.create(any(CachedContentRequest.class))).thenReturn(c1, c2);
        when(cachedContentService.delete(anyString())).thenReturn(true);

        promptCache.getOrCreate(new CacheRequest("k1", "model", "inst", "ctx", 5000L));
        promptCache.getOrCreate(new CacheRequest("k2", "model", "inst", "ctx", 5000L));

        assertThat(promptCache.getOwnedResourceNames()).containsExactlyInAnyOrder(
                "cachedContents/res-1", "cachedContents/res-2");

        promptCache.clearOwned();

        verify(cachedContentService).delete("cachedContents/res-1");
        verify(cachedContentService).delete("cachedContents/res-2");
        assertThat(promptCache.getOwnedResourceNames()).isEmpty();
    }
}
