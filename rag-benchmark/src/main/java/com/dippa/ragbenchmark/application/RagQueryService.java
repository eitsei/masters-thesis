package com.dippa.ragbenchmark.application;

import com.dippa.ragbenchmark.domain.ModuleId;
import com.dippa.ragbenchmark.domain.ModuleNotImplementedException;
import com.dippa.ragbenchmark.domain.Reranker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the M0 baseline RAG pipeline: retrieve top-K chunks, pass them through the
 * (currently no-op) {@link Reranker} seam, assemble a prompt and generate an answer.
 *
 * <p>Any request for M1-M4 optimization modules fails fast with {@link
 * ModuleNotImplementedException} before any retrieval/generation work happens, since none of
 * those modules are implemented in v1.</p>
 */
@Service
public class RagQueryService {

    private static final int TOP_K = 5;
    private static final int SNIPPET_LENGTH = 200;

    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final Reranker reranker;
    private final MeterRegistry meterRegistry;
    // A Spring AI PromptTemplate loaded from its own file (src/main/resources/prompts) rather
    // than hand-rolled String.formatted() or an inline literal: M1-M4 will need to vary this
    // shape later (e.g. M3 cache markers, M1 expanded-window context), and keeping the prompt
    // text out of Java source lets it be reviewed/edited without touching pipeline code, and
    // reused if a second consumer needs the same template.
    private final PromptTemplate answerPromptTemplate;

    public RagQueryService(VectorStore vectorStore, ChatModel chatModel, Reranker reranker,
            MeterRegistry meterRegistry,
            @Value("classpath:prompts/answer-generation.st") Resource answerPromptResource) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.reranker = reranker;
        this.meterRegistry = meterRegistry;
        this.answerPromptTemplate = new PromptTemplate(answerPromptResource);
    }

    public RagQueryResult query(String query, Set<ModuleId> requestedModules) {
        Set<ModuleId> modules = requestedModules == null ? Set.of() : requestedModules;
        if (!modules.isEmpty()) {
            throw new ModuleNotImplementedException(modules);
        }

        Timer.Sample retrievalSample = Timer.start(meterRegistry);
        List<Document> retrieved = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(TOP_K).build());
        long retrievalNanos = retrievalSample.stop(meterRegistry.timer("rag.pipeline.retrieval"));

        List<Document> reranked = reranker.rerank(query, retrieved);

        Timer.Sample generationSample = Timer.start(meterRegistry);
        String answer = chatModel.call(buildPrompt(query, reranked));
        long generationNanos = generationSample.stop(meterRegistry.timer("rag.pipeline.generation"));

        List<RetrievedChunk> retrievedChunks = reranked.stream()
                .map(document -> new RetrievedChunk(document.getId(), snippet(document.getText())))
                .toList();

        return new RagQueryResult(
                answer,
                Set.of(),
                retrievedChunks,
                TimeUnit.NANOSECONDS.toMillis(retrievalNanos),
                TimeUnit.NANOSECONDS.toMillis(generationNanos));
    }

    private String buildPrompt(String query, List<Document> chunks) {
        String context = chunks.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));
        return answerPromptTemplate.render(Map.of("context", context, "question", query));
    }

    private String snippet(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= SNIPPET_LENGTH ? text : text.substring(0, SNIPPET_LENGTH) + "...";
    }
}
