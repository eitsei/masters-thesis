package com.dippa.ragbenchmark;

import com.dippa.ragbenchmark.infrastructure.config.ModelCatalogProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ModelCatalogProperties.class)
public class RagBenchmarkApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagBenchmarkApplication.class, args);
    }
}
