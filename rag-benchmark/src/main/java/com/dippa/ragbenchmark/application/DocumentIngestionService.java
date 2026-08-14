package com.dippa.ragbenchmark.application;

import com.dippa.ragbenchmark.domain.ChunkPersistenceException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * Ingests raw text into the vector store: splits it into fixed-size token chunks and stores
 * the resulting {@link Document}s via {@link VectorStore#add(List)}.
 *
 * <p><b>Chunking parameters (thesis M0 spec: 512-token chunks, 50-token overlap):</b> Spring AI
 * 2.0's {@link TokenTextSplitter} is configured here with {@code chunkSize = 512} (tokens, via
 * jtokkit). Its public API (constructors and {@link TokenTextSplitter.Builder}) does
 * <b>not</b> expose an overlap parameter at all — it exposes {@code chunkSize},
 * {@code minChunkSizeChars}, {@code minChunkLengthToEmbed}, {@code maxNumChunks} and
 * {@code keepSeparator}, none of which control inter-chunk overlap. This was verified directly
 * against the {@code spring-ai-commons:2.0.0} class file (no source jar was available to
 * double-check via Javadoc). The 50-token overlap called for in the thesis spec is therefore
 * <b>not applied</b> in this v1 implementation; chunks are contiguous, non-overlapping 512-token
 * windows. Flagged here for review — if overlap is required for the benchmark experiments, a
 * custom {@code TextSplitter} would need to be written, since none of the built-in Spring AI
 * splitters support it.</p>
 */
@Service
public class DocumentIngestionService {

    private static final int CHUNK_SIZE_TOKENS = 512;

    private final VectorStore vectorStore;
    private final TokenTextSplitter tokenTextSplitter;

    public DocumentIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.tokenTextSplitter = TokenTextSplitter.builder()
                .withChunkSize(CHUNK_SIZE_TOKENS)
                .build();
    }

    /**
     * Splits {@code text} into chunks and stores them in the vector store.
     *
     * @param text     raw document text (must be non-blank)
     * @param metadata optional caller-supplied metadata, copied onto every chunk; may be {@code null}
     * @return the generated document id and the ids of the stored chunks
     */
    public IngestionResult ingest(String text, Map<String, Object> metadata) {
        String documentId = UUID.randomUUID().toString();

        Map<String, Object> chunkMetadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
        chunkMetadata.put("documentId", documentId);

        Document document = new Document(text, chunkMetadata);
        List<Document> chunks = tokenTextSplitter.split(document);
        List<String> chunkIds = chunks.stream().map(Document::getId).toList();

        try {
            vectorStore.add(chunks);
        } catch (RuntimeException ex) {
            throw new ChunkPersistenceException(documentId, chunkIds, ex);
        }

        return new IngestionResult(documentId, chunkIds.size(), chunkIds);
    }
}
