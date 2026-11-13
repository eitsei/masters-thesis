package com.dippa.ragbenchmark.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Result of looking up or creating an explicit context cache in the provider infrastructure.
 */
public record CacheLookupResult(
        CacheStatus status,
        String cacheId,
        long tokenCount,
        Instant expiresAt
) {
    public CacheLookupResult {
        Objects.requireNonNull(status, "status must not be null");
    }

    public static CacheLookupResult hit(String cacheId, long tokenCount, Instant expiresAt) {
        return new CacheLookupResult(CacheStatus.EXPLICIT_HIT, cacheId, tokenCount, expiresAt);
    }

    public static CacheLookupResult miss(String cacheId, long tokenCount, Instant expiresAt) {
        return new CacheLookupResult(CacheStatus.EXPLICIT_MISS, cacheId, tokenCount, expiresAt);
    }

    public static CacheLookupResult notEligible(long estimatedTokens) {
        return new CacheLookupResult(CacheStatus.NOT_ELIGIBLE, null, estimatedTokens, null);
    }

    public static CacheLookupResult disabled() {
        return new CacheLookupResult(CacheStatus.DISABLED, null, 0L, null);
    }

    public static CacheLookupResult none() {
        return new CacheLookupResult(CacheStatus.NONE, null, 0L, null);
    }

    public boolean isExplicitlyCached() {
        return status == CacheStatus.EXPLICIT_HIT || status == CacheStatus.EXPLICIT_MISS;
    }
}
