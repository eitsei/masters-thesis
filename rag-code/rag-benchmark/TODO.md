# TODO

Scope tracking beyond the initial M0 baseline. M1 (Small-to-Big), M2 (Semantic Re-ranking),
and M3 (Prompt / Context Caching) are now implemented; remaining items are active starting points for ongoing work.

- **Real dataset ingestion (MuSiQue).** v1 ships no sample data and no ingestion
  pipeline for a benchmark dataset — `POST /api/documents` only accepts one raw text
  string at a time. The next step is loading the
  [MuSiQue](https://github.com/StonyBrookNLP/musique) multi-hop QA benchmark dataset
  and batch-ingesting it into the pgvector store.
- **M1 — small-to-big retrieval.** Implemented through `RetrievalExpander` (`domain/RetrievalExpander.java`), including explicit parent-expansion failures and ranked parent deduplication. Integration benchmarking and full overlap-aware ingestion remain follow-up work.
- **M2 — re-ranking.** Implemented via `VertexAiReranker` (`infrastructure/VertexAiReranker.java`) using GCP Vertex AI / Discovery Engine Ranking API (`semantic-ranker-default-004`), with `NoOpReranker` retained for baseline and non-M2 queries. Includes candidate pool retrieval (50 items), top-N ranking, metadata score attachment (`rerank_score`), and telemetry (`rag.pipeline.rerank`). Local TEI cross-encoder fallback remains a future addition.
- **M3 — prompt/context caching.** Implemented via `GoogleGenAiPromptCache` (`infrastructure/GoogleGenAiPromptCache.java`) and `RagPromptRenderer` (`application/RagPromptRenderer.java`). Supports Google GenAI explicit Context Caching via `CachedContentService` for contexts >= 4096 tokens, thread-safe single-flight concurrency, TTL lifecycle tracking, owned resource cleanup (`POST /api/benchmark/cache/clear`), token usage accounting with cache-read/cache-write metrics, and timing telemetry (`rag.pipeline.cache`). `NoOpPromptCache` serves non-cached configurations.
- **M4 — model routing / cascading.** Implemented via `GoogleGenAiModelRouter` (zero-shot Gemini 3.5 Flash-Lite complexity classifier), `OracleModelRouter` (deterministic hop-count mapping for empirical ceiling benchmarks), and `NoOpModelRouter`. Supports dynamic routing across Gemini tiers (`gemini-3.5-flash-lite`, `gemini-3.8-flash`, `gemini-3.1-pro`), $T_{\text{route}}$ timing telemetry, prompt/completion router token accounting in `TokenUsageDto`, model-specific M3 prompt caching binding, and fallback resilience. Configuration managed via `rag.router.provider` (`noop`, `classifier`, `oracle`).
- **RAGAS quality evaluation (RQ3).** No answer-quality evaluation harness exists yet
  (faithfulness, answer relevancy, context precision/recall, etc.).
- **Cost accounting (RQ2).** No cost-per-query formula or tracking exists yet (token
  counts are available via Spring AI's usage metadata, but nothing aggregates them into
  a cost figure per provider/model).
- **Batch benchmark-runner endpoint.** There is no endpoint or job for running a full
  dataset (once ingested) through the pipeline end-to-end and collecting
  timings/quality/cost metrics in one pass — today each query is issued one at a time
  via `POST /api/query`.
- **Integration tests against real Ollama/Gemini APIs.** The current test suite
  (`DocumentIngestionServiceTest`, `RagQueryServiceTest`, `NoOpRerankerTest`) is unit
  tests only, with `VectorStore`/`ChatModel` mocked out — there is no test that
  actually talks to a running Ollama instance or the Gemini API, and no Testcontainers
  setup for spinning up a real pgvector container in tests.
