package com.dippa.ragbenchmark.application;

import com.dippa.ragbenchmark.api.TokenUsageDto;
import com.dippa.ragbenchmark.domain.ModuleId;
import com.dippa.ragbenchmark.domain.ModuleNotImplementedException;
import com.dippa.ragbenchmark.domain.Reranker;
import com.dippa.ragbenchmark.domain.RerankingConfigurationException;
import com.dippa.ragbenchmark.domain.RetrievalExpander;
import com.dippa.ragbenchmark.infrastructure.NoOpReranker;
import com.dippa.ragbenchmark.infrastructure.TableNameValidator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
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
 * Orchestrates the RAG pipeline: retrieves chunks, passes them through the
 * {@link Reranker} seam, expands small-to-big contexts via {@link RetrievalExpander} when M1 is active,
 * assembles a prompt, and generates an answer.
 */
@Service
public class RagQueryService {

    private static final Logger logger = LoggerFactory.getLogger(RagQueryService.class);
    private static final java.util.concurrent.ScheduledExecutorService PROGRESS_SCHEDULER = java.util.concurrent.Executors
            .newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "rag-progress-logger");
                thread.setDaemon(true);
                return thread;
            });

    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final Reranker reranker;
    private final RetrievalExpander retrievalExpander;
    private final MeterRegistry meterRegistry;
    private final int snippetLengthChars;
    private final int topK;
    private final int candidatePoolSize;
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
            @Value("${rag.reranker.candidate-pool-size:50}") int candidatePoolSize,
            @Value("classpath:prompts/answer-generation.st") Resource answerPromptResource,
            @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store_test}") String defaultTableName,
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            ObjectProvider<RetrievalExpander> retrievalExpanderProvider) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.reranker = reranker;
        this.meterRegistry = meterRegistry;
        this.snippetLengthChars = snippetLengthChars;
        this.topK = topK;
        this.candidatePoolSize = candidatePoolSize;
        this.answerPromptTemplate = new PromptTemplate(answerPromptResource);
        this.defaultTableName = (defaultTableName != null && !defaultTableName.isBlank()) ? defaultTableName : "vector_store_test";
        this.jdbcTemplate = jdbcTemplateProvider != null ? jdbcTemplateProvider.getIfAvailable() : null;
        this.embeddingModel = embeddingModelProvider != null ? embeddingModelProvider.getIfAvailable() : null;
        this.retrievalExpander = (retrievalExpanderProvider != null && retrievalExpanderProvider.getIfAvailable() != null)
                ? retrievalExpanderProvider.getIfAvailable()
                : childChunks -> childChunks;
    }

    public RagQueryService(
            VectorStore vectorStore,
            ChatModel chatModel,
            Reranker reranker,
            MeterRegistry meterRegistry,
            int snippetLengthChars,
            int topK,
            Resource answerPromptResource) {
        this(vectorStore, chatModel, reranker, (RetrievalExpander) null, meterRegistry, snippetLengthChars, topK, 50, answerPromptResource,
                "vector_store_test", (JdbcTemplate) null, (EmbeddingModel) null);
    }

    public RagQueryService(
            VectorStore vectorStore,
            ChatModel chatModel,
            Reranker reranker,
            RetrievalExpander retrievalExpander,
            MeterRegistry meterRegistry,
            int snippetLengthChars,
            int topK,
            Resource answerPromptResource) {
        this(vectorStore, chatModel, reranker, retrievalExpander, meterRegistry, snippetLengthChars, topK, 50, answerPromptResource,
                "vector_store_test", (JdbcTemplate) null, (EmbeddingModel) null);
    }

    public RagQueryService(
            VectorStore vectorStore,
            ChatModel chatModel,
            Reranker reranker,
            RetrievalExpander retrievalExpander,
            MeterRegistry meterRegistry,
            int snippetLengthChars,
            int topK,
            int candidatePoolSize,
            Resource answerPromptResource) {
        this(vectorStore, chatModel, reranker, retrievalExpander, meterRegistry, snippetLengthChars, topK, candidatePoolSize, answerPromptResource,
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
        this(vectorStore, chatModel, reranker, (RetrievalExpander) null, meterRegistry, snippetLengthChars, topK, 50, answerPromptResource,
                defaultTableName, jdbcTemplate, embeddingModel);
    }

    public RagQueryService(
            VectorStore vectorStore,
            ChatModel chatModel,
            Reranker reranker,
            RetrievalExpander retrievalExpander,
            MeterRegistry meterRegistry,
            int snippetLengthChars,
            int topK,
            Resource answerPromptResource,
            String defaultTableName,
            JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel) {
        this(vectorStore, chatModel, reranker, retrievalExpander, meterRegistry, snippetLengthChars, topK, 50,
                answerPromptResource, defaultTableName, jdbcTemplate, embeddingModel);
    }

    public RagQueryService(
            VectorStore vectorStore,
            ChatModel chatModel,
            Reranker reranker,
            RetrievalExpander retrievalExpander,
            MeterRegistry meterRegistry,
            int snippetLengthChars,
            int topK,
            int candidatePoolSize,
            Resource answerPromptResource,
            String defaultTableName,
            JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.reranker = reranker;
        this.retrievalExpander = retrievalExpander != null ? retrievalExpander : childChunks -> childChunks;
        this.meterRegistry = meterRegistry;
        this.snippetLengthChars = snippetLengthChars;
        this.topK = topK;
        this.candidatePoolSize = candidatePoolSize;
        this.answerPromptTemplate = new PromptTemplate(answerPromptResource);
        this.defaultTableName = (defaultTableName != null && !defaultTableName.isBlank()) ? defaultTableName : "vector_store_test";
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
    }

    public String getDefaultTableName() {
        return defaultTableName;
    }

    public RagQueryResult query(String query, Set<ModuleId> requestedModules) {
        return query(query, requestedModules, null, null, null);
    }

    public RagQueryResult query(String query, Set<ModuleId> requestedModules, String tableName, Integer topKOverride) {
        return query(query, requestedModules, tableName, null, topKOverride);
    }

    public RagQueryResult query(
            String query,
            Set<ModuleId> requestedModules,
            String tableName,
            String parentTableName,
            Integer topKOverride) {
        Set<ModuleId> modules = requestedModules == null ? Set.of() : requestedModules;
        Set<ModuleId> unsupported = modules.stream()
                .filter(m -> m != ModuleId.M1 && m != ModuleId.M2)
                .collect(Collectors.toSet());
        if (!unsupported.isEmpty()) {
            throw new ModuleNotImplementedException(unsupported);
        }

        if (modules.contains(ModuleId.M2) && this.reranker instanceof NoOpReranker) {
            throw new RerankingConfigurationException(
                    "Module M2 was requested, but 'rag.reranker.provider' is configured as 'noop'. "
                    + "Set RAG_RERANKER_PROVIDER=vertex in your environment or properties to enable M2 semantic re-ranking.");
        }

        String searchTable;
        String resolvedParentTable = null;

        if (modules.contains(ModuleId.M1)) {
            String baseTable = (tableName != null && !tableName.isBlank()) ? tableName : this.defaultTableName;
            if (baseTable.equalsIgnoreCase("vector_store_musique")) {
                searchTable = "vector_store_musique_small";
                resolvedParentTable = (parentTableName != null && !parentTableName.isBlank()) ? parentTableName : "vector_store_musique_large";
            } else if (baseTable.equalsIgnoreCase("vector_store_nordea")) {
                searchTable = "vector_store_nordea_small";
                resolvedParentTable = (parentTableName != null && !parentTableName.isBlank()) ? parentTableName : "vector_store_nordea_large";
            } else if (baseTable.endsWith("_small")) {
                searchTable = baseTable;
                resolvedParentTable = (parentTableName != null && !parentTableName.isBlank()) ? parentTableName : baseTable.replaceAll("_small$", "_large");
            } else if (parentTableName != null && !parentTableName.isBlank()) {
                searchTable = baseTable;
                resolvedParentTable = parentTableName;
            } else {
                searchTable = baseTable + "_small";
                resolvedParentTable = baseTable + "_large";
            }
        } else {
            searchTable = (tableName != null && !tableName.isBlank()) ? tableName : this.defaultTableName;
        }

        VectorStore storeToUse = resolveVectorStore(searchTable);
        if (topKOverride != null && topKOverride <= 0) {
            throw new IllegalArgumentException("topK must be greater than zero");
        }
        int effectiveTopK = topKOverride != null ? topKOverride : this.topK;
        int retrievalTopK = modules.contains(ModuleId.M2) ? this.candidatePoolSize : effectiveTopK;

        logger.info("Executing RAG query: \"{}\" (searchTable={}, parentTable={}, retrievalTopK={}, effectiveTopK={}, modules={})",
                query, searchTable, resolvedParentTable, retrievalTopK, effectiveTopK, modules);

        Timer.Sample retrievalSample = Timer.start(meterRegistry);
        List<Document> retrieved = storeToUse.similaritySearch(
                SearchRequest.builder().query(query).topK(retrievalTopK).build());
        long retrievalNanos = retrievalSample.stop(meterRegistry.timer("rag.pipeline.retrieval"));
        long retrievalMs = TimeUnit.NANOSECONDS.toMillis(retrievalNanos);
        logger.info("Retrieved {} candidate chunk(s) in {} ms.", retrieved.size(), retrievalMs);

        List<Document> reranked;
        long rerankMs = 0L;
        if (modules.contains(ModuleId.M2)) {
            Timer.Sample rerankSample = Timer.start(meterRegistry);
            reranked = reranker.rerank(query, retrieved, effectiveTopK);
            long rerankNanos = rerankSample.stop(meterRegistry.timer("rag.pipeline.rerank"));
            rerankMs = TimeUnit.NANOSECONDS.toMillis(rerankNanos);
            logger.info("Reranked {} candidate(s) to {} chunk(s) in {} ms.",
                    retrieved.size(), reranked.size(), rerankMs);
        } else {
            reranked = reranker.rerank(query, retrieved);
        }

        List<Document> contextDocuments = reranked;
        long expansionMs = 0L;
        if (modules.contains(ModuleId.M1)) {
            Timer.Sample expansionSample = Timer.start(meterRegistry);
            contextDocuments = retrievalExpander.expand(reranked, resolvedParentTable);
            long expansionNanos = expansionSample.stop(meterRegistry.timer("rag.pipeline.expansion"));
            expansionMs = TimeUnit.NANOSECONDS.toMillis(expansionNanos);
            logger.info("Expanded {} child chunk(s) into {} parent document(s) in {} ms",
                    reranked.size(), contextDocuments.size(), expansionMs);
        }

        long startGenTime = System.currentTimeMillis();
        java.util.concurrent.ScheduledFuture<?> progressTask = PROGRESS_SCHEDULER.scheduleAtFixedRate(() -> {
            long elapsedSeconds = (System.currentTimeMillis() - startGenTime) / 1000;
            logger.info("Still generating answer... took {}s", elapsedSeconds);
        }, 3, 3, TimeUnit.SECONDS);

        Timer.Sample generationSample = Timer.start(meterRegistry);
        ChatResponse chatResponse;
        long generationNanos;
        try {
            Prompt prompt = new Prompt(buildPrompt(query, contextDocuments));
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

        List<RetrievedChunk> retrievedChunks = contextDocuments.stream()
                .map(document -> new RetrievedChunk(document.getId(), snippet(document.getText()), document.getText()))
                .toList();

        return new RagQueryResult(
                answer,
                modules,
                retrievedChunks,
                retrievalMs,
                generationMs,
                expansionMs,
                rerankMs,
                tokenUsage);
    }

    private VectorStore resolveVectorStore(String tableName) {
        if (tableName == null || tableName.isBlank() || tableName.equals(defaultTableName)) {
            return this.vectorStore;
        }
        TableNameValidator.requireValid(tableName, "table");
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
