package com.dippa.ragbenchmark.api;

import java.util.List;

/**
 * Error body returned with HTTP 500 when storing a document's chunks in the vector store fails.
 * {@code documentId}/{@code chunkIds} are included as structured fields (not just folded into
 * {@code message}) so a caller can identify exactly what may have been partially persisted.
 */
public record ChunkPersistenceErrorResponse(String error, String message, String documentId, List<String> chunkIds) {
}
