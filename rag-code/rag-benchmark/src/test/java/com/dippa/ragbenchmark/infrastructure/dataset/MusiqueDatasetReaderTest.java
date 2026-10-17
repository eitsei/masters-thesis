package com.dippa.ragbenchmark.infrastructure.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import com.dippa.ragbenchmark.domain.dataset.MusiqueParagraph;
import com.dippa.ragbenchmark.domain.dataset.MusiqueQuestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MusiqueDatasetReaderTest {

    private ObjectMapper objectMapper;
    private MusiqueDatasetProperties properties;
    private MusiqueDatasetReader reader;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new MusiqueDatasetProperties(
                "data/musique/musique_full_v1.0_dev.jsonl",
                100,
                0.40,
                0.20,
                0.20,
                0.20,
                42L,
                100,
                true
        );
        reader = new MusiqueDatasetReader(properties, objectMapper);
    }

    @Test
    void parseLine_parsesValidJsonLineCorrectly() {
        String json = """
                {
                  "id": "2hop__460946_294723",
                  "paragraphs": [
                    {"idx": 0, "title": "Grant's First Stand", "paragraph_text": "Grant's First Stand is the debut album...", "is_supporting": false},
                    {"idx": 1, "title": "Miquette Giraudy", "paragraph_text": "Miquette Giraudy is a keyboard player...", "is_supporting": true}
                  ],
                  "question": "Who is the spouse of the Green performer?",
                  "question_decomposition": [
                    {"id": 460946, "question": "Green >> performer", "answer": "Steve Hillage", "paragraph_support_idx": 10},
                    {"id": 294723, "question": "#1 >> spouse", "answer": "Miquette Giraudy", "paragraph_support_idx": 1}
                  ],
                  "answer": "Miquette Giraudy",
                  "answer_aliases": ["Miquette"],
                  "answerable": true
                }
                """;

        MusiqueQuestion question = reader.parseLine(json);

        assertThat(question).isNotNull();
        assertThat(question.id()).isEqualTo("2hop__460946_294723");
        assertThat(question.question()).isEqualTo("Who is the spouse of the Green performer?");
        assertThat(question.answer()).isEqualTo("Miquette Giraudy");
        assertThat(question.answerAliases()).containsExactly("Miquette");
        assertThat(question.hopCount()).isEqualTo(2);
        assertThat(question.paragraphs()).hasSize(2);
        assertThat(question.getSupportingParagraphs()).hasSize(1);
        assertThat(question.getSupportingTitles()).containsExactly("Miquette Giraudy");
        assertThat(question.questionDecomposition()).hasSize(2);
    }

    @Test
    void parseLine_standardizesUnanswerableQuestion() {
        String json = """
                {
                  "id": "2hop__153573_109006",
                  "paragraphs": [
                    {"idx": 0, "title": "History of the Internet", "paragraph_text": "Precursors...", "is_supporting": false}
                  ],
                  "question": "Who developed the character?",
                  "question_decomposition": [],
                  "answer": "Walt Disney",
                  "answer_aliases": [],
                  "answerable": false
                }
                """;

        MusiqueQuestion question = reader.parseLine(json);

        assertThat(question).isNotNull();
        assertThat(question.id()).isEqualTo("2hop__153573_109006_unans");
        assertThat(question.answerable()).isFalse();
        assertThat(question.answer()).isEqualTo(MusiqueDatasetReader.CANONICAL_REFUSAL_EN);
        assertThat(question.answerAliases()).contains("does not contain sufficient information to answer this query");
    }

    @Test
    void extractHopCount_handles3hopAnd4hopPrefixes() {
        MusiqueQuestion q3 = reader.parseLine("{\"id\": \"3hop1__1234_5678\", \"question\": \"q\", \"answer\": \"a\", \"paragraphs\": []}");
        MusiqueQuestion q4 = reader.parseLine("{\"id\": \"4hop2__9999_8888\", \"question\": \"q\", \"answer\": \"a\", \"paragraphs\": []}");

        assertThat(q3.hopCount()).isEqualTo(3);
        assertThat(q4.hopCount()).isEqualTo(4);
    }

    @Test
    void loadAll_readsActualDatasetAndCaches() {
        List<MusiqueQuestion> all = reader.loadAll();

        assertThat(all).isNotEmpty();
        assertThat(all.size()).isGreaterThanOrEqualTo(4000);

        // Second call should return same cached reference
        List<MusiqueQuestion> secondCall = reader.loadAll();
        assertThat(secondCall).isSameAs(all);
    }

    @Test
    void getCanonicalBenchmarkSample_returnsTargetDistribution() {
        List<MusiqueQuestion> sample = reader.getCanonicalBenchmarkSample();

        assertThat(sample).hasSize(100);

        long answerableCount = sample.stream().filter(MusiqueQuestion::answerable).count();
        long unanswerableCount = sample.stream().filter(q -> !q.answerable()).count();

        assertThat(answerableCount).isEqualTo(80L);
        assertThat(unanswerableCount).isEqualTo(20L);

        Map<Integer, Long> ansHopCounts = sample.stream()
                .filter(MusiqueQuestion::answerable)
                .collect(Collectors.groupingBy(MusiqueQuestion::hopCount, Collectors.counting()));

        assertThat(ansHopCounts.get(2)).isEqualTo(40L);
        assertThat(ansHopCounts.get(3)).isEqualTo(20L);
        assertThat(ansHopCounts.get(4)).isEqualTo(20L);

        // Verify unanswerable questions have canonical refusal ground truth
        List<MusiqueQuestion> unanswerable = sample.stream().filter(q -> !q.answerable()).toList();
        for (MusiqueQuestion q : unanswerable) {
            assertThat(q.answer()).isEqualTo(MusiqueDatasetReader.CANONICAL_REFUSAL_EN);
            assertThat(q.answerAliases()).contains("does not contain sufficient information to answer this query");
        }

        // Verify zero overlap in base IDs between answerable and unanswerable questions
        java.util.Set<String> answerableBaseIds = sample.stream()
                .filter(MusiqueQuestion::answerable)
                .map(MusiqueQuestion::id)
                .collect(Collectors.toSet());
        java.util.Set<String> unanswerableBaseIds = unanswerable.stream()
                .map(q -> q.id().replace("_unans", ""))
                .collect(Collectors.toSet());
        assertThat(answerableBaseIds).doesNotContainAnyElementsOf(unanswerableBaseIds);

        // Verify that for every unanswerable question, none of its removed supporting bridge titles
        // leak into the shared vector store via other questions in the 100-question sample
        java.util.Set<String> allIngestedTitles = sample.stream()
                .flatMap(q -> q.paragraphs().stream())
                .map(MusiqueParagraph::title)
                .collect(Collectors.toSet());

        for (MusiqueQuestion u : unanswerable) {
            String baseId = u.id().replace("_unans", "");
            MusiqueQuestion pairedAns = reader.findById(baseId).orElseThrow();
            java.util.Set<String> uTitles = u.paragraphs().stream()
                    .map(MusiqueParagraph::title)
                    .collect(Collectors.toSet());
            List<String> removedBridges = pairedAns.getSupportingTitles().stream()
                    .filter(title -> !uTitles.contains(title))
                    .toList();
            assertThat(removedBridges).isNotEmpty();
            assertThat(allIngestedTitles).doesNotContainAnyElementsOf(removedBridges);
        }
    }

    @Test
    void getStratifiedSample_returnsExactDistributionAndIsDeterministic() {
        List<MusiqueQuestion> sample1 = reader.getStratifiedSample(100, 0.40, 0.20, 0.20, 0.20, 42L);
        List<MusiqueQuestion> sample2 = reader.getStratifiedSample(100, 0.40, 0.20, 0.20, 0.20, 42L);

        assertThat(sample1).hasSize(100);

        // Deterministic reproduction
        List<String> ids1 = sample1.stream().map(MusiqueQuestion::id).toList();
        List<String> ids2 = sample2.stream().map(MusiqueQuestion::id).toList();
        assertThat(ids1).isEqualTo(ids2);
    }

    @Test
    void findById_returnsExpectedQuestion() {
        List<MusiqueQuestion> all = reader.loadAll();
        String expectedId = all.getFirst().id();

        MusiqueQuestion found = reader.findById(expectedId).orElseThrow();
        assertThat(found.id()).isEqualTo(expectedId);
    }

    @Test
    void loadAll_indexesAllQuestionsWithoutIdCollision() {
        List<MusiqueQuestion> all = reader.loadAll();
        assertThat(all).hasSize(4834);

        // Verify that all IDs are strictly unique across the full dataset
        List<String> ids = all.stream().map(MusiqueQuestion::id).toList();
        assertThat(java.util.Set.copyOf(ids)).hasSize(4834);

        // Verify that findById retrieves both answerable and unanswerable versions of paired questions
        MusiqueQuestion ans = reader.findById("2hop__837900_71701").orElseThrow();
        assertThat(ans.answerable()).isTrue();
        assertThat(ans.answer()).isEqualTo("November 5");

        MusiqueQuestion unans = reader.findById("2hop__837900_71701_unans").orElseThrow();
        assertThat(unans.answerable()).isFalse();
        assertThat(unans.answer()).isEqualTo(MusiqueDatasetReader.CANONICAL_REFUSAL_EN);
    }

    @Test
    void exportCanonicalBenchmarkSampleToFile() throws Exception {
        List<MusiqueQuestion> sample = reader.getCanonicalBenchmarkSample();
        assertThat(sample).hasSize(100);

        java.nio.file.Path outPath = java.nio.file.Path.of("..", "rag-evaluator", "data", "musique", "musique_100_final.jsonl");
        if (java.nio.file.Files.exists(outPath.getParent())) {
            try (var writer = java.nio.file.Files.newBufferedWriter(outPath, java.nio.charset.StandardCharsets.UTF_8)) {
                for (MusiqueQuestion q : sample) {
                    com.dippa.ragbenchmark.api.MusiqueQuestionResponse resp =
                            com.dippa.ragbenchmark.api.MusiqueQuestionResponse.fromDomain(q);
                    writer.write(objectMapper.writeValueAsString(resp));
                    writer.newLine();
                }
            }
        }
    }
}
