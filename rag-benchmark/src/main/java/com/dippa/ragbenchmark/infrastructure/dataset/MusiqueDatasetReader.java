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
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Reads, parses, and stratifies the MuSiQue JSONL dataset.
 */
@Component
public class MusiqueDatasetReader {

    private static final Logger logger = LoggerFactory.getLogger(MusiqueDatasetReader.class);

    private final MusiqueDatasetProperties properties;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    private final Object lock = new Object();
    private volatile List<MusiqueQuestion> cachedQuestions;
    private final Map<String, MusiqueQuestion> questionIndex = new ConcurrentHashMap<>();

    public MusiqueDatasetReader(MusiqueDatasetProperties properties) {
        this(properties, new ObjectMapper(), new DefaultResourceLoader());
    }

    public MusiqueDatasetReader(MusiqueDatasetProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, new DefaultResourceLoader());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MusiqueDatasetReader(
            MusiqueDatasetProperties properties,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ResourceLoader resourceLoader) {
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
            logger.info("Loaded {} MuSiQue questions from {}", cachedQuestions.size(), properties.filePath());
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

    /**
     * Produces a reproducible stratified sample matching the specified hop ratios and random seed.
     */
    public List<MusiqueQuestion> getStratifiedSample(int sampleSize, double twoHopRatio, double threeHopRatio, double fourHopRatio, long seed) {
        List<MusiqueQuestion> all = loadAll();
        if (all.isEmpty()) {
            return List.of();
        }

        Map<Integer, List<MusiqueQuestion>> byHop = all.stream()
                .collect(Collectors.groupingBy(MusiqueQuestion::hopCount));

        List<MusiqueQuestion> twoHopPool = new ArrayList<>(byHop.getOrDefault(2, List.of()));
        List<MusiqueQuestion> threeHopPool = new ArrayList<>(byHop.getOrDefault(3, List.of()));
        List<MusiqueQuestion> fourHopPool = new ArrayList<>(byHop.getOrDefault(4, List.of()));

        Random random = new Random(seed);
        Collections.shuffle(twoHopPool, random);
        Collections.shuffle(threeHopPool, random);
        Collections.shuffle(fourHopPool, random);

        int count2 = (int) Math.round(sampleSize * twoHopRatio);
        int count3 = (int) Math.round(sampleSize * threeHopRatio);
        int count4 = sampleSize - count2 - count3;

        List<MusiqueQuestion> sample = new ArrayList<>(sampleSize);
        sample.addAll(twoHopPool.stream().limit(count2).toList());
        sample.addAll(threeHopPool.stream().limit(count3).toList());
        sample.addAll(fourHopPool.stream().limit(count4).toList());

        return Collections.unmodifiableList(sample);
    }

    /**
     * Returns the canonical benchmark evaluation sample defined in properties (thesis §4.2.1).
     */
    public List<MusiqueQuestion> getCanonicalBenchmarkSample() {
        return getStratifiedSample(
                properties.sampleSize(),
                properties.twoHopRatio(),
                properties.threeHopRatio(),
                properties.fourHopRatio(),
                properties.randomSeed()
        );
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
            throw new IllegalStateException("Failed to read MuSiQue dataset from: " + location, e);
        }
    }

    private BufferedReader openReader(String location) throws IOException {
        Path path = Path.of(location);
        if (Files.exists(path)) {
            return Files.newBufferedReader(path, StandardCharsets.UTF_8);
        }

        // Try relative to workspace sub-directory if executed from root
        Path subPath = Path.of("rag-benchmark", location);
        if (Files.exists(subPath)) {
            return Files.newBufferedReader(subPath, StandardCharsets.UTF_8);
        }

        // Fallback to Spring ResourceLoader
        Resource resource = resourceLoader.getResource(location);
        if (resource.exists()) {
            InputStream is = resource.getInputStream();
            return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        }

        throw new IOException("MuSiQue dataset file not found at: " + location + " (also checked " + subPath.toAbsolutePath() + ")");
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
                for (JsonNode d : decompNode) {
                    Integer supportIdx = d.hasNonNull("paragraph_support_idx") ? d.get("paragraph_support_idx").asInt() : null;
                    decomposition.add(new MusiqueDecompositionStep(
                            d.path("id").asLong(0),
                            d.path("question").asText(""),
                            d.path("answer").asText(""),
                            supportIdx
                    ));
                }
            }

            int hopCount = extractHopCount(id, decomposition);

            return new MusiqueQuestion(id, question, answer, aliases, hopCount, answerable, paragraphs, decomposition);
        } catch (Exception e) {
            logger.warn("Failed to parse MuSiQue JSON line: {}", jsonLine, e);
            return null;
        }
    }

    private int extractHopCount(String id, List<MusiqueDecompositionStep> decomposition) {
        if (id != null) {
            if (id.startsWith("2hop")) return 2;
            if (id.startsWith("3hop")) return 3;
            if (id.startsWith("4hop")) return 4;
        }
        if (decomposition != null && !decomposition.isEmpty()) {
            return decomposition.size();
        }
        return 2;
    }
}
