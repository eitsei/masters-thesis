package com.dippa.ragbenchmark.domain;

import java.util.Optional;

/**
 * Checks for and stores cached prompt/context content, keyed by an opaque cache key
 * (e.g. a hash of the prompt or retrieval context).
 *
 * <p>Spring AI has no built-in opinion on prompt/context caching, so this is a project-owned
 * port. Not needed by the M0 baseline — no bean is registered for it in v1. A real
 * implementation (M3) can satisfy this interface later.</p>
 */
public interface PromptCache {

    Optional<String> get(String key);

    void put(String key, String value);
}
