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
                "data/musique/musique_ans_v1.0_dev.jsonl",
                100,
                0.40,
                0.30,
                0.30,
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
        assertThat(all.size()).isGreaterThanOrEqualTo(2000);

        // Second call should return same cached reference
        List<MusiqueQuestion> secondCall = reader.loadAll();
        assertThat(secondCall).isSameAs(all);
    }

    @Test
    void getStratifiedSample_returnsExactDistributionAndIsDeterministic() {
        List<MusiqueQuestion> sample1 = reader.getStratifiedSample(100, 0.40, 0.30, 0.30, 42L);
        List<MusiqueQuestion> sample2 = reader.getStratifiedSample(100, 0.40, 0.30, 0.30, 42L);

        assertThat(sample1).hasSize(100);

        Map<Integer, Long> hopCounts = sample1.stream()
                .collect(Collectors.groupingBy(MusiqueQuestion::hopCount, Collectors.counting()));

        assertThat(hopCounts.get(2)).isEqualTo(40L);
        assertThat(hopCounts.get(3)).isEqualTo(30L);
        assertThat(hopCounts.get(4)).isEqualTo(30L);

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
}
