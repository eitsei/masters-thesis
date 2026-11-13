package com.dippa.ragbenchmark.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.dippa.ragbenchmark.api.TokenUsageDto;
import com.dippa.ragbenchmark.domain.ModuleId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RagQueryResultTest {

    @Test
    void sixArgConstructor_setsTokenUsageAndCalculatesTotalLatency() {
        TokenUsageDto usage = new TokenUsageDto(100, 50, 150, 20);
        RetrievedChunk chunk = new RetrievedChunk("id-1", "snippet", "text");
        RagQueryResult result = new RagQueryResult(
                "answer",
                Set.of(ModuleId.M1),
                List.of(chunk),
                120L,
                450L,
                usage
        );

        assertThat(result.answer()).isEqualTo("answer");
        assertThat(result.modulesApplied()).containsExactly(ModuleId.M1);
        assertThat(result.retrievedChunks()).containsExactly(chunk);
        assertThat(result.retrievalMs()).isEqualTo(120L);
        assertThat(result.generationMs()).isEqualTo(450L);
        assertThat(result.tokenUsage()).isEqualTo(usage);
        assertThat(result.totalLatencyMs()).isEqualTo(570L);
    }

    @Test
    void fiveArgConstructor_backwardCompatible_defaultsTokenUsageToZero() {
        RetrievedChunk chunk = new RetrievedChunk("id-1", "snippet", "text");
        RagQueryResult result = new RagQueryResult(
                "answer",
                Set.of(),
                List.of(chunk),
                50L,
                200L
        );

        assertThat(result.tokenUsage()).isEqualTo(TokenUsageDto.ZERO);
        assertThat(result.totalLatencyMs()).isEqualTo(250L);
    }
}
