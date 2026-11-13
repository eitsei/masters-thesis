package com.dippa.ragbenchmark.api;

import com.dippa.ragbenchmark.domain.CacheLookupResult;
import com.dippa.ragbenchmark.domain.CacheStatus;
import java.time.Instant;

/**
 * Exposes prompt cache outcome and resource metadata in REST API responses.
 */
public record CacheMetadataDto(
        CacheStatus status,
        String cacheId,
        Long tokenCount,
        Instant expiresAt) {

    public static final CacheMetadataDto NONE = new CacheMetadataDto(CacheStatus.NONE, null, null, null);

    public static CacheMetadataDto fromDomain(CacheLookupResult result) {
        if (result == null) {
            return NONE;
        }
        return new CacheMetadataDto(
                result.status(),
                result.cacheId(),
                result.tokenCount() > 0 ? result.tokenCount() : null,
                result.expiresAt());
    }
}
