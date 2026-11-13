package com.dippa.ragbenchmark.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.dippa.ragbenchmark.domain.RerankingException;
import com.dippa.ragbenchmark.infrastructure.config.RerankerProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class VertexAiRerankerTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private RerankerProperties properties;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        properties = new RerankerProperties(
                RerankerProperties.Provider.VERTEX,
                50,
                "semantic-ranker-default-004",
                new RerankerProperties.VertexProperties("test-project", "global", "default_ranking_config"));
    }

    @Test
    void rerank_success_reordersCandidatesAndAttachesScore() {
        String expectedUrl = "https://discoveryengine.googleapis.com/v1/projects/test-project/locations/global/rankingConfigs/default_ranking_config:rank";
        String responseJson = """
                {
                  "records": [
                    {"id": "doc2", "score": 0.98},
                    {"id": "doc1", "score": 0.45}
                  ]
                }
                """;

        mockServer.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-access-token"))
                .andExpect(header("X-Goog-User-Project", "test-project"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        VertexAiReranker reranker = new VertexAiReranker(properties, restClientBuilder.build(), () -> "test-access-token");

        Document doc1 = new Document("doc1", "First document content", Map.of("title", "Doc One"));
        Document doc2 = new Document("doc2", "Second document content", Map.of("title", "Doc Two"));
        Document doc3 = new Document("doc3", "Third document content", Map.of());

        List<Document> result = reranker.rerank("test query", List.of(doc1, doc2, doc3), 2);

        mockServer.verify();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("doc2");
        assertThat(result.get(0).getText()).isEqualTo("Second document content");
        assertThat(result.get(0).getMetadata().get("rerank_score")).isEqualTo(0.98);
        assertThat(result.get(1).getId()).isEqualTo("doc1");
        assertThat(result.get(1).getText()).isEqualTo("First document content");
        assertThat(result.get(1).getMetadata().get("rerank_score")).isEqualTo(0.45);
    }

    @Test
    void rerank_emptyCandidates_returnsEmptyListWithoutApiCall() {
        VertexAiReranker reranker = new VertexAiReranker(properties, restClientBuilder.build(), () -> "test-access-token");

        List<Document> result = reranker.rerank("query", List.of(), 5);

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    void rerank_missingProjectId_throwsRerankingException() {
        RerankerProperties invalidProps = new RerankerProperties(
                RerankerProperties.Provider.VERTEX,
                50,
                "semantic-ranker-default-004",
                new RerankerProperties.VertexProperties("", "global", "default_ranking_config"));

        VertexAiReranker reranker = new VertexAiReranker(invalidProps, restClientBuilder.build(), () -> "test-token");
        Document doc1 = new Document("doc1", "content", Map.of());

        assertThatThrownBy(() -> reranker.rerank("query", List.of(doc1), 1))
                .isInstanceOf(RerankingException.class)
                .hasMessageContaining("GCP project ID is required");
    }

    @Test
    void rerank_nullToken_throwsRerankingException() {
        VertexAiReranker reranker = new VertexAiReranker(properties, restClientBuilder.build(), () -> null);
        Document doc1 = new Document("doc1", "content", Map.of());

        assertThatThrownBy(() -> reranker.rerank("query", List.of(doc1), 1))
                .isInstanceOf(RerankingException.class)
                .hasMessageContaining("Failed to acquire OAuth2 access token");
    }

    @Test
    void rerank_apiServerError_throwsRerankingException() {
        String expectedUrl = "https://discoveryengine.googleapis.com/v1/projects/test-project/locations/global/rankingConfigs/default_ranking_config:rank";
        mockServer.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        VertexAiReranker reranker = new VertexAiReranker(properties, restClientBuilder.build(), () -> "test-access-token");
        Document doc1 = new Document("doc1", "content", Map.of());

        assertThatThrownBy(() -> reranker.rerank("query", List.of(doc1), 1))
                .isInstanceOf(RerankingException.class)
                .hasMessageContaining("Vertex AI Ranking API call failed");

        mockServer.verify();
    }
}
