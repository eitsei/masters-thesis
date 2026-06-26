package com.example.rag.pipeline.steps;

import com.example.rag.pipeline.PipelineConfig;
import com.example.rag.pipeline.RagContext;

public interface RagPipelineStep {
    void execute(RagContext context);
    boolean shouldExecute(PipelineConfig config);
    int getOrder();
}
