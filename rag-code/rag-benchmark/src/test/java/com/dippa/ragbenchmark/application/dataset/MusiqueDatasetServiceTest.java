package com.dippa.ragbenchmark.application.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dippa.ragbenchmark.domain.dataset.MusiqueParagraph;
import com.dippa.ragbenchmark.domain.dataset.MusiqueQuestion;
import com.dippa.ragbenchmark.infrastructure.dataset.MusiqueDatasetProperties;
import com.dippa.ragbenchmark.infrastructure.dataset.MusiqueDatasetReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

class MusiqueDatasetServiceTest {

    private MusiqueDatasetReader reader;
    private MusiqueDatasetProperties properties;
    private VectorStore vectorStore;
    private MusiqueDatasetService datasetService;

    @BeforeEach
    void setUp() {
        reader = mock(MusiqueDatasetReader.class);
        properties = new MusiqueDatasetProperties(
                "data/musique/musique_full_v1.0_dev.jsonl",
                2,
                0.50,
                0.50,
                0.00,
                0.00,
                42L,
                2,
                true
        );
        vectorStore = mock(VectorStore.class);
        datasetService = new MusiqueDatasetService(reader, properties, vectorStore);
    }

    @Test
    void getQuestions_supportsHopFilteringAndPagination() {
        MusiqueQuestion q1 = new MusiqueQuestion("2hop_1", "q1", "a1", List.of(), 2, true, List.of(), List.of());
        MusiqueQuestion q2 = new MusiqueQuestion("3hop_2", "q2", "a2", List.of(), 3, true, List.of(), List.of());
        MusiqueQuestion q3 = new MusiqueQuestion("2hop_3", "q3", "a3", List.of(), 2, true, List.of(), List.of());

        when(reader.loadAll()).thenReturn(List.of(q1, q2, q3));

        List<MusiqueQuestion> twoHop = datasetService.getQuestions(0, 10, 2, false);
        assertThat(twoHop).containsExactly(q1, q3);

        List<MusiqueQuestion> paged = datasetService.getQuestions(1, 1, null, false);
        assertThat(paged).containsExactly(q2);
    }

    @Test
    void getDatasetStats_returnsFormattedSummary() {
        MusiqueQuestion q1 = new MusiqueQuestion("2hop_1", "q1", "a1", List.of(), 2, true, List.of(), List.of());
        MusiqueQuestion q2 = new MusiqueQuestion("3hop_2", "q2", "a2", List.of(), 3, true, List.of(), List.of());

        when(reader.loadAll()).thenReturn(List.of(q1, q2));

        Map<String, Object> stats = datasetService.getDatasetStats();
        assertThat(stats.get("totalQuestions")).isEqualTo(2);
        assertThat(stats.get("twoHopCount")).isEqualTo(1L);
        assertThat(stats.get("threeHopCount")).isEqualTo(1L);
        assertThat(stats.get("fourHopCount")).isEqualTo(0L);
    }

    @Test
    void ingestBaseline_deduplicatesParagraphsAndBatchesVectorStoreAdd() {
        MusiqueParagraph p1 = new MusiqueParagraph(0, "Title A", "Paragraph text 1", true);
        MusiqueParagraph p2 = new MusiqueParagraph(1, "Title B", "Paragraph text 2", false);
        // Duplicate paragraph with same title and text:
        MusiqueParagraph pDuplicate = new MusiqueParagraph(0, "Title A", "Paragraph text 1", false);
        MusiqueParagraph p3 = new MusiqueParagraph(1, "Title C", "Paragraph text 3", false);

        MusiqueQuestion q1 = new MusiqueQuestion("2hop_1", "q1", "a1", List.of(), 2, true, List.of(p1, p2), List.of());
        MusiqueQuestion q2 = new MusiqueQuestion("3hop_2", "q2", "a2", List.of(), 3, true, List.of(pDuplicate, p3), List.of());

        when(reader.getStratifiedSample(2, 0.50, 0.50, 0.00, 0.00, 42L)).thenReturn(List.of(q1, q2));

        MusiqueIngestionSummary summary = datasetService.ingest(true, 2, "baseline");

        assertThat(summary.questionsProcessed()).isEqualTo(2);
        assertThat(summary.uniqueParagraphsIngested()).isEqualTo(3); // p1, p2, p3 (duplicate ignored)
        assertThat(summary.baselineChunksWritten()).isEqualTo(3);
        assertThat(summary.parentChunksWritten()).isEqualTo(0);
        assertThat(summary.childChunksWritten()).isEqualTo(0);
        assertThat(summary.batchCount()).isEqualTo(2); // 3 items with batchSize 2 -> 2 batches (2 + 1)
        assertThat(summary.mode()).isEqualTo("baseline");
        assertThat(summary.databaseCleared()).isFalse();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, atLeastOnce()).add(captor.capture());

        List<List<Document>> allBatches = captor.getAllValues();
        assertThat(allBatches).hasSize(2);
        assertThat(allBatches.get(0)).hasSize(2);
        assertThat(allBatches.get(1)).hasSize(1);

        Document firstDoc = allBatches.get(0).get(0);
        assertThat(firstDoc.getText()).contains("Title A").contains("Paragraph text 1");
        assertThat(firstDoc.getMetadata().get("dataset")).isEqualTo("musique");
        assertThat(firstDoc.getMetadata().get("title")).isEqualTo("Title A");
    }

    @Test
    void ingestOriginal_runsBaselineAndSmallToBigWithTableClearing() {
        org.springframework.jdbc.core.JdbcTemplate mockJdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        datasetService = new MusiqueDatasetService(reader, properties, vectorStore, null, mockJdbc);

        MusiqueParagraph p1 = new MusiqueParagraph(0, "Title A", "Paragraph text 1", true);
        MusiqueParagraph p2 = new MusiqueParagraph(1, "Title B", "Paragraph text 2", false);
        MusiqueQuestion q1 = new MusiqueQuestion("2hop_1", "q1", "a1", List.of(), 2, true, List.of(p1, p2), List.of());

        when(reader.getStratifiedSample(1, 0.50, 0.50, 0.00, 0.00, 42L)).thenReturn(List.of(q1));

        MusiqueIngestionSummary summary = datasetService.ingest(true, 1, "original", true);

        assertThat(summary.questionsProcessed()).isEqualTo(1);
        assertThat(summary.uniqueParagraphsIngested()).isEqualTo(2);
        assertThat(summary.baselineChunksWritten()).isEqualTo(2);
        assertThat(summary.parentChunksWritten()).isGreaterThan(0);
        assertThat(summary.childChunksWritten()).isGreaterThan(0);
        assertThat(summary.mode()).isEqualTo("original");
        assertThat(summary.databaseCleared()).isTrue();

        verify(mockJdbc).execute("TRUNCATE TABLE vector_store_musique");
        verify(mockJdbc).execute("TRUNCATE TABLE vector_store_musique_large");
        verify(mockJdbc).execute("TRUNCATE TABLE vector_store_musique_small");
    }
}
