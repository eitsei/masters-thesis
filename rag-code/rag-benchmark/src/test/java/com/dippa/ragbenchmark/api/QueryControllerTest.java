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
    void validModuleName_isCaseInsensitive_andReturns501WhenNotImplemented() throws Exception {
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
}
