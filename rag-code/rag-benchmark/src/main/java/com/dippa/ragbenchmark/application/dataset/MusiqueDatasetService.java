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

    public MusiqueDatasetService(MusiqueDatasetReader reader, MusiqueDatasetProperties properties, VectorStore vectorStore) {
        this(reader, properties, vectorStore, (PgVectorStoreFactory) null);
    }

    public MusiqueDatasetService(
            MusiqueDatasetReader reader,
            MusiqueDatasetProperties properties,
            VectorStore vectorStore,
            PgVectorStoreFactory vectorStoreFactory) {
        this.reader = reader;
        this.properties = properties;
        this.vectorStore = vectorStore;
        this.vectorStoreFactory = vectorStoreFactory;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MusiqueDatasetService(
            MusiqueDatasetReader reader,
            MusiqueDatasetProperties properties,
            VectorStore vectorStore,
            org.springframework.beans.factory.ObjectProvider<PgVectorStoreFactory> vectorStoreFactoryProvider) {
        this(reader, properties, vectorStore, vectorStoreFactoryProvider != null ? vectorStoreFactoryProvider.getIfAvailable() : null);
    }

    /**
     * Retrieves questions with optional filtering by hop count, sampling mode, and pagination.
     */
    public List<MusiqueQuestion> getQuestions(int offset, int limit, Integer hopFilter, boolean sampleOnly) {
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
        for (MusiqueQuestion q : all) {
            hopCounts.merge(q.hopCount(), 1L, Long::sum);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalQuestions", all.size());
        stats.put("twoHopCount", hopCounts.getOrDefault(2, 0L));
        stats.put("threeHopCount", hopCounts.getOrDefault(3, 0L));
        stats.put("fourHopCount", hopCounts.getOrDefault(4, 0L));
        stats.put("canonicalSampleSize", properties.sampleSize());
        stats.put("datasetPath", properties.filePath());
        return stats;
    }

    /**
     * Ingests paragraphs from MuSiQue questions into the vector store.
     *
     * @param sampleOnly if true, ingests paragraphs only from the stratified benchmark sample
     * @param sampleSize number of questions to sample if sampleOnly is true (if <= 0, uses properties default)
     * @return summary of the ingestion execution
     */
    public MusiqueIngestionSummary ingest(boolean sampleOnly, int sampleSize) {
        return ingest(sampleOnly, sampleSize, "original");
    }

    /**
     * Ingests paragraphs from MuSiQue questions into the vector store in either "original" or "s2b" mode.
     */
    public MusiqueIngestionSummary ingest(boolean sampleOnly, int sampleSize, String mode) {
        if ("s2b".equalsIgnoreCase(mode) || "small-to-big".equalsIgnoreCase(mode)) {
            return ingestSmallToBig(sampleOnly, sampleSize);
        }
        return ingestOriginal(sampleOnly, sampleSize);
    }

    private MusiqueIngestionSummary ingestOriginal(boolean sampleOnly, int sampleSize) {
        long startTime = System.currentTimeMillis();

        int targetSampleSize = sampleSize > 0 ? sampleSize : properties.sampleSize();
        List<MusiqueQuestion> selectedQuestions = sampleOnly
                ? reader.getStratifiedSample(
                        targetSampleSize,
                        properties.twoHopRatio(),
                        properties.threeHopRatio(),
                        properties.fourHopRatio(),
                        properties.randomSeed())
                : reader.loadAll();

        logger.info("Ingesting MuSiQue paragraphs from {} question(s) (sampleOnly={})", selectedQuestions.size(), sampleOnly);

        // Deduplicate paragraphs by title and text content
        Map<String, MusiqueParagraph> uniqueParagraphs = new LinkedHashMap<>();
        for (MusiqueQuestion question : selectedQuestions) {
            if (question.paragraphs() == null) continue;
            for (MusiqueParagraph p : question.paragraphs()) {
                if (p.paragraphText() == null || p.paragraphText().isBlank()) continue;
                String key = p.title() + "\n" + p.paragraphText();
                uniqueParagraphs.putIfAbsent(key, p);
            }
        }

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
            metadata.put("source", "musique_ans_v1.0_dev");

            documents.add(new Document(deterministicId, text, metadata));
        }

        int batchSize = properties.batchSize() > 0 ? properties.batchSize() : 100;
        int batchCount = 0;
        for (int i = 0; i < documents.size(); i += batchSize) {
            int toIndex = Math.min(i + batchSize, documents.size());
            List<Document> batch = documents.subList(i, toIndex);
            vectorStore.add(batch);
            batchCount++;
            logger.info("Ingested batch {}/{} ({} chunks)", batchCount, (documents.size() + batchSize - 1) / batchSize, batch.size());
        }

        long durationMs = System.currentTimeMillis() - startTime;
        logger.info("Completed MuSiQue ingestion: {} unique paragraphs across {} questions in {} ms ({} batches)",
                documents.size(), selectedQuestions.size(), durationMs, batchCount);

        return new MusiqueIngestionSummary(selectedQuestions.size(), documents.size(), batchCount, durationMs);
    }

    private MusiqueIngestionSummary ingestSmallToBig(boolean sampleOnly, int sampleSize) {
        long startTime = System.currentTimeMillis();

        int targetSampleSize = sampleSize > 0 ? sampleSize : properties.sampleSize();
        List<MusiqueQuestion> selectedQuestions = sampleOnly
                ? reader.getStratifiedSample(
                        targetSampleSize,
                        properties.twoHopRatio(),
                        properties.threeHopRatio(),
                        properties.fourHopRatio(),
                        properties.randomSeed())
                : reader.loadAll();

        logger.info("Starting MuSiQue Small-to-Big ingestion from {} questions (sampleOnly={})",
                selectedQuestions.size(), sampleOnly);

        // Deduplicate paragraphs by title and text content
        Map<String, MusiqueParagraph> uniqueParagraphs = new LinkedHashMap<>();
        for (MusiqueQuestion question : selectedQuestions) {
            if (question.paragraphs() == null) continue;
            for (MusiqueParagraph p : question.paragraphs()) {
                if (p.paragraphText() == null || p.paragraphText().isBlank()) continue;
                String key = p.title() + "\n" + p.paragraphText();
                uniqueParagraphs.putIfAbsent(key, p);
            }
        }

        // Group paragraphs by Wikipedia article title
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
                parentMeta.put("source", "musique_ans_v1.0_dev");
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
                    childMeta.put("source", "musique_ans_v1.0_dev");
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

        int batchCount = parentBatches + childBatches;
        long durationMs = System.currentTimeMillis() - startTime;
        logger.info("Completed MuSiQue Small-to-Big ingestion: {} parent chunks, {} child chunks in {} ms ({} batches)",
                parentDocuments.size(), childDocuments.size(), durationMs, batchCount);

        return new MusiqueIngestionSummary(selectedQuestions.size(), childDocuments.size(), batchCount, durationMs);
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
