package com.dippa.ragbenchmark.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dippa.ragbenchmark.ingest.enterprise.EnterpriseIngestionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EnterpriseIngestController.class)
class EnterpriseIngestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EnterpriseIngestionService ingestionService;

    @Test
    void postWithoutBodyDefaultsToS2bAndAll() throws Exception {
        when(ingestionService.executePipeline(any())).thenReturn(
                new EnterpriseIngestionService.EnterpriseIngestionSummary(10, 2, 5, 20, 1500L, "s2b")
        );

        mockMvc.perform(post("/api/ingest/enterprise"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagesCrawled").value(10))
                .andExpect(jsonPath("$.pdfsExtracted").value(2))
                .andExpect(jsonPath("$.parentChunksWritten").value(5))
                .andExpect(jsonPath("$.childChunksWritten").value(20))
                .andExpect(jsonPath("$.durationMs").value(1500))
                .andExpect(jsonPath("$.mode").value("s2b"));

        ArgumentCaptor<EnterpriseIngestController.EnterpriseIngestRequest> captor =
                ArgumentCaptor.forClass(EnterpriseIngestController.EnterpriseIngestRequest.class);
        verify(ingestionService).executePipeline(captor.capture());

        EnterpriseIngestController.EnterpriseIngestRequest request = captor.getValue();
        assertThat(request.resolvedMode()).isEqualTo("s2b");
        assertThat(request.resolvedAction()).isEqualTo("all");
        assertThat(request.shouldClearDatabase()).isFalse();
        assertThat(request.shouldClearFiles()).isFalse();
    }

    @Test
    void postWithQueryParamModePreservesBackwardsCompatibility() throws Exception {
        when(ingestionService.executePipeline(any())).thenReturn(
                new EnterpriseIngestionService.EnterpriseIngestionSummary(5, 0, 0, 10, 500L, "original")
        );

        mockMvc.perform(post("/api/ingest/enterprise?mode=original"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("original"));

        ArgumentCaptor<EnterpriseIngestController.EnterpriseIngestRequest> captor =
                ArgumentCaptor.forClass(EnterpriseIngestController.EnterpriseIngestRequest.class);
        verify(ingestionService).executePipeline(captor.capture());

        EnterpriseIngestController.EnterpriseIngestRequest request = captor.getValue();
        assertThat(request.resolvedMode()).isEqualTo("original");
        assertThat(request.resolvedAction()).isEqualTo("all");
    }

    @Test
    void postWithFullJsonBodyDelegatesCorrectly() throws Exception {
        when(ingestionService.executePipeline(any())).thenReturn(
                new EnterpriseIngestionService.EnterpriseIngestionSummary(12, 0, 0, 0, 300L, "clean")
        );

        String jsonBody = """
                {
                    "mode": "s2b",
                    "action": "clean",
                    "clearDatabase": true,
                    "clearFiles": true
                }
                """;

        mockMvc.perform(post("/api/ingest/enterprise")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagesCrawled").value(12))
                .andExpect(jsonPath("$.mode").value("clean"));

        ArgumentCaptor<EnterpriseIngestController.EnterpriseIngestRequest> captor =
                ArgumentCaptor.forClass(EnterpriseIngestController.EnterpriseIngestRequest.class);
        verify(ingestionService).executePipeline(captor.capture());

        EnterpriseIngestController.EnterpriseIngestRequest request = captor.getValue();
        assertThat(request.resolvedMode()).isEqualTo("s2b");
        assertThat(request.resolvedAction()).isEqualTo("clean");
        assertThat(request.shouldClearDatabase()).isTrue();
        assertThat(request.shouldClearFiles()).isTrue();
    }

    @Test
    void postWithQueryParamModeS2bBaselineDefaultsToS2bBaselineAction() throws Exception {
        when(ingestionService.executePipeline(any())).thenReturn(
                new EnterpriseIngestionService.EnterpriseIngestionSummary(50, 0, 50, 200, 800L, "s2b-baseline")
        );

        mockMvc.perform(post("/api/ingest/enterprise?mode=s2b-baseline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("s2b-baseline"));

        ArgumentCaptor<EnterpriseIngestController.EnterpriseIngestRequest> captor =
                ArgumentCaptor.forClass(EnterpriseIngestController.EnterpriseIngestRequest.class);
        verify(ingestionService).executePipeline(captor.capture());

        EnterpriseIngestController.EnterpriseIngestRequest request = captor.getValue();
        assertThat(request.resolvedMode()).isEqualTo("s2b-baseline");
        assertThat(request.resolvedAction()).isEqualTo("s2b-baseline");
    }
}
