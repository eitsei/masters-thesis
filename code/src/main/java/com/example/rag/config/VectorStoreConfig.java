package com.example.rag.config;

import io.qdrant.client.QdrantClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class VectorStoreConfig {

    @Value("${rag.collections.simple:banking-simple}")
    private String simpleCollection;

    @Value("${rag.collections.small-to-big:banking-small-to-big}")
    private String smallToBigCollection;

    @Bean(name = "simpleVectorStore")
    @Primary
    public QdrantVectorStore simpleVectorStore(QdrantClient qdrantClient, EmbeddingModel embeddingModel) {
        return new QdrantVectorStore(qdrantClient, simpleCollection, embeddingModel, true);
    }

    @Bean(name = "smallToBigVectorStore")
    public QdrantVectorStore smallToBigVectorStore(QdrantClient qdrantClient, EmbeddingModel embeddingModel) {
        return new QdrantVectorStore(qdrantClient, smallToBigCollection, embeddingModel, true);
    }
}
