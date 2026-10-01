package com.dippa.ragbenchmark.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Encapsulates prompt assembly, canonical cache key computation, and token estimation.
 *
 * <p>Separates cacheable prefix content (system instruction and context documents) from
 * dynamic query suffix content, ensuring deterministic keying and preventing prompt drift.
 * All prompt templates and system instructions are loaded exclusively from external
 * {@code src/main/resources/prompts/*.st} resources.</p>
 */
@Component
public class RagPromptRenderer {

    public static final String PROMPT_SCHEMA_VERSION = "v2";

    private final PromptTemplate fullPromptTemplate;
    private final PromptTemplate suffixTemplate;
    private final PromptTemplate dynamicPayloadTemplate;
    private final String systemInstruction;

    @Autowired
    public RagPromptRenderer(@Value("classpath:prompts/answer-generation.st") Resource promptResource) {
        this(loadResourceContent(promptResource));
    }

    public RagPromptRenderer() {
        this(new ClassPathResource("prompts/answer-generation.st"));
    }

    public RagPromptRenderer(String templateContent) {
        String content = (templateContent != null && !templateContent.isBlank())
                ? templateContent
                : loadResourceContent(new ClassPathResource("prompts/answer-generation.st"));

        this.fullPromptTemplate = new PromptTemplate(content);

        // Derive system instruction, dynamic payload, and dynamic query suffix from prompt template
        int contextIndex = content.indexOf("Context:\n{context}");
        if (contextIndex < 0) {
            contextIndex = content.indexOf("Context:\r\n{context}");
        }
        if (contextIndex < 0) {
            contextIndex = content.lastIndexOf("Context:");
        }

        if (contextIndex >= 0) {
            this.systemInstruction = content.substring(0, contextIndex).trim();
            String dynamicPayloadTemplateString = content.substring(contextIndex).trim();
            this.dynamicPayloadTemplate = new PromptTemplate(dynamicPayloadTemplateString);
        } else {
            this.systemInstruction = content.trim();
            this.dynamicPayloadTemplate = null;
        }

        int questionIndex = content.indexOf("Question: {question}");
        if (questionIndex < 0) {
            questionIndex = content.indexOf("Question:\n{question}");
        }
        if (questionIndex < 0) {
            questionIndex = content.lastIndexOf("Question:");
        }

        if (questionIndex >= 0) {
            String suffixTemplateString = content.substring(questionIndex).trim();
            this.suffixTemplate = new PromptTemplate(suffixTemplateString);
        } else {
            throw new IllegalArgumentException("Prompt template must contain 'Question:' marker: " + content);
        }
    }

    public String getSystemInstruction() {
        return systemInstruction;
    }

    /**
     * Renders context documents joined by separator in their retrieved/pipeline order.
     */
    public String renderContext(List<Document> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        return chunks.stream()
                .map(Document::getText)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("\n---\n"));
    }

    /**
     * Renders dynamic query suffix appended to cached contexts or sent in explicit cached calls.
     */
    public String renderDynamicSuffix(String question) {
        String sanitizedQuestion = question != null ? question.trim() : "";
        return suffixTemplate.render(Map.of("question", sanitizedQuestion));
    }

    /**
     * Renders dynamic query payload (context documents + question) sent in explicit cached calls.
     */
    public String renderDynamicPayload(String question, List<Document> chunks) {
        String context = renderContext(chunks);
        String sanitizedQuestion = question != null ? question.trim() : "";
        if (dynamicPayloadTemplate != null) {
            return dynamicPayloadTemplate.render(Map.of("context", context, "question", sanitizedQuestion));
        }
        return "Context:\n" + context + "\n\n" + renderDynamicSuffix(sanitizedQuestion);
    }

    /**
     * Renders full monolithic prompt for baseline queries or sub-threshold/bypassed queries.
     */
    public String renderFullPrompt(String question, List<Document> chunks) {
        String context = renderContext(chunks);
        String sanitizedQuestion = question != null ? question.trim() : "";
        return fullPromptTemplate.render(Map.of("context", context, "question", sanitizedQuestion));
    }

    /**
     * Computes a canonical, length-safe SHA-256 cache key for the static cacheable prefix.
     *
     * <p>In accordance with Strategy 1 (Shared Static Prefix Caching), the cache key depends
     * exclusively on schema version, provider, model identifier, and system instruction prefix,
     * ensuring that queries with distinct retrieved context chunks share the exact same cached prefix
     * and achieve a 100% cache hit rate.</p>
     */
    public String computeCanonicalCacheKey(
            String provider,
            String model,
            String systemInstructionText) {
        String effectiveProvider = provider != null ? provider.trim() : "default";
        String effectiveModel = model != null ? model.trim() : "default";
        String effectiveInstruction = systemInstructionText != null ? systemInstructionText.trim() : this.systemInstruction;

        String keyMaterial = String.join("::",
                PROMPT_SCHEMA_VERSION,
                effectiveProvider,
                effectiveModel,
                effectiveInstruction);

        return sha256Hex(keyMaterial);
    }

    /**
     * Computes canonical cache key for backward compatibility with 5-parameter callers.
     * Delegates to the static prefix key computation to maintain 100% cache hit rate.
     */
    public String computeCanonicalCacheKey(
            String provider,
            String model,
            String tableName,
            String systemInstructionText,
            List<Document> chunks) {
        return computeCanonicalCacheKey(provider, model, systemInstructionText);
    }

    /**
     * Estimates token count for a text using character-to-token ratio heuristic.
     */
    public long estimateTokens(String text, double charsPerToken) {
        if (text == null || text.isBlank()) {
            return 0L;
        }
        double ratio = charsPerToken > 0.0 ? charsPerToken : 3.5;
        return Math.max(1L, Math.round(text.length() / ratio));
    }

    private static String loadResourceContent(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read prompt resource: " + resource, e);
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in JVM", e);
        }
    }
}
