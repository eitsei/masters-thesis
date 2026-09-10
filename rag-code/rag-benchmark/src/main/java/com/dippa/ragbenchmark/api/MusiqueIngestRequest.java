package com.dippa.ragbenchmark.api;

public record MusiqueIngestRequest(
        Boolean sampleOnly,
        Integer sampleSize,
        String mode
) {
    public MusiqueIngestRequest(Boolean sampleOnly, Integer sampleSize) {
        this(sampleOnly, sampleSize, "original");
    }

    public boolean isSampleOnly() {
        return sampleOnly == null || sampleOnly;
    }

    public int resolvedSampleSize(int defaultSize) {
        return sampleSize != null && sampleSize > 0 ? sampleSize : defaultSize;
    }

    public boolean isSmallToBig() {
        return mode != null && (mode.equalsIgnoreCase("s2b") || mode.equalsIgnoreCase("small-to-big"));
    }
}
