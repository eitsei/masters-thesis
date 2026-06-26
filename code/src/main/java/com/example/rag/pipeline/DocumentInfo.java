package com.example.rag.pipeline;

import java.util.Map;

public record DocumentInfo(
    Map<String, Object> metadata
) {}
