package com.dippa.ragbenchmark.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RetrievedChunkTest {

    @Test
    void threeArgConstructor_setsAllFields() {
        RetrievedChunk chunk = new RetrievedChunk("chunk-1", "snippet preview", "full chunk text");
        assertThat(chunk.id()).isEqualTo("chunk-1");
        assertThat(chunk.snippet()).isEqualTo("snippet preview");
        assertThat(chunk.text()).isEqualTo("full chunk text");
    }

    @Test
    void twoArgConstructor_backwardCompatible_defaultsTextToSnippet() {
        RetrievedChunk chunk = new RetrievedChunk("chunk-1", "snippet text");
        assertThat(chunk.id()).isEqualTo("chunk-1");
        assertThat(chunk.snippet()).isEqualTo("snippet text");
        assertThat(chunk.text()).isEqualTo("snippet text");
    }
}
