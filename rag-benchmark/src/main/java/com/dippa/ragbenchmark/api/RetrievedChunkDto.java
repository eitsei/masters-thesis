package com.dippa.ragbenchmark.api;

/**
 * A retrieved chunk surfaced in the {@code POST /api/query} response.
 */
public record RetrievedChunkDto(String id, String snippet) {
}
