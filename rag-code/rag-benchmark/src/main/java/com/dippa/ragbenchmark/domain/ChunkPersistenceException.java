package com.dippa.ragbenchmark.domain;

import java.util.List;

/**
 * Thrown when {@code VectorStore.add} fails while ingesting a document. Chunk ids are
 * generated before insertion, so even on failure this carries exactly which
 * {@code documentId}/{@code chunkIds} may have been partially persisted — {@code VectorStore}
 * gives no partial-failure reporting of its own.
 */
public class ChunkPersistenceException extends RuntimeException {

    private final String documentId;
    private final List<String> chunkIds;

    public ChunkPersistenceException(String documentId, List<String> chunkIds, Throwable cause) {
        super("Failed to store chunks for documentId=" + documentId
                + "; some of chunkIds=" + chunkIds + " may have been partially persisted", cause);
        this.documentId = documentId;
        this.chunkIds = chunkIds;
    }

    public String getDocumentId() {
        return documentId;
    }

    public List<String> getChunkIds() {
        return chunkIds;
    }
}
