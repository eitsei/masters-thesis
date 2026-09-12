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
        SpringApplication.run(RagBenchmarkApplication.class, args);
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
