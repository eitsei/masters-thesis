package com.dippa.ragbenchmark.application;

/**
 * A single retrieved (and possibly reranked) chunk surfaced back to the caller: its vector
 * store id and a short text snippet.
 *
 * <p>Also serialized directly as part of the {@code POST /api/query} response body — see
 * {@link com.dippa.ragbenchmark.api.QueryResponse}.</p>
 */
public record RetrievedChunk(String id, String snippet) {
}
