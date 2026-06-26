package com.example.rag.pipeline.steps;

import com.example.rag.pipeline.PipelineConfig;
import com.example.rag.pipeline.RagContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class QueryRouterStep implements RagPipelineStep {

    @Value("${spring.ai.ollama.chat.options.model:gemma4:e2b}")
    private String defaultModel;

    @Override
    public void execute(RagContext context) {
        // Baseline: no routing, just select the default model
        context.setModelUsed(defaultModel);
    }

    @Override
    public boolean shouldExecute(PipelineConfig config) {
        return true; // Always run routing to establish model selection
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
