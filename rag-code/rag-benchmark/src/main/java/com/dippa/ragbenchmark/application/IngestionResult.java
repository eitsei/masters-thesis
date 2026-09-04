package com.dippa.ragbenchmark.application;

import java.util.List;

/**
 * Outcome of ingesting one raw document: the generated correlation id shared by all of its
 * chunks (stored under the {@code documentId} metadata key), and the ids of the individual
 * chunks written to the vector store.
 *
 * <p>Also serialized directly as the {@code POST /api/documents} response body — see
 * {@link com.dippa.ragbenchmark.api.DocumentController}.</p>
 */
public record IngestionResult(String documentId, int chunkCount, List<String> chunkIds) {
}
