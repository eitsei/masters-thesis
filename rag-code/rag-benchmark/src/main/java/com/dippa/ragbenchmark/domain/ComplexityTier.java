package com.dippa.ragbenchmark.domain;

/**
 * Reasoning difficulty tiers evaluated in the thesis (Chapter 4, Section 4.3.4 & Table 4.2).
 *
 * <ul>
 *     <li>{@link #SINGLE_HOP}: Direct factual lookup, single entity, simple definition (target: Gemini 3.5 Flash-Lite).</li>
 *     <li>{@link #TWO_HOP}: Relational query connecting two concepts/entities, multi-passage synthesis (target: Gemini 3.8 Flash).</li>
 *     <li>{@link #MULTI_HOP}: Complex multi-step reasoning, 3+ entity chain, deep analytical deduction (target: Gemini 3.1 Pro).</li>
 * </ul>
 */
public enum ComplexityTier {
    SINGLE_HOP(1),
    TWO_HOP(2),
    MULTI_HOP(3);

    private final int hopEquivalent;

    ComplexityTier(int hopEquivalent) {
        this.hopEquivalent = hopEquivalent;
    }

    public int hopEquivalent() {
        return hopEquivalent;
    }

    public static ComplexityTier fromHopCount(int hops) {
        if (hops <= 1) {
            return SINGLE_HOP;
        } else if (hops == 2) {
            return TWO_HOP;
        } else {
            return MULTI_HOP;
        }
    }
}
