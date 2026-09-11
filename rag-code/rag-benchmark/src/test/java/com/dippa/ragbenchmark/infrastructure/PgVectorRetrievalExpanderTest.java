package com.dippa.ragbenchmark.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;

class PgVectorRetrievalExpanderTest {

    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private PgVectorRetrievalExpander expander;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = new ObjectMapper();
        expander = new PgVectorRetrievalExpander(jdbcTemplate, objectMapper);
    }

    @Test
    void expand_nullOrEmpty_returnsEmptyList() {
        assertThat(expander.expand(null)).isEmpty();
        assertThat(expander.expand(List.of())).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void expand_noParentIdInMetadata_failsExplicitly() {
        Document child = new Document("c1", "text", Map.of("key", "val"));
        assertThatThrownBy(() -> expander.expand(List.of(child), "vector_store_musique_large"))
                .isInstanceOf(com.dippa.ragbenchmark.domain.ParentExpansionException.class)
                .hasMessageContaining("does not contain a parent_id");
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void expand_noParentTable_failsExplicitly() {
        Document child = new Document("c1", "text", Map.of("parent_id", UUID.randomUUID().toString()));
        assertThatThrownBy(() -> expander.expand(List.of(child), null))
                .isInstanceOf(com.dippa.ragbenchmark.domain.ParentExpansionException.class)
                .hasMessageContaining("No parent table specified");
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void expand_invalidParentTableName_throwsIllegalArgumentException() {
        Document child = new Document("c1", "text", Map.of("parent_id", UUID.randomUUID().toString()));
        assertThatThrownBy(() -> expander.expand(List.of(child), "table; DROP TABLE students;"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid parent table name");
    }

    @Test
    void expand_validParentIds_deduplicatesAndPreservesChildRankingOrder() {
        UUID parentIdA = UUID.randomUUID();
        UUID parentIdB = UUID.randomUUID();

        // 3 child chunks: child 1 and child 2 point to parent A, child 3 points to parent B
        Document child1 = new Document("c1", "child 1 text", Map.of("parent_id", parentIdA.toString(), "score", 0.95));
        Document child2 = new Document("c2", "child 2 text", Map.of("parent_id", parentIdA.toString(), "score", 0.90));
        Document child3 = new Document("c3", "child 3 text", Map.of("parent_id", parentIdB.toString(), "score", 0.85));

        doAnswer(invocation -> {
            RowCallbackHandler rch = invocation.getArgument(2);
            ResultSet rs = mock(ResultSet.class);

            // Row for parent B
            when(rs.getString("id")).thenReturn(parentIdB.toString(), parentIdA.toString());
            when(rs.getString("content")).thenReturn("Parent B content", "Parent A content");
            when(rs.getString("metadata")).thenReturn("{\"title\":\"Title B\"}", "{\"title\":\"Title A\"}");

            rch.processRow(rs);
            rch.processRow(rs);
            return null;
        }).when(jdbcTemplate).query(contains("vector_store_test_large"), any(PreparedStatementSetter.class), any(RowCallbackHandler.class));

        List<Document> expanded = expander.expand(List.of(child1, child2, child3), "vector_store_test_large");

        // Deduplication yields 2 parent documents
        assertThat(expanded).hasSize(2);
        // Parent A must be #1 because child 1 was higher ranked than child 3
        assertThat(expanded.get(0).getId()).isEqualTo(parentIdA.toString());
        assertThat(expanded.get(0).getText()).isEqualTo("Parent A content");
        assertThat(expanded.get(0).getMetadata().get("retrieved_child_id")).isEqualTo("c1");
        assertThat(expanded.get(0).getMetadata().get("title")).isEqualTo("Title A");

        // Parent B must be #2
        assertThat(expanded.get(1).getId()).isEqualTo(parentIdB.toString());
        assertThat(expanded.get(1).getText()).isEqualTo("Parent B content");
        assertThat(expanded.get(1).getMetadata().get("retrieved_child_id")).isEqualTo("c3");
    }
}
