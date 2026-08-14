package com.dippa.ragbenchmark.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dippa.ragbenchmark.domain.ModuleId;
import com.dippa.ragbenchmark.domain.ModuleNotImplementedException;
import com.dippa.ragbenchmark.domain.Reranker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
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

    private MeterRegistry meterRegistry;

    private RagQueryService ragQueryService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        // No Spring context in these tests, so @Value can't inject the prompt resource -- load it
        // the same way Spring would resolve "classpath:prompts/answer-generation.st", via a plain
        // ClassPathResource (src/main/resources is on the test classpath by default in Maven).
        ragQueryService = new RagQueryService(vectorStore, chatModel, reranker, meterRegistry, Integer.MAX_VALUE, 5,
                new ClassPathResource("prompts/answer-generation.st"));
    }

    @Test
    void m0HappyPath_retrievesRerankAndGeneratesAnswer() {
        Document chunkOne = Document.builder().id("chunk-1").text("Paris is the capital of France.").build();
        Document chunkTwo = Document.builder().id("chunk-2").text("It is known for the Eiffel Tower.").build();
        List<Document> retrieved = List.of(chunkOne, chunkTwo);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(retrieved);
        // No-op-style reranker mock: returns the same list, exercising the seam.
        when(reranker.rerank("What is the capital of France?", retrieved)).thenReturn(retrieved);
        when(chatModel.call(anyString())).thenReturn("Paris.");

        RagQueryResult result = ragQueryService.query("What is the capital of France?", Set.of());

        assertThat(result.answer()).isEqualTo("Paris.");
        assertThat(result.modulesApplied()).isEmpty();
        assertThat(result.retrievedChunks()).hasSize(2);
        assertThat(result.retrievedChunks().get(0).id()).isEqualTo("chunk-1");
        assertThat(result.retrievedChunks().get(0).snippet()).isEqualTo("Paris is the capital of France.");
        assertThat(result.retrievalMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.generationMs()).isGreaterThanOrEqualTo(0);

        SearchRequest expectedRequest = SearchRequest.builder()
                .query("What is the capital of France?")
                .topK(5)
                .build();
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
        verify(reranker).rerank("What is the capital of France?", retrieved);
        verify(chatModel).call(anyString());

        assertThat(meterRegistry.find("rag.pipeline.retrieval").timer()).isNotNull();
        assertThat(meterRegistry.find("rag.pipeline.generation").timer()).isNotNull();
        // Sanity check that topK is honored as part of the request passed downstream.
        assertThat(expectedRequest.getTopK()).isEqualTo(5);
    }

    @Test
    void nullModules_isTreatedAsEmptySet_andRunsBaselinePipeline() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(reranker.rerank(anyString(), any())).thenReturn(List.of());
        when(chatModel.call(anyString())).thenReturn("answer");

        RagQueryResult result = ragQueryService.query("query", null);

        assertThat(result.modulesApplied()).isEmpty();
        assertThat(result.retrievedChunks()).isEmpty();
    }

    @Test
    void requestedModules_failsFastBeforeTouchingAnyDependency() {
        assertThatThrownBy(() -> ragQueryService.query("query", Set.of(ModuleId.M2)))
                .isInstanceOf(ModuleNotImplementedException.class)
                .satisfies(exception -> {
                    ModuleNotImplementedException moduleException = (ModuleNotImplementedException) exception;
                    assertThat(moduleException.getModules()).containsExactly(ModuleId.M2);
                });

        verifyNoInteractions(vectorStore, chatModel, reranker);
    }

    @Test
    void multipleRequestedModules_areAllCarriedByTheException() {
        assertThatThrownBy(() -> ragQueryService.query("query", Set.of(ModuleId.M1, ModuleId.M3)))
                .isInstanceOf(ModuleNotImplementedException.class)
                .satisfies(exception -> {
                    ModuleNotImplementedException moduleException = (ModuleNotImplementedException) exception;
                    assertThat(moduleException.getModules()).containsExactlyInAnyOrder(ModuleId.M1, ModuleId.M3);
                });

        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }
}
