package com.dippa.ragbenchmark.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

/**
 * Encapsulates prompt assembly, canonical cache key computation, and token estimation.
 *
 * <p>Separates cacheable prefix content (system instruction and context documents) from
 * dynamic query suffix content, ensuring deterministic keying and preventing prompt drift.</p>
 */
@Component
public class RagPromptRenderer {

    public static final String PROMPT_SCHEMA_VERSION = "v1";
    public static final String DEFAULT_SYSTEM_INSTRUCTION =
            "Answer the question using only the context provided below. If the answer cannot be found in the context, say you don't know.";

    private final String systemInstruction;

    public RagPromptRenderer() {
        this(DEFAULT_SYSTEM_INSTRUCTION);
    }

    public RagPromptRenderer(String systemInstruction) {
        this.systemInstruction = (systemInstruction != null && !systemInstruction.isBlank())
                ? systemInstruction.trim()
                : DEFAULT_SYSTEM_INSTRUCTION;
    }

    public String getSystemInstruction() {
        return systemInstruction;
    }

    /**
     * Renders context documents joined by separator.
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
        return "Question: " + sanitizedQuestion + "\nAnswer:";
    }

    /**
     * Renders full monolithic prompt for baseline queries or sub-threshold/bypassed queries.
     */
    public String renderFullPrompt(String question, List<Document> chunks) {
        String context = renderContext(chunks);
        String suffix = renderDynamicSuffix(question);
        return systemInstruction + "\n\nContext:\n" + context + "\n\n" + suffix;
    }

    /**
     * Computes a canonical, length-safe SHA-256 cache key including schema version, provider,
     * model identifier, table name, system instruction, and deterministically sorted context chunks.
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

        String sortedChunksRepresentation = "";
        if (chunks != null && !chunks.isEmpty()) {
            sortedChunksRepresentation = chunks.stream()
                    .sorted(Comparator.comparing(doc -> doc.getId() != null ? doc.getId() : ""))
                    .map(doc -> (doc.getId() != null ? doc.getId() : "") + ":" + (doc.getText() != null ? doc.getText() : ""))
                    .collect(Collectors.joining("\n"));
        }

        String keyMaterial = String.join("::",
                PROMPT_SCHEMA_VERSION,
                effectiveProvider,
                effectiveModel,
                effectiveTable,
                effectiveInstruction,
                sortedChunksRepresentation);

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
