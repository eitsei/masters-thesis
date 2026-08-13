package com.dippa.ragbenchmark.domain;

/**
 * Identifies one of the four RAG optimization modules studied in the thesis.
 *
 * <p>None of these modules are implemented in v1 (the M0 baseline) — they exist as a stable
 * vocabulary that the API and domain ports can already refer to.</p>
 *
 * <ul>
 *     <li>{@code M1} — small-to-big retrieval expansion (child chunk retrieval, parent window expansion)</li>
 *     <li>{@code M2} — re-ranking of retrieved candidates</li>
 *     <li>{@code M3} — prompt / context caching</li>
 *     <li>{@code M4} — model routing / cascading across model tiers</li>
 * </ul>
 */
public enum ModuleId {
    M1,
    M2,
    M3,
    M4
}
