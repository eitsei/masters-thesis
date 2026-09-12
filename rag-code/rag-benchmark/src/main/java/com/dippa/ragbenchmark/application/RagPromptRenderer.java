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

        // Derive system instruction and dynamic query suffix from the single prompt template source
        int contextIndex = content.indexOf("Context:");
        if (contextIndex > 0) {
            this.systemInstruction = content.substring(0, contextIndex).trim();
        } else {
            this.systemInstruction = content.trim();
        }

        int questionIndex = content.indexOf("Question:");
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
     * Renders full monolithic prompt for baseline queries or sub-threshold/bypassed queries.
     */
    public String renderFullPrompt(String question, List<Document> chunks) {
        String context = renderContext(chunks);
        String sanitizedQuestion = question != null ? question.trim() : "";
        return fullPromptTemplate.render(Map.of("context", context, "question", sanitizedQuestion));
    }

    /**
     * Computes a canonical, length-safe SHA-256 cache key including schema version, provider,
     * model identifier, table name, system instruction, and ordered context chunks.
     *
     * <p>Preserves pipeline input order to ensure distinct prefix orderings yield distinct cache keys.</p>
     */
    public String computeCanonicalCacheKey(
            String provider,
            String model,
            String tableName,
            String systemInstructionText,
            List<Document> chunks) {
        String effectiveProvider = provider != null ? provider.trim() : "default";
        String effectiveModel = model != null ? model.trim() : "default";
        String effectiveTable = tableName != null ? tableName.trim() : "default";
        String effectiveInstruction = systemInstructionText != null ? systemInstructionText.trim() : this.systemInstruction;

        StringBuilder chunksRepresentation = new StringBuilder();
        if (chunks != null) {
            for (int i = 0; i < chunks.size(); i++) {
                Document doc = chunks.get(i);
                String id = doc.getId() != null ? doc.getId() : "";
                String text = doc.getText() != null ? doc.getText() : "";
                chunksRepresentation.append("[")
                        .append(i)
                        .append("]id=")
                        .append(id.length())
                        .append(":")
                        .append(id)
                        .append(":text=")
                        .append(text.length())
                        .append(":")
                        .append(text)
                        .append("\n");
            }
        }

        String keyMaterial = String.join("::",
                PROMPT_SCHEMA_VERSION,
                effectiveProvider,
                effectiveModel,
                effectiveTable,
                effectiveInstruction,
                chunksRepresentation.toString());

        return sha256Hex(keyMaterial);
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
