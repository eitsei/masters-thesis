package com.dippa.ragbenchmark.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dippa.ragbenchmark.domain.ComplexityTier;
import com.dippa.ragbenchmark.domain.RoutingDecision;
import com.dippa.ragbenchmark.domain.RoutingMode;
import com.dippa.ragbenchmark.domain.RoutingRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class GoogleGenAiModelRouterTest {

    private ChatModel chatModel;
    private MeterRegistry meterRegistry;
    private GoogleGenAiModelRouter router;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        meterRegistry = new SimpleMeterRegistry();
        router = new GoogleGenAiModelRouter(
                chatModel,
                "gemini-3.5-flash-lite",
                "gemini-3.5-flash-lite",
                "gemini-3.8-flash",
                "gemini-3.1-pro",
                "gemini-3.8-flash",
                0.0,
                5000,
                meterRegistry);
    }

    @AfterEach
    void tearDown() {
        if (router != null) {
            router.destroy();
        }
    }

    @Test
    void isConfigured_returnsTrueWhenChatModelPresent() {
        assertThat(router.isConfigured()).isTrue();
    }

    @Test
    void route_classifierOutputsDigit1_routesToFlashLite() {
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("1", 20, 1));

        RoutingDecision decision = router.route(new RoutingRequest("What is the enterprise contact phone?"));

        assertThat(decision.selectedModel()).isEqualTo("gemini-3.5-flash-lite");
        assertThat(decision.complexityTier()).isEqualTo(ComplexityTier.SINGLE_HOP);
        assertThat(decision.predictedHops()).isEqualTo(1);
        assertThat(decision.routingPromptTokens()).isEqualTo(20);
        assertThat(decision.routingCompletionTokens()).isEqualTo(1);
        assertThat(decision.totalRoutingTokens()).isEqualTo(21);
        assertThat(decision.routingMode()).isEqualTo(RoutingMode.CLASSIFIER);
    }

    @Test
    void route_classifierOutputsDigit2_routesToFlash() {
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("2", 22, 1));

        RoutingDecision decision = router.route(new RoutingRequest("Compare mortgage vs consumer credit."));

        assertThat(decision.selectedModel()).isEqualTo("gemini-3.8-flash");
        assertThat(decision.complexityTier()).isEqualTo(ComplexityTier.TWO_HOP);
        assertThat(decision.predictedHops()).isEqualTo(2);
        assertThat(decision.routingMode()).isEqualTo(RoutingMode.CLASSIFIER);
    }

    @Test
    void route_classifierOutputsDigit3_routesToPro() {
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("3", 25, 1));

        RoutingDecision decision = router.route(new RoutingRequest("Synthesize three conflicting financial reports."));

        assertThat(decision.selectedModel()).isEqualTo("gemini-3.1-pro");
        assertThat(decision.complexityTier()).isEqualTo(ComplexityTier.MULTI_HOP);
        assertThat(decision.predictedHops()).isEqualTo(3);
        assertThat(decision.routingMode()).isEqualTo(RoutingMode.CLASSIFIER);
    }

    @Test
    void route_classifierOutputsVerboseFormattedText_extractsCorrectDigit() {
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("Difficulty tier: 2 (Relational)", 24, 6));

        RoutingDecision decision = router.route(new RoutingRequest("Where was the founder educated?"));

        assertThat(decision.selectedModel()).isEqualTo("gemini-3.8-flash");
        assertThat(decision.complexityTier()).isEqualTo(ComplexityTier.TWO_HOP);
        assertThat(decision.predictedHops()).isEqualTo(2);
        assertThat(decision.routingMode()).isEqualTo(RoutingMode.CLASSIFIER);
    }

    @Test
    void route_classifierOutputsUnparseableText_fallsBackToWorkhorse() {
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("Uncertain query complexity", 18, 5));

        RoutingDecision decision = router.route(new RoutingRequest("Ambiguous query"));

        assertThat(decision.selectedModel()).isEqualTo("gemini-3.8-flash");
        assertThat(decision.complexityTier()).isEqualTo(ComplexityTier.TWO_HOP);
        assertThat(decision.predictedHops()).isEqualTo(2);
        assertThat(decision.routingMode()).isEqualTo(RoutingMode.FALLBACK);
    }

    @Test
    void route_chatModelThrowsException_gracefullyFallsBackToWorkhorse() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("Vertex AI rate limit 429"));

        RoutingDecision decision = router.route(new RoutingRequest("Any query"));

        assertThat(decision.selectedModel()).isEqualTo("gemini-3.8-flash");
        assertThat(decision.complexityTier()).isEqualTo(ComplexityTier.TWO_HOP);
        assertThat(decision.predictedHops()).isEqualTo(2);
        assertThat(decision.routingMode()).isEqualTo(RoutingMode.FALLBACK);
        assertThat(decision.rationale()).contains("Vertex AI rate limit 429");
    }

    @Test
    void route_classifierTimesOut_gracefullyFallsBackToWorkhorse() {
        GoogleGenAiModelRouter timeoutRouter = new GoogleGenAiModelRouter(
                chatModel,
                "gemini-3.5-flash-lite",
                "gemini-3.5-flash-lite",
                "gemini-3.8-flash",
                "gemini-3.1-pro",
                "gemini-3.8-flash",
                0.0,
                50,
                meterRegistry);

        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Thread.sleep(200);
            return createChatResponse("1", 20, 1);
        });

        RoutingDecision decision = timeoutRouter.route(new RoutingRequest("Stalled classifier query"));

        assertThat(decision.selectedModel()).isEqualTo("gemini-3.8-flash");
        assertThat(decision.complexityTier()).isEqualTo(ComplexityTier.TWO_HOP);
        assertThat(decision.predictedHops()).isEqualTo(2);
        assertThat(decision.routingMode()).isEqualTo(RoutingMode.FALLBACK);
        assertThat(decision.rationale()).contains("Classifier timeout after 50 ms");

        timeoutRouter.destroy();
    }

    @Test
    void route_passesTemperatureAndModelInPromptOptions() {
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        when(chatModel.call(promptCaptor.capture())).thenReturn(createChatResponse("1", 20, 1));

        router.route(new RoutingRequest("Check prompt options"));

        Prompt capturedPrompt = promptCaptor.getValue();
        assertThat(capturedPrompt.getContents()).contains("You are a query complexity classifier for an enterprise retrieval-augmented question answering system.");
        assertThat(capturedPrompt.getContents()).contains("Query: Check prompt options");
        assertThat(capturedPrompt.getOptions()).isNotNull();
        assertThat(capturedPrompt.getOptions().getModel()).isEqualTo("gemini-3.5-flash-lite");
        assertThat(capturedPrompt.getOptions().getTemperature()).isEqualTo(0.0);
    }

    private ChatResponse createChatResponse(String text, int promptTokens, int completionTokens) {
        Generation gen = new Generation(new AssistantMessage(text));
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .usage(new DefaultUsage(promptTokens, completionTokens, promptTokens + completionTokens))
                .build();
        return new ChatResponse(List.of(gen), metadata);
    }
}
