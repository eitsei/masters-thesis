package com.example.rag.pipeline.steps;

import com.example.rag.pipeline.PipelineConfig;
import com.example.rag.pipeline.RagContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class ResponseGenerationStep implements RagPipelineStep {

    private static final Logger logger = LoggerFactory.getLogger(ResponseGenerationStep.class);

    private final ChatClient.Builder chatClientBuilder;
    private final com.example.rag.config.ModelPricingConfig modelPricingConfig;
    private ChatClient chatClient;

    public ResponseGenerationStep(
            ChatClient.Builder chatClientBuilder, 
            com.example.rag.config.ModelPricingConfig modelPricingConfig) {
        this.chatClientBuilder = chatClientBuilder;
        this.modelPricingConfig = modelPricingConfig;
    }

    private synchronized ChatClient getChatClient() {
        if (this.chatClient == null) {
            this.chatClient = this.chatClientBuilder.build();
        }
        return this.chatClient;
    }

    @Override
    public void execute(RagContext context) {
        logger.info("Executing Generation Step...");
        long start = System.currentTimeMillis();

        // 1. Augment context
        String docsContext = context.getRerankedDocuments().stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n"));

        String systemPrompt = """
                You are a helpful banking assistant. Answer the user's question based ONLY on the following context.
                Don't say according to my knowledge or something like that. Try to be very concise and to the point. If you don't know the answer, say "I don't know". You know you are cabable model so you can answer questions about banking, finance, and related topics. Use the context below to answer the question. If the context does not contain the answer, say "I don't know". Do not make up answers. Do not provide information that is not in the context. Do not provide information that is not relevant to the question. Do not provide information that is not accurate. Do not provide information that is not up-to-date. Do not provide information that is not complete. Do not provide information that is not clear. Do not provide information that is not concise. Do not provide information that is not to the point. Do not provide information that is not helpful. Do not provide information that is not useful. Do not provide information that is not relevant to the user's question. Do not provide information that is not relevant to the user's needs. Do not provide information that is not relevant to the user's situation. Do not provide information that is not relevant to the user's context. Do not provide information that is not relevant to the user's goals. Do not provide information that is not relevant to the user's objectives. Do not provide information that is not relevant to the user's interests. Do not provide information that is not relevant to the user's preferences. Do not provide information that is not relevant to the user's values. Do not provide information that is not relevant to the user's beliefs. Do not provide information that is not relevant to the user's opinions. Do not provide information that is not relevant to the user's attitudes. Do not provide information that is not relevant to the user's behaviors.
                \n\n
                """ + docsContext;

        context.setSystemPrompt(systemPrompt);

        // 2. Call chat client with streaming
        StringBuilder answerBuilder = new StringBuilder();
        AtomicLong firstTokenTime = new AtomicLong(0L);
        AtomicBoolean isFirstToken = new AtomicBoolean(true);
        
        logger.info("Streaming response from ChatClient using model: {}", context.getModelUsed());
        
        // We block on the stream collection, but record the arrival of the first token
        getChatClient().prompt()
                .system(systemPrompt)
                .user(context.getProcessedQuery())
                .stream()
                .chatResponse()
                .doOnNext(chatResponse -> {
                    if (isFirstToken.compareAndSet(true, false)) {
                        firstTokenTime.set(System.currentTimeMillis() - start);
                        logger.info("First token received in {} ms", firstTokenTime.get());
                    }
                    if (chatResponse.getResults() != null && !chatResponse.getResults().isEmpty()) {
                        String content = chatResponse.getResults().get(0).getOutput().getContent();
                        if (content != null) {
                            answerBuilder.append(content);
                        }
                    }
                    // Extract token usage if available in chunk metadata
                    ChatResponseMetadata metadata = chatResponse.getMetadata();
                    if (metadata != null && metadata.getUsage() != null) {
                        Usage usage = metadata.getUsage();
                        if (usage.getPromptTokens() != null && usage.getPromptTokens() > 0) {
                            context.setInputTokens(usage.getPromptTokens().intValue());
                        }
                        if (usage.getGenerationTokens() != null && usage.getGenerationTokens() > 0) {
                            context.setOutputTokens(usage.getGenerationTokens().intValue());
                        }
                    }
                })
                .blockLast(); // Collect all stream events synchronously

        // 3. Calculate cost
        double inputCostRate = 0.0;
        double outputCostRate = 0.0;
        
        String modelName = context.getModelUsed();
        com.example.rag.config.ModelPricingConfig.ModelPrice price = modelPricingConfig.getModels().get(modelName);
        if (price == null && modelName != null) {
            String normalizedKey = modelName.replaceAll("[^a-zA-Z0-9]", "");
            price = modelPricingConfig.getModels().get(normalizedKey);
        }

        if (price != null) {
            inputCostRate = price.getInputCostPerMillion();
            outputCostRate = price.getOutputCostPerMillion();
        } else {
            logger.warn("No pricing model configuration found for: {}", modelName);
        }

        double cost = (context.getInputTokens() * inputCostRate / 1_000_000.0) 
                + (context.getOutputTokens() * outputCostRate / 1_000_000.0);
        
        // Round to 8 decimal places to prevent double precision errors
        cost = Math.round(cost * 100_000_000.0) / 100_000_000.0;
                
        context.setEstimatedCostUsd(cost);
        context.setAnswer(answerBuilder.toString());
        context.setTtftMs(firstTokenTime.get());
        context.setGenerationLatencyMs(System.currentTimeMillis() - start);
        logger.info("Generation completed. Answer length: {} chars, cost: ${}", answerBuilder.length(), cost);
    }

    @Override
    public boolean shouldExecute(PipelineConfig config) {
        return true; // Generation is always needed in RAG
    }

    @Override
    public int getOrder() {
        return 5; // Run after Router, Retriever, Reranker
    }
}
