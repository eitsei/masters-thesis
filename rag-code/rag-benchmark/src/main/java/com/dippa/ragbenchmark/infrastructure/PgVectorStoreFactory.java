package com.dippa.ragbenchmark.infrastructure;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Factory and cache for dynamic {@link PgVectorStore} instances across the 6 benchmark tables.
 */
@Component
public class PgVectorStoreFactory {

    private static final Logger logger = LoggerFactory.getLogger(PgVectorStoreFactory.class);
    private final VectorStore defaultVectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final ConcurrentMap<String, VectorStore> stores = new ConcurrentHashMap<>();

    public PgVectorStoreFactory(
            VectorStore defaultVectorStore,
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        this.defaultVectorStore = defaultVectorStore;
        this.jdbcTemplate = jdbcTemplateProvider != null ? jdbcTemplateProvider.getIfAvailable() : null;
        this.embeddingModel = embeddingModelProvider != null ? embeddingModelProvider.getIfAvailable() : null;
    }

    public VectorStore getStore(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return defaultVectorStore;
        }
        TableNameValidator.requireValid(tableName, "table");
        if (!(defaultVectorStore instanceof PgVectorStore)) {
            logger.warn("Default store is not PgVectorStore. Returning default store for table '{}'", tableName);
            return defaultVectorStore;
        }

        JdbcTemplate effectiveJdbc = this.jdbcTemplate;
        if (effectiveJdbc == null && defaultVectorStore instanceof PgVectorStore pgStore) {
            effectiveJdbc = pgStore.<JdbcTemplate>getNativeClient().orElse(null);
        }
        if (effectiveJdbc == null || embeddingModel == null) {
            logger.warn("JdbcTemplate or EmbeddingModel unavailable. Returning default store for table '{}'", tableName);
            return defaultVectorStore;
        }

        final JdbcTemplate finalJdbc = effectiveJdbc;
        return stores.computeIfAbsent(tableName, tbl -> {
            logger.info("Initializing dynamic PgVectorStore for table '{}'", tbl);
            PgVectorStore basePg = (PgVectorStore) defaultVectorStore;
            PgVectorStore store = PgVectorStore.builder(finalJdbc, this.embeddingModel)
                    .vectorTableName(tbl)
                    .dimensions(768)
                    .initializeSchema(true)
                    .vectorTableValidationsEnabled(false)
                    .distanceType(basePg.getDistanceType())
                    .build();
            try {
                store.afterPropertiesSet();
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to initialize PgVectorStore schema for table '" + tbl + "'", ex);
            }
            return store;
        });
    }

    public VectorStore getDefaultStore() {
        return defaultVectorStore;
    }
}
