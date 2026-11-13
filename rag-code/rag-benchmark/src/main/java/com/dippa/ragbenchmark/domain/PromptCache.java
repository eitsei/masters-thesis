package com.dippa.ragbenchmark.domain;

/**
 * Port for managing provider-level prompt and context caching (Module M3).
 *
 * <p>Pure domain interface with zero Spring AI or provider imports. Encapsulates
 * capability reporting, lookup/creation of cached context resources, and explicit
 * clearing of resources owned by the application instance.</p>
 */
public interface PromptCache {

    /**
     * Indicates whether a real caching provider (e.g. Google GenAI / Vertex AI) is configured and active.
     */
    boolean isConfigured();

    /**
     * Looks up an existing cached context by request key or creates a new remote cache resource
     * if the payload qualifies (e.g. meets the token threshold).
     *
     * @param request the cache request containing context payload and metadata
     * @return result indicating cache status (EXPLICIT_HIT, EXPLICIT_MISS, NOT_ELIGIBLE, DISABLED) and metadata
     */
    CacheLookupResult getOrCreate(CacheRequest request);

    /**
     * Explicitly deletes all remote cached resources created by this benchmark application instance
     * and clears local tracking state.
     */
    void clearOwned();
}
