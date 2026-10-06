package com.dippa.ragbenchmark.infrastructure;

import com.dippa.ragbenchmark.domain.ComplexityTier;
import com.dippa.ragbenchmark.domain.ModelRouter;
import com.dippa.ragbenchmark.domain.RoutingDecision;
import com.dippa.ragbenchmark.domain.RoutingMode;
import com.dippa.ragbenchmark.domain.RoutingRequest;
import com.dippa.ragbenchmark.infrastructure.config.ModelCatalogProperties;
import com.dippa.ragbenchmark.infrastructure.config.RouterProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.DisposableBean;

/**
 * Production implementation of {@link ModelRouter} powered by zero-shot Gemini 3.5 Flash-Lite.
 * Dynamically classifies query reasoning complexity into single-hop, two-hop, or multi-hop tiers,
 * enforcing configured timeout limits, recording routing latency ($T_{\text{route}}$), and classifier token usage.
 */
public class GoogleGenAiModelRouter implements ModelRouter, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(GoogleGenAiModelRouter.class);
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\b([123])\\b");

    private static final String CLASSIFIER_PROMPT_TEMPLATE = """
            You are a query complexity classifier for an enterprise retrieval-augmented question answering system.
            Estimate how many independent reasoning steps (evidence passages) are needed to answer the query,
            and assign exactly one tier:

            1 = SINGLE_HOP:
                - The answer can be found in a single passage: a single fact, value, term, or definition.
                - Greetings, small talk, or questions clearly unrelated to the organization's domain.

            2 = TWO_HOP:
                - The answer requires connecting two facts or entities from different passages.
                - Domain questions where it is uncertain whether the answer exists in the documentation.

            3 = MULTI_HOP:
                - The answer requires a chain of three or more dependent reasoning steps across multiple passages,
                  or comparative / temporal / numerical synthesis over several entities.

            Respond with ONLY the single digit: 1, 2, or 3.

            Query: %s
            """;

    private final ChatModel chatModel;
    private final String classifierModel;
    private final String flashLiteModel;
    private final String flashModel;
    private final String proModel;
    private final String fallbackModel;
    private final double temperature;
    private final int timeoutMs;
    private final MeterRegistry meterRegistry;
    private final ExecutorService executor;

    public GoogleGenAiModelRouter(
            ChatModel chatModel,
            ModelCatalogProperties catalogProperties,
            RouterProperties routerProperties,
            MeterRegistry meterRegistry) {
        this(
                chatModel,
                routerProperties != null && routerProperties.classifierModel() != null && !routerProperties.classifierModel().isBlank()
                        ? routerProperties.classifierModel()
                        : (catalogProperties != null && catalogProperties.googleGenai() != null && catalogProperties.googleGenai().flashLite() != null
                                ? catalogProperties.googleGenai().flashLite() : "gemini-3.5-flash-lite"),
                catalogProperties != null && catalogProperties.googleGenai() != null && catalogProperties.googleGenai().flashLite() != null
                        ? catalogProperties.googleGenai().flashLite() : "gemini-3.5-flash-lite",
                catalogProperties != null && catalogProperties.googleGenai() != null && catalogProperties.googleGenai().flash() != null
                        ? catalogProperties.googleGenai().flash() : "gemini-3.8-flash",
                catalogProperties != null && catalogProperties.googleGenai() != null && catalogProperties.googleGenai().pro() != null
                        ? catalogProperties.googleGenai().pro() : "gemini-3.1-pro",
                routerProperties != null && routerProperties.fallbackModel() != null && !routerProperties.fallbackModel().isBlank()
                        ? routerProperties.fallbackModel()
                        : (catalogProperties != null && catalogProperties.googleGenai() != null && catalogProperties.googleGenai().flash() != null
                                ? catalogProperties.googleGenai().flash() : "gemini-3.8-flash"),
                routerProperties != null ? routerProperties.temperature() : 0.0,
                routerProperties != null ? routerProperties.timeoutMs() : 5000,
                meterRegistry,
                Executors.newVirtualThreadPerTaskExecutor());
    }

    public GoogleGenAiModelRouter(
            ChatModel chatModel,
            String classifierModel,
            String flashLiteModel,
            String flashModel,
            String proModel,
            String fallbackModel,
            MeterRegistry meterRegistry) {
        this(chatModel, classifierModel, flashLiteModel, flashModel, proModel, fallbackModel, 0.0, 5000, meterRegistry,
                Executors.newVirtualThreadPerTaskExecutor());
    }

    public GoogleGenAiModelRouter(
            ChatModel chatModel,
            String classifierModel,
            String flashLiteModel,
            String flashModel,
            String proModel,
            String fallbackModel,
            double temperature,
            int timeoutMs,
            MeterRegistry meterRegistry) {
        this(chatModel, classifierModel, flashLiteModel, flashModel, proModel, fallbackModel,
                temperature, timeoutMs, meterRegistry, Executors.newVirtualThreadPerTaskExecutor());
    }

    public GoogleGenAiModelRouter(
            ChatModel chatModel,
            String classifierModel,
            String flashLiteModel,
            String flashModel,
            String proModel,
            String fallbackModel,
            double temperature,
            int timeoutMs,
            MeterRegistry meterRegistry,
            ExecutorService executor) {
        this.chatModel = chatModel;
        this.classifierModel = (classifierModel != null && !classifierModel.isBlank()) ? classifierModel : "gemini-3.5-flash-lite";
        this.flashLiteModel = (flashLiteModel != null && !flashLiteModel.isBlank()) ? flashLiteModel : "gemini-3.5-flash-lite";
        this.flashModel = (flashModel != null && !flashModel.isBlank()) ? flashModel : "gemini-3.8-flash";
        this.proModel = (proModel != null && !proModel.isBlank()) ? proModel : "gemini-3.1-pro";
        this.fallbackModel = (fallbackModel != null && !fallbackModel.isBlank()) ? fallbackModel : "gemini-3.8-flash";
        this.temperature = temperature;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 5000;
        this.meterRegistry = meterRegistry;
        this.executor = executor != null ? executor : Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public RoutingDecision route(RoutingRequest request) {
        String query = (request != null && request.query() != null) ? request.query().trim() : "";
        String promptContent = CLASSIFIER_PROMPT_TEMPLATE.formatted(query);

        Prompt classifierPrompt = createClassifierPrompt(promptContent);

        logger.info("Classifier '{}' analyzing query reasoning complexity...", this.classifierModel);

        Timer.Sample timerSample = meterRegistry != null ? Timer.start(meterRegistry) : null;
        long startTime = System.currentTimeMillis();

        try {
            CompletableFuture<ChatResponse> future = CompletableFuture.supplyAsync(
                    () -> chatModel.call(classifierPrompt), executor);
            ChatResponse response;
            try {
                response = future.get(this.timeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeoutEx) {
                future.cancel(true);
                throw timeoutEx;
            }

            long latencyMs = (timerSample != null && meterRegistry != null)
                    ? TimeUnit.NANOSECONDS.toMillis(timerSample.stop(meterRegistry.timer("rag.pipeline.route")))
                    : (System.currentTimeMillis() - startTime);

            int promptTokens = 0;
            int completionTokens = 0;
            if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                Usage usage = response.getMetadata().getUsage();
                if (usage.getPromptTokens() != null) {
                    promptTokens = usage.getPromptTokens().intValue();
                }
                if (usage.getCompletionTokens() != null) {
                    completionTokens = usage.getCompletionTokens().intValue();
                }
            }

            String outputText = "";
            if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
                outputText = response.getResult().getOutput().getText();
            }

            Integer parsedDigit = extractTierDigit(outputText);
            ComplexityTier tier;
            String targetModel;
            int hops;
            RoutingMode mode;

            if (parsedDigit != null) {
                mode = RoutingMode.CLASSIFIER;
                switch (parsedDigit) {
                    case 1 -> {
                        tier = ComplexityTier.SINGLE_HOP;
                        targetModel = flashLiteModel;
                        hops = 1;
                    }
                    case 2 -> {
                        tier = ComplexityTier.TWO_HOP;
                        targetModel = flashModel;
                        hops = 2;
                    }
                    case 3 -> {
                        tier = ComplexityTier.MULTI_HOP;
                        targetModel = proModel;
                        hops = 3;
                    }
                    default -> {
                        logger.warn("Unexpected classifier digit: {}. Falling back to default workhorse model.", parsedDigit);
                        tier = ComplexityTier.TWO_HOP;
                        targetModel = fallbackModel;
                        hops = 2;
                        mode = RoutingMode.FALLBACK;
                    }
                }
            } else {
                logger.warn("Unable to parse complexity tier from classifier output: '{}'. Falling back to default model '{}'",
                        outputText, fallbackModel);
                tier = ComplexityTier.TWO_HOP;
                targetModel = fallbackModel;
                hops = 2;
                mode = RoutingMode.FALLBACK;
            }

            if (meterRegistry != null) {
                meterRegistry.counter("rag.pipeline.route.decision",
                        "tier", tier.name(),
                        "mode", mode.name()).increment();
            }

            logger.info("Classifier '{}' completed: classified as {} (hops={}) -> routing query to target model '{}' in {} ms (tokens={}+{})",
                    this.classifierModel, tier, hops, targetModel, latencyMs, promptTokens, completionTokens);

            return new RoutingDecision(
                    targetModel,
                    tier,
                    hops,
                    latencyMs,
                    promptTokens,
                    completionTokens,
                    mode,
                    outputText != null ? outputText.trim() : null);

        } catch (TimeoutException ex) {
            long latencyMs = (timerSample != null && meterRegistry != null)
                    ? TimeUnit.NANOSECONDS.toMillis(timerSample.stop(meterRegistry.timer("rag.pipeline.route")))
                    : (System.currentTimeMillis() - startTime);

            logger.warn("Model routing classifier timed out after {} ms. Gracefully falling back to '{}'",
                    timeoutMs, fallbackModel);

            if (meterRegistry != null) {
                meterRegistry.counter("rag.pipeline.route.decision",
                        "tier", ComplexityTier.TWO_HOP.name(),
                        "mode", RoutingMode.FALLBACK.name()).increment();
            }

            return new RoutingDecision(
                    fallbackModel,
                    ComplexityTier.TWO_HOP,
                    2,
                    latencyMs,
                    0,
                    0,
                    RoutingMode.FALLBACK,
                    "Classifier timeout after " + timeoutMs + " ms");
        } catch (Exception ex) {
            Throwable rootCause = ex instanceof java.util.concurrent.ExecutionException ? ex.getCause() : ex;
            long latencyMs = (timerSample != null && meterRegistry != null)
                    ? TimeUnit.NANOSECONDS.toMillis(timerSample.stop(meterRegistry.timer("rag.pipeline.route")))
                    : (System.currentTimeMillis() - startTime);

            logger.warn("Model routing classifier failed with error: '{}'. Gracefully falling back to '{}'",
                    rootCause.getMessage(), fallbackModel, rootCause);

            if (meterRegistry != null) {
                meterRegistry.counter("rag.pipeline.route.decision",
                        "tier", ComplexityTier.TWO_HOP.name(),
                        "mode", RoutingMode.FALLBACK.name()).increment();
            }

            return new RoutingDecision(
                    fallbackModel,
                    ComplexityTier.TWO_HOP,
                    2,
                    latencyMs,
                    0,
                    0,
                    RoutingMode.FALLBACK,
                    "Classifier invocation error: " + rootCause.getMessage());
        }
    }

    private Prompt createClassifierPrompt(String promptContent) {
        if (this.chatModel instanceof GoogleGenAiChatModel) {
            GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                    .model(this.classifierModel)
                    .temperature(this.temperature)
                    .thinkingBudget(0)
                    .maxTokens(5)
                    .includeExtendedUsageMetadata(true)
                    .build();
            return new Prompt(promptContent, options);
        }
        if (this.chatModel instanceof OllamaChatModel) {
            return new Prompt(promptContent, OllamaChatOptions.builder()
                    .model(this.classifierModel)
                    .temperature(this.temperature)
                    .maxTokens(5)
                    .build());
        }
        return new Prompt(promptContent, ChatOptions.builder()
                .model(this.classifierModel)
                .temperature(this.temperature)
                .maxTokens(5)
                .build());
    }

    private Integer extractTierDigit(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.equals("1") || trimmed.equals("2") || trimmed.equals("3")) {
            return Integer.parseInt(trimmed);
        }
        Matcher matcher = DIGIT_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    @Override
    public boolean isConfigured() {
        return chatModel != null;
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
