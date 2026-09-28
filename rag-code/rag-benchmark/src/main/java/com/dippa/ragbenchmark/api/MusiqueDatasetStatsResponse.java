package com.dippa.ragbenchmark.api;

import java.util.Map;

public record MusiqueDatasetStatsResponse(
        int totalQuestions,
        long twoHopCount,
        long threeHopCount,
        long fourHopCount,
        long answerableCount,
        long unanswerableCount,
        int canonicalSampleSize,
        String datasetPath
) {
    public static MusiqueDatasetStatsResponse fromMap(Map<String, Object> map) {
        return new MusiqueDatasetStatsResponse(
                (Integer) map.getOrDefault("totalQuestions", 0),
                (Long) map.getOrDefault("twoHopCount", 0L),
                (Long) map.getOrDefault("threeHopCount", 0L),
                (Long) map.getOrDefault("fourHopCount", 0L),
                (Long) map.getOrDefault("answerableCount", 0L),
                (Long) map.getOrDefault("unanswerableCount", 0L),
                (Integer) map.getOrDefault("canonicalSampleSize", 100),
                (String) map.getOrDefault("datasetPath", "")
        );
    }
}
