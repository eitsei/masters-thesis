package com.dippa.ragbenchmark.application.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dippa.ragbenchmark.domain.dataset.MusiqueParagraph;
import com.dippa.ragbenchmark.domain.dataset.MusiqueQuestion;
import com.dippa.ragbenchmark.infrastructure.PgVectorStoreFactory;
import com.dippa.ragbenchmark.infrastructure.dataset.EnterpriseClosedDatasetProperties;
import com.dippa.ragbenchmark.infrastructure.dataset.EnterpriseClosedDatasetReader;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

class EnterpriseClosedDatasetServiceTest {

    private EnterpriseClosedDatasetReader reader;
    private EnterpriseClosedDatasetProperties properties;
    private VectorStore defaultVectorStore;
    private PgVectorStoreFactory vectorStoreFactory;
    private JdbcTemplate jdbcTemplate;
    private EnterpriseClosedDatasetService service;

    @BeforeEach
    void setUp() {
        reader = mock(EnterpriseClosedDatasetReader.class);
        properties = new EnterpriseClosedDatasetProperties(
                "data/enterprise/enterprise_100_final.jsonl",
                10,
                5,
                10,
                512,
                1024,
                128,
                true
        );
        defaultVectorStore = mock(VectorStore.class);
        vectorStoreFactory = mock(PgVectorStoreFactory.class);
        jdbcTemplate = mock(JdbcTemplate.class);

        @SuppressWarnings("unchecked")
        ObjectProvider<PgVectorStoreFactory> factoryProvider = mock(ObjectProvider.class);
        when(factoryProvider.getIfAvailable()).thenReturn(vectorStoreFactory);

        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> jdbcProvider = mock(ObjectProvider.class);
        when(jdbcProvider.getIfAvailable()).thenReturn(jdbcTemplate);

        when(vectorStoreFactory.getStore(any())).thenReturn(defaultVectorStore);

        service = new EnterpriseClosedDatasetService(
                reader,
                properties,
                defaultVectorStore,
                factoryProvider,
                jdbcProvider
        );
    }

    @Test
    void ingestOriginal_deduplicatesParagraphsAndPersists() {
        MusiqueParagraph p1 = new MusiqueParagraph(0, "Tili A", "Teksti tilistä A", true);
        MusiqueParagraph p2 = new MusiqueParagraph(1, "Tili B", "Teksti tilistä B", false);
        // Duplicate paragraph
        MusiqueParagraph p1Dup = new MusiqueParagraph(0, "Tili A", "Teksti tilistä A", true);

        MusiqueQuestion q1 = new MusiqueQuestion("2hop_1", "Kysymys 1?", "Vastaus 1", List.of(), 2, true, List.of(p1, p2), List.of());
        MusiqueQuestion q2 = new MusiqueQuestion("2hop_2", "Kysymys 2?", "Vastaus 2", List.of(), 2, true, List.of(p1Dup), List.of());

        when(reader.loadAll()).thenReturn(List.of(q1, q2));

        EnterpriseClosedDatasetService.EnterpriseClosedIngestionSummary summary = service.ingest("original", false);

        assertThat(summary.questionsCount()).isEqualTo(2);
        assertThat(summary.uniqueParagraphsCount()).isEqualTo(2); // Deduplicated 3 -> 2
        assertThat(summary.baselineChunksWritten()).isEqualTo(2);
        assertThat(summary.parentChunksWritten()).isEqualTo(0);
        assertThat(summary.childChunksWritten()).isEqualTo(0);

        verify(vectorStoreFactory).getStore("vector_store_enterprise_closed");
        verify(defaultVectorStore, atLeastOnce()).add(anyList());
    }

    @Test
    void ingestOriginal_splitsLongParagraphsIntoBaselineChunks() {
        // Create a long paragraph that definitely exceeds 512 tokens
        String repeatedSentence = "Tämä on pitkää pankkipalveluiden ja tiliehtojen kuvausta, jota toistetaan moneen kertaan tekstissä. ";
        String longText = repeatedSentence.repeat(50); // ~1000 words -> ~1300 tokens

        MusiqueParagraph longParagraph = new MusiqueParagraph(0, "Pitkä Sopimus", longText, true);
        MusiqueQuestion q = new MusiqueQuestion("1hop_long", "Mitä sopimus sanoo?", "Kaikkea", List.of(), 1, true, List.of(longParagraph), List.of());

        when(reader.loadAll()).thenReturn(List.of(q));

        EnterpriseClosedDatasetService.EnterpriseClosedIngestionSummary summary = service.ingest("original", false);

        assertThat(summary.questionsCount()).isEqualTo(1);
        assertThat(summary.uniqueParagraphsCount()).isEqualTo(1);
        // The long paragraph must be split into at least 2 baseline chunks
        assertThat(summary.baselineChunksWritten()).isGreaterThanOrEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(defaultVectorStore, atLeastOnce()).add(captor.capture());

        List<Document> addedDocs = captor.getValue();
        assertThat(addedDocs).isNotEmpty();
        Document firstDoc = addedDocs.get(0);
        assertThat(firstDoc.getMetadata().get("dataset")).isEqualTo("enterprise");
        assertThat(firstDoc.getMetadata().get("corpus_mode")).isEqualTo("closed");
        assertThat(firstDoc.getMetadata().get("chunk_index")).isEqualTo(0);
    }

    @Test
    void ingestSmallToBig_createsParentAndChildChunksWithLinkage() {
        MusiqueParagraph p1 = new MusiqueParagraph(0, "Kortit", "Kortti A tiedot", true);
        MusiqueParagraph p2 = new MusiqueParagraph(1, "Kortit", "Kortti B tiedot", false);

        MusiqueQuestion q = new MusiqueQuestion("1hop_1", "Miten kortti toimii?", "Hyvin", List.of(), 1, true, List.of(p1, p2), List.of());
        when(reader.loadAll()).thenReturn(List.of(q));

        VectorStore largeStore = mock(VectorStore.class);
        VectorStore smallStore = mock(VectorStore.class);
        when(vectorStoreFactory.getStore("vector_store_enterprise_closed_large")).thenReturn(largeStore);
        when(vectorStoreFactory.getStore("vector_store_enterprise_closed_small")).thenReturn(smallStore);

        EnterpriseClosedDatasetService.EnterpriseClosedIngestionSummary summary = service.ingest("s2b", false);

        assertThat(summary.questionsCount()).isEqualTo(1);
        assertThat(summary.uniqueParagraphsCount()).isEqualTo(2);
        assertThat(summary.parentChunksWritten()).isGreaterThanOrEqualTo(1);
        assertThat(summary.childChunksWritten()).isGreaterThanOrEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> childCaptor = ArgumentCaptor.forClass(List.class);
        verify(smallStore, atLeastOnce()).add(childCaptor.capture());

        List<Document> addedChildren = childCaptor.getValue();
        assertThat(addedChildren).isNotEmpty();
        Document firstChild = addedChildren.get(0);
        assertThat(firstChild.getMetadata()).containsKey("parent_id");
        assertThat(firstChild.getMetadata().get("parent_table")).isEqualTo("vector_store_enterprise_closed_large");
        assertThat(firstChild.getMetadata().get("chunk_type")).isEqualTo("child");
    }

    @Test
    void clearTables_truncatesAppropriateTables() {
        int count = service.clearTables("both");
        assertThat(count).isEqualTo(3);
        verify(jdbcTemplate).execute("TRUNCATE TABLE vector_store_enterprise_closed");
        verify(jdbcTemplate).execute("TRUNCATE TABLE vector_store_enterprise_closed_large");
        verify(jdbcTemplate).execute("TRUNCATE TABLE vector_store_enterprise_closed_small");
    }
}
