package com.dippa.ragbenchmark.application;

/**
 * A single retrieved (and possibly reranked) chunk surfaced back to the caller: its vector
 * store id and a short text snippet.
 */
public record RetrievedChunk(String id, String snippet) {
}
