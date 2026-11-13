package com.dippa.ragbenchmark.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.dippa.ragbenchmark.domain.ChunkPersistenceException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * Plain unit tests for {@link DocumentIngestionService} — no Spring context, no database. Only
 * {@link VectorStore} is mocked; {@code TokenTextSplitter} is exercised for real since it is a
 * plain, side-effect-free utility with no injection seam of its own.
 */
@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock
    private VectorStore vectorStore;

    private DocumentIngestionService documentIngestionService;

    @BeforeEach
    void setUp() {
        documentIngestionService = new DocumentIngestionService(vectorStore);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingest_splitsTextAndStoresChunksTaggedWithSharedDocumentId() {
        String text = "Sentence one. ".repeat(50);
        Map<String, Object> metadata = Map.of("source", "unit-test");

        IngestionResult result = documentIngestionService.ingest(text, metadata);

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        List<Document> storedChunks = captor.getValue();

        assertThat(storedChunks).isNotEmpty();
        assertThat(result.chunkCount()).isEqualTo(storedChunks.size());
        assertThat(result.chunkIds()).containsExactlyElementsOf(
                storedChunks.stream().map(Document::getId).toList());

        for (Document chunk : storedChunks) {
            assertThat(chunk.getMetadata()).containsEntry("documentId", result.documentId());
            assertThat(chunk.getMetadata()).containsEntry("source", "unit-test");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingest_withNullMetadata_stillTagsChunksWithGeneratedDocumentId() {
        IngestionResult result = documentIngestionService.ingest("Short piece of text to ingest.", null);

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        List<Document> storedChunks = captor.getValue();

        assertThat(storedChunks).isNotEmpty();
        assertThat(storedChunks.get(0).getMetadata()).containsEntry("documentId", result.documentId());
        assertThat(result.documentId()).isNotBlank();
    }

    @Test
    void ingest_whenVectorStoreAddFails_throwsChunkPersistenceExceptionWithDocumentAndChunkIds() {
        doThrow(new RuntimeException("connection refused")).when(vectorStore).add(anyList());

        assertThatThrownBy(() -> documentIngestionService.ingest("Some text to chunk and store.", null))
                .isInstanceOf(ChunkPersistenceException.class)
                .satisfies(ex -> {
                    ChunkPersistenceException cpe = (ChunkPersistenceException) ex;
                    assertThat(cpe.getDocumentId()).isNotBlank();
                    assertThat(cpe.getChunkIds()).isNotEmpty();
                    assertThat(cpe.getCause()).hasMessage("connection refused");
                });
    }
}
