package com.dippa.ragbenchmark.application;

import com.dippa.ragbenchmark.api.CacheMetadataDto;
import com.dippa.ragbenchmark.api.TokenUsageDto;
import com.dippa.ragbenchmark.domain.CacheLookupResult;
import com.dippa.ragbenchmark.domain.CacheRequest;
import com.dippa.ragbenchmark.domain.CacheStatus;
import com.dippa.ragbenchmark.domain.ModuleId;
import com.dippa.ragbenchmark.domain.ModuleNotImplementedException;
import com.dippa.ragbenchmark.domain.PromptCache;
import com.dippa.ragbenchmark.domain.PromptCacheConfigurationException;
import com.dippa.ragbenchmark.domain.Reranker;
import com.dippa.ragbenchmark.domain.RerankingConfigurationException;
import com.dippa.ragbenchmark.domain.RetrievalExpander;
import com.dippa.ragbenchmark.infrastructure.NoOpPromptCache;
import com.dippa.ragbenchmark.infrastructure.NoOpReranker;
import com.dippa.ragbenchmark.infrastructure.TableNameValidator;
import com.dippa.ragbenchmark.infrastructure.config.GenerationProperties;
import com.dippa.ragbenchmark.infrastructure.config.PromptCacheProperties;
import com.dippa.ragbenchmark.infrastructure.config.RerankerProperties;
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
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
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
 * {@link Reranker} seam (M2), expands small-to-big contexts via {@link RetrievalExpander} (M1),
 * applies prompt/context caching via {@link PromptCache} (M3),
 * assembles the prompt, and generates an answer.
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
    private final PromptCache promptCache;
    private final RagPromptRenderer promptRenderer;
    private final PromptCacheProperties promptCacheProperties;
    private final GenerationProperties generationProperties;
    private final String chatModelName;
    private final MeterRegistry meterRegistry;
    private final int snippetLengthChars;
    private final int topK;
    private final int candidatePoolSize;
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
            RerankerProperties rerankerProperties,
            @Value("classpath:prompts/answer-generation.st") Resource answerPromptResource,
            @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store_test}") String defaultTableName,
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            ObjectProvider<RetrievalExpander> retrievalExpanderProvider,
            ObjectProvider<PromptCache> promptCacheProvider,
            ObjectProvider<RagPromptRenderer> promptRendererProvider,
            ObjectProvider<PromptCacheProperties> promptCachePropertiesProvider,
            ObjectProvider<GenerationProperties> generationPropertiesProvider,
            @Value("${spring.ai.google.genai.chat.model:gemini-3.8-flash}") String chatModelName) {
        this(
                vectorStore,
                chatModel,
                reranker,
                retrievalExpanderProvider != null ? retrievalExpanderProvider.getIfAvailable() : null,
                promptCacheProvider != null ? promptCacheProvider.getIfAvailable() : null,
                promptRendererProvider != null ? promptRendererProvider.getIfAvailable() : null,
                promptCachePropertiesProvider != null ? promptCachePropertiesProvider.getIfAvailable() : null,
                generationPropertiesProvider != null ? generationPropertiesProvider.getIfAvailable() : null,
                chatModelName,
                meterRegistry,
                snippetLengthChars,
                topK,
                rerankerProperties,
                answerPromptResource,
                defaultTableName,
                jdbcTemplateProvider != null ? jdbcTemplateProvider.getIfAvailable() : null,
                embeddingModelProvider != null ? embeddingModelProvider.getIfAvailable() : null);
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
        this(vectorStore, chatModel, reranker, retrievalExpander, null, null, null, null, null,
                meterRegistry, snippetLengthChars, topK,
                RerankerProperties.defaultProperties(), answerPromptResource, "vector_store_test", null, null);
    }

    public RagQueryService(
            VectorStore vectorStore,
            ChatModel chatModel,
            Reranker reranker,
            RetrievalExpander retrievalExpander,
            MeterRegistry meterRegistry,
            int snippetLengthChars,
            int topK,
            RerankerProperties rerankerProperties,
            Resource answerPromptResource) {
        this(vectorStore, chatModel, reranker, retrievalExpander, null, null, null, null, null,
                meterRegistry, snippetLengthChars, topK,
                rerankerProperties, answerPromptResource, "vector_store_test", null, null);
    }

    public RagQueryService(
            VectorStore vectorStore,
            ChatModel chatModel,
            Reranker reranker,
            RetrievalExpander retrievalExpander,
            MeterRegistry meterRegistry,
            int snippetLengthChars,
            int topK,
            RerankerProperties rerankerProperties,
            Resource answerPromptResource,
            String defaultTableName,
            JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel) {
        this(vectorStore, chatModel, reranker, retrievalExpander, null, null, null, null, null,
                meterRegistry, snippetLengthChars, topK,
                rerankerProperties, answerPromptResource, defaultTableName, jdbcTemplate, embeddingModel);
    }

    public RagQueryService(
            VectorStore vectorStore,
            ChatModel chatModel,
            Reranker reranker,
            RetrievalExpander retrievalExpander,
            PromptCache promptCache,
            RagPromptRenderer promptRenderer,
            PromptCacheProperties promptCacheProperties,
            String chatModelName,
            MeterRegistry meterRegistry,
            int snippetLengthChars,
            int topK,
            RerankerProperties rerankerProperties,
            Resource answerPromptResource,
            String defaultTableName,
            JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel) {
        this(vectorStore, chatModel, reranker, retrievalExpander, promptCache, promptRenderer, promptCacheProperties, null,
                chatModelName, meterRegistry, snippetLengthChars, topK, rerankerProperties, answerPromptResource,
                defaultTableName, jdbcTemplate, embeddingModel);
    }

    public RagQueryService(
            VectorStore vectorStore,
            ChatModel chatModel,
            Reranker reranker,
            RetrievalExpander retrievalExpander,
            PromptCache promptCache,
            RagPromptRenderer promptRenderer,
            PromptCacheProperties promptCacheProperties,
            GenerationProperties generationProperties,
            String chatModelName,
            MeterRegistry meterRegistry,
            int snippetLengthChars,
            int topK,
            RerankerProperties rerankerProperties,
            Resource answerPromptResource,
            String defaultTableName,
            JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.reranker = reranker;
        this.retrievalExpander = retrievalExpander != null ? retrievalExpander : childChunks -> childChunks;
        this.promptCache = promptCache != null ? promptCache : new NoOpPromptCache();
        this.promptRenderer = promptRenderer != null ? promptRenderer : new RagPromptRenderer(answerPromptResource);
        this.promptCacheProperties = promptCacheProperties != null ? promptCacheProperties : PromptCacheProperties.defaultProperties();
        this.generationProperties = generationProperties != null ? generationProperties : GenerationProperties.defaultProperties();
        this.chatModelName = (chatModelName != null && !chatModelName.isBlank()) ? chatModelName : "gemini-3.8-flash";
        this.meterRegistry = meterRegistry;
        this.snippetLengthChars = snippetLengthChars;
        this.topK = topK;
        this.candidatePoolSize = rerankerProperties != null ? rerankerProperties.candidatePoolSize() : 50;
        this.defaultTableName = (defaultTableName != null && !defaultTableName.isBlank()) ? defaultTableName : "vector_store_test";
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
    }

    public GenerationProperties getGenerationProperties() {
        return generationProperties;
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
                .filter(m -> m != ModuleId.M1 && m != ModuleId.M2 && m != ModuleId.M3)
                .collect(Collectors.toSet());
        if (!unsupported.isEmpty()) {
            throw new ModuleNotImplementedException(unsupported);
        }

        if (modules.contains(ModuleId.M2) && this.reranker instanceof NoOpReranker) {
            throw new RerankingConfigurationException(
                    "Module M2 was requested, but 'rag.reranker.provider' is configured as 'noop'. "
                    + "Set RAG_RERANKER_PROVIDER=vertex in your environment or properties to enable M2 semantic re-ranking.");
        }

        if (modules.contains(ModuleId.M3) && !this.promptCache.isConfigured()) {
            throw new PromptCacheConfigurationException(
                    "Module M3 was requested, but prompt caching provider is not configured or inactive. "
                    + "Set RAG_PROMPT_CACHE_PROVIDER=google-genai and configure Google GenAI credentials to enable M3 prompt caching.");
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
            reranked = retrieved;
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

        long cacheMs = 0L;
        CacheStatus cacheStatus = CacheStatus.NONE;
        CacheMetadataDto cacheMetadata = CacheMetadataDto.NONE;
        CacheLookupResult cacheResult = null;
        Prompt promptToSend;

        if (modules.contains(ModuleId.M3)) {
            Timer.Sample cacheSample = Timer.start(meterRegistry);
            String contextContent = promptRenderer.renderContext(contextDocuments);
            String systemInstruction = promptRenderer.getSystemInstruction();
            String fullCacheableContent = (systemInstruction != null && !systemInstruction.isBlank())
                    ? systemInstruction + "\n\n" + contextContent
                    : contextContent;
            long estimatedTokens = promptRenderer.estimateTokens(
                    fullCacheableContent, promptCacheProperties.charsPerTokenEstimate());
            String cacheKey = promptRenderer.computeCanonicalCacheKey(
                    promptCacheProperties.provider().name().toLowerCase(),
                    this.chatModelName,
                    searchTable,
                    systemInstruction,
                    contextDocuments);

            CacheRequest cacheRequest = new CacheRequest(
                    cacheKey,
                    this.chatModelName,
                    systemInstruction,
                    contextContent,
                    estimatedTokens);

            cacheResult = promptCache.getOrCreate(cacheRequest);
            long cacheNanos = cacheSample.stop(meterRegistry.timer("rag.pipeline.cache"));
            cacheMs = TimeUnit.NANOSECONDS.toMillis(cacheNanos);
            cacheStatus = cacheResult.status();
            cacheMetadata = CacheMetadataDto.fromDomain(cacheResult);

            if (cacheResult.isExplicitlyCached()) {
                String dynamicSuffix = promptRenderer.renderDynamicSuffix(query);
                GoogleGenAiChatOptions.Builder chatOptionsBuilder = GoogleGenAiChatOptions.builder()
                        .cachedContentName(cacheResult.cacheId())
                        .includeExtendedUsageMetadata(true);
                if (generationProperties.thinkingBudget() != null && generationProperties.thinkingBudget() >= 0) {
                    chatOptionsBuilder.thinkingBudget(generationProperties.thinkingBudget());
                }
                promptToSend = new Prompt(dynamicSuffix, chatOptionsBuilder.build());
                logger.info("Explicit prompt cache {} (id={}) evaluated in {} ms",
                        cacheStatus, cacheResult.cacheId(), cacheMs);
            } else {
                String fullPrompt = promptRenderer.renderFullPrompt(query, contextDocuments);
                promptToSend = createPromptWithGenerationOptions(fullPrompt);
                logger.info("Prompt cache evaluated in {} ms (status={})", cacheMs, cacheStatus);
            }
        } else {
            promptToSend = createPromptWithGenerationOptions(promptRenderer.renderFullPrompt(query, contextDocuments));
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
            chatResponse = chatModel.call(promptToSend);
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

        TokenUsageDto tokenUsage = extractTokenUsage(chatResponse, cacheResult);

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
                cacheMs,
                cacheStatus,
                cacheMetadata,
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

    private Prompt createPromptWithGenerationOptions(String promptContent) {
        if (this.chatModel instanceof GoogleGenAiChatModel) {
            GoogleGenAiChatOptions.Builder optionsBuilder = GoogleGenAiChatOptions.builder()
                    .includeExtendedUsageMetadata(true);
            if (generationProperties.thinkingBudget() != null && generationProperties.thinkingBudget() >= 0) {
                optionsBuilder.thinkingBudget(generationProperties.thinkingBudget());
            }
            return new Prompt(promptContent, optionsBuilder.build());
        }
        return new Prompt(promptContent);
    }

    private TokenUsageDto extractTokenUsage(ChatResponse chatResponse, CacheLookupResult cacheResult) {
        if (chatResponse == null || chatResponse.getMetadata() == null || chatResponse.getMetadata().getUsage() == null) {
            return TokenUsageDto.ZERO;
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        long promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens().longValue() : 0L;
        long completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens().longValue() : 0L;
        long totalTokens = usage.getTotalTokens() != null ? usage.getTotalTokens().longValue() : 0L;
        long cachedTokens = usage.getCacheReadInputTokens() != null ? usage.getCacheReadInputTokens().longValue() : 0L;
        long cacheWriteTokens = usage.getCacheWriteInputTokens() != null ? usage.getCacheWriteInputTokens().longValue() : 0L;

        if (cacheResult != null && cacheResult.isExplicitlyCached()) {
            if (cachedTokens == 0L && cacheResult.status() == CacheStatus.EXPLICIT_HIT) {
                cachedTokens = cacheResult.tokenCount();
            } else if (cacheWriteTokens == 0L && cacheResult.status() == CacheStatus.EXPLICIT_MISS) {
                cacheWriteTokens = cacheResult.tokenCount();
            }
        }
        return new TokenUsageDto(promptTokens, completionTokens, totalTokens, cachedTokens, cacheWriteTokens);
    }

    private String snippet(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= snippetLengthChars ? text : text.substring(0, snippetLengthChars) + "...";
    }
}
