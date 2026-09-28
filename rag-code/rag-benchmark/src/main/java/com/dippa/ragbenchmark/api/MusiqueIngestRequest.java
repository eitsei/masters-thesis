package com.dippa.ragbenchmark.api;

public record MusiqueIngestRequest(
        Boolean sampleOnly,
        Integer sampleSize,
        String mode,
        Boolean clearDatabase
) {
    public MusiqueIngestRequest(Boolean sampleOnly, Integer sampleSize, String mode) {
        this(sampleOnly, sampleSize, mode, false);
    }

    public MusiqueIngestRequest(Boolean sampleOnly, Integer sampleSize) {
        this(sampleOnly, sampleSize, "original", false);
    }

    public boolean isSampleOnly() {
        return sampleOnly == null || sampleOnly;
    }

    public int resolvedSampleSize(int defaultSize) {
        return sampleSize != null && sampleSize > 0 ? sampleSize : defaultSize;
    }

    public String resolvedMode() {
        return mode != null && !mode.isBlank() ? mode : "original";
    }

    public boolean shouldClearDatabase() {
        return Boolean.TRUE.equals(clearDatabase);
    }

    public boolean isSmallToBig() {
        return mode != null && (mode.equalsIgnoreCase("s2b") || mode.equalsIgnoreCase("small-to-big"));
    }
}
