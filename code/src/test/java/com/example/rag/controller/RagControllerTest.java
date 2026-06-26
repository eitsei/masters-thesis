package com.example.rag.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.chat.model.ChatResponse;
import io.micrometer.core.instrument.MeterRegistry;
import com.example.rag.service.IngestionService;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
public class RagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockBean
    private ChatClient.Builder chatClientBuilder;

    @MockBean
    private ChatClient chatClient;

    @MockBean(name = "simpleVectorStore")
    private VectorStore simpleVectorStore;

    @MockBean(name = "smallToBigVectorStore")
    private VectorStore smallToBigVectorStore;

    @BeforeEach
    public void setUp() {
        Mockito.when(chatClientBuilder.build()).thenReturn(chatClient);
    }

    @Test
    public void testPing() throws Exception {
        mockMvc.perform(get("/api/rag/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }

    @Test
    public void testIngestSimple() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/rag/ingest")
                .param("filePath", "sample.txt")
                .param("strategy", "SIMPLE"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Successfully ingested: sample.txt with strategy SIMPLE")));

        // Verify that the simpleVectorStore's accept method was invoked to store chunks
        Mockito.verify(simpleVectorStore, Mockito.times(1)).accept(Mockito.anyList());
        // Verify smallToBigVectorStore was NOT invoked
        Mockito.verifyNoInteractions(smallToBigVectorStore);
    }

    @Test
    public void testAskBaseline() throws Exception {
        // 1. Mock Qdrant retrieval
        List<Document> mockDocs = List.of(
            new Document("Finland's capital is Helsinki.", Map.of("title", "Capital Info", "source", "encyclopedia.txt"))
        );
        Mockito.when(simpleVectorStore.similaritySearch(Mockito.any(org.springframework.ai.vectorstore.SearchRequest.class)))
               .thenReturn(mockDocs);

        // 2. Mock ChatClient stream
        ChatClient.ChatClientRequestSpec promptSpec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec responseSpec = Mockito.mock(ChatClient.StreamResponseSpec.class);
        
        Mockito.when(chatClient.prompt()).thenReturn(promptSpec);
        Mockito.when(promptSpec.system(Mockito.anyString())).thenReturn(promptSpec);
        Mockito.when(promptSpec.user(Mockito.anyString())).thenReturn(promptSpec);
        Mockito.when(promptSpec.stream()).thenReturn(responseSpec);

        // Create mock ChatResponse chunks
        org.springframework.ai.chat.metadata.Usage mockUsage = Mockito.mock(org.springframework.ai.chat.metadata.Usage.class);
        Mockito.when(mockUsage.getPromptTokens()).thenReturn(30L);
        Mockito.when(mockUsage.getGenerationTokens()).thenReturn(10L);

        ChatResponse responseChunk1 = Mockito.mock(ChatResponse.class);
        org.springframework.ai.chat.model.Generation gen1 = new org.springframework.ai.chat.model.Generation(
            new org.springframework.ai.chat.messages.AssistantMessage("Helsinki is ")
        );
        Mockito.when(responseChunk1.getResults()).thenReturn(List.of(gen1));

        ChatResponse responseChunk2 = Mockito.mock(ChatResponse.class);
        org.springframework.ai.chat.model.Generation gen2 = new org.springframework.ai.chat.model.Generation(
            new org.springframework.ai.chat.messages.AssistantMessage("the capital.")
        );
        Mockito.when(responseChunk2.getResults()).thenReturn(List.of(gen2));
        org.springframework.ai.chat.metadata.ChatResponseMetadata metadata = Mockito.mock(org.springframework.ai.chat.metadata.ChatResponseMetadata.class);
        Mockito.when(metadata.getUsage()).thenReturn(mockUsage);
        Mockito.when(responseChunk2.getMetadata()).thenReturn(metadata);

        reactor.core.publisher.Flux<ChatResponse> responseFlux = reactor.core.publisher.Flux.just(responseChunk1, responseChunk2);
        Mockito.when(responseSpec.chatResponse()).thenReturn(responseFlux);

        // 3. Execute request
        String requestJson = """
            {
              "query": "What is the capital of Finland?",
              "config": {
                "useRouting": false,
                "useSmallToBig": false,
                "useReranking": false,
                "usePromptCaching": false
              }
            }
            """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/rag/ask")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Helsinki is the capital."))
                .andExpect(jsonPath("$.retrievedDocuments[0].content").doesNotExist())
                .andExpect(jsonPath("$.retrievedDocuments[0].metadata").exists())
                .andExpect(jsonPath("$.metrics.totalLatencyMs").exists())
                .andExpect(jsonPath("$.metrics.ttftMs").exists())
                .andExpect(jsonPath("$.metrics.inputTokens").value(30))
                .andExpect(jsonPath("$.metrics.outputTokens").value(10))
                .andExpect(jsonPath("$.metrics.estimatedCostUsd").value(0.000005))
                .andExpect(jsonPath("$.metrics.modelUsed").exists());
    }

    @Test
    public void testAskMetricsRecorded() throws Exception {
        // Run a query
        testAskBaseline();

        // Check that timers are registered and recorded in Micrometer
        var latencyTimer = meterRegistry.find("rag.query.latency").timer();
        org.junit.jupiter.api.Assertions.assertNotNull(latencyTimer, "Timer rag.query.latency should be registered");
        org.junit.jupiter.api.Assertions.assertTrue(latencyTimer.count() >= 1, "Should record latency");

        var ttftTimer = meterRegistry.find("rag.query.ttft").timer();
        org.junit.jupiter.api.Assertions.assertNotNull(ttftTimer, "Timer rag.query.ttft should be registered");
        org.junit.jupiter.api.Assertions.assertTrue(ttftTimer.count() >= 1, "Should record TTFT");
    }
}
