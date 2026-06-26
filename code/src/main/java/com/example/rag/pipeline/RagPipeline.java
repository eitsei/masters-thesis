package com.example.rag.pipeline;

import com.example.rag.pipeline.steps.RagPipelineStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class RagPipeline {

    private static final Logger logger = LoggerFactory.getLogger(RagPipeline.class);

    private final List<RagPipelineStep> steps;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    public RagPipeline(List<RagPipelineStep> steps, io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        // Sort steps by getOrder()
        this.steps = steps.stream()
                .sorted(Comparator.comparingInt(RagPipelineStep::getOrder))
                .toList();
        this.meterRegistry = meterRegistry;
    }

    public RagResponse execute(RagRequest request) {
        logger.info("Starting execution of RAG pipeline for query: '{}'", request.query());
        RagContext context = new RagContext(request);

        for (RagPipelineStep step : steps) {
            if (step.shouldExecute(request.config())) {
                logger.info("Running step: {}", step.getClass().getSimpleName());
                step.execute(context);
            }
        }

        long totalLatency = System.currentTimeMillis() - context.getStartTime();
        
        List<DocumentInfo> docInfos = context.getRetrievedDocuments().stream()
                .map(doc -> new DocumentInfo(doc.getMetadata()))
                .toList();

        PipelineMetrics metrics = new PipelineMetrics(
                totalLatency,
                context.getTtftMs(),
                context.getRetrievalLatencyMs(),
                context.getRerankingLatencyMs(),
                context.getGenerationLatencyMs(),
                context.getInputTokens(),
                context.getOutputTokens(),
                context.getEstimatedCostUsd(),
                context.isCacheHit(),
                context.getModelUsed()
        );

        logger.info("Pipeline execution finished in {} ms. Answer length: {}", totalLatency, 
                context.getAnswer() != null ? context.getAnswer().length() : 0);

        // Record Micrometer metrics
        try {
            String model = context.getModelUsed() != null ? context.getModelUsed() : "unknown";
            String useRouting = String.valueOf(request.config().useRouting());
            String useSmallToBig = String.valueOf(request.config().useSmallToBig());
            String useReranking = String.valueOf(request.config().useReranking());
            String usePromptCaching = String.valueOf(request.config().usePromptCaching());

            io.micrometer.core.instrument.Timer.builder("rag.query.latency")
                    .tag("routing", useRouting)
                    .tag("small_to_big", useSmallToBig)
                    .tag("reranking", useReranking)
                    .tag("prompt_caching", usePromptCaching)
                    .tag("model", model)
                    .register(meterRegistry)
                    .record(totalLatency, java.util.concurrent.TimeUnit.MILLISECONDS);

            if (context.getTtftMs() > 0) {
                io.micrometer.core.instrument.Timer.builder("rag.query.ttft")
                        .tag("routing", useRouting)
                        .tag("small_to_big", useSmallToBig)
                        .tag("reranking", useReranking)
                        .tag("prompt_caching", usePromptCaching)
                        .tag("model", model)
                        .register(meterRegistry)
                        .record(context.getTtftMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
            }

            meterRegistry.counter("rag.query.tokens.input", "model", model).increment(context.getInputTokens());
            meterRegistry.counter("rag.query.tokens.output", "model", model).increment(context.getOutputTokens());
            meterRegistry.summary("rag.query.cost", "model", model).record(context.getEstimatedCostUsd());
        } catch (Exception e) {
            logger.error("Failed to record Micrometer metrics", e);
        }

        return new RagResponse(
                context.getAnswer(),
                docInfos,
                metrics
        );
    }
}
