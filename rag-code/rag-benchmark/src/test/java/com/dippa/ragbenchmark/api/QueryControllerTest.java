package com.dippa.ragbenchmark.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dippa.ragbenchmark.application.RagQueryResult;
import com.dippa.ragbenchmark.application.RagQueryService;
import com.dippa.ragbenchmark.application.RetrievedChunk;
import com.dippa.ragbenchmark.domain.ModuleId;
import com.dippa.ragbenchmark.domain.ModuleNotImplementedException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc tests for {@link QueryController}. {@code @WebMvcTest} auto-registers {@link
 * GlobalExceptionHandler} as a real {@code @RestControllerAdvice} bean, so these tests exercise
 * the actual HTTP-translation seam (request parsing, error mapping, response shape) rather than
 * mocking it away.
 */
@WebMvcTest(QueryController.class)
class QueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagQueryService ragQueryService;

    @Test
    void unknownModule_returns400WithMessage() throws Exception {
        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"hi\", \"modules\": [\"bogus\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"))
                .andExpect(jsonPath("$.message").value("Unknown module: bogus"));
    }

    @Test
    void validModuleName_isCaseInsensitive_andReturns501WhenModuleNotImplementedExceptionThrown() throws Exception {
        when(ragQueryService.query(eq("hi"), eq(Set.of(ModuleId.M2))))
                .thenThrow(new ModuleNotImplementedException(Set.of(ModuleId.M2)));

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"hi\", \"modules\": [\"m2\"]}"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.error").value("module_not_implemented"))
                .andExpect(jsonPath("$.modules[0]").value("M2"));
    }

    @Test
    void baselinePipeline_returnsAnswerAndRetrievedChunks() throws Exception {
        RagQueryResult result = new RagQueryResult(
                "Paris.",
                Set.of(),
                List.of(new RetrievedChunk("chunk-1", "Paris is the capital of France.")),
                12L,
                34L);
        when(ragQueryService.query(eq("What is the capital of France?"), eq(Set.of()))).thenReturn(result);

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"What is the capital of France?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Paris."))
                .andExpect(jsonPath("$.modulesApplied").isEmpty())
                .andExpect(jsonPath("$.retrievedChunks[0].id").value("chunk-1"))
                .andExpect(jsonPath("$.retrievedChunks[0].snippet").value("Paris is the capital of France."))
                .andExpect(jsonPath("$.timingsMs.retrievalMs").value(12))
                .andExpect(jsonPath("$.timingsMs.generationMs").value(34));
    }

    @Test
    void m4Requested_whenRouterNotConfigured_returns400() throws Exception {
        when(ragQueryService.query(eq("hi"), eq(Set.of(ModuleId.M4))))
                .thenThrow(new com.dippa.ragbenchmark.domain.RouterConfigurationException("Router not configured"));

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"hi\", \"modules\": [\"m4\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("router_not_configured"))
                .andExpect(jsonPath("$.message").value("Router not configured"));
    }

    @Test
    void m4Pipeline_returnsModelUsedAndRouteMs() throws Exception {
        TokenUsageDto usage = new TokenUsageDto(100, 20, 120, 0, 0, 0, 0, 25, 1);
        RagQueryResult result = new RagQueryResult(
                "Lite answer.",
                Set.of(ModuleId.M4),
                List.of(new RetrievedChunk("chunk-1", "Text snippet")),
                10L,
                40L,
                0L,
                0L,
                0L,
                45L,
                "gemini-3.5-flash-lite",
                com.dippa.ragbenchmark.domain.CacheStatus.NONE,
                CacheMetadataDto.NONE,
                usage);
        when(ragQueryService.query(eq("Simple factual question"), eq(Set.of(ModuleId.M4))))
                .thenReturn(result);

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"Simple factual question\", \"modules\": [\"M4\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Lite answer."))
                .andExpect(jsonPath("$.modulesApplied[0]").value("M4"))
                .andExpect(jsonPath("$.modelUsed").value("gemini-3.5-flash-lite"))
                .andExpect(jsonPath("$.timingsMs.routeMs").value(45))
                .andExpect(jsonPath("$.tokenUsage.routingPromptTokens").value(25))
                .andExpect(jsonPath("$.tokenUsage.routingCompletionTokens").value(1))
                .andExpect(jsonPath("$.tokenUsage.routingTokens").value(26));
    }
}
