package com.dippa.ragbenchmark.domain;

/**
 * Identifies the mechanism that generated a {@link RoutingDecision}.
 */
public enum RoutingMode {
    /** Dynamic zero-shot classification via Gemini 3.5 Flash-Lite. */
    CLASSIFIER,
    /** Deterministic routing based on dataset ground-truth hop metadata (theoretical upper bound). */
    ORACLE,
    /** Inactive baseline routing (returns default workhorse model). */
    NOOP,
    /** Fallback to default model due to router exception, timeout, or unparseable output. */
    FALLBACK
}
