# Coding Agent Guidelines: RAG Benchmark

A local-first Spring Boot 4 + Spring AI testbed evaluating trade-offs between latency, cost, and quality across 4 RAG optimization modules against a naive baseline. Derived from [`thesis/chapters/04_research_methods.tex`](/home/eitsei/dippa/thesis/chapters/04_research_methods.tex).

---

## 1. System Architecture & Tech Stack

- **Runtime & Framework:** Java 21 LTS, Spring Boot 4.1.0, Spring AI 2.0.0.
- **Database & Vector Store:** PostgreSQL 16 + `pgvector` 0.8.0.
  - HNSW index with Cosine Distance (`<=>`), 768 dimensions.
  - Search tuning: `hnsw.ef_search = 40` (target <5ms retrieval).
- **Embedding Model:** `gemini-embedding-2` with Matryoshka dimensionality 768 (`spring.ai.google.genai.embedding.text.dimensions: 768`). Local fallback: `nomic-embed-text`.
- **Model Tiers:**
  - **Tier 1 (Lite / Router):** `gemini-3.5-flash-lite` (query complexity classification & 1-hop queries).
  - **Tier 2 (Workhorse / Baseline):** `gemini-3.7-flash` ($M_0$ baseline & 2-hop queries).
  - **Tier 3 (Pro Reasoning):** `gemini-3.1-pro` (complex 3/4-hop queries & fallback).
- **Reranker:** GCP Vertex AI Ranking API (`semantic-ranker-default-004`). Local fallback: TEI cross-encoders (`deberta-v3-large`).

---

## 2. Codebase Layering & Seams

Keep clean boundaries between domain logic, orchestration, and provider infrastructure:

```
com.dippa.ragbenchmark
├── domain/            # Pure Java. ZERO Spring AI / Web imports.
│   ├── ModuleId.java  # M1-M4 vocabulary
│   ├── RetrievalExpander.java  # M1 port
│   ├── Reranker.java           # M2 port (default: NoOpReranker)
│   ├── PromptCache.java        # M3 port
│   └── ModelRouter.java        # M4 port
├── application/       # Pipeline orchestration & DTOs
│   ├── RagQueryService.java    # Coordinates retrieval, reranking, generation
│   └── DocumentIngestionService.java
├── infrastructure/    # Adapters implementing domain ports & config properties
│   └── config/ModelCatalogProperties.java
└── api/               # REST controllers & GlobalExceptionHandler
```

### Key Rules:
1. **Domain Purity:** Keep `domain/` free of Spring/Web dependencies.
2. **Prompts:** Store prompts in `src/main/resources/prompts/*.st` (StringTemplate), never hardcoded in Java.
3. **Properties:** Bind config via `@ConfigurationProperties` under `rag.*`.

---

## 3. Optimization Modules ($M_0$–$M_4$) & Key Logic

| Module | Name | Key Parameters & Critical Logic |
|---|---|---|
| **$M_0$** | **Baseline** | 512-token chunks, 50 overlap, top-$k=5$, no reranking, Gemini 3.7 Flash. |
| **$M_1$** | **Small-to-Big** | Child chunks (128 tokens) expand to parent windows (1024 tokens). **Must merge overlapping parent boundaries** to prevent redundant context duplication. |
| **$M_2$** | **Re-ranking** | Vector search retrieves top-50 candidates $\to$ cross-encoder reranks down to top-5. **In $M_1+M_2$ pipelines**: rerank 128-token child chunks *before* expanding to parent windows (reduces cross-encoder token load by $8\times$). |
| **$M_3$** | **Prompt Caching** | Dual-tier: Explicit Context Caching for static context $\ge 4096$ tokens + dynamic KV prefix reuse. Track cold-write vs warm-read latency. |
| **$M_4$** | **Model Cascading** | Zero-shot query router (Flash-Lite) routes 1-hop $\to$ Flash-Lite, 2-hop $\to$ Flash, 3/4-hop $\to$ Pro. Include router latency ($T_{\text{route}}$) and token costs in accounting. |

---

## 4. Experimental Matrix & Execution Protocol

- **16 Configurations ($2^4$):** Baseline $C_0$, single modules ($C_1$–$C_4$), pairwise pairs ($C_5$–$C_{10}$), triplets ($C_{11}$–$C_{14}$), full-stack $C_{15}$ ($M_1+M_2+M_3+M_4$).
- **Datasets:** MuSiQue (academic multi-hop) & Synthetic Customer Support (enterprise single/multi-hop + 20% out-of-domain refusals: *"The provided context does not contain sufficient information to answer this query."*).
- **Execution Protocol:**
  - Run **2 unmeasured warm-up queries** before recording to prime JVM JIT, connection pools, and TLS.
  - Record **10 steady-state iterations** reporting $P_{50}$ (median) and $P_{95}$ (tail).
  - Clear/isolate caches between configuration runs.

---

## 5. Metrics & Testing

- **Telemetry (RQ1 & RQ2):** Micrometer timers for micro-phases ($T_{\text{embed}}, T_{\text{route}}, T_{\text{ret}}, T_{\text{rerank}}, T_{\text{expand}}, T_{\text{cache}}, T_{\text{stream}}$). Time to First Token $\text{TTFT} = T_{\text{total}} - T_{\text{stream}}$. Token costs reflect a 90% discount on cached tokens.
- **Quality (RQ3):** RAGAS metrics evaluated offline with Claude Sonnet 5 ($T=0.0$) as independent judge.
- **Testing:** Unit test all logic with Mockito (`mvn test`). Use Testcontainers for pgvector integration tests.
