package com.dippa.ragbenchmark.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dippa.ragbenchmark.application.DocumentIngestionService;
import com.dippa.ragbenchmark.application.IngestionResult;
import com.dippa.ragbenchmark.domain.ChunkPersistenceException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc test for {@link DocumentController} — part of the HTTP-translation seam candidate 3
 * flagged as having zero coverage.
 */
@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentIngestionService documentIngestionService;

    @Test
    void ingest_returns201WithGeneratedIds() throws Exception {
        IngestionResult result = new IngestionResult("doc-1", 2, List.of("chunk-1", "chunk-2"));
        when(documentIngestionService.ingest(anyString(), any())).thenReturn(result);

        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"Paris is the capital of France.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value("doc-1"))
                .andExpect(jsonPath("$.chunkCount").value(2))
                .andExpect(jsonPath("$.chunkIds[0]").value("chunk-1"))
                .andExpect(jsonPath("$.chunkIds[1]").value("chunk-2"));
    }

    @Test
    void ingest_whenChunkPersistenceFails_returns500WithDocumentAndChunkIds() throws Exception {
        ChunkPersistenceException exception =
                new ChunkPersistenceException("doc-1", List.of("chunk-1", "chunk-2"), new RuntimeException("boom"));
        when(documentIngestionService.ingest(anyString(), any())).thenThrow(exception);

        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"Paris is the capital of France.\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("chunk_persistence_failed"))
                .andExpect(jsonPath("$.documentId").value("doc-1"))
                .andExpect(jsonPath("$.chunkIds[0]").value("chunk-1"))
                .andExpect(jsonPath("$.chunkIds[1]").value("chunk-2"));
    }
}
