package com.dippa.ragbenchmark.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dippa.ragbenchmark.api.TokenUsageDto;
import com.dippa.ragbenchmark.domain.ModuleId;
import com.dippa.ragbenchmark.domain.ModuleNotImplementedException;
import com.dippa.ragbenchmark.domain.Reranker;
import com.dippa.ragbenchmark.domain.RerankingConfigurationException;
import com.dippa.ragbenchmark.domain.RetrievalExpander;
import com.dippa.ragbenchmark.infrastructure.NoOpReranker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;

/**
 * Plain unit tests for {@link RagQueryService} — no Spring context, no database, no real model
 * calls. All Spring AI collaborators are Mockito mocks. A real {@link SimpleMeterRegistry} is
 * used in place of a mocked {@link MeterRegistry}: Micrometer's {@code MeterRegistry} is an
 * abstract class whose {@code Timer.Sample} machinery reaches into {@code config().clock()} and
 * {@code timer(String)} internals, so a real in-memory registry (no I/O, no Spring, negligible
 * overhead) is the standard and far less brittle way to exercise timer-instrumented code than
 * hand-stubbing those internals with Mockito.
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

    private MeterRegistry meterRegistry;

    private RagQueryService ragQueryService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        // No Spring context in these tests, so @Value can't inject the prompt resource -- load it
        // the same way Spring would resolve "classpath:prompts/answer-generation.st", via a plain
        // ClassPathResource (src/main/resources is on the test classpath by default in Maven).
        ragQueryService = new RagQueryService(vectorStore, chatModel, reranker, retrievalExpander, meterRegistry, Integer.MAX_VALUE, 5,
                new ClassPathResource("prompts/answer-generation.st"));
    }

    private ChatResponse createChatResponse(String text, Usage usage) {
        Generation generation = new Generation(new AssistantMessage(text));
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .usage(usage)
                .build();
        return new ChatResponse(List.of(generation), metadata);
    }

    @Test
    void m0HappyPath_retrievesRerankAndGeneratesAnswerWithUsage() {
        Document chunkOne = Document.builder().id("chunk-1").text("Paris is the capital of France.").build();
        Document chunkTwo = Document.builder().id("chunk-2").text("It is known for the Eiffel Tower.").build();
        List<Document> retrieved = List.of(chunkOne, chunkTwo);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(retrieved);
        when(reranker.rerank("What is the capital of France?", retrieved)).thenReturn(retrieved);
        Usage usage = new DefaultUsage(120, 30, 150, null, 25L, 0L);
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("Paris.", usage));

        RagQueryResult result = ragQueryService.query("What is the capital of France?", Set.of());

        assertThat(result.answer()).isEqualTo("Paris.");
        assertThat(result.modulesApplied()).isEmpty();
        assertThat(result.retrievedChunks()).hasSize(2);
        assertThat(result.retrievedChunks().get(0).id()).isEqualTo("chunk-1");
        assertThat(result.retrievedChunks().get(0).snippet()).isEqualTo("Paris is the capital of France.");
        assertThat(result.retrievedChunks().get(0).text()).isEqualTo("Paris is the capital of France.");
        assertThat(result.retrievalMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.generationMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.totalLatencyMs()).isEqualTo(result.retrievalMs() + result.generationMs());
        assertThat(result.tokenUsage()).isEqualTo(new TokenUsageDto(120, 30, 150, 25));

        verify(vectorStore).similaritySearch(any(SearchRequest.class));
        verify(reranker).rerank("What is the capital of France?", retrieved);
        verify(chatModel).call(any(Prompt.class));

        assertThat(meterRegistry.find("rag.pipeline.retrieval").timer()).isNotNull();
        assertThat(meterRegistry.find("rag.pipeline.generation").timer()).isNotNull();
    }

    @Test
    void nullMetadataOrUsage_safelyDefaultsTokenUsageToZero() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(reranker.rerank(anyString(), any())).thenReturn(List.of());
        ChatResponse responseWithoutUsage = new ChatResponse(List.of(new Generation(new AssistantMessage("answer"))));
        when(chatModel.call(any(Prompt.class))).thenReturn(responseWithoutUsage);

        RagQueryResult result = ragQueryService.query("query", null);

        assertThat(result.answer()).isEqualTo("answer");
        assertThat(result.tokenUsage()).isEqualTo(TokenUsageDto.ZERO);
    }

    @Test
    void partialUsageWithNullValues_safelyDefaultsNullsToZero() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(reranker.rerank(anyString(), any())).thenReturn(List.of());
        Usage mockUsage = org.mockito.Mockito.mock(Usage.class);
        when(mockUsage.getPromptTokens()).thenReturn(null);
        when(mockUsage.getCompletionTokens()).thenReturn(null);
        when(mockUsage.getTotalTokens()).thenReturn(null);
        when(mockUsage.getCacheReadInputTokens()).thenReturn(null);
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("partial", mockUsage));

        RagQueryResult result = ragQueryService.query("query", Set.of());

        assertThat(result.tokenUsage().promptTokens()).isZero();
        assertThat(result.tokenUsage().completionTokens()).isZero();
        assertThat(result.tokenUsage().totalTokens()).isZero();
        assertThat(result.tokenUsage().cachedTokens()).isZero();
    }

    @Test
    void topKOverride_isUsedInSearchRequest() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(reranker.rerank(anyString(), any())).thenReturn(List.of());
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("ans", null));

        ragQueryService.query("query", Set.of(), null, 15);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getTopK()).isEqualTo(15);
    }

    @Test
    void dynamicTable_withNonPgVectorStore_fallsBackGracefullyToDefaultStore() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(reranker.rerank(anyString(), any())).thenReturn(List.of());
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("ans", null));

        RagQueryResult result = ragQueryService.query("query", Set.of(), "other_table", 5);

        assertThat(result.answer()).isEqualTo("ans");
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void dynamicTable_withInvalidCharacters_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> ragQueryService.query("query", Set.of(), "table; drop table students;", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid table name");
    }

    @Test
    void nullModules_isTreatedAsEmptySet_andRunsBaselinePipeline() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(reranker.rerank(anyString(), any())).thenReturn(List.of());
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("answer", null));

        RagQueryResult result = ragQueryService.query("query", null);

        assertThat(result.modulesApplied()).isEmpty();
        assertThat(result.retrievedChunks()).isEmpty();
    }

    @Test
    void requestedModules_failsFastBeforeTouchingAnyDependency() {
        assertThatThrownBy(() -> ragQueryService.query("query", Set.of(ModuleId.M3)))
                .isInstanceOf(ModuleNotImplementedException.class)
                .satisfies(exception -> {
                    ModuleNotImplementedException moduleException = (ModuleNotImplementedException) exception;
                    assertThat(moduleException.getModules()).containsExactly(ModuleId.M3);
                });

        verifyNoInteractions(vectorStore, chatModel, reranker);
    }

    @Test
    void multipleRequestedModules_areAllCarriedByTheException() {
        assertThatThrownBy(() -> ragQueryService.query("query", Set.of(ModuleId.M3, ModuleId.M4)))
                .isInstanceOf(ModuleNotImplementedException.class)
                .satisfies(exception -> {
                    ModuleNotImplementedException moduleException = (ModuleNotImplementedException) exception;
                    assertThat(moduleException.getModules()).containsExactlyInAnyOrder(ModuleId.M3, ModuleId.M4);
                });

        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void m2HappyPath_retrievesCandidatePoolAndReranksTopK() {
        Document candidate1 = Document.builder().id("c1").text("Text 1").build();
        Document candidate2 = Document.builder().id("c2").text("Text 2").build();
        List<Document> retrievedPool = List.of(candidate1, candidate2);
        Document reranked1 = Document.builder().id("c2").text("Text 2").metadata("rerank_score", 0.99).build();
        List<Document> rerankedDocs = List.of(reranked1);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(retrievedPool);
        when(reranker.rerank("test query", retrievedPool, 5)).thenReturn(rerankedDocs);
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("Reranked answer", null));

        RagQueryResult result = ragQueryService.query("test query", Set.of(ModuleId.M2));

        assertThat(result.answer()).isEqualTo("Reranked answer");
        assertThat(result.modulesApplied()).containsExactly(ModuleId.M2);
        assertThat(result.retrievedChunks()).hasSize(1);
        assertThat(result.retrievedChunks().get(0).id()).isEqualTo("c2");
        assertThat(result.rerankMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.totalLatencyMs()).isEqualTo(result.retrievalMs() + result.rerankMs() + result.generationMs());

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getTopK()).isEqualTo(50); // candidatePoolSize default 50
        verify(reranker).rerank("test query", retrievedPool, 5);
        assertThat(meterRegistry.find("rag.pipeline.rerank").timer()).isNotNull();
    }

    @Test
    void m1PlusM2Combined_retrievesCandidatesReranksThenExpandsToParents() {
        Document child1 = Document.builder().id("child1").text("child text 1").build();
        Document child2 = Document.builder().id("child2").text("child text 2").build();
        List<Document> childPool = List.of(child1, child2);

        Document winningChild = Document.builder().id("child2").text("child text 2").metadata("rerank_score", 0.95).build();
        List<Document> rerankedChildren = List.of(winningChild);

        Document parentDoc = Document.builder().id("parent2").text("full parent text 2").build();
        List<Document> expandedParents = List.of(parentDoc);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(childPool);
        when(reranker.rerank("combined query", childPool, 5)).thenReturn(rerankedChildren);
        when(retrievalExpander.expand(rerankedChildren, "vector_store_test_large")).thenReturn(expandedParents);
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("Combined answer", null));

        RagQueryResult result = ragQueryService.query("combined query", Set.of(ModuleId.M1, ModuleId.M2));

        assertThat(result.answer()).isEqualTo("Combined answer");
        assertThat(result.modulesApplied()).containsExactlyInAnyOrder(ModuleId.M1, ModuleId.M2);
        assertThat(result.retrievedChunks()).hasSize(1);
        assertThat(result.retrievedChunks().get(0).id()).isEqualTo("parent2");
        assertThat(result.rerankMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.expansionMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.totalLatencyMs()).isEqualTo(result.retrievalMs() + result.rerankMs() + result.expansionMs() + result.generationMs());

        verify(reranker).rerank("combined query", childPool, 5);
        verify(retrievalExpander).expand(rerankedChildren, "vector_store_test_large");
    }

    @Test
    void m2Requested_whenRerankerIsNoOp_failsFastWithConfigurationException() {
        RagQueryService serviceWithNoOp = new RagQueryService(
                vectorStore, chatModel, new NoOpReranker(), retrievalExpander, meterRegistry,
                Integer.MAX_VALUE, 5, new ClassPathResource("prompts/answer-generation.st"));

        assertThatThrownBy(() -> serviceWithNoOp.query("query", Set.of(ModuleId.M2)))
                .isInstanceOf(RerankingConfigurationException.class)
                .hasMessageContaining("rag.reranker.provider");

        verifyNoInteractions(vectorStore, chatModel);
    }

    @Test
    void m1HappyPath_retrievesChildChunksAndExpandsToParentDocuments() {
        Document childOne = Document.builder().id("child-1").text("Paris is the capital.").build();
        Document parentDoc = Document.builder().id("parent-1").text("Paris is the capital of France and has the Eiffel Tower.").build();
        List<Document> retrievedChildren = List.of(childOne);
        List<Document> expandedParents = List.of(parentDoc);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(retrievedChildren);
        when(reranker.rerank("Where is the Eiffel Tower?", retrievedChildren)).thenReturn(retrievedChildren);
        when(retrievalExpander.expand(any(), any())).thenReturn(expandedParents);
        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse("In Paris.", null));

        RagQueryResult result = ragQueryService.query("Where is the Eiffel Tower?", Set.of(ModuleId.M1));

        assertThat(result.answer()).isEqualTo("In Paris.");
        assertThat(result.modulesApplied()).containsExactly(ModuleId.M1);
        assertThat(result.retrievedChunks()).hasSize(1);
        assertThat(result.retrievedChunks().get(0).id()).isEqualTo("parent-1");
        assertThat(result.retrievedChunks().get(0).text()).isEqualTo("Paris is the capital of France and has the Eiffel Tower.");
        assertThat(result.expansionMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.totalLatencyMs()).isEqualTo(result.retrievalMs() + result.generationMs() + result.expansionMs());

        verify(retrievalExpander).expand(any(), any());
        assertThat(meterRegistry.find("rag.pipeline.expansion").timer()).isNotNull();
    }
}
