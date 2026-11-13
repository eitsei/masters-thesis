package com.dippa.ragbenchmark.infrastructure.dataset;

import com.dippa.ragbenchmark.domain.dataset.MusiqueDecompositionStep;
import com.dippa.ragbenchmark.domain.dataset.MusiqueParagraph;
import com.dippa.ragbenchmark.domain.dataset.MusiqueQuestion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Reads and parses the Enterprise closed-corpus JSONL dataset (conforming to the MuSiQue JSON schema).
 */
@Component
public class EnterpriseClosedDatasetReader {

    private static final Logger logger = LoggerFactory.getLogger(EnterpriseClosedDatasetReader.class);

    private final EnterpriseClosedDatasetProperties properties;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    private final Object lock = new Object();
    private volatile List<MusiqueQuestion> cachedQuestions;
    private final Map<String, MusiqueQuestion> questionIndex = new ConcurrentHashMap<>();

    public EnterpriseClosedDatasetReader(EnterpriseClosedDatasetProperties properties) {
        this(properties, new ObjectMapper(), new DefaultResourceLoader());
    }

    public EnterpriseClosedDatasetReader(EnterpriseClosedDatasetProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, new DefaultResourceLoader());
    }

    @Autowired
    public EnterpriseClosedDatasetReader(
            EnterpriseClosedDatasetProperties properties,
            @Autowired(required = false) ObjectMapper objectMapper,
            @Autowired(required = false) ResourceLoader resourceLoader) {
        this.properties = properties;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.resourceLoader = resourceLoader != null ? resourceLoader : new DefaultResourceLoader();
    }

    /**
     * Loads all questions from the configured dataset file, caching them in memory.
     */
    public List<MusiqueQuestion> loadAll() {
        if (cachedQuestions != null) {
            return cachedQuestions;
        }
        synchronized (lock) {
            if (cachedQuestions != null) {
                return cachedQuestions;
            }
            List<MusiqueQuestion> questions = readDatasetFile(properties.filePath());
            for (MusiqueQuestion q : questions) {
                questionIndex.put(q.id(), q);
            }
            cachedQuestions = Collections.unmodifiableList(questions);
            logger.info("Loaded {} Enterprise closed-corpus questions from {}", cachedQuestions.size(), properties.filePath());
            return cachedQuestions;
        }
    }

    /**
     * Finds a question by its unique identifier.
     */
    public Optional<MusiqueQuestion> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        loadAll();
        return Optional.ofNullable(questionIndex.get(id));
    }

    private List<MusiqueQuestion> readDatasetFile(String location) {
        try (BufferedReader reader = openReader(location)) {
            List<MusiqueQuestion> list = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                MusiqueQuestion question = parseLine(line);
                if (question != null) {
                    list.add(question);
                }
            }
            return list;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Enterprise closed-corpus dataset from: " + location, e);
        }
    }

    private BufferedReader openReader(String location) throws IOException {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Enterprise closed-corpus dataset file path must not be null or blank");
        }
        Path path = Path.of(location);
        if (Files.exists(path)) {
            return Files.newBufferedReader(path, StandardCharsets.UTF_8);
        }

        // Try relative paths across workspace layouts
        List<Path> candidatePaths = List.of(
                Path.of("rag-code", "rag-benchmark", location),
                Path.of("rag-benchmark", location),
                Path.of("rag-code", "rag-evaluator", location),
                Path.of("..", "rag-evaluator", location),
                Path.of("data", "enterprise", "enterprise_100_final.jsonl"),
                Path.of("..", "rag-evaluator", "data", "enterprise", "enterprise_100_final.jsonl"),
                Path.of("rag-code", "rag-evaluator", "data", "enterprise", "enterprise_100_final.jsonl"),
                Path.of("data", "nordea", "nordea_100_final.jsonl"),
                Path.of("rag-code", "rag-evaluator", "data", "nordea", "nordea_100_final.jsonl")
        );

        for (Path candidate : candidatePaths) {
            if (Files.exists(candidate)) {
                logger.info("Resolved Enterprise closed-corpus file at: {}", candidate.toAbsolutePath());
                return Files.newBufferedReader(candidate, StandardCharsets.UTF_8);
            }
        }

        // Fallback to Spring ResourceLoader
        Resource resource = resourceLoader.getResource(location);
        if (resource.exists()) {
            InputStream is = resource.getInputStream();
            return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        }

        throw new IOException("Enterprise closed-corpus dataset file not found at: " + location
                + " (checked candidates: " + candidatePaths + ")");
    }

    MusiqueQuestion parseLine(String jsonLine) {
        try {
            JsonNode root = objectMapper.readTree(jsonLine);
            String id = root.path("id").asText("");
            String question = root.path("question").asText("");
            String answer = root.path("answer").asText("");
            boolean answerable = root.path("answerable").asBoolean(true);

            List<String> aliases = new ArrayList<>();
            JsonNode aliasesNode = root.path("answer_aliases");
            if (aliasesNode.isArray()) {
                for (JsonNode alias : aliasesNode) {
                    aliases.add(alias.asText());
                }
            }

            List<MusiqueParagraph> paragraphs = new ArrayList<>();
            JsonNode paragraphsNode = root.path("paragraphs");
            if (paragraphsNode.isArray()) {
                for (JsonNode p : paragraphsNode) {
                    paragraphs.add(new MusiqueParagraph(
                            p.path("idx").asInt(0),
                            p.path("title").asText(""),
                            p.path("paragraph_text").asText(""),
                            p.path("is_supporting").asBoolean(false)
                    ));
                }
            }

            List<MusiqueDecompositionStep> decomposition = new ArrayList<>();
            JsonNode decompNode = root.path("question_decomposition");
            if (decompNode.isArray()) {
                for (JsonNode step : decompNode) {
                    decomposition.add(new MusiqueDecompositionStep(
                            step.path("id").asInt(0),
                            step.path("question").asText(""),
                            step.path("answer").asText(""),
                            step.has("paragraph_support_idx") && !step.get("paragraph_support_idx").isNull()
                                    ? step.get("paragraph_support_idx").asInt()
                                    : null
                    ));
                }
            }

            int hopCount = root.has("hopCount") ? root.get("hopCount").asInt(2) : (id.startsWith("2hop") ? 2 : 1);

            return new MusiqueQuestion(id, question, answer, aliases, hopCount, answerable, paragraphs, decomposition);
        } catch (Exception e) {
            logger.warn("Failed to parse Enterprise question line: {}", e.getMessage());
            return null;
        }
    }
}
