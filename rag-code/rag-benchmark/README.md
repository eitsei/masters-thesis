# rag-benchmark

A Spring Boot benchmark service for evaluating Retrieval-Augmented Generation (RAG) optimization techniques, developed as the implementation artifact for a Master's thesis. The application exposes a REST API for document ingestion into PostgreSQL with `pgvector` and for executing configurable RAG query pipelines against either a local model stack (Ollama) or a cloud stack (Google Gemini / Vertex AI via Spring AI), selectable via environment variables.

The service implements the **M0** baseline pipeline alongside four optimization modules: **M1** (small-to-big retrieval expansion), **M2** (semantic re-ranking), **M3** (prompt and context caching), and **M4** (model routing and cascading). The experimental design and research questions are documented in [`thesis/chapters/04_research_methods.tex`](../../thesis/chapters/04_research_methods.tex).

## Prerequisites

- **Java 21** and **Maven** (`mvn`)
- **Docker** and **Docker Compose** (for the `pgvector` database container)
- **Local execution (default):** [Ollama](https://ollama.com/) with the default models pulled:
  ```bash
  ollama pull gemma4:e2b
  ollama pull nomic-embed-text
  ```
  *Note for WSL2 environments:* if Ollama runs on the Windows host rather than inside WSL2, set `OLLAMA_BASE_URL` to the WSL2 default gateway (e.g., `http://$(ip route show | grep default | awk '{print $3}'):11434`).
- **Cloud execution (optional):** Google AI Studio API key (`GOOGLE_AI_API_KEY`) or Google Cloud Project with Vertex AI / Discovery Engine credentials (`gcloud auth application-default login`).

## Quickstart

```bash
cd rag-code/rag-benchmark
cp .env.example .env
export $(grep -v '^#' .env | xargs)
mvn spring-boot:run
```

Spring Boot does not load `.env` files natively, so variables must be exported into the process environment (or configured via `direnv` or an IDE run configuration). The `spring-boot-docker-compose` dependency automatically starts `docker-compose.yml` (`pgvector/pgvector:0.8.6-pg16`, mapped to host port `5433`) and configures the datasource connection without manual `DB_HOST`, `DB_USER`, or `DB_PASSWORD` variables.

### Switching to Google GenAI / Vertex AI

By default, `application.yml` configures `ollama` for chat and embeddings. To switch to Google Gemini, set:

```bash
SPRING_AI_MODEL_CHAT=google-genai
SPRING_AI_MODEL_EMBEDDING=google-genai
GOOGLE_AI_API_KEY=<your-api-key>
```

For Vertex AI billing instead of an AI Studio key, set `GOOGLE_CLOUD_PROJECT_ID` and `GOOGLE_CLOUD_LOCATION=global` (see `.env.example` and `src/main/resources/application.yml` for full provider options).

## REST API & Usage

### Core Endpoints

| Endpoint | Description |
|---|---|
| `POST /api/documents` | Ingest a single document into the active vector store |
| `POST /api/query` (alias `/api/v1/query`) | Execute a RAG query with optional optimization `modules` (`M1`–`M4`) |
| `POST /api/ingest/enterprise` | Crawl, clean, or ingest the enterprise web/PDF corpus (`s2b` or `original`) |
| `POST /api/ingest/enterprise-closed` | Ingest the closed-corpus enterprise evaluation dataset |
| `GET/POST /api/benchmark/musique/*` | Inspect, ingest, and query the MuSiQue multi-hop QA benchmark |
| `POST /api/benchmark/cache/prewarm` | Pre-warm explicit context cache entries for M3 benchmarking |
| `POST /api/benchmark/cache/clear` | Clear cached context entries |

### Example Requests

Ingest a document:

```bash
curl -X POST http://localhost:8080/api/documents \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Paris is the capital of France.",
    "metadata": {"source": "example"}
  }'
```

Execute an **M0** baseline query:

```bash
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{"query": "What is the capital of France?"}'
```

Execute a query with optimization modules enabled (`modules` accepts any combination of `M1`, `M2`, `M3`, `M4`):

```bash
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{"query": "What is the capital of France?", "modules": ["M2", "M4"]}'
```

### Error Handling & Query Configuration

If an optimization module is requested while its backing provider is unconfigured or inactive, `GlobalExceptionHandler` returns an explicit error response rather than silently falling back:

- `400 Bad Request`: `reranking_not_configured`, `prompt_cache_not_configured`, `router_not_configured`, or `bad_request`
- `502 Bad Gateway`: `parent_expansion_failed`, `reranking_failed`, or `routing_failed`
- `503 Service Unavailable`: `prompt_cache_unavailable`
- `501 Not Implemented`: `module_not_implemented`

Key query parameters in `src/main/resources/application.yml`:
- `rag.query.top-k` (default `5`): number of chunks retrieved from the active vector store (default table: `vector_store_musique`).
- `rag.query.snippet-length-chars` (default `200`): display-only truncation length for `retrievedChunks[].snippet` in API responses; retrieval and prompt construction always use full chunk text.

## Datasets & Ingestion

### Enterprise Corpus Ingestion

The `com.dippa.ragbenchmark.ingest.enterprise` package provides an ingestion pipeline that crawls target pages and PDFs, converts them to Markdown under `data/enterprise/md/`, filters boilerplate noise, and writes chunks to `pgvector` (512-token baseline chunks in `vector_store_enterprise`, or 1024-token parent / 128-token child chunks in `vector_store_enterprise_large` and `vector_store_enterprise_small`). Automatic startup ingestion is disabled by default (`ingest.enterprise.enabled=false`).

Trigger stages via `POST /api/ingest/enterprise`:

```bash
# Ingest cleaned Markdown files from disk into S2B tables
curl -X POST http://localhost:8080/api/ingest/enterprise \
  -H "Content-Type: application/json" \
  -d '{"action": "ingest", "mode": "s2b", "clearDatabase": true}'

# Run the full pipeline (crawl -> clean -> ingest)
curl -X POST http://localhost:8080/api/ingest/enterprise \
  -H "Content-Type: application/json" \
  -d '{"action": "all", "clearDatabase": true, "clearFiles": true, "mode": "s2b"}'
```

`EnterpriseIngestRequest` supports `mode` (`"s2b"` default, or `"original"`), `action` (`"all"`, `"crawl"`, `"clean"`, `"ingest"`), `clearDatabase`, and `clearFiles`. Both `EnterpriseCrawler` and `EnterprisePdfExtractor` include 3x exponential-backoff retries (1s, 2s, 4s). Standalone Markdown cleaning can also be run via:

```bash
python3 data/enterprise/clean_nordea_markdown.py --dry-run
python3 data/enterprise/clean_nordea_markdown.py --in-place --backup
```

### MuSiQue Multi-Hop Benchmark

The application includes integration with the **MuSiQue** multi-hop QA dataset (2-hop, 3-hop, 4-hop, and unanswerable splits). Paragraphs can be ingested into `vector_store_musique` (and `_large`/`_small` for S2B) via `./scripts/ingest_musique.sh` or `POST /api/ingest/musique`.

## Optimization Modules (M0–M4)

| Module | Description | Implementation |
|---|---|---|
| **M0** | Baseline retrieve-then-generate pipeline | `RagQueryService` |
| **M1** | Small-to-big retrieval expansion (128-token child retrieval, 1024-token parent expansion, ranked deduplication) | `PgVectorRetrievalExpander` |
| **M2** | Semantic re-ranking (50-candidate pool re-ranked to top-N with `rerank_score` metadata; `NoOpReranker` for baseline) | `VertexAiReranker` (`semantic-ranker-default-004`), `NoOpReranker` |
| **M3** | Explicit prompt/context caching (>= 4096-token threshold, single-flight concurrency, TTL lifecycle, pre-warm/clear endpoints) | `GoogleGenAiPromptCache` (`GoogleGenAiCachedContentService`), `NoOpPromptCache` |
| **M4** | Model routing and cascading (zero-shot complexity classifier, deterministic hop-count oracle, fallback resilience, telemetry) | `GoogleGenAiModelRouter`, `OracleModelRouter`, `NoOpModelRouter` |

## Architecture

The codebase follows a clean layered architecture to keep the RAG pipeline and optimization seams decoupled from HTTP transport and specific model or vector-store providers:

- `domain` — Core vocabulary (`ModuleId`, `ComplexityTier`, `RoutingMode`) and port interfaces (`Reranker`, `RetrievalExpander`, `PromptCache`, `ModelRouter`) with no Spring AI or web dependencies.
- `application` — Pipeline orchestration (`RagQueryService`, `DocumentIngestionService`), prompt rendering (`RagPromptRenderer`), and dataset/benchmark services (`MusiqueDatasetService`, `MusiqueBenchmarkQueryService`, `EnterpriseClosedDatasetService`).
- `infrastructure` — Concrete adapters for domain ports (`PgVectorRetrievalExpander`, `VertexAiReranker`, `GoogleGenAiPromptCache`, `GoogleGenAiModelRouter`, `OracleModelRouter`, and their `NoOp*` counterparts), dataset readers, and configuration properties (`ModelCatalogProperties`, `RouterProperties`, `RerankerProperties`, `PromptCacheProperties`, `GenerationProperties`).
- `api` — REST controllers, request/response DTOs, and `GlobalExceptionHandler`.

### Prompts & Model Catalog

Prompt templates are stored as Spring AI StringTemplate files under `src/main/resources/prompts/` (primarily `answer-generation.st`) and loaded via `PromptTemplate(Resource)`.

`ModelCatalogProperties` (`infrastructure/config`) binds `rag.models.*` in `application.yml`, defining the Gemini tiers (`flash-lite`: `gemini-3.5-flash-lite`, `flash`: `gemini-3.5-flash`, `pro`: `gemini-3.1-pro-preview`) and reranker backends (`semantic-ranker-default-004`, `semantic-ranker-fast-004`). `rag.models.google-genai.flash` serves as the default base model for `spring.ai.google.genai.chat.model` (overridable via `GOOGLE_GENAI_CHAT_MODEL`) and as the fallback tier for M4 routing.

## Testing

```bash
mvn test
```

This executes the unit and `@WebMvcTest` controller test suites using Mockito-mocked collaborators; no external network calls to Ollama, Google GenAI, or PostgreSQL are made during `mvn test`.
