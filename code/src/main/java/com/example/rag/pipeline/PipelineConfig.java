package com.example.rag.pipeline;

public record PipelineConfig(
    boolean useRouting,
    boolean useSmallToBig,
    boolean useReranking,
    boolean usePromptCaching
) {}
