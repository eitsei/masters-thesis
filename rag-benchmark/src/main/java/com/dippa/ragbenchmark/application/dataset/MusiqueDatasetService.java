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
import org.springframework.ai.vectorstore.VectorStore;
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

    public MusiqueDatasetService(MusiqueDatasetReader reader, MusiqueDatasetProperties properties, VectorStore vectorStore) {
        this.reader = reader;
        this.properties = properties;
        this.vectorStore = vectorStore;
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
}
