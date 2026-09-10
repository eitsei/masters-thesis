package com.dippa.ragbenchmark.infrastructure;

import com.dippa.ragbenchmark.domain.RetrievalExpander;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
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
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

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
        List<Document> unlinkedChildren = new ArrayList<>();

        for (Document child : childChunks) {
            String parentId = extractParentId(child);
            if (parentId != null && !parentId.isBlank()) {
                uniqueParents.putIfAbsent(parentId, child);
            } else {
                unlinkedChildren.add(child);
            }
        }

        if (uniqueParents.isEmpty()) {
            logger.debug("No parent_id found on retrieved child chunks; returning child chunks as-is.");
            return childChunks;
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
            logger.warn("No parent table specified or discoverable from child metadata. Returning child chunks as-is.");
            return childChunks;
        }

        if (!TABLE_NAME_PATTERN.matcher(parentTable).matches()) {
            throw new IllegalArgumentException("Invalid parent table name: " + parentTable);
        }

        if (this.jdbcTemplate == null) {
            logger.warn("JdbcTemplate not available in PgVectorRetrievalExpander. Returning child chunks as-is.");
            return childChunks;
        }

        List<UUID> parentUuids = uniqueParents.keySet().stream()
                .map(idStr -> {
                    try {
                        return UUID.fromString(idStr);
                    } catch (IllegalArgumentException e) {
                        logger.warn("Invalid UUID parent_id '{}': {}", idStr, e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        if (parentUuids.isEmpty()) {
            return childChunks;
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
            return childChunks;
        }

        List<Document> expandedDocuments = new ArrayList<>(uniqueParents.size() + unlinkedChildren.size());
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
                logger.warn("Parent document '{}' not found in table '{}'; falling back to child chunk", parentId, parentTable);
                expandedDocuments.add(triggeringChild);
            }
        }

        // Append any unlinked children at the end
        expandedDocuments.addAll(unlinkedChildren);

        logger.info("Expanded {} child chunk(s) into {} unique parent window(s) from table '{}'",
                childChunks.size(), expandedDocuments.size(), parentTable);
        return expandedDocuments;
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
