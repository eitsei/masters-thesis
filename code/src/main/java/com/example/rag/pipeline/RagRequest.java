package com.example.rag.pipeline;

public record RagRequest(
    String query,
    PipelineConfig config
) {}
