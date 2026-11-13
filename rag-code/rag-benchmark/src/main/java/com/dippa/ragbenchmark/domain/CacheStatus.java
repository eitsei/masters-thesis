package com.dippa.ragbenchmark.domain;

/**
 * Status of prompt/context caching evaluation for a query.
 */
public enum CacheStatus {
    /**
     * An explicit provider-level context cache was found and reused (warm cache read).
     */
    EXPLICIT_HIT,

    /**
     * An explicit provider-level context cache was created for this query (cold cache write).
     */
    EXPLICIT_MISS,

    /**
     * Context was evaluated for explicit caching but did not qualify (e.g. below the 4096-token threshold).
     * Does not imply or prove that provider-side implicit KV prefix caching occurred.
     */
    NOT_ELIGIBLE,

    /**
     * Prompt caching module M3 is disabled or unconfigured.
     */
    DISABLED,

    /**
     * No caching evaluation was performed.
     */
    NONE
}
