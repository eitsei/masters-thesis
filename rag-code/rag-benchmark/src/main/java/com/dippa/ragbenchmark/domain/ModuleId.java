package com.dippa.ragbenchmark.domain;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Identifies one of the four RAG optimization modules studied in the thesis.
 *
 * <p>All four modules are implemented in the benchmark application to support the full
 * 2^4 = 16 factorial evaluation matrix (C0 to C15).</p>
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
    M4;

    /**
     * Parses a raw module identifier string (case-insensitive), e.g. from a query request.
     *
     * @throws InvalidModuleException if {@code raw} doesn't match any {@link ModuleId}
     */
    public static ModuleId parse(String raw) {
        try {
            return ModuleId.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidModuleException(raw, ex);
        }
    }

    /** Formats a set of module ids as sorted, uppercase names, e.g. for an error response body. */
    public static List<String> sortedNames(Set<ModuleId> modules) {
        return modules.stream().map(Enum::name).sorted().toList();
    }
}
