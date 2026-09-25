package com.dippa.ragbenchmark.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dippa.ragbenchmark.api.TokenUsageDto;
import com.dippa.ragbenchmark.domain.CacheLookupResult;
import com.dippa.ragbenchmark.domain.CacheRequest;
import com.dippa.ragbenchmark.domain.CacheStatus;
import com.dippa.ragbenchmark.domain.ComplexityTier;
import com.dippa.ragbenchmark.domain.ModelRouter;
import com.dippa.ragbenchmark.domain.ModuleId;
import com.dippa.ragbenchmark.domain.ModuleNotImplementedException;
import com.dippa.ragbenchmark.domain.PromptCache;
import com.dippa.ragbenchmark.domain.PromptCacheConfigurationException;
import com.dippa.ragbenchmark.domain.Reranker;
import com.dippa.ragbenchmark.domain.RerankingConfigurationException;
import com.dippa.ragbenchmark.domain.RetrievalExpander;
import com.dippa.ragbenchmark.domain.RouterConfigurationException;
import com.dippa.ragbenchmark.domain.RoutingDecision;
import com.dippa.ragbenchmark.domain.RoutingMode;
import com.dippa.ragbenchmark.domain.RoutingRequest;
import com.dippa.ragbenchmark.infrastructure.NoOpReranker;
import com.dippa.ragbenchmark.infrastructure.config.GenerationProperties;
import com.dippa.ragbenchmark.infrastructure.config.PromptCacheProperties;
import com.dippa.ragbenchmark.infrastructure.config.RerankerProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import reactor.core.publisher.Flux;

/**
 * Plain unit tests for {@link RagQueryService} — exercises M0, M1, M2, and M3 pipelines.
 */
@ExtendWith(MockitoExtension.class)
class RagQueryServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChatModel chatModel;

    @Mock
    private Reranker reranker;

    @Mock
    private RetrievalExpander retrievalExpander;

    @Mock
    private PromptCache promptCache;

    private MeterRegistry meterRegistry;
    private RagPromptRenderer promptRenderer;
    private PromptCacheProperties promptCacheProperties;
    private RagQueryService ragQueryService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        promptRenderer = new RagPromptRenderer();
        promptCacheProperties = PromptCacheProperties.defaultProperties();

        ragQueryService = new RagQueryService(
                vectorStore,
                chatModel,
                reranker,
                retrievalExpander,
                promptCache,
                promptRenderer,
                promptCacheProperties,
                "gemini-3.8-flash",
                meterRegistry,
                Integer.MAX_VALUE,
                5,
                RerankerProperties.defaultProperties(),
                new ClassPathResource("prompts/answer-generation.st"),
                "vector_store_test",
                null,
                null);
    }

    private ChatResponse createChatResponse(String text, Usage usage) {
        Generation generation = new Generation(new AssistantMessage(text));
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .usage(usage)
                .build();
        return new ChatResponse(List.of(generation), metadata);
    }

    @Test
    void happyPath_retrievesChunksAssemblesPromptAndReturnsAnswerWithTimingsAndUsage() {
        Document chunk1 = Document.builder().id("1").text("First chunk").build();
        Document chunk2 = Document.builder().id("2").text("Second chunk").build();
        List<Document> retrievedDocs = List.of(chunk1, chunk2);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(retrievedDocs);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(createChatResponse("Test answer", new DefaultUsage(10, 5, 15)));

        RagQueryResult result = ragQueryService.query("What is the test question?", null);

        assertThat(result.answer()).isEqualTo("Test answer");
        assertThat(result.modulesApplied()).isEmpty();
        assertThat(result.retrievedChunks()).hasSize(2);
        assertThat(result.retrievedChunks().get(0).id()).isEqualTo("1");
        assertThat(result.retrievedChunks().get(0).text()).isEqualTo("First chunk");
        assertThat(result.retrievedChunks().get(1).id()).isEqualTo("2");
        assertThat(result.retrievedChunks().get(1).text()).isEqualTo("Second chunk");

        assertThat(result.retrievalMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.generationMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.totalLatencyMs()).isEqualTo(result.retrievalMs() + result.generationMs() + result.expansionMs() + result.rerankMs() + result.cacheMs());

        assertThat(result.tokenUsage()).isEqualTo(new TokenUsageDto(10, 5, 15, 0, 0, 0, 0, 0));

        assertThat(meterRegistry.find("rag.pipeline.retrieval").timer()).isNotNull();
        assertThat(meterRegistry.find("rag.pipeline.generation").timer()).isNotNull();
    }

    @Test
    void topKOverride_usesProvidedValueInsteadOfDefault() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("Answer", null));

        ragQueryService.query("query", null, null, 10);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getTopK()).isEqualTo(10);
    }

    @Test
    void topKOverride_zeroOrNegative_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> ragQueryService.query("query", null, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topK must be greater than zero");

        assertThatThrownBy(() -> ragQueryService.query("query", null, null, -5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topK must be greater than zero");
    }

    @Test
    void promptAssembly_joinsChunksWithDashesAndRendersQuestion() {
        Document chunk1 = Document.builder().id("1").text("First chunk text").build();
        Document chunk2 = Document.builder().id("2").text("Second chunk text").build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(chunk1, chunk2));
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("Answer", null));

        ragQueryService.query("My Question?", null);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());

        String renderedPrompt = promptCaptor.getValue().getContents();
        assertThat(renderedPrompt)
                .contains("Context:\nFirst chunk text\n---\nSecond chunk text")
                .contains("Question: My Question?")
                .contains("Answer the question using only the context provided below.");
    }

    @Test
    void snippetTruncation_respectsSnippetLengthChars() {
        RagQueryService serviceWithShortSnippets = new RagQueryService(
                vectorStore, chatModel, reranker, retrievalExpander, meterRegistry, 5, 5,
                new ClassPathResource("prompts/answer-generation.st"));

        Document doc = Document.builder().id("1").text("1234567890").build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("Answer", null));

        RagQueryResult result = serviceWithShortSnippets.query("query", null);

        assertThat(result.retrievedChunks().get(0).snippet()).isEqualTo("12345...");
        assertThat(result.retrievedChunks().get(0).text()).isEqualTo("1234567890");
    }

    @Test
    void nullUsage_mapsToZeroTokens() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("Answer", null));

        RagQueryResult result = ragQueryService.query("query", null);

        assertThat(result.tokenUsage()).isEqualTo(TokenUsageDto.ZERO);
    }

    @Test
    void nullChatResponse_handlesGracefully() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(chatModel.call(any(Prompt.class))).thenReturn(null);

        RagQueryResult result = ragQueryService.query("query", null);

        assertThat(result.answer()).isEmpty();
        assertThat(result.tokenUsage()).isEqualTo(TokenUsageDto.ZERO);
    }

    @Test
    void emptyRetrieval_generatesAnswerWithEmptyContext() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("I don't know.", null));

        RagQueryResult result = ragQueryService.query("query", null);

        assertThat(result.modulesApplied()).isEmpty();
        assertThat(result.retrievedChunks()).isEmpty();
    }

    @Test
    void requestedModules_failsFastBeforeTouchingAnyDependency() {
        assertThatThrownBy(() -> ragQueryService.query("query", Set.of(ModuleId.M4)))
                .isInstanceOf(RouterConfigurationException.class)
                .hasMessageContaining("Module M4 was requested, but 'rag.router.provider' is configured as 'noop'");

        verifyNoInteractions(vectorStore, chatModel, reranker);
    }

    @Test
    void m2Requested_whenRerankerIsNoOp_throwsRerankingConfigurationException() {
        RagQueryService serviceWithNoOp = new RagQueryService(
                vectorStore, chatModel, new NoOpReranker(), retrievalExpander,
                meterRegistry, 50, 5, new ClassPathResource("prompts/answer-generation.st"));

        assertThatThrownBy(() -> serviceWithNoOp.query("query", Set.of(ModuleId.M2)))
                .isInstanceOf(RerankingConfigurationException.class)
                .hasMessageContaining("RAG_RERANKER_PROVIDER=vertex");

        verifyNoInteractions(vectorStore, chatModel);
    }

    @Test
    void m3Requested_whenPromptCacheNotConfigured_throwsPromptCacheConfigurationException() {
        when(promptCache.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> ragQueryService.query("query", Set.of(ModuleId.M3)))
                .isInstanceOf(PromptCacheConfigurationException.class)
                .hasMessageContaining("RAG_PROMPT_CACHE_PROVIDER=google-genai");

        verifyNoInteractions(vectorStore, chatModel, reranker);
    }

    @Test
    void m3SubThreshold_reportsNotEligibleAndExecutesFullPrompt() {
        Document chunk = Document.builder().id("c1").text("Short chunk content.").build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(chunk));
        when(promptCache.isConfigured()).thenReturn(true);
        when(promptCache.getOrCreate(any(CacheRequest.class)))
                .thenReturn(CacheLookupResult.notEligible(100L));
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("Short answer.", null));

        RagQueryResult result = ragQueryService.query("What is short?", Set.of(ModuleId.M3));

        assertThat(result.modulesApplied()).containsExactly(ModuleId.M3);
        assertThat(result.cacheStatus()).isEqualTo(CacheStatus.NOT_ELIGIBLE);
        assertThat(result.cacheMs()).isGreaterThanOrEqualTo(0L);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getContents())
                .contains("Context:\nShort chunk content.")
                .contains("Question: What is short?");

        assertThat(meterRegistry.find("rag.pipeline.cache").timer()).isNotNull();
    }

    @Test
    void m3ExplicitHit_setsCachedContentOptionAndPassesDynamicSuffixOnly() {
        Document chunk = Document.builder().id("c1").text("Large chunk content that meets the threshold.").build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(chunk));
        when(promptCache.isConfigured()).thenReturn(true);
        when(promptCache.getOrCreate(any(CacheRequest.class)))
                .thenReturn(CacheLookupResult.hit("cachedContents/hit-123", 5120L, Instant.now().plusSeconds(300)));
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("Cached answer.", null));

        RagQueryResult result = ragQueryService.query("Cached question?", Set.of(ModuleId.M3));

        assertThat(result.modulesApplied()).containsExactly(ModuleId.M3);
        assertThat(result.cacheStatus()).isEqualTo(CacheStatus.EXPLICIT_HIT);
        assertThat(result.cacheMetadata().cacheId()).isEqualTo("cachedContents/hit-123");
        assertThat(result.cacheMetadata().tokenCount()).isEqualTo(5120L);
        assertThat(result.cacheMs()).isGreaterThanOrEqualTo(0L);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());

        Prompt promptSent = promptCaptor.getValue();
        // The prompt body should only contain the dynamic suffix
        assertThat(promptSent.getContents()).isEqualTo("Question: Cached question?\nAnswer:");
        // The options should contain cachedContentName
        assertThat(promptSent.getOptions()).isInstanceOf(GoogleGenAiChatOptions.class);
        GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) promptSent.getOptions();
        assertThat(options.getCachedContentName()).isEqualTo("cachedContents/hit-123");
        assertThat(options.getThinkingBudget()).isEqualTo(0);
        assertThat(options.getIncludeExtendedUsageMetadata()).isTrue();
    }

    @Test
    void m3ExplicitMiss_setsCachedContentOptionAndReportsExplicitMiss() {
        Document chunk = Document.builder().id("c1").text("Large chunk content on cold creation.").build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(chunk));
        when(promptCache.isConfigured()).thenReturn(true);
        when(promptCache.getOrCreate(any(CacheRequest.class)))
                .thenReturn(CacheLookupResult.miss("cachedContents/miss-456", 5120L, Instant.now().plusSeconds(300)));
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("Cold created answer.", null));

        RagQueryResult result = ragQueryService.query("Cold question?", Set.of(ModuleId.M3));

        assertThat(result.modulesApplied()).containsExactly(ModuleId.M3);
        assertThat(result.cacheStatus()).isEqualTo(CacheStatus.EXPLICIT_MISS);
        assertThat(result.cacheMs()).isGreaterThanOrEqualTo(0L);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());

        Prompt promptSent = promptCaptor.getValue();
        assertThat(promptSent.getContents()).isEqualTo("Question: Cold question?\nAnswer:");
        GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) promptSent.getOptions();
        assertThat(options.getCachedContentName()).isEqualTo("cachedContents/miss-456");
        assertThat(options.getThinkingBudget()).isEqualTo(0);
        assertThat(options.getIncludeExtendedUsageMetadata()).isTrue();
    }

    @Test
    void customThinkingBudget_appliesToPromptOptions() {
        RagQueryService customService = new RagQueryService(
                vectorStore,
                chatModel,
                reranker,
                retrievalExpander,
                promptCache,
                promptRenderer,
                promptCacheProperties,
                new GenerationProperties(256),
                "gemini-3.8-flash",
                meterRegistry,
                Integer.MAX_VALUE,
                5,
                RerankerProperties.defaultProperties(),
                new ClassPathResource("prompts/answer-generation.st"),
                "vector_store_test",
                null,
                null);

        Document chunk = Document.builder().id("c1").text("Sample text.").build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(chunk));
        when(promptCache.isConfigured()).thenReturn(true);
        when(promptCache.getOrCreate(any(CacheRequest.class)))
                .thenReturn(CacheLookupResult.hit("cachedContents/hit-custom", 5120L, Instant.now().plusSeconds(300)));
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("Custom answer.", null));

        customService.query("Custom question?", Set.of(ModuleId.M3));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());

        GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) promptCaptor.getValue().getOptions();
        assertThat(options.getThinkingBudget()).isEqualTo(256);
    }

    @Test
    void m2HappyPath_retrievesCandidatePoolAndReranksTopK() {
        Document candidate1 = Document.builder().id("c1").text("Text 1").build();
        Document candidate2 = Document.builder().id("c2").text("Text 2").build();
        List<Document> retrievedPool = List.of(candidate1, candidate2);
        Document reranked1 = Document.builder().id("c2").text("Text 2").metadata("rerank_score", 0.99).build();
        List<Document> rerankedDocs = List.of(reranked1);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(retrievedPool);
        when(reranker.rerank("What is re-ranking?", retrievedPool, 5)).thenReturn(rerankedDocs);
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("Re-ranked answer", null));

        RagQueryResult result = ragQueryService.query("What is re-ranking?", Set.of(ModuleId.M2));

        assertThat(result.modulesApplied()).containsExactly(ModuleId.M2);
        assertThat(result.retrievedChunks()).hasSize(1);
        assertThat(result.retrievedChunks().get(0).id()).isEqualTo("c2");
        assertThat(result.rerankMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.totalLatencyMs()).isEqualTo(result.retrievalMs() + result.generationMs() + result.expansionMs() + result.rerankMs() + result.cacheMs());

        ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(searchCaptor.capture());
        assertThat(searchCaptor.getValue().getTopK()).isEqualTo(50);

        verify(reranker).rerank("What is re-ranking?", retrievedPool, 5);
        assertThat(meterRegistry.find("rag.pipeline.rerank").timer()).isNotNull();
    }

    @Test
    void m1HappyPath_retrievesChildChunksAndExpandsToParentDocuments() {
        Document childOne = Document.builder().id("child-1").text("Paris is the capital.").build();
        Document parentDoc = Document.builder().id("parent-1").text("Paris is the capital of France and has the Eiffel Tower.").build();
        List<Document> retrievedChildren = List.of(childOne);
        List<Document> expandedParents = List.of(parentDoc);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(retrievedChildren);
        when(retrievalExpander.expand(any(), any())).thenReturn(expandedParents);
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("In Paris.", null));

        RagQueryResult result = ragQueryService.query("Where is the Eiffel Tower?", Set.of(ModuleId.M1));

        assertThat(result.answer()).isEqualTo("In Paris.");
        assertThat(result.modulesApplied()).containsExactly(ModuleId.M1);
        assertThat(result.retrievedChunks()).hasSize(1);
        assertThat(result.retrievedChunks().get(0).id()).isEqualTo("parent-1");
        assertThat(result.retrievedChunks().get(0).text()).isEqualTo("Paris is the capital of France and has the Eiffel Tower.");
        assertThat(result.expansionMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.rerankMs()).isZero();
        assertThat(result.totalLatencyMs()).isEqualTo(result.retrievalMs() + result.generationMs() + result.expansionMs() + result.cacheMs());

        verifyNoInteractions(reranker);
        verify(retrievalExpander).expand(any(), any());
        assertThat(meterRegistry.find("rag.pipeline.expansion").timer()).isNotNull();
    }

    @Test
    void m1_routesToNordeaClosedSmallAndLarge_whenTableNameIsVectorStoreNordeaClosed() {
        Document childChunk = Document.builder().id("c-nordea-1").text("Nordea child text").build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(childChunk));

        Document parentDoc = Document.builder().id("p-nordea-1").text("Nordea parent text").build();
        when(retrievalExpander.expand(any(), org.mockito.ArgumentMatchers.eq("vector_store_nordea_closed_large"))).thenReturn(List.of(parentDoc));
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("Nordea vastaus", null));

        RagQueryResult result = ragQueryService.query(
                "Miten tili avataan?",
                Set.of(ModuleId.M1),
                "vector_store_nordea_closed",
                null,
                null,
                null
        );

        assertThat(result.answer()).isEqualTo("Nordea vastaus");
        assertThat(result.modulesApplied()).containsExactly(ModuleId.M1);
        verify(retrievalExpander).expand(any(), org.mockito.ArgumentMatchers.eq("vector_store_nordea_closed_large"));
    }

    @Test
    void m1m2m3Combination_executesRerankThenExpansionThenExplicitCaching() {
        Document child1 = Document.builder().id("c1").text("Child text 1").build();
        Document child2 = Document.builder().id("c2").text("Child text 2").build();
        List<Document> retrievedPool = List.of(child1, child2);

        Document rerankedChild = Document.builder().id("c2").text("Child text 2").build();
        List<Document> rerankedPool = List.of(rerankedChild);

        Document parentDoc = Document.builder().id("p2").text("Expanded parent window for child 2 containing 1024 tokens.").build();
        List<Document> expandedParents = List.of(parentDoc);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(retrievedPool);
        when(reranker.rerank(anyString(), any(), any(Integer.class))).thenReturn(rerankedPool);
        when(retrievalExpander.expand(any(), any())).thenReturn(expandedParents);
        when(promptCache.isConfigured()).thenReturn(true);
        when(promptCache.getOrCreate(any(CacheRequest.class)))
                .thenReturn(CacheLookupResult.hit("cachedContents/comb-789", 5120L, Instant.now().plusSeconds(300)));
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("Combo answer.", null));

        RagQueryResult result = ragQueryService.query("Complex combo query?", Set.of(ModuleId.M1, ModuleId.M2, ModuleId.M3));

        assertThat(result.modulesApplied()).containsExactlyInAnyOrder(ModuleId.M1, ModuleId.M2, ModuleId.M3);
        assertThat(result.cacheStatus()).isEqualTo(CacheStatus.EXPLICIT_HIT);
        assertThat(result.retrievedChunks().get(0).id()).isEqualTo("p2");
        assertThat(result.rerankMs()).isGreaterThanOrEqualTo(0L);
        assertThat(result.expansionMs()).isGreaterThanOrEqualTo(0L);
        assertThat(result.cacheMs()).isGreaterThanOrEqualTo(0L);

        verify(reranker).rerank("Complex combo query?", retrievedPool, 5);
        verify(retrievalExpander).expand(rerankedPool, "vector_store_test_large");
        verify(promptCache).getOrCreate(any(CacheRequest.class));
    }

    @Test
    void m4Requested_isolated_routesToExpectedModelTier_andRecordsRouteMsAndTokens() {
        ModelRouter mockRouter = mock(ModelRouter.class);
        when(mockRouter.isConfigured()).thenReturn(true);
        when(mockRouter.route(any(RoutingRequest.class))).thenReturn(new RoutingDecision(
                "gemini-3.1-pro",
                ComplexityTier.MULTI_HOP,
                3,
                142L,
                28,
                1,
                RoutingMode.CLASSIFIER,
                "3"
        ));

        RagQueryService serviceWithM4 = new RagQueryService(
                vectorStore, chatModel, reranker, retrievalExpander, promptCache, mockRouter,
                meterRegistry, 50, 5, new org.springframework.core.io.ClassPathResource("prompts/answer-generation.st"));

        Document doc = Document.builder().id("d1").text("Sample text").build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("Pro answer", new DefaultUsage(100, 50, 150)));

        RagQueryResult result = serviceWithM4.query("Complex multi-hop question", Set.of(ModuleId.M4), null, null, null, 3);

        assertThat(result.modulesApplied()).containsExactly(ModuleId.M4);
        assertThat(result.modelUsed()).isEqualTo("gemini-3.1-pro");
        assertThat(result.routeMs()).isEqualTo(142L);
        assertThat(result.tokenUsage().routingPromptTokens()).isEqualTo(28);
        assertThat(result.tokenUsage().routingCompletionTokens()).isEqualTo(1);
        assertThat(result.tokenUsage().routingTokens()).isEqualTo(29);
        assertThat(result.totalLatencyMs()).isGreaterThanOrEqualTo(142L);

        verify(mockRouter).route(new RoutingRequest("Complex multi-hop question", 3));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        Prompt generationPrompt = promptCaptor.getValue();
        assertThat(generationPrompt.getOptions()).isNotNull();
        assertThat(generationPrompt.getOptions().getModel()).isEqualTo("gemini-3.1-pro");
    }

    @Test
    void m4AndM3Requested_bindsContextCacheKeyAndGeneratorToRoutedModel() {
        ModelRouter mockRouter = mock(ModelRouter.class);
        when(mockRouter.isConfigured()).thenReturn(true);
        when(mockRouter.route(any(RoutingRequest.class))).thenReturn(new RoutingDecision(
                "gemini-3.5-flash-lite",
                ComplexityTier.SINGLE_HOP,
                1,
                85L,
                25,
                1,
                RoutingMode.CLASSIFIER,
                "1"
        ));

        RagQueryService serviceWithM3M4 = new RagQueryService(
                vectorStore, chatModel, reranker, retrievalExpander, promptCache, mockRouter,
                meterRegistry, 50, 5, new org.springframework.core.io.ClassPathResource("prompts/answer-generation.st"));

        Document doc = Document.builder().id("d1").text("Sample text").build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
        when(promptCache.isConfigured()).thenReturn(true);
        when(promptCache.getOrCreate(any(CacheRequest.class)))
                .thenReturn(CacheLookupResult.hit("cachedContents/routed-lite-123", 4200L, Instant.now().plusSeconds(300)));
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("Lite answer", new DefaultUsage(10, 20, 30)));

        RagQueryResult result = serviceWithM3M4.query("Simple query", Set.of(ModuleId.M3, ModuleId.M4));

        assertThat(result.modulesApplied()).containsExactlyInAnyOrder(ModuleId.M3, ModuleId.M4);
        assertThat(result.modelUsed()).isEqualTo("gemini-3.5-flash-lite");
        assertThat(result.routeMs()).isEqualTo(85L);

        ArgumentCaptor<CacheRequest> cacheRequestCaptor = ArgumentCaptor.forClass(CacheRequest.class);
        verify(promptCache).getOrCreate(cacheRequestCaptor.capture());
        assertThat(cacheRequestCaptor.getValue().model()).isEqualTo("gemini-3.5-flash-lite");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        Prompt generationPrompt = promptCaptor.getValue();
        assertThat(generationPrompt.getOptions()).isNotNull();
        assertThat(generationPrompt.getOptions().getModel()).isEqualTo("gemini-3.5-flash-lite");
    }

    @Test
    void query_withStreaming_assemblesAnswerAndCapturesTtft() {
        Document doc = Document.builder().id("d1").text("Stream context").build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        ChatResponse chunk1 = createChatResponse("Hello ", null);
        ChatResponse chunk2 = createChatResponse("world!", new DefaultUsage(25, 10, 35));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(chunk1, chunk2));

        RagQueryResult result = ragQueryService.query("Hello query", Set.of());

        assertThat(result.answer()).isEqualTo("Hello world!");
        assertThat(result.ttftMs()).isGreaterThanOrEqualTo(0L);
        assertThat(result.tokenUsage().promptTokens()).isEqualTo(25);
        assertThat(result.tokenUsage().completionTokens()).isEqualTo(10);
        assertThat(result.tokenUsage().totalTokens()).isEqualTo(35);
        verify(chatModel).stream(any(Prompt.class));
    }

    @Test
    void query_withEmbeddingModel_capturesMeasuredEmbedTokens() {
        EmbeddingModel mockEmbeddingModel = mock(EmbeddingModel.class);
        Usage embedUsage = new DefaultUsage(7, 0, 7);
        EmbeddingResponseMetadata embedMeta = new EmbeddingResponseMetadata("text-embedding-004", embedUsage);
        EmbeddingResponse embedResp = new EmbeddingResponse(List.of(), embedMeta);
        when(mockEmbeddingModel.call(any(EmbeddingRequest.class))).thenReturn(embedResp);

        RagQueryService serviceWithEmbedding = new RagQueryService(
                vectorStore,
                chatModel,
                reranker,
                retrievalExpander,
                promptCache,
                promptRenderer,
                promptCacheProperties,
                null,
                null,
                null,
                "gemini-3.8-flash",
                meterRegistry,
                Integer.MAX_VALUE,
                5,
                RerankerProperties.defaultProperties(),
                new ClassPathResource("prompts/answer-generation.st"),
                "vector_store_test",
                null,
                mockEmbeddingModel);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("Test answer", new DefaultUsage(10, 5, 15)));

        RagQueryResult result = serviceWithEmbedding.query("How many tokens?", null);

        assertThat(result.tokenUsage().embedTokens()).isEqualTo(7);
        verify(mockEmbeddingModel).call(any(EmbeddingRequest.class));
    }
}
