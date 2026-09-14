package com.dippa.ragbenchmark;

import com.dippa.ragbenchmark.infrastructure.config.GenerationProperties;
import com.dippa.ragbenchmark.infrastructure.config.ModelCatalogProperties;
import com.dippa.ragbenchmark.infrastructure.config.PromptCacheProperties;
import com.dippa.ragbenchmark.infrastructure.config.RerankerProperties;
import com.dippa.ragbenchmark.infrastructure.dataset.MusiqueDatasetProperties;
import com.dippa.ragbenchmark.ingest.nordea.NordeaIngestionProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties({
        ModelCatalogProperties.class,
        RerankerProperties.class,
        PromptCacheProperties.class,
        GenerationProperties.class,
        NordeaIngestionProperties.class,
        MusiqueDatasetProperties.class
})
public class RagBenchmarkApplication {

    public static void main(String[] args) {
        loadDotEnvIfPresent();
        SpringApplication.run(RagBenchmarkApplication.class, args);
    }

    static void loadDotEnvIfPresent() {
        java.nio.file.Path[] candidates = new java.nio.file.Path[] {
                java.nio.file.Path.of(".env"),
                java.nio.file.Path.of("rag-code", "rag-benchmark", ".env"),
                java.nio.file.Path.of("..", ".env")
        };
        for (java.nio.file.Path path : candidates) {
            if (java.nio.file.Files.isRegularFile(path)) {
                try {
                    int count = 0;
                    for (String line : java.nio.file.Files.readAllLines(path)) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                            continue;
                        }
                        int idx = trimmed.indexOf('=');
                        if (idx > 0) {
                            String key = trimmed.substring(0, idx).trim();
                            String val = trimmed.substring(idx + 1).trim();
                            if ((val.startsWith("\"") && val.endsWith("\""))
                                    || (val.startsWith("'") && val.endsWith("'"))) {
                                val = val.substring(1, val.length() - 1);
                            }
                            if (!val.isEmpty() && System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, val);
                                count++;
                            }
                        }
                    }
                    if (count > 0) {
                        System.out.println("[RagBenchmarkApplication] Auto-loaded " + count + " environment properties from " + path.toAbsolutePath());
                    }
                    break;
                } catch (Exception e) {
                    System.err.println("[RagBenchmarkApplication] Warning: failed to load .env from " + path + ": " + e.getMessage());
                }
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public org.springframework.boot.ApplicationRunner logActiveConfiguration(org.springframework.core.env.Environment env) {
        return args -> {
            String chatProvider = env.getProperty("spring.ai.model.chat", "ollama");
            String chatModel = "google-genai".equalsIgnoreCase(chatProvider)
                    ? env.getProperty("spring.ai.google.genai.chat.model", "gemini-3.8-flash")
                    : env.getProperty("spring.ai.ollama.chat.model", "gemma4:e2b");
            String embProvider = env.getProperty("spring.ai.model.embedding", "ollama");
            String rerankerProvider = env.getProperty("rag.reranker.provider", "noop");
            String promptCacheProvider = env.getProperty("rag.prompt-cache.provider", "noop");
            String gcpProject = env.getProperty("GOOGLE_CLOUD_PROJECT_ID", env.getProperty("spring.ai.google.genai.project-id", "dippatyo"));
            String gcpLocation = env.getProperty("GOOGLE_CLOUD_LOCATION", env.getProperty("spring.ai.google.genai.location", "global"));

            System.out.println("\n=======================================================");
            System.out.println("  RAG BENCHMARK ACTIVE CONFIGURATION");
            System.out.println("  Chat Provider:         " + chatProvider + " (" + chatModel + ")");
            System.out.println("  Embedding Provider:    " + embProvider);
            System.out.println("  Reranker Provider:     " + rerankerProvider);
            System.out.println("  Prompt Cache Provider: " + promptCacheProvider);
            System.out.println("  GCP Project / Loc:     " + gcpProject + " / " + gcpLocation);
            System.out.println("=======================================================\n");
        };
    }
}
