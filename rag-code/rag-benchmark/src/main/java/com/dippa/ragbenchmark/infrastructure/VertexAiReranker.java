package com.dippa.ragbenchmark.infrastructure;

import com.dippa.ragbenchmark.domain.Reranker;
import com.dippa.ragbenchmark.domain.RerankingException;
import com.dippa.ragbenchmark.infrastructure.config.RerankerProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Reranker implementation backed by Google Cloud Vertex AI / Discovery Engine Ranking API.
 * Uses {@code semantic-ranker-default-004} cross-encoder model to re-score candidate passages.
 */
@Component
@ConditionalOnProperty(name = "rag.reranker.provider", havingValue = "vertex")
public class VertexAiReranker implements Reranker {

    private static final Logger logger = LoggerFactory.getLogger(VertexAiReranker.class);
    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
    private static final String DISCOVERY_ENGINE_ENDPOINT = "https://discoveryengine.googleapis.com/v1/projects/%s/locations/%s/rankingConfigs/%s:rank";

    private final RerankerProperties properties;
    private final RestClient restClient;
    private final Supplier<String> tokenSupplier;

    public VertexAiReranker(RerankerProperties properties, RestClient.Builder restClientBuilder) {
        this(properties, restClientBuilder.build(), createDefaultTokenSupplier());
    }

    public VertexAiReranker(RerankerProperties properties, RestClient restClient, Supplier<String> tokenSupplier) {
        this.properties = properties;
        this.restClient = restClient;
        this.tokenSupplier = tokenSupplier != null ? tokenSupplier : createDefaultTokenSupplier();
    }

    @Override
    public List<Document> rerank(String query, List<Document> candidates) {
        return rerank(query, candidates, candidates != null ? candidates.size() : 0);
    }

    @Override
    public List<Document> rerank(String query, List<Document> candidates, int topN) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        int effectiveTopN = (topN > 0) ? topN : candidates.size();

        String projectId = properties.vertex().projectId();
        if (projectId == null || projectId.isBlank()) {
            throw new RerankingException("GCP project ID is required for Vertex AI Ranking API. Set GOOGLE_CLOUD_PROJECT_ID in environment or rag.reranker.vertex.project-id in properties.");
        }

        String location = properties.vertex().location();
        String rankingConfig = properties.vertex().rankingConfig();
        String url = String.format(DISCOVERY_ENGINE_ENDPOINT, projectId, location, rankingConfig);
        String modelName = properties.model();

        Map<String, Document> candidateMap = new HashMap<>();
        List<RankingRecord> records = new ArrayList<>(candidates.size());

        for (Document candidate : candidates) {
            String id = candidate.getId();
            candidateMap.put(id, candidate);
            String title = extractTitle(candidate);
            String content = candidate.getText() != null ? candidate.getText() : "";
            records.add(new RankingRecord(id, title, content));
        }

        RankRequest rankRequest = new RankRequest(modelName, query, records, effectiveTopN, true);

        String token = tokenSupplier.get();
        if (token == null || token.isBlank()) {
            throw new RerankingException("Failed to acquire OAuth2 access token for Google Cloud Vertex AI Ranking API.");
        }

        logger.debug("Sending rank request to {} for {} candidates with topN={}", url, candidates.size(), effectiveTopN);

        RankResponse rankResponse;
        try {
            rankResponse = restClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("X-Goog-User-Project", projectId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(rankRequest)
                    .retrieve()
                    .body(RankResponse.class);
        } catch (Exception ex) {
            logger.error("Error invoking Vertex AI Ranking API: {}", ex.getMessage(), ex);
            throw new RerankingException("Vertex AI Ranking API call failed: " + ex.getMessage(), ex);
        }

        if (rankResponse == null || rankResponse.records() == null) {
            logger.warn("Empty response received from Vertex AI Ranking API");
            return List.of();
        }

        List<Document> rerankedDocuments = new ArrayList<>();
        for (RankedRecordResult ranked : rankResponse.records()) {
            Document original = candidateMap.get(ranked.id());
            if (original != null) {
                Map<String, Object> metadata = new HashMap<>(original.getMetadata());
                if (ranked.score() != null) {
                    metadata.put("rerank_score", ranked.score());
                }
                rerankedDocuments.add(new Document(original.getId(), original.getText(), metadata));
            } else {
                logger.warn("Ranked record ID {} was not found in original candidate set", ranked.id());
            }
        }

        logger.info("Vertex AI Ranking API reordered {} candidates to {} results", candidates.size(), rerankedDocuments.size());
        return rerankedDocuments;
    }

    private String extractTitle(Document document) {
        if (document == null || document.getMetadata() == null) {
            return null;
        }
        Object title = document.getMetadata().get("title");
        return title != null ? title.toString() : null;
    }

    private static Supplier<String> createDefaultTokenSupplier() {
        return () -> {
            try {
                GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                        .createScoped(Collections.singletonList(CLOUD_PLATFORM_SCOPE));
                credentials.refreshIfExpired();
                AccessToken accessToken = credentials.getAccessToken();
                return accessToken != null ? accessToken.getTokenValue() : null;
            } catch (IOException e) {
                throw new RerankingException("Failed to obtain Application Default Credentials for Vertex AI Ranking API: " + e.getMessage(), e);
            }
        };
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RankRequest(
            String model,
            String query,
            List<RankingRecord> records,
            @JsonProperty("topN") Integer topN,
            @JsonProperty("ignoreRecordDetailsInResponse") Boolean ignoreRecordDetailsInResponse
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RankingRecord(
            String id,
            String title,
            String content
    ) {}

    public record RankResponse(
            List<RankedRecordResult> records
    ) {}

    public record RankedRecordResult(
            String id,
            Double score
    ) {}
}
