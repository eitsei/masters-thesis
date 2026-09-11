package com.dippa.ragbenchmark.api;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dippa.ragbenchmark.domain.PromptCache;
import com.dippa.ragbenchmark.domain.PromptCacheUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
}
