package com.dippa.ragbenchmark.api;

public record MusiqueIngestRequest(
        Boolean sampleOnly,
        Integer sampleSize
) {
    public boolean isSampleOnly() {
        return sampleOnly == null || sampleOnly;
    }

    public int resolvedSampleSize(int defaultSize) {
        return sampleSize != null && sampleSize > 0 ? sampleSize : defaultSize;
    }
}
