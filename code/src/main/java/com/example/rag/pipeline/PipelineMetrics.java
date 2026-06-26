package com.example.rag.pipeline;

public record PipelineMetrics(
    long totalLatencyMs,
    long ttftMs,
    long retrievalLatencyMs,
    long rerankingLatencyMs,
    long generationLatencyMs,
    int inputTokens,
    int outputTokens,
    double estimatedCostUsd,
    boolean cacheHit,
    String modelUsed
) {}
