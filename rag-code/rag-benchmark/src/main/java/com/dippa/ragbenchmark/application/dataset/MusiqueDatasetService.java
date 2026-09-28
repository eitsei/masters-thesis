package com.dippa.ragbenchmark.application.dataset;

import com.dippa.ragbenchmark.domain.dataset.MusiqueParagraph;
import com.dippa.ragbenchmark.domain.dataset.MusiqueQuestion;
import com.dippa.ragbenchmark.infrastructure.dataset.MusiqueDatasetProperties;
import com.dippa.ragbenchmark.infrastructure.dataset.MusiqueDatasetReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import com.dippa.ragbenchmark.infrastructure.PgVectorStoreFactory;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for accessing MuSiQue benchmark questions and ingesting the benchmark corpus into pgvector.
 */
@Service
public class MusiqueDatasetService {

    private static final Logger logger = LoggerFactory.getLogger(MusiqueDatasetService.class);

    private final MusiqueDatasetReader reader;
    private final MusiqueDatasetProperties properties;
    private final VectorStore vectorStore;
    private final PgVectorStoreFactory vectorStoreFactory;
    private final JdbcTemplate jdbcTemplate;
    private final AtomicBoolean ingestionInProgress = new AtomicBoolean(false);

    public MusiqueDatasetService(MusiqueDatasetReader reader, MusiqueDatasetProperties properties, VectorStore vectorStore) {
        this(reader, properties, vectorStore, (PgVectorStoreFactory) null, (JdbcTemplate) null);
    }

    public MusiqueDatasetService(
            MusiqueDatasetReader reader,
            MusiqueDatasetProperties properties,
            VectorStore vectorStore,
            PgVectorStoreFactory vectorStoreFactory) {
        this(reader, properties, vectorStore, vectorStoreFactory, (JdbcTemplate) null);
    }

    public MusiqueDatasetService(
            MusiqueDatasetReader reader,
            MusiqueDatasetProperties properties,
            VectorStore vectorStore,
            PgVectorStoreFactory vectorStoreFactory,
            JdbcTemplate jdbcTemplate) {
        this.reader = reader;
        this.properties = properties;
        this.vectorStore = vectorStore;
        this.vectorStoreFactory = vectorStoreFactory;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Autowired
    public MusiqueDatasetService(
            MusiqueDatasetReader reader,
            MusiqueDatasetProperties properties,
            VectorStore vectorStore,
            ObjectProvider<PgVectorStoreFactory> vectorStoreFactoryProvider,
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this(
                reader,
                properties,
                vectorStore,
                vectorStoreFactoryProvider != null ? vectorStoreFactoryProvider.getIfAvailable() : null,
                jdbcTemplateProvider != null ? jdbcTemplateProvider.getIfAvailable() : null
        );
    }

    /**
     * Retrieves questions with optional filtering by hop count, sampling mode, and pagination.
     */
    public List<MusiqueQuestion> getQuestions(int offset, int limit, Integer hopFilter, boolean sampleOnly) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
        if (hopFilter != null && (hopFilter < 2 || hopFilter > 4)) {
            throw new IllegalArgumentException("hops must be between 2 and 4");
        }
        List<MusiqueQuestion> source = sampleOnly ? reader.getCanonicalBenchmarkSample() : reader.loadAll();

        return source.stream()
                .filter(q -> hopFilter == null || q.hopCount() == hopFilter)
                .skip(Math.max(0, offset))
                .limit(limit > 0 ? limit : 20)
                .toList();
    }

    /**
     * Looks up a single question by ID.
     */
    public Optional<MusiqueQuestion> getQuestionById(String id) {
        return reader.findById(id);
    }

    /**
     * Returns the canonical 100-question benchmark sample (thesis §4.2.1).
     */
    public List<MusiqueQuestion> getCanonicalBenchmarkSample() {
        return reader.getCanonicalBenchmarkSample();
    }

    /**
     * Returns summary statistics about the MuSiQue dataset.
     */
    public Map<String, Object> getDatasetStats() {
        List<MusiqueQuestion> all = reader.loadAll();
        Map<Integer, Long> hopCounts = new HashMap<>();
        long answerableCount = 0;
        long unanswerableCount = 0;
        for (MusiqueQuestion q : all) {
            hopCounts.merge(q.hopCount(), 1L, Long::sum);
            if (q.answerable()) {
                answerableCount++;
            } else {
                unanswerableCount++;
            }
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalQuestions", all.size());
        stats.put("twoHopCount", hopCounts.getOrDefault(2, 0L));
        stats.put("threeHopCount", hopCounts.getOrDefault(3, 0L));
        stats.put("fourHopCount", hopCounts.getOrDefault(4, 0L));
        stats.put("answerableCount", answerableCount);
        stats.put("unanswerableCount", unanswerableCount);
        stats.put("canonicalSampleSize", properties.sampleSize());
        stats.put("datasetPath", properties.filePath());
        return stats;
    }

    /**
     * Truncates target tables in pgvector according to the specified mode.
     *
     * @param mode ingestion mode determining which tables to truncate
     * @return number of tables successfully truncated
     */
    public int clearTables(String mode) {
        if (jdbcTemplate == null) {
            logger.warn("JdbcTemplate is null, skipping table truncation");
            return 0;
        }

        List<String> tables;
        String normalized = mode != null ? mode.trim().toLowerCase(Locale.ROOT) : "original";
        if ("s2b".equals(normalized) || "small-to-big".equals(normalized)) {
            tables = List.of("vector_store_musique_large", "vector_store_musique_small");
        } else if ("baseline".equals(normalized) || "baseline-only".equals(normalized)) {
            tables = List.of("vector_store_musique");
        } else {
            // "original", "both", "all"
            tables = List.of("vector_store_musique", "vector_store_musique_large", "vector_store_musique_small");
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

    public record BaselineIngestResult(int chunkCount, int batchCount) {}
    public record S2bIngestResult(int parentCount, int childCount, int batchCount) {}

    /**
     * Ingests paragraphs from MuSiQue questions into the vector store.
     * Defaults to "original" mode (runs baseline chunking followed by Small-to-Big chunking).
     *
     * @param sampleOnly if true, ingests paragraphs only from the stratified benchmark sample
     * @param sampleSize number of questions to sample if sampleOnly is true (if <= 0, uses properties default)
     * @return summary of the ingestion execution
     */
    public MusiqueIngestionSummary ingest(boolean sampleOnly, int sampleSize) {
        return ingest(sampleOnly, sampleSize, "original", false);
    }

    /**
     * Ingests paragraphs from MuSiQue questions into the vector store in the specified mode without clearing tables.
     */
    public MusiqueIngestionSummary ingest(boolean sampleOnly, int sampleSize, String mode) {
        return ingest(sampleOnly, sampleSize, mode, false);
    }

    /**
     * Executes MuSiQue ingestion according to requested mode ("original", "baseline", "s2b", or "both").
     * If clearDatabase is true, truncates target tables prior to ingestion.
     *
     * <p>Mode {@code "original"} (or {@code "both"}, {@code "all"}) ingests normal chunks into
     * {@code vector_store_musique} first, followed by Small-to-Big chunks into {@code vector_store_musique_large}
     * and {@code vector_store_musique_small}.</p>
     */
    public MusiqueIngestionSummary ingest(boolean sampleOnly, int sampleSize, String mode, boolean clearDatabase) {
        String effectiveMode = (mode != null && !mode.isBlank()) ? mode.toLowerCase(Locale.ROOT) : "original";
        java.util.Set<String> validModes = java.util.Set.of("original", "both", "all", "baseline", "baseline-only", "s2b", "small-to-big");
        if (!validModes.contains(effectiveMode)) {
            throw new IllegalArgumentException("Unsupported MuSiQue ingestion mode: " + mode);
        }

        if (!ingestionInProgress.compareAndSet(false, true)) {
            throw new IllegalStateException("A MuSiQue ingestion run is already in progress.");
        }
        try {
            boolean tablesCleared = false;
            if (clearDatabase) {
                clearTables(effectiveMode);
                tablesCleared = true;
            }

            long startTime = System.currentTimeMillis();
            int targetSampleSize = sampleSize > 0 ? sampleSize : properties.sampleSize();
            List<MusiqueQuestion> selectedQuestions = sampleOnly
                    ? reader.getStratifiedSample(
                            targetSampleSize,
                            properties.twoHopRatio(),
                            properties.threeHopRatio(),
                            properties.fourHopRatio(),
                            properties.unanswerableRatio(),
                            properties.randomSeed())
                    : reader.loadAll();

            logger.info("Starting MuSiQue ingestion from {} question(s) (sampleOnly={}, mode={}, clearDatabase={})",
                    selectedQuestions.size(), sampleOnly, effectiveMode, clearDatabase);

            Map<String, MusiqueParagraph> uniqueParagraphs = extractUniqueParagraphs(selectedQuestions);

            int baselineChunks = 0;
            int parentChunks = 0;
            int childChunks = 0;
            int totalBatches = 0;

            if ("original".equals(effectiveMode) || "baseline".equals(effectiveMode) || "baseline-only".equals(effectiveMode)
                    || "both".equals(effectiveMode) || "all".equals(effectiveMode)) {
                logger.info("Ingesting MuSiQue baseline chunks ({} unique paragraphs)...", uniqueParagraphs.size());
                BaselineIngestResult baselineResult = ingestOriginalChunks(uniqueParagraphs);
                baselineChunks = baselineResult.chunkCount();
                totalBatches += baselineResult.batchCount();
            }

            if ("original".equals(effectiveMode) || "s2b".equals(effectiveMode) || "small-to-big".equals(effectiveMode)
                    || "both".equals(effectiveMode) || "all".equals(effectiveMode)) {
                logger.info("Ingesting MuSiQue Small-to-Big chunks...");
                S2bIngestResult s2bResult = ingestSmallToBigChunks(uniqueParagraphs);
                parentChunks = s2bResult.parentCount();
                childChunks = s2bResult.childCount();
                totalBatches += s2bResult.batchCount();
            }

            long durationMs = System.currentTimeMillis() - startTime;
            logger.info("Completed MuSiQue ingestion in {} ms (mode={}, questions={}, uniqueParas={}, baseline={}, parents={}, children={})",
                    durationMs, effectiveMode, selectedQuestions.size(), uniqueParagraphs.size(), baselineChunks, parentChunks, childChunks);

            return new MusiqueIngestionSummary(
                    selectedQuestions.size(),
                    uniqueParagraphs.size(),
                    baselineChunks,
                    parentChunks,
                    childChunks,
                    totalBatches,
                    durationMs,
                    effectiveMode,
                    tablesCleared
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
                String key = p.title() + "\n" + p.paragraphText();
                unique.putIfAbsent(key, p);
            }
        }
        return unique;
    }

    private BaselineIngestResult ingestOriginalChunks(Map<String, MusiqueParagraph> uniqueParagraphs) {
        List<Document> documents = new ArrayList<>(uniqueParagraphs.size());
        for (MusiqueParagraph p : uniqueParagraphs.values()) {
            String text = properties.includeTitleInChunkText()
                    ? p.title() + "\n\n" + p.paragraphText()
                    : p.paragraphText();

            String deterministicId = UUID.nameUUIDFromBytes(
                    ("musique:" + p.title() + "\n" + p.paragraphText()).getBytes(StandardCharsets.UTF_8)
            ).toString();

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("dataset", "musique");
            metadata.put("title", p.title());
            metadata.put("source", "musique_full_v1.0_dev");

            documents.add(new Document(deterministicId, text, metadata));
        }

        VectorStore targetStore = (vectorStoreFactory != null)
                ? vectorStoreFactory.getStore("vector_store_musique")
                : this.vectorStore;

        int batchSize = properties.batchSize() > 0 ? properties.batchSize() : 100;
        int batchCount = addInBatches(targetStore, documents, batchSize);
        return new BaselineIngestResult(documents.size(), batchCount);
    }

    private S2bIngestResult ingestSmallToBigChunks(Map<String, MusiqueParagraph> uniqueParagraphs) {
        Map<String, List<MusiqueParagraph>> paragraphsByTitle = new LinkedHashMap<>();
        for (MusiqueParagraph p : uniqueParagraphs.values()) {
            paragraphsByTitle.computeIfAbsent(p.title(), k -> new ArrayList<>()).add(p);
        }

        TokenTextSplitter parentSplitter = TokenTextSplitter.builder().withChunkSize(1024).build();
        TokenTextSplitter childSplitter = TokenTextSplitter.builder().withChunkSize(128).build();

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

            Document articleDoc = new Document(fullArticleText, Map.of("title", title));
            List<Document> parentChunks = parentSplitter.split(articleDoc);

            for (int parentIdx = 0; parentIdx < parentChunks.size(); parentIdx++) {
                Document parentChunk = parentChunks.get(parentIdx);
                String parentId = UUID.nameUUIDFromBytes(
                        ("musique:parent:" + title + "#" + parentIdx).getBytes(StandardCharsets.UTF_8)
                ).toString();

                Map<String, Object> parentMeta = new HashMap<>();
                parentMeta.put("dataset", "musique");
                parentMeta.put("title", title);
                parentMeta.put("source", "musique_full_v1.0_dev");
                parentMeta.put("chunk_type", "parent");
                parentMeta.put("chunk_index", parentIdx);
                parentMeta.put("total_parent_chunks", parentChunks.size());

                parentDocuments.add(new Document(parentId, parentChunk.getText(), parentMeta));

                List<Document> childChunks = childSplitter.split(new Document(parentChunk.getText()));
                for (int childIdx = 0; childIdx < childChunks.size(); childIdx++) {
                    Document childChunk = childChunks.get(childIdx);
                    String childId = UUID.nameUUIDFromBytes(
                            ("musique:child:" + parentId + "#" + childIdx).getBytes(StandardCharsets.UTF_8)
                    ).toString();

                    Map<String, Object> childMeta = new HashMap<>();
                    childMeta.put("dataset", "musique");
                    childMeta.put("title", title);
                    childMeta.put("source", "musique_full_v1.0_dev");
                    childMeta.put("chunk_type", "child");
                    childMeta.put("parent_id", parentId);
                    childMeta.put("parent_table", "vector_store_musique_large");
                    childMeta.put("chunk_index", childIdx);
                    childMeta.put("total_child_chunks", childChunks.size());

                    childDocuments.add(new Document(childId, childChunk.getText(), childMeta));
                }
            }
        }

        VectorStore largeStore = (vectorStoreFactory != null)
                ? vectorStoreFactory.getStore("vector_store_musique_large")
                : this.vectorStore;
        VectorStore smallStore = (vectorStoreFactory != null)
                ? vectorStoreFactory.getStore("vector_store_musique_small")
                : this.vectorStore;

        int parentBatchSize = 10;
        int childBatchSize = 25;

        logger.info("Writing {} parent chunks to vector_store_musique_large", parentDocuments.size());
        int parentBatches = addInBatches(largeStore, parentDocuments, parentBatchSize);

        logger.info("Writing {} child chunks to vector_store_musique_small", childDocuments.size());
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
}
