package com.dippa.ragbenchmark.api;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dippa.ragbenchmark.domain.CacheLookupResult;
import com.dippa.ragbenchmark.domain.CacheRequest;
import com.dippa.ragbenchmark.domain.PromptCache;
import com.dippa.ragbenchmark.domain.PromptCacheUnavailableException;
import com.dippa.ragbenchmark.infrastructure.config.ModelCatalogProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BenchmarkCacheController.class)
class BenchmarkCacheControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PromptCache promptCache;

    @Test
    void clearCache_whenConfigured_clearsOwnedAndReturnsOk() throws Exception {
        when(promptCache.isConfigured()).thenReturn(true);
        doNothing().when(promptCache).clearOwned();

        mockMvc.perform(post("/api/benchmark/cache/clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cleared"))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.message").value("Successfully cleared owned prompt cache resources."));

        verify(promptCache).clearOwned();
    }

    @Test
    void clearCache_whenNotConfigured_returnsOkWithConfiguredFalse() throws Exception {
        when(promptCache.isConfigured()).thenReturn(false);
        doNothing().when(promptCache).clearOwned();

        mockMvc.perform(post("/api/benchmark/cache/clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cleared"))
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.message").value("Successfully cleared owned prompt cache resources."));

        verify(promptCache).clearOwned();
    }

    @Test
    void clearCache_whenFails_returns503ServiceUnavailable() throws Exception {
        doThrow(new PromptCacheUnavailableException("Provider RPC failed during cache deletion"))
                .when(promptCache).clearOwned();

        mockMvc.perform(post("/api/benchmark/cache/clear"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("prompt_cache_unavailable"))
                .andExpect(jsonPath("$.message").value("Provider RPC failed during cache deletion"));

        verify(promptCache).clearOwned();
    }

    @Autowired(required = false)
    private ModelCatalogProperties modelCatalogProperties;

    @Test
    void prewarmCache_whenConfigured_prewarmsDefaultModels() throws Exception {
        when(promptCache.isConfigured()).thenReturn(true);
        when(promptCache.getOrCreate(any(CacheRequest.class)))
                .thenReturn(CacheLookupResult.hit("caches/default-cache-id", 4200L, Instant.now()));

        String expectedProModel = (modelCatalogProperties != null && modelCatalogProperties.googleGenai() != null)
                ? modelCatalogProperties.googleGenai().pro()
                : "gemini-3.1-pro";

        mockMvc.perform(post("/api/benchmark/cache/prewarm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("prewarmed"))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.models").isArray())
                .andExpect(jsonPath("$.models", hasSize(3)))
                .andExpect(jsonPath("$.models[0].model").value("gemini-3.5-flash-lite"))
                .andExpect(jsonPath("$.models[0].cacheId").value("caches/default-cache-id"))
                .andExpect(jsonPath("$.models[0].status").value("EXPLICIT_HIT"))
                .andExpect(jsonPath("$.models[0].tokenCount").value(4200))
                .andExpect(jsonPath("$.models[1].model").value("gemini-3.5-flash"))
                .andExpect(jsonPath("$.models[2].model").value(expectedProModel));

        ArgumentCaptor<CacheRequest> captor = ArgumentCaptor.forClass(CacheRequest.class);
        verify(promptCache, times(3)).getOrCreate(captor.capture());
        List<CacheRequest> requests = captor.getAllValues();
        assertEquals("gemini-3.5-flash-lite", requests.get(0).model());
        assertEquals("gemini-3.5-flash", requests.get(1).model());
        assertEquals(expectedProModel, requests.get(2).model());
    }

    @Test
    void prewarmCache_withSpecificModels_prewarmsGivenModels() throws Exception {
        when(promptCache.isConfigured()).thenReturn(true);
        when(promptCache.getOrCreate(any(CacheRequest.class)))
                .thenReturn(CacheLookupResult.miss("caches/custom-cache-id", 5500L, Instant.now()));

        String requestJson = """
                {
                    "models": ["gemini-3.8-flash", "gemini-3.5-flash"]
                }
                """;

        mockMvc.perform(post("/api/benchmark/cache/prewarm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("prewarmed"))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.models", hasSize(2)))
                .andExpect(jsonPath("$.models[0].model").value("gemini-3.8-flash"))
                .andExpect(jsonPath("$.models[0].cacheId").value("caches/custom-cache-id"))
                .andExpect(jsonPath("$.models[0].status").value("EXPLICIT_MISS"))
                .andExpect(jsonPath("$.models[0].tokenCount").value(5500))
                .andExpect(jsonPath("$.models[1].model").value("gemini-3.5-flash"));

        ArgumentCaptor<CacheRequest> captor = ArgumentCaptor.forClass(CacheRequest.class);
        verify(promptCache, times(2)).getOrCreate(captor.capture());
        List<CacheRequest> requests = captor.getAllValues();
        assertEquals("gemini-3.8-flash", requests.get(0).model());
        assertEquals("gemini-3.5-flash", requests.get(1).model());
    }

    @Test
    void prewarmCache_whenNotConfigured_returnsNoop() throws Exception {
        when(promptCache.isConfigured()).thenReturn(false);

        mockMvc.perform(post("/api/benchmark/cache/prewarm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("noop"))
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.message").value("Prompt cache provider is not configured or inactive."));

        verify(promptCache, never()).getOrCreate(any(CacheRequest.class));
    }

    @Test
    void prewarmCache_whenPromptCacheUnavailable_returns503ServiceUnavailable() throws Exception {
        when(promptCache.isConfigured()).thenReturn(true);
        when(promptCache.getOrCreate(any(CacheRequest.class)))
                .thenThrow(new PromptCacheUnavailableException("Provider RPC failed during cache prewarm"));

        mockMvc.perform(post("/api/benchmark/cache/prewarm"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("prompt_cache_unavailable"))
                .andExpect(jsonPath("$.message").value("Provider RPC failed during cache prewarm"));
    }
}
