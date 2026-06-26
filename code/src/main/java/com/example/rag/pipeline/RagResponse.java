package com.example.rag.pipeline;

import java.util.List;

public record RagResponse(
    String answer,
    List<DocumentInfo> retrievedDocuments,
    PipelineMetrics metrics
) {}
