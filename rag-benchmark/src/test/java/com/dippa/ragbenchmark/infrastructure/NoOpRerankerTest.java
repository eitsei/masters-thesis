package com.dippa.ragbenchmark.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class NoOpRerankerTest {

    @Test
    void rerank_returnsCandidatesUnchangedAndInOriginalOrder() {
        NoOpReranker reranker = new NoOpReranker();
        Document first = Document.builder().id("1").text("a").build();
        Document second = Document.builder().id("2").text("b").build();
        List<Document> candidates = List.of(first, second);

        List<Document> result = reranker.rerank("query", candidates);

        assertThat(result).isSameAs(candidates);
        assertThat(result).containsExactly(first, second);
    }

    @Test
    void rerank_withEmptyList_returnsEmptyList() {
        NoOpReranker reranker = new NoOpReranker();

        List<Document> result = reranker.rerank("query", List.of());

        assertThat(result).isEmpty();
    }
}
