package com.dippa.ragbenchmark.infrastructure;

import com.dippa.ragbenchmark.domain.RetrievalExpander;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dippa.ragbenchmark.domain.ParentExpansionException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL-backed implementation of {@link RetrievalExpander}.
 *
 * <p>Expands retrieved child chunks to their corresponding parent context windows by looking up
 * the {@code parent_id} recorded in chunk metadata against the parent vector/document table.</p>
 */
@Component
public class PgVectorRetrievalExpander implements RetrievalExpander {

    private static final Logger logger = LoggerFactory.getLogger(PgVectorRetrievalExpander.class);
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PgVectorRetrievalExpander(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public List<Document> expand(List<Document> childChunks) {
        return expand(childChunks, null);
    }

    @Override
    public List<Document> expand(List<Document> childChunks, String defaultParentTable) {
        if (childChunks == null || childChunks.isEmpty()) {
            return List.of();
        }

        // Maintain unique parent IDs in order of highest similarity child ranking
        Map<String, Document> uniqueParents = new LinkedHashMap<>();

        for (Document child : childChunks) {
            String parentId = extractParentId(child);
            if (parentId == null || parentId.isBlank()) {
                throw new ParentExpansionException("Retrieved M1 child '" + child.getId()
                        + "' does not contain a parent_id");
            }
            uniqueParents.putIfAbsent(parentId, child);
        }

        if (uniqueParents.isEmpty()) {
            throw new ParentExpansionException("No parent IDs were available for M1 expansion");
        }

        // Determine parent table name
        String parentTable = defaultParentTable;
        if (parentTable == null || parentTable.isBlank()) {
            // Check if specified in child chunk metadata
            for (Document child : uniqueParents.values()) {
                Object tableMeta = child.getMetadata().get("parent_table");
                if (tableMeta instanceof String s && !s.isBlank()) {
                    parentTable = s;
                    break;
                }
            }
        }

        if (parentTable == null || parentTable.isBlank()) {
            throw new ParentExpansionException("No parent table specified for M1 expansion");
        }

        TableNameValidator.requireValid(parentTable, "parent table");

        if (this.jdbcTemplate == null) {
            throw new ParentExpansionException("JdbcTemplate is required for M1 parent expansion");
        }

        List<UUID> parentUuids = uniqueParents.keySet().stream()
                .map(idStr -> {
                    try {
                        return UUID.fromString(idStr);
                    } catch (IllegalArgumentException e) {
                        throw new ParentExpansionException("Invalid UUID parent_id '" + idStr + "'", e);
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        if (parentUuids.isEmpty()) {
            throw new ParentExpansionException("No valid parent IDs were available for M1 expansion");
        }

        String inPlaceholders = String.join(",", Collections.nCopies(parentUuids.size(), "?"));
        String sql = "SELECT id, content, metadata FROM " + parentTable + " WHERE id IN (" + inPlaceholders + ")";

        Map<String, Document> fetchedParents = new HashMap<>();
        try {
            jdbcTemplate.query(sql, ps -> {
                for (int i = 0; i < parentUuids.size(); i++) {
                    ps.setObject(i + 1, parentUuids.get(i));
                }
            }, rs -> {
                String id = rs.getString("id");
                String content = rs.getString("content");
                String metadataJson = rs.getString("metadata");

                Map<String, Object> metadata = new HashMap<>();
                if (metadataJson != null && !metadataJson.isBlank()) {
                    try {
                        metadata = objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {});
                    } catch (Exception e) {
                        logger.warn("Failed to parse metadata JSON for parent document {}: {}", id, e.getMessage());
                    }
                }
                fetchedParents.put(id, new Document(id, content, metadata));
            });
        } catch (Exception ex) {
            logger.error("Failed to query parent table '{}': {}", parentTable, ex.getMessage(), ex);
            throw new ParentExpansionException("Failed to query parent table '" + parentTable + "'", ex);
        }

        List<Document> expandedDocuments = new ArrayList<>(uniqueParents.size());
        for (Map.Entry<String, Document> entry : uniqueParents.entrySet()) {
            String parentId = entry.getKey();
            Document triggeringChild = entry.getValue();
            Document parentDoc = fetchedParents.get(parentId);

            if (parentDoc != null) {
                Map<String, Object> enrichedMeta = new HashMap<>(parentDoc.getMetadata());
                enrichedMeta.put("retrieved_child_id", triggeringChild.getId());
                enrichedMeta.put("retrieved_child_snippet", triggeringChild.getText());
                if (triggeringChild.getMetadata().containsKey("distance")) {
                    enrichedMeta.put("child_distance", triggeringChild.getMetadata().get("distance"));
                }
                expandedDocuments.add(new Document(parentDoc.getId(), parentDoc.getText(), enrichedMeta));
            } else {
                throw new ParentExpansionException("Parent document '" + parentId
                        + "' was not found in table '" + parentTable + "'");
            }
        }

        expandedDocuments = mergeOverlappingParents(expandedDocuments);

        logger.info("Expanded {} child chunk(s) into {} unique parent window(s) from table '{}'",
                childChunks.size(), expandedDocuments.size(), parentTable);
        return expandedDocuments;
    }

    /**
     * Merges windows only when ingestion supplied explicit character/token boundaries. Plain
     * legacy parent rows are left untouched, avoiding accidental concatenation of unrelated
     * documents while still preventing duplicate context for overlapping S2B windows.
     */
    private List<Document> mergeOverlappingParents(List<Document> parents) {
        List<Document> merged = new ArrayList<>();
        for (Document candidate : parents) {
            if (merged.isEmpty()) {
                merged.add(candidate);
                continue;
            }
            Document previous = merged.get(merged.size() - 1);
            Integer previousStart = boundary(previous, "window_start");
            Integer previousEnd = boundary(previous, "window_end");
            Integer candidateStart = boundary(candidate, "window_start");
            Integer candidateEnd = boundary(candidate, "window_end");
            String previousSource = sourceIdentity(previous);
            String candidateSource = sourceIdentity(candidate);
            if (previousStart == null || previousEnd == null || candidateStart == null || candidateEnd == null
                    || !previousSource.equals(candidateSource) || candidateStart > previousEnd + 1) {
                merged.add(candidate);
                continue;
            }

            Map<String, Object> metadata = new HashMap<>(previous.getMetadata());
            metadata.put("window_end", Math.max(previousEnd, candidateEnd));
            metadata.put("merged_parent_ids", List.of(
                    previous.getId(),
                    candidate.getId()));
            String previousText = previous.getText() == null ? "" : previous.getText();
            String candidateText = candidate.getText() == null ? "" : candidate.getText();
            String combinedText = combineOverlappingText(previousText, candidateText);
            merged.set(merged.size() - 1, new Document(previous.getId(), combinedText, metadata));
        }
        return merged;
    }

    private Integer boundary(Document document, String key) {
        Object value = document.getMetadata().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.valueOf(string);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String sourceIdentity(Document document) {
        Object source = document.getMetadata().get("source_url");
        if (source == null) {
            source = document.getMetadata().get("title");
        }
        return source == null ? "" : source.toString();
    }

    private String combineOverlappingText(String first, String second) {
        if (first.contains(second)) {
            return first;
        }
        if (second.contains(first)) {
            return second;
        }
        return first + "\n\n" + second;
    }

    private String extractParentId(Document child) {
        if (child == null || child.getMetadata() == null) {
            return null;
        }
        Object id = child.getMetadata().get("parent_id");
        if (id == null) {
            id = child.getMetadata().get("parentId");
        }
        return id != null ? id.toString() : null;
    }
}
