package com.dippa.ragbenchmark.application;

import com.dippa.ragbenchmark.api.TokenUsageDto;
import com.dippa.ragbenchmark.domain.ModuleId;
import com.dippa.ragbenchmark.domain.ModuleNotImplementedException;
import com.dippa.ragbenchmark.domain.Reranker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private static final Logger logger = LoggerFactory.getLogger(RagQueryService.class);
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

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
    private final PromptTemplate answerPromptTemplate;
    private final String defaultTableName;
    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final ConcurrentMap<String, VectorStore> dynamicStores = new ConcurrentHashMap<>();

    @Autowired
    public RagQueryService(
            VectorStore vectorStore,
            ChatModel chatModel,
            Reranker reranker,
            MeterRegistry meterRegistry,
            @Value("${rag.query.snippet-length-chars}") int snippetLengthChars,
            @Value("${rag.query.top-k}") int topK,
            @Value("classpath:prompts/answer-generation.st") Resource answerPromptResource,
            @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store_test}") String defaultTableName,
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.reranker = reranker;
        this.meterRegistry = meterRegistry;
        this.snippetLengthChars = snippetLengthChars;
        this.topK = topK;
        this.answerPromptTemplate = new PromptTemplate(answerPromptResource);
        this.defaultTableName = (defaultTableName != null && !defaultTableName.isBlank()) ? defaultTableName : "vector_store_test";
        this.jdbcTemplate = jdbcTemplateProvider != null ? jdbcTemplateProvider.getIfAvailable() : null;
        this.embeddingModel = embeddingModelProvider != null ? embeddingModelProvider.getIfAvailable() : null;
    }

    public RagQueryService(
            VectorStore vectorStore,
            ChatModel chatModel,
            Reranker reranker,
            MeterRegistry meterRegistry,
            int snippetLengthChars,
            int topK,
            Resource answerPromptResource) {
        this(vectorStore, chatModel, reranker, meterRegistry, snippetLengthChars, topK, answerPromptResource,
                "vector_store_test", (JdbcTemplate) null, (EmbeddingModel) null);
    }

    public RagQueryService(
            VectorStore vectorStore,
            ChatModel chatModel,
            Reranker reranker,
            MeterRegistry meterRegistry,
            int snippetLengthChars,
            int topK,
            Resource answerPromptResource,
            String defaultTableName,
            JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.reranker = reranker;
        this.meterRegistry = meterRegistry;
        this.snippetLengthChars = snippetLengthChars;
        this.topK = topK;
        this.answerPromptTemplate = new PromptTemplate(answerPromptResource);
        this.defaultTableName = (defaultTableName != null && !defaultTableName.isBlank()) ? defaultTableName : "vector_store_test";
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
    }

    public String getDefaultTableName() {
        return defaultTableName;
    }

    public RagQueryResult query(String query, Set<ModuleId> requestedModules) {
        return query(query, requestedModules, null, null);
    }

    public RagQueryResult query(String query, Set<ModuleId> requestedModules, String tableName, Integer topKOverride) {
        Set<ModuleId> modules = requestedModules == null ? Set.of() : requestedModules;
        if (!modules.isEmpty()) {
            throw new ModuleNotImplementedException(modules);
        }

        VectorStore storeToUse = resolveVectorStore(tableName);
        int effectiveTopK = (topKOverride != null && topKOverride > 0) ? topKOverride : this.topK;

        logger.info("Executing RAG query: \"{}\" (table={}, topK={})", query, tableName != null ? tableName : defaultTableName, effectiveTopK);

        Timer.Sample retrievalSample = Timer.start(meterRegistry);
        List<Document> retrieved = storeToUse.similaritySearch(
                SearchRequest.builder().query(query).topK(effectiveTopK).build());
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
        ChatResponse chatResponse;
        long generationNanos;
        try {
            Prompt prompt = new Prompt(buildPrompt(query, reranked));
            chatResponse = chatModel.call(prompt);
        } finally {
            progressTask.cancel(false);
            generationNanos = generationSample.stop(meterRegistry.timer("rag.pipeline.generation"));
        }
        long generationMs = TimeUnit.NANOSECONDS.toMillis(generationNanos);

        String answer = "";
        if (chatResponse != null && chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
            String text = chatResponse.getResult().getOutput().getText();
            if (text != null) {
                answer = text;
            }
        }
        logger.info("Answer generated in {} ms ({} chars)", generationMs, answer.length());

        TokenUsageDto tokenUsage = extractTokenUsage(chatResponse);

        List<RetrievedChunk> retrievedChunks = reranked.stream()
                .map(document -> new RetrievedChunk(document.getId(), snippet(document.getText()), document.getText()))
                .toList();

        return new RagQueryResult(
                answer,
                Set.of(),
                retrievedChunks,
                retrievalMs,
                generationMs,
                tokenUsage);
    }

    private VectorStore resolveVectorStore(String tableName) {
        if (tableName == null || tableName.isBlank() || tableName.equals(defaultTableName)) {
            return this.vectorStore;
        }
        if (!TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }
        if (!(this.vectorStore instanceof PgVectorStore)) {
            logger.warn("Dynamic table '{}' requested, but vectorStore is not PgVectorStore. Falling back to default store.", tableName);
            return this.vectorStore;
        }

        JdbcTemplate effectiveJdbc = this.jdbcTemplate;
        if (effectiveJdbc == null && this.vectorStore instanceof PgVectorStore pgStore) {
            effectiveJdbc = pgStore.<JdbcTemplate>getNativeClient().orElse(null);
        }

        if (effectiveJdbc == null || this.embeddingModel == null) {
            logger.warn("Dynamic table '{}' requested via PgVectorStore, but JdbcTemplate or EmbeddingModel is not available. Falling back to default store.", tableName);
            return this.vectorStore;
        }

        final JdbcTemplate finalJdbc = effectiveJdbc;
        return dynamicStores.computeIfAbsent(tableName, tbl -> {
            logger.info("Initializing dynamic PgVectorStore for table '{}'", tbl);
            PgVectorStore pgStore = (PgVectorStore) this.vectorStore;
            return PgVectorStore.builder(finalJdbc, this.embeddingModel)
                    .vectorTableName(tbl)
                    .initializeSchema(false)
                    .vectorTableValidationsEnabled(false)
                    .distanceType(pgStore.getDistanceType())
                    .build();
        });
    }

    private TokenUsageDto extractTokenUsage(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null || chatResponse.getMetadata().getUsage() == null) {
            return TokenUsageDto.ZERO;
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        long promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens().longValue() : 0L;
        long completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens().longValue() : 0L;
        long totalTokens = usage.getTotalTokens() != null ? usage.getTotalTokens().longValue() : 0L;
        long cachedTokens = usage.getCacheReadInputTokens() != null ? usage.getCacheReadInputTokens().longValue() : 0L;
        return new TokenUsageDto(promptTokens, completionTokens, totalTokens, cachedTokens);
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
