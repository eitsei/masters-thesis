package com.dippa.ragbenchmark.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class RagPromptRendererTest {

    private final RagPromptRenderer renderer = new RagPromptRenderer();

    @Test
    void renderContext_joinsChunksWithDashesInInputOrder() {
        Document d1 = Document.builder().id("1").text("First chunk").build();
        Document d2 = Document.builder().id("2").text("Second chunk").build();

        String context = renderer.renderContext(List.of(d1, d2));

        assertThat(context).isEqualTo("First chunk\n---\nSecond chunk");
    }

    @Test
    void renderDynamicSuffix_formatsQuestionAndAnswer() {
        String suffix = renderer.renderDynamicSuffix("What is the capital of Finland?");

        assertThat(suffix).isEqualTo("Question: What is the capital of Finland?\nAnswer:");
    }

    @Test
    void renderFullPrompt_matchesStandardAnswerTemplateStructure() {
        Document d1 = Document.builder().id("1").text("Helsinki is the capital.").build();

        String fullPrompt = renderer.renderFullPrompt("Capital of Finland?", List.of(d1));

        assertThat(fullPrompt)
                .contains(renderer.getSystemInstruction())
                .contains("Context:\nHelsinki is the capital.")
                .contains("Question: Capital of Finland?\nAnswer:");
    }

    @Test
    void computeCanonicalCacheKey_preservesPipelineOrder_andDifferentiatesOrderChanges() {
        Document docA = Document.builder().id("chunk-a").text("Context Alpha").build();
        Document docB = Document.builder().id("chunk-b").text("Context Beta").build();

        // Pass chunks in order A, B
        String key1 = renderer.computeCanonicalCacheKey(
                "google-genai", "gemini-3.8-flash", "table_1", "system instruction", List.of(docA, docB));

        // Pass identical chunks in same order A, B
        String key1Duplicate = renderer.computeCanonicalCacheKey(
                "google-genai", "gemini-3.8-flash", "table_1", "system instruction", List.of(docA, docB));

        // Pass chunks in reversed order B, A
        String key2 = renderer.computeCanonicalCacheKey(
                "google-genai", "gemini-3.8-flash", "table_1", "system instruction", List.of(docB, docA));

        assertThat(key1).isEqualTo(key1Duplicate);
        assertThat(key1).isNotEqualTo(key2);
        assertThat(key1).hasSize(64); // Valid 64-char SHA-256 hex string
    }

    @Test
    void computeCanonicalCacheKey_differentiatesDifferentModels() {
        Document doc = Document.builder().id("c1").text("Text").build();

        String keyFlash = renderer.computeCanonicalCacheKey(
                "google-genai", "gemini-3.8-flash", "table", "sys", List.of(doc));
        String keyLite = renderer.computeCanonicalCacheKey(
                "google-genai", "gemini-3.5-flash-lite", "table", "sys", List.of(doc));

        assertThat(keyFlash).isNotEqualTo(keyLite);
    }

    @Test
    void estimateTokens_calculatesCorrectRatio() {
        // 35 chars / 3.5 = 10 tokens
        String text = "12345678901234567890123456789012345";
        long tokens = renderer.estimateTokens(text, 3.5);

        assertThat(tokens).isEqualTo(10L);
    }
}
