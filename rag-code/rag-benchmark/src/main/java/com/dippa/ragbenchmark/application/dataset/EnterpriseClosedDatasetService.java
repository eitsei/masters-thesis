package com.dippa.ragbenchmark.application.dataset;

import com.dippa.ragbenchmark.domain.dataset.MusiqueParagraph;
import com.dippa.ragbenchmark.domain.dataset.MusiqueQuestion;
import com.dippa.ragbenchmark.infrastructure.PgVectorStoreFactory;
import com.dippa.ragbenchmark.infrastructure.dataset.EnterpriseClosedDatasetProperties;
import com.dippa.ragbenchmark.infrastructure.dataset.EnterpriseClosedDatasetReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Service orchestrating the closed-corpus Enterprise dataset ingestion into pgvector.
 *
 * <p>Populates three dedicated tables matching the MuSiQue closed-corpus methodology:
 * <ul>
 *   <li>{@code vector_store_enterprise_closed} - deduplicated paragraphs for Baseline/C0, C2, C3, C4</li>
 *   <li>{@code vector_store_enterprise_closed_large} - 1024-token parent context chunks for S2B/M1</li>
 *   <li>{@code vector_store_enterprise_closed_small} - 128-token child retrieval index chunks for S2B/M1</li>
 * </ul>
 * </p>
 */
@Service
public class EnterpriseClosedDatasetService {

    private static final Logger logger = LoggerFactory.getLogger(EnterpriseClosedDatasetService.class);

    private final EnterpriseClosedDatasetReader reader;
    private final EnterpriseClosedDatasetProperties properties;
    private final VectorStore defaultVectorStore;
    private final PgVectorStoreFactory vectorStoreFactory;
    private final JdbcTemplate jdbcTemplate;
    private final java.util.concurrent.atomic.AtomicBoolean ingestionInProgress = new java.util.concurrent.atomic.AtomicBoolean(false);

    @Autowired
    public EnterpriseClosedDatasetService(
            EnterpriseClosedDatasetReader reader,
            EnterpriseClosedDatasetProperties properties,
            VectorStore defaultVectorStore,
            ObjectProvider<PgVectorStoreFactory> vectorStoreFactoryProvider,
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.reader = reader;
        this.properties = properties;
        this.defaultVectorStore = defaultVectorStore;
        this.vectorStoreFactory = vectorStoreFactoryProvider != null ? vectorStoreFactoryProvider.getIfAvailable() : null;
        this.jdbcTemplate = jdbcTemplateProvider != null ? jdbcTemplateProvider.getIfAvailable() : null;
    }

    public record EnterpriseClosedIngestionSummary(
            int questionsCount,
            int uniqueParagraphsCount,
            int baselineChunksWritten,
            int parentChunksWritten,
            int childChunksWritten,
            int batchCount,
            long durationMs,
            String mode
    ) {}

    /**
     * Executes closed-corpus ingestion according to the requested mode ("original", "s2b", or "both").
     */
    public EnterpriseClosedIngestionSummary ingest(String mode, boolean clearDatabase) {
        String effectiveMode = (mode != null && !mode.isBlank()) ? mode.toLowerCase(Locale.ROOT) : "both";

        if (!ingestionInProgress.compareAndSet(false, true)) {
            throw new IllegalStateException("A closed-corpus ingestion run is already in progress.");
        }
        try {
            if (clearDatabase) {
                clearTables(effectiveMode);
            }

            long startTime = System.currentTimeMillis();
            List<MusiqueQuestion> questions = reader.loadAll();
            Map<String, MusiqueParagraph> uniqueParagraphs = extractUniqueParagraphs(questions);

            int baselineChunks = 0;
            int parentChunks = 0;
            int childChunks = 0;
            int totalBatches = 0;

            if ("original".equals(effectiveMode) || "baseline".equals(effectiveMode) || "both".equals(effectiveMode) || "all".equals(effectiveMode)) {
                logger.info("Ingesting Enterprise closed-corpus baseline chunks ({} unique paragraphs)...", uniqueParagraphs.size());
                BaselineIngestResult baselineResult = ingestOriginal(uniqueParagraphs);
                baselineChunks = baselineResult.documentCount();
                totalBatches += baselineResult.batchCount();
            }

            if ("s2b".equals(effectiveMode) || "small-to-big".equals(effectiveMode) || "both".equals(effectiveMode) || "all".equals(effectiveMode)) {
                logger.info("Ingesting Enterprise closed-corpus Small-to-Big chunks...");
                S2bIngestResult s2bResult = ingestSmallToBig(uniqueParagraphs);
                parentChunks = s2bResult.parentCount();
                childChunks = s2bResult.childCount();
                totalBatches += s2bResult.batchCount();
            }

            long durationMs = System.currentTimeMillis() - startTime;
            logger.info("Completed Enterprise closed-corpus ingestion in {} ms (mode={}, questions={}, uniqueParas={}, baseline={}, parents={}, children={})",
                    durationMs, effectiveMode, questions.size(), uniqueParagraphs.size(), baselineChunks, parentChunks, childChunks);

            return new EnterpriseClosedIngestionSummary(
                    questions.size(),
                    uniqueParagraphs.size(),
                    baselineChunks,
                    parentChunks,
                    childChunks,
                    totalBatches,
                    durationMs,
                    effectiveMode
            );
        } finally {
            ingestionInProgress.set(false);
        }
    }

    private Map<String, MusiqueParagraph> extractUniqueParagraphs(List<MusiqueQuestion> questions) {
        Map<String, MusiqueParagraph> unique = new LinkedHashMap<>();
        if (questions == null) return unique;
        for (MusiqueQuestion q : questions) {
            if (q == null || q.paragraphs() == null) continue;
            for (MusiqueParagraph p : q.paragraphs()) {
                if (p == null || p.paragraphText() == null || p.paragraphText().isBlank()) continue;
                String title = p.title() != null ? p.title() : "";
                String key = title + "\n" + p.paragraphText();
                unique.putIfAbsent(key, p);
            }
        }
        return unique;
    }

    private BaselineIngestResult ingestOriginal(Map<String, MusiqueParagraph> uniqueParagraphs) {
        int baselineChunkSize = properties.baselineChunkSize() > 0 ? properties.baselineChunkSize() : 512;
        TokenTextSplitter splitter = TokenTextSplitter.builder().withChunkSize(baselineChunkSize).build();

        List<Document> documents = new ArrayList<>(uniqueParagraphs.size());
        for (MusiqueParagraph p : uniqueParagraphs.values()) {
            String text = properties.includeTitleInChunkText()
                    ? (p.title() != null && !p.title().isBlank() ? p.title() + "\n\n" + p.paragraphText() : p.paragraphText())
                    : p.paragraphText();

            String safeTitle = p.title() != null ? p.title() : "";
            Document rawDoc = new Document(text, Map.of("title", safeTitle));
            List<Document> chunks = splitter.split(rawDoc);

            for (int i = 0; i < chunks.size(); i++) {
                Document chunk = chunks.get(i);
                String deterministicId = UUID.nameUUIDFromBytes(
                        ("enterprise_closed:baseline:" + p.title() + "\n" + p.paragraphText() + "#" + i).getBytes(StandardCharsets.UTF_8)
                ).toString();

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("dataset", "enterprise");
                metadata.put("corpus_mode", "closed");
                metadata.put("title", p.title());
                metadata.put("source", "enterprise_100_final.jsonl");
                metadata.put("chunk_index", i);
                metadata.put("total_chunks", chunks.size());

                documents.add(new Document(deterministicId, chunk.getText(), metadata));
            }
        }

        VectorStore targetStore = (vectorStoreFactory != null)
                ? vectorStoreFactory.getStore("vector_store_enterprise_closed")
                : defaultVectorStore;

        int batchSize = properties.batchSize() > 0 ? properties.batchSize() : 100;
        logger.info("Writing {} baseline documents to vector_store_enterprise_closed in batches of {}", documents.size(), batchSize);
        int batches = addInBatches(targetStore, documents, batchSize);
        return new BaselineIngestResult(documents.size(), batches);
    }

    private record BaselineIngestResult(int documentCount, int batchCount) {}

    private record S2bIngestResult(int parentCount, int childCount, int batchCount) {}

    private S2bIngestResult ingestSmallToBig(Map<String, MusiqueParagraph> uniqueParagraphs) {
        // Group paragraphs by title into virtual document articles
        Map<String, List<MusiqueParagraph>> paragraphsByTitle = new LinkedHashMap<>();
        for (MusiqueParagraph p : uniqueParagraphs.values()) {
            paragraphsByTitle.computeIfAbsent(p.title(), k -> new ArrayList<>()).add(p);
        }

        int parentChunkSize = properties.parentChunkSize() > 0 ? properties.parentChunkSize() : 1024;
        int childChunkSize = properties.childChunkSize() > 0 ? properties.childChunkSize() : 128;

        TokenTextSplitter parentSplitter = TokenTextSplitter.builder().withChunkSize(parentChunkSize).build();
        TokenTextSplitter childSplitter = TokenTextSplitter.builder().withChunkSize(childChunkSize).build();

        List<Document> parentDocuments = new ArrayList<>();
        List<Document> childDocuments = new ArrayList<>();

        for (Map.Entry<String, List<MusiqueParagraph>> entry : paragraphsByTitle.entrySet()) {
            String title = entry.getKey();
            List<MusiqueParagraph> paras = entry.getValue();

            StringBuilder articleBuilder = new StringBuilder();
            articleBuilder.append(title).append("\n\n");
            for (MusiqueParagraph p : paras) {
                articleBuilder.append(p.paragraphText()).append("\n\n");
            }
            String fullArticleText = articleBuilder.toString().trim();

            String safeTitle = title != null ? title : "";
            Document articleDoc = new Document(fullArticleText, Map.of("title", safeTitle));
            List<Document> parentChunks = parentSplitter.split(articleDoc);

            for (int parentIdx = 0; parentIdx < parentChunks.size(); parentIdx++) {
                Document parentChunk = parentChunks.get(parentIdx);
                String parentId = UUID.nameUUIDFromBytes(
                        ("enterprise_closed:parent:" + title + "#" + parentIdx).getBytes(StandardCharsets.UTF_8)
                ).toString();

                Map<String, Object> parentMeta = new HashMap<>();
                parentMeta.put("dataset", "enterprise");
                parentMeta.put("corpus_mode", "closed");
                parentMeta.put("title", title);
                parentMeta.put("source", "enterprise_100_final.jsonl");
                parentMeta.put("chunk_type", "parent");
                parentMeta.put("chunk_index", parentIdx);
                parentMeta.put("total_parent_chunks", parentChunks.size());

                parentDocuments.add(new Document(parentId, parentChunk.getText(), parentMeta));

                List<Document> childChunks = childSplitter.split(new Document(parentChunk.getText()));
                for (int childIdx = 0; childIdx < childChunks.size(); childIdx++) {
                    Document childChunk = childChunks.get(childIdx);
                    String childId = UUID.nameUUIDFromBytes(
                            ("enterprise_closed:child:" + parentId + "#" + childIdx).getBytes(StandardCharsets.UTF_8)
                    ).toString();

                    Map<String, Object> childMeta = new HashMap<>();
                    childMeta.put("dataset", "enterprise");
                    childMeta.put("corpus_mode", "closed");
                    childMeta.put("title", title);
                    childMeta.put("source", "enterprise_100_final.jsonl");
                    childMeta.put("chunk_type", "child");
                    childMeta.put("parent_id", parentId);
                    childMeta.put("parent_table", "vector_store_enterprise_closed_large");
                    childMeta.put("chunk_index", childIdx);
                    childMeta.put("total_child_chunks", childChunks.size());

                    childDocuments.add(new Document(childId, childChunk.getText(), childMeta));
                }
            }
        }

        VectorStore largeStore = (vectorStoreFactory != null)
                ? vectorStoreFactory.getStore("vector_store_enterprise_closed_large")
                : defaultVectorStore;
        VectorStore smallStore = (vectorStoreFactory != null)
                ? vectorStoreFactory.getStore("vector_store_enterprise_closed_small")
                : defaultVectorStore;

        int parentBatchSize = properties.parentBatchSize() > 0 ? properties.parentBatchSize() : 10;
        int childBatchSize = properties.childBatchSize() > 0 ? properties.childBatchSize() : 25;

        logger.info("Writing {} parent chunks to vector_store_enterprise_closed_large (batchSize={})", parentDocuments.size(), parentBatchSize);
        int parentBatches = addInBatches(largeStore, parentDocuments, parentBatchSize);

        logger.info("Writing {} child chunks to vector_store_enterprise_closed_small (batchSize={})", childDocuments.size(), childBatchSize);
        int childBatches = addInBatches(smallStore, childDocuments, childBatchSize);

        return new S2bIngestResult(parentDocuments.size(), childDocuments.size(), parentBatches + childBatches);
    }

    private int addInBatches(VectorStore store, List<Document> documents, int batchSize) {
        if (documents == null || documents.isEmpty()) return 0;
        int totalBatches = (documents.size() + batchSize - 1) / batchSize;
        for (int i = 0; i < documents.size(); i += batchSize) {
            int toIndex = Math.min(i + batchSize, documents.size());
            List<Document> batch = documents.subList(i, toIndex);
            int batchNum = (i / batchSize) + 1;
            int retries = 0;
            while (true) {
                try {
                    store.add(batch);
                    if (batchNum % 10 == 0 || batchNum == totalBatches) {
                        logger.info("Ingested batch {}/{} ({}/{} chunks)", batchNum, totalBatches, toIndex, documents.size());
                    }
                    break;
                } catch (Exception ex) {
                    retries++;
                    if (retries > 3) {
                        logger.error("Failed adding batch {}/{} after {} attempts: {}", batchNum, totalBatches, retries, ex.getMessage(), ex);
                        throw ex;
                    }
                    logger.warn("Transient error adding batch {}/{} (retry {}/3): {}", batchNum, totalBatches, retries, ex.getMessage());
                    try {
                        Thread.sleep(2000L * retries);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ie);
                    }
                }
            }
        }
        return totalBatches;
    }

    public int clearTables(String mode) {
        if (jdbcTemplate == null) {
            logger.warn("JdbcTemplate is null, skipping table truncation");
            return 0;
        }

        List<String> tables;
        String normalized = mode != null ? mode.trim().toLowerCase(Locale.ROOT) : "all";
        if ("s2b".equals(normalized) || "small-to-big".equals(normalized)) {
            tables = List.of("vector_store_enterprise_closed_large", "vector_store_enterprise_closed_small");
        } else if ("original".equals(normalized) || "baseline".equals(normalized)) {
            tables = List.of("vector_store_enterprise_closed");
        } else {
            tables = List.of("vector_store_enterprise_closed", "vector_store_enterprise_closed_large", "vector_store_enterprise_closed_small");
        }

        int count = 0;
        for (String table : tables) {
            try {
                jdbcTemplate.execute("TRUNCATE TABLE " + table);
                logger.info("Cleared table {}", table);
                count++;
            } catch (Exception ex) {
                logger.warn("Failed to truncate table {}: {}", table, ex.getMessage());
            }
        }
        return count;
    }
}
