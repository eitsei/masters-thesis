package com.dippa.ragbenchmark.infrastructure.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dippa.ragbenchmark.domain.dataset.MusiqueQuestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EnterpriseClosedDatasetReaderTest {

    private ObjectMapper objectMapper;
    private EnterpriseClosedDatasetProperties properties;
    private EnterpriseClosedDatasetReader reader;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new EnterpriseClosedDatasetProperties(
                "data/enterprise/enterprise_100_final.jsonl",
                100,
                10,
                25,
                512,
                1024,
                128,
                true
        );
        reader = new EnterpriseClosedDatasetReader(properties, objectMapper);
    }

    @Test
    void parseLine_parsesValidEnterpriseJsonLineCorrectly() {
        String json = """
                {
                  "id": "2hop__enterprise_0005",
                  "question": "Miten rahasto-osuuksien lunastaminen toteutetaan?",
                  "answer": "Tekemällä lunastustoimeksianto mobiili- tai verkkopankissa.",
                  "answer_aliases": ["Verkkopankin kautta"],
                  "paragraphs": [
                    {"idx": 0, "title": "Rahastojen myynti", "paragraph_text": "Voit antaa toimeksiannon verkossa...", "is_supporting": true},
                    {"idx": 1, "title": "Kuukausisäästäminen", "paragraph_text": "Säästä kuukausittain...", "is_supporting": false}
                  ],
                  "question_decomposition": [
                    {"id": 1, "question": "Miten lunastus tehdään?", "answer": "Mobiilipankissa", "paragraph_support_idx": 0}
                  ],
                  "answerable": true
                }
                """;

        MusiqueQuestion q = reader.parseLine(json);

        assertThat(q).isNotNull();
        assertThat(q.id()).isEqualTo("2hop__enterprise_0005");
        assertThat(q.question()).isEqualTo("Miten rahasto-osuuksien lunastaminen toteutetaan?");
        assertThat(q.answer()).isEqualTo("Tekemällä lunastustoimeksianto mobiili- tai verkkopankissa.");
        assertThat(q.answerAliases()).containsExactly("Verkkopankin kautta");
        assertThat(q.answerable()).isTrue();
        assertThat(q.paragraphs()).hasSize(2);
        assertThat(q.getSupportingParagraphs()).hasSize(1);
        assertThat(q.getSupportingParagraphs().get(0).title()).isEqualTo("Rahastojen myynti");
    }

    @Test
    void parseLine_returnsNullOnInvalidJson() {
        MusiqueQuestion q = reader.parseLine("not-a-json-string");
        assertThat(q).isNull();
    }

    @Test
    void loadAll_throwsOnNullOrBlankPath() {
        EnterpriseClosedDatasetProperties badProps = new EnterpriseClosedDatasetProperties(
                "",
                100, 10, 25, 512, 1024, 128, true
        );
        EnterpriseClosedDatasetReader badReader = new EnterpriseClosedDatasetReader(badProps, objectMapper);
        assertThatThrownBy(badReader::loadAll)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Enterprise closed-corpus dataset file path must not be null or blank");
    }
}
