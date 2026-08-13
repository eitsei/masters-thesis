package com.dippa.ragbenchmark.api;

import java.util.List;

/**
 * Response body for {@code POST /api/documents}: acknowledges ingestion with the generated
 * document id and the ids of the chunks written to the vector store.
 */
public record IngestDocumentResponse(String documentId, int chunkCount, List<String> chunkIds) {
}
