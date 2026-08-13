package com.dippa.ragbenchmark.application;

import java.util.List;

/**
 * Outcome of ingesting one raw document: the generated correlation id shared by all of its
 * chunks (stored under the {@code documentId} metadata key), and the ids of the individual
 * chunks written to the vector store.
 */
public record IngestionResult(String documentId, int chunkCount, List<String> chunkIds) {
}
