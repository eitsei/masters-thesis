# Copilot instructions for `rag-benchmark`

## Build, run, and test

This is a Maven project; there is no Maven wrapper. Use Java 21 and plain `mvn`.

```bash
# Compile and run the unit-test suite
mvn test

# Run one test class
mvn -Dtest=RagQueryServiceTest test

# Run one test method
mvn -Dtest=RagQueryServiceTest#m1HappyPath_retrievesChildChunksAndExpandsToParentDocuments test

# Start the application
mvn spring-boot:run
```

`spring-boot-docker-compose` automatically starts the PostgreSQL/pgvector service from
`docker-compose.yml` when the application starts, so `docker compose up` is normally not
needed. The database data persists in the `rag-benchmark-pgdata` volume. Do not use
`docker compose down -v` unless deleting the local benchmark database is intentional.

The default path uses Ollama. Pull the models documented in `README.md` before running
the app. To use Google GenAI, set both `SPRING_AI_MODEL_CHAT=google-genai` and
`SPRING_AI_MODEL_EMBEDDING=google-genai`, and provide `GOOGLE_AI_API_KEY`. Spring Boot
does not load `.env` automatically; export variables into the process, for example:

```bash
cp .env.example .env
export $(grep -v '^#' .env | xargs)
```

There is no configured repository lint or format command. `mvn test` is the normal
validation command and the current tests are unit tests with mocked Spring AI
collaborators; they do not require live Ollama, Gemini, or Testcontainers.

## Architecture

The application is a local-first Spring Boot REST service for measuring a retrieve-then-
generate RAG pipeline against the thesis optimization modules.

- `domain` contains the RAG vocabulary and extension ports (`ModuleId`, `Reranker`,
  `RetrievalExpander`, `PromptCache`, and `ModelRouter`). Keep this layer free of Spring
  MVC and Spring AI provider types.
- `application` contains orchestration and result models. `RagQueryService` performs
  vector similarity search, invokes the reranker seam, optionally expands M1 child
  chunks to parent documents, renders the external prompt template, calls the abstract
  `ChatModel`, and records Micrometer phase timings/token usage. `DocumentIngestionService`
  uses Spring AI's `TokenTextSplitter` with 512-token chunks and persists them through
  `VectorStore`.
- `infrastructure` supplies provider/database adapters and configuration. The pgvector
  factory and query service can create cached stores for validated dynamic table names.
  `PgVectorRetrievalExpander` resolves `parent_id` metadata in PostgreSQL and preserves
  the ranking order of unique parent windows.
- `api` is the HTTP boundary. Controllers map request DTOs to application calls and
  `GlobalExceptionHandler` maps domain errors such as invalid modules, unsupported
  modules, and chunk persistence failures to stable HTTP responses.
- `ingest` contains one-off dataset/site ingestion tools, separate from the query
  pipeline. MuSiQue endpoints and workflows are documented in `MUSIQUE_BENCHMARK.md`.

The implemented baseline is M0. M1 is wired through the `RetrievalExpander` seam and
uses small/large vector-store table conventions when requested. M2, M3, and M4 remain
unsupported: requests containing them must produce `501 Not Implemented`, rather than
being silently ignored. The domain ports and model catalog are intentional seams for
future implementations.

Provider selection is configuration-driven in `src/main/resources/application.yml`.
The application uses Spring AI abstractions so Ollama and Google GenAI can be switched
without changing application code. `rag.models.*` is the typed model catalog; the
configured Google `flash` model is also the default cloud chat model. PostgreSQL uses
pgvector with HNSW/COSINE and 768-dimensional embeddings.

## Repository-specific conventions

- Keep prompt text in `src/main/resources/prompts/*.st` and load it with Spring AI
  `PromptTemplate`; do not embed prompt strings in Java.
- Bind grouped settings under `rag.*` using configuration properties where appropriate.
  Preserve the separate Google GenAI chat and embedding configuration blocks in
  `application.yml`; Spring AI 2.0 does not treat them as one connection.
- Treat `ModuleId` as the canonical M1–M4 vocabulary. Parse request module names
  case-insensitively through `ModuleId.parse`, and preserve the fail-fast unsupported
  module behavior.
- Dynamic PostgreSQL table names are interpolated into SQL, so validate them against the
  existing alphanumeric/underscore pattern before creating stores or queries. Values
  must be bound as SQL parameters; only an already-validated identifier may be placed
  in the SQL text.
- For M1, retrieve/rerank child chunks first, then expand to parent documents. Parent
  IDs come from `parent_id` (with `parentId` accepted as a compatibility alias);
  deduplicate parents while retaining highest-ranked child order and fall back to the
  child when a parent cannot be resolved.
- Keep query response snippets display-only. `snippet-length-chars` truncates returned
  previews; retrieval and prompt generation use complete document text.
- Unit tests are plain JUnit 5/AssertJ/Mockito tests. Prefer a real in-memory
  `SimpleMeterRegistry` when testing timer-instrumented services, and load classpath
  prompt resources with `ClassPathResource` when constructing services without a Spring
  context.
- Preserve the benchmark's telemetry names and phase separation when changing the
  pipeline (`rag.pipeline.retrieval`, `rag.pipeline.expansion`, and
  `rag.pipeline.generation` are currently used).
- Keep ingestion and query table selection explicit. The configured pgvector table and
  dataset-specific tables are not interchangeable; use the request/configuration table
  parameters rather than assuming one global store.

## Relevant project guidance

`AGENTS.md` is the authoritative architecture and experimental-design guidance for
this benchmark. `README.md` documents local setup, provider switching, API examples,
and the M0/M1–M4 implementation status. `MUSIQUE_BENCHMARK.md` documents the MuSiQue
dataset endpoints, canonical sample, ingestion options, and evaluation workflow.
