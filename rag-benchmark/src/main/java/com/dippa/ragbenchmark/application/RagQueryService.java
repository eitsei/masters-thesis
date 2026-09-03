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

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RagQueryService.class);
    private static final java.util.concurrent.ScheduledExecutorService PROGRESS_SCHEDULER = java.util.concurrent.Executors
            .newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "rag-progress-logger");
                thread.setDaemon(true);
                return thread;
            });

    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final Reranker reranker;
    private final MeterRegistry meterRegistry;
    private final int snippetLengthChars;
    private final int topK;
    // A Spring AI PromptTemplate loaded from its own file (src/main/resources/prompts) rather
    // than hand-rolled String.formatted() or an inline literal: M1-M4 will need to vary this
    // shape later (e.g. M3 cache markers, M1 expanded-window context), and keeping the prompt
    // text out of Java source lets it be reviewed/edited without touching pipeline code, and
    // reused if a second consumer needs the same template.
    private final PromptTemplate answerPromptTemplate;

    public RagQueryService(VectorStore vectorStore, ChatModel chatModel, Reranker reranker,
            MeterRegistry meterRegistry,
            @Value("${rag.query.snippet-length-chars}") int snippetLengthChars,
            @Value("${rag.query.top-k}") int topK,
            @Value("classpath:prompts/answer-generation.st") Resource answerPromptResource) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.reranker = reranker;
        this.meterRegistry = meterRegistry;
        this.snippetLengthChars = snippetLengthChars;
        this.topK = topK;
        this.answerPromptTemplate = new PromptTemplate(answerPromptResource);
    }

    public RagQueryResult query(String query, Set<ModuleId> requestedModules) {
        Set<ModuleId> modules = requestedModules == null ? Set.of() : requestedModules;
        if (!modules.isEmpty()) {
            throw new ModuleNotImplementedException(modules);
        }

        logger.info("Executing RAG query: \"{}\"", query);

        Timer.Sample retrievalSample = Timer.start(meterRegistry);
        List<Document> retrieved = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build());
        long retrievalNanos = retrievalSample.stop(meterRegistry.timer("rag.pipeline.retrieval"));
        long retrievalMs = TimeUnit.NANOSECONDS.toMillis(retrievalNanos);

        List<Document> reranked = reranker.rerank(query, retrieved);
        logger.info("Retrieved {} chunk(s) in {} ms. Generating answer...", reranked.size(), retrievalMs);

        long startGenTime = System.currentTimeMillis();
        java.util.concurrent.ScheduledFuture<?> progressTask = PROGRESS_SCHEDULER.scheduleAtFixedRate(() -> {
            long elapsedSeconds = (System.currentTimeMillis() - startGenTime) / 1000;
            logger.info("Still generating answer... took {}s", elapsedSeconds);
        }, 3, 3, TimeUnit.SECONDS);

        Timer.Sample generationSample = Timer.start(meterRegistry);
        String answer;
        long generationNanos;
        try {
            answer = chatModel.call(buildPrompt(query, reranked));
        } finally {
            progressTask.cancel(false);
            generationNanos = generationSample.stop(meterRegistry.timer("rag.pipeline.generation"));
        }
        long generationMs = TimeUnit.NANOSECONDS.toMillis(generationNanos);
        logger.info("Answer generated in {} ms ({} chars)", generationMs, answer != null ? answer.length() : 0);

        List<RetrievedChunk> retrievedChunks = reranked.stream()
                .map(document -> new RetrievedChunk(document.getId(), snippet(document.getText())))
                .toList();

        return new RagQueryResult(
                answer,
                Set.of(),
                retrievedChunks,
                retrievalMs,
                generationMs);
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
        return text.length() <= snippetLengthChars ? text : text.substring(0, snippetLengthChars) + "...";
    }
}
