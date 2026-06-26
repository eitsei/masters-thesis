package com.example.rag.pipeline;

import org.springframework.ai.document.Document;
import java.util.ArrayList;
import java.util.List;

public class RagContext {
    private final RagRequest request;
    private String processedQuery;
    private List<Document> retrievedDocuments = new ArrayList<>();
    private List<Document> rerankedDocuments = new ArrayList<>();
    private String systemPrompt;
    private String answer;

    // Metrics
    private final long startTime;
    private long ttftMs;
    private long retrievalLatencyMs;
    private long rerankingLatencyMs;
    private long generationLatencyMs;
    private int inputTokens;
    private int outputTokens;
    private double estimatedCostUsd;
    private boolean cacheHit;
    private String modelUsed;

    public RagContext(RagRequest request) {
        this.request = request;
        this.processedQuery = request.query();
        this.startTime = System.currentTimeMillis();
    }

    public RagRequest getRequest() { return request; }
    public String getProcessedQuery() { return processedQuery; }
    public void setProcessedQuery(String processedQuery) { this.processedQuery = processedQuery; }
    public List<Document> getRetrievedDocuments() { return retrievedDocuments; }
    public void setRetrievedDocuments(List<Document> retrievedDocuments) { this.retrievedDocuments = retrievedDocuments; }
    public List<Document> getRerankedDocuments() { return rerankedDocuments; }
    public void setRerankedDocuments(List<Document> rerankedDocuments) { this.rerankedDocuments = rerankedDocuments; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public long getStartTime() { return startTime; }
    public long getTtftMs() { return ttftMs; }
    public void setTtftMs(long ttftMs) { this.ttftMs = ttftMs; }
    public long getRetrievalLatencyMs() { return retrievalLatencyMs; }
    public void setRetrievalLatencyMs(long retrievalLatencyMs) { this.retrievalLatencyMs = retrievalLatencyMs; }
    public long getRerankingLatencyMs() { return rerankingLatencyMs; }
    public void setRerankingLatencyMs(long rerankingLatencyMs) { this.rerankingLatencyMs = rerankingLatencyMs; }
    public long getGenerationLatencyMs() { return generationLatencyMs; }
    public void setGenerationLatencyMs(long generationLatencyMs) { this.generationLatencyMs = generationLatencyMs; }
    public int getInputTokens() { return inputTokens; }
    public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }
    public double getEstimatedCostUsd() { return estimatedCostUsd; }
    public void setEstimatedCostUsd(double estimatedCostUsd) { this.estimatedCostUsd = estimatedCostUsd; }
    public boolean isCacheHit() { return cacheHit; }
    public void setCacheHit(boolean cacheHit) { this.cacheHit = cacheHit; }
    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }
}
