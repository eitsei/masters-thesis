# rag-benchmark

A local-first Spring Boot application for benchmarking Retrieval-Augmented Generation
(RAG) optimization techniques, built as the implementation artifact for a master's
thesis. It exposes a small REST API to ingest documents into a pgvector-backed store
and to run a RAG query pipeline against them, against either a fully local model stack
(Ollama) or a cloud one (Google Gemini via Spring AI's Google GenAI integration),
switchable with a single environment variable. The current version implements the
**M0 baseline** pipeline along with **M1 (small-to-big retrieval expansion)**, **M2 (semantic re-ranking)**,
**M3 (prompt / context caching)**, and **M4 (model routing / cascading)**. The design and research questions this
project supports are documented in
[`/home/eitsei/dippa/thesis/chapters/04_research_methods.tex`](/home/eitsei/dippa/thesis/chapters/04_research_methods.tex).

## Prerequisites

- Java 21
- Maven (plain `mvn` — this project does not use the Maven wrapper)
- Docker and Docker Compose
- [Ollama](https://ollama.com/), with the default models pulled:
  ```
  ollama pull gemma4:e2b
  ollama pull nomic-embed-text
  ```
  **WSL2 note**: if Ollama runs on the Windows side rather than inside WSL2,
  `OLLAMA_BASE_URL=http://localhost:11434` (the default in `.env.example`) will not
  reach it — the `ollama` CLI itself may be pointed elsewhere via `OLLAMA_HOST` and
  not tell you. Check `echo $OLLAMA_HOST`, or point `OLLAMA_BASE_URL` at the WSL2
  default gateway instead: `http://$(ip route show | grep default | awk '{print $3}'):11434`.

## Quickstart

```
git clone <this repo>
cd rag-benchmark
cp .env.example .env
export $(grep -v '^#' .env | xargs)   # see .env.example for why this step is needed
mvn spring-boot:run
```

`mvn spring-boot:run` starts the app, and the `spring-boot-docker-compose` dependency
automatically starts `docker-compose.yml` (a Postgres + pgvector container) alongside
it and wires up the datasource connection — you do not need to run `docker compose up`
yourself, and you do not need to set any `DB_HOST`/`DB_USER`/`DB_PASSWORD` variables.
See the comments in `docker-compose.yml` and `application.yml` for how that
auto-detection works.

### Example requests

Ingest a document:

```
curl -X POST http://localhost:8080/api/documents \
  -H "Content-Type: application/json" \
  -d '{
        "text": "Paris is the capital of France.",
        "metadata": {"source": "example"}
      }'
```

Run a query against the M0 baseline pipeline:

```
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{"query": "What is the capital of France?"}'
```

Requesting optimization modules (`modules` accepts any combination of `M1`, `M2`, `M3`, `M4`)
enables the corresponding pipeline stages:

```
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{"query": "What is the capital of France?", "modules": ["M2", "M4"]}'
```

If an optimization module is requested while its backing provider is inactive or unconfigured
(for example, requesting `M4` with `rag.router.provider=noop`, `M2` with `rag.reranker.provider=noop`,
or `M1` without a valid parent table), the API returns `400 Bad Request` with a descriptive error code
(`router_not_configured`, `reranker_not_configured`, `prompt_cache_not_configured`, or `parent_expansion_failed`)
rather than silently ignoring the requested optimization. If an unsupported module identifier is supplied,
the service returns `501 Not Implemented`.

### Retrieved-chunk snippet length and top-K

Each entry in `POST /api/query`'s `retrievedChunks` array has a `snippet` field — a preview of
that chunk's text, controlled by `rag.query.snippet-length-chars` in `application.yml`. It
defaults to `2147483647` (`Integer.MAX_VALUE`), i.e. no truncation: the full chunk text is shown.
This is display-only — retrieval and generation always use the complete chunk regardless of this
setting. To get short previews back instead (e.g. for more compact terminal/Postman output),
lower it:

```yaml
rag:
  query:
    snippet-length-chars: 200
```

`rag.query.top-k` (default `5`) controls how many chunks are retrieved from `vector_store_test`
per query. Since the M0 baseline's `Reranker` is a no-op, this is also how many chunks reach the
prompt and how many entries `retrievedChunks` has:

```yaml
rag:
  query:
    top-k: 10
```

## Local test data: Nordea site ingestion

For local benchmarking you need realistic banking content in the vector store (`vector_store_nordea` or `vector_store_nordea_large`/`small`).
The package `com.dippa.ragbenchmark.ingest.nordea` provides a comprehensive pipeline that crawls [nordea.fi/henkiloasiakkaat/](https://www.nordea.fi/henkiloasiakkaat/), extracts text from discovered PDFs, exports clean Markdown to disk (`data/nordea/md/`), strips boilerplate noise, and writes chunks to PgVector (either 512-token baseline chunks or S2B 1024-token parent / 128-token child pairs).

It is disabled by default so a normal `mvn spring-boot:run` never triggers an automatic crawl. You can control the pipeline via the REST API or run the crawler on startup.

### 1. Triggering via REST API (`POST /api/ingest/nordea`)

Once the app is running (`mvn spring-boot:run`), you can execute any stage of the pipeline dynamically:

```bash
# Ingest already downloaded & cleaned files directly from disk into S2B tables (fastest, no web crawling):
curl -X POST http://localhost:8080/api/ingest/nordea \
  -H "Content-Type: application/json" \
  -d '{"action": "ingest", "mode": "s2b", "clearDatabase": true}'

# Clean existing markdown files on disk without crawling or re-indexing:
curl -X POST http://localhost:8080/api/ingest/nordea \
  -H "Content-Type: application/json" \
  -d '{"action": "clean"}'

# Full end-to-end pipeline from a clean slate (crawl -> clean -> ingest):
curl -X POST http://localhost:8080/api/ingest/nordea \
  -H "Content-Type: application/json" \
  -d '{"action": "all", "clearDatabase": true, "clearFiles": true, "mode": "s2b"}'
```

**Request Payload Options (`NordeaIngestRequest`):**
* `mode` (default `"s2b"`): `"s2b"` (stores to `vector_store_nordea_large` and `_small`) or `"original"` (stores to `vector_store_nordea`).
* `action` (default `"all"`):
  * `"ingest"`: Chunks and writes existing Markdown files from `data/nordea/md/` to PgVector without fetching from the web.
  * `"clean"`: Cleans noise from Markdown files on disk.
  * `"crawl"`: Crawls pages and PDFs, converting to Markdown on disk without database writes.
  * `"all"`: Runs full pipeline in sequence.
* `clearDatabase` (default `false`): Truncates target vector store tables before ingestion.
* `clearFiles` (default `false`): Deletes existing `.md` files in `data/nordea/md/pages` and `pdfs` before a new crawl.

### 2. Standalone Python Cleaning Script

You can also inspect or clean the Markdown corpus directly from the terminal without running the Spring server:

```bash
# Preview modifications (dry-run, shows affected files, metrics, and diff preview):
python3 data/nordea/clean_nordea_markdown.py --dry-run

# Clean files in-place with automatic .bak backups:
python3 data/nordea/clean_nordea_markdown.py --in-place --backup
```

**Automated Noise Filtering:**
* Strips video/marketing cookie placeholders (`Hups! Jos haluat nähdä tällaista sisältöä, hyväksy ensin Nordean markkinointievästeet`).
* Removes screen reader artifacts (`Avautuu uuteen ikkunaan`) from headings and link prose.
* Strips the repeated contact footer (`## Apua asiointiin ... - Nordea Omaposti`) across pages while preserving actual contact information on the dedicated contact page.
* Strictly preserves YAML frontmatter (`url`, `title`, `type`) and document structure.

### 3. Resilience & Retries
Both `NordeaCrawler` and `NordeaPdfExtractor` feature automatic 3x retries with exponential backoff (1s, 2s, 4s) to survive transient network or HTTP 5xx errors during live crawls.

### 4. Running on Startup (Alternative)

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--ingest.nordea.enabled=true
```

## Multi-hop benchmark dataset: MuSiQue

The project includes full integration with the **MuSiQue** multi-hop QA benchmark dataset for evaluating retrieval recall and answer generation across 2-hop, 3-hop, and 4-hop questions.

For full instructions, REST API endpoints, and evaluation workflows, see [`MUSIQUE_BENCHMARK.md`](MUSIQUE_BENCHMARK.md).

## Switching to the cloud (Gemini) path

By default, both chat and embeddings run against local Ollama. To use Google Gemini
instead, set (see `.env.example` for the full list and Vertex AI notes):

```
SPRING_AI_MODEL_CHAT=google-genai
SPRING_AI_MODEL_EMBEDDING=google-genai
GOOGLE_AI_API_KEY=<your key from https://aistudio.google.com/apikey>
```

then export the updated `.env` and re-run `mvn spring-boot:run`. No code or config
file changes are needed for this switch — only environment variables.

## What's implemented (M0-M4)

| Module | Status |
|---|---|
| M0 — baseline retrieve-then-generate pipeline | Implemented (`RagQueryService`) |
| M1 — small-to-big retrieval expansion | Implemented with child-table retrieval, parent-table expansion, ranked deduplication, and explicit failure responses |
| M2 — re-ranking | Implemented via `VertexAiReranker` (`semantic-ranker-default-004`), retrieving a candidate pool of 50 chunks, re-ranking down to top-N, and attaching score metadata (`rerank_score`); `NoOpReranker` serves the baseline |
| M3 — prompt / context caching | Implemented via `GoogleGenAiPromptCache` and `CachedContentService` for contexts meeting the 4096-token minimum threshold with single-flight concurrency, TTL lifecycle, and clear-down endpoint; `NoOpPromptCache` serves non-cached configurations |
| M4 — model routing / cascading | Implemented via `GoogleGenAiModelRouter` (Gemini 3.5 Flash-Lite zero-shot complexity classifier), `OracleModelRouter` (hop-count deterministic baseline), and `NoOpModelRouter` with fallback resilience, model option propagation, and timing/token telemetry |

See `TODO.md` for what's deferred beyond the module stubs (dataset ingestion,
evaluation, cost accounting, etc).

## Running tests

```
mvn test
```

This runs only unit tests with mocked collaborators (Mockito) — no real network calls
to Ollama or Gemini are made, and there is no Testcontainers-based integration test
suite yet (see `TODO.md`).

## Architecture

The code is organized in layers rather than as a typical Spring MVC
controller/service/repository stack, because this is a pipeline/API service (a
benchmark harness), not a UI-backing application — the layering exists to keep the
RAG pipeline logic and the M1-M4 extension seams testable and swappable independently
of both the HTTP layer and the concrete model/vector-store providers:

- `domain` — the vocabulary and ports of the RAG pipeline itself: `ModuleId` (the
  M1-M4 vocabulary), and the `Reranker`, `RetrievalExpander`, `PromptCache`,
  `ModelRouter` seams that the optimization modules will implement. No Spring AI or
  web dependencies here.
- `application` — orchestrates the domain ports into the actual M0 pipeline
  (`RagQueryService`, `DocumentIngestionService`) and defines the results/DTOs they
  return. Depends on Spring AI's `VectorStore`/`ChatModel` abstractions, not on any
  specific provider (Ollama vs. Gemini is an application.yml concern).
- `infrastructure` — concrete adapters for the domain ports (e.g. `NoOpReranker`,
  the current M2 stand-in) and cross-cutting config, e.g. `ModelCatalogProperties`.
- `api` — the HTTP surface (`DocumentController`, `QueryController`), request/response
  DTOs, and `GlobalExceptionHandler` translating domain exceptions (like
  `ModuleNotImplementedException`) into HTTP status codes.

### Prompts

Prompt text lives under `src/main/resources/prompts/` (one file per prompt, `.st`
extension — Spring AI's StringTemplate placeholder syntax) rather than as inline Java
string literals, and is loaded via Spring AI's `PromptTemplate(Resource)` constructor.
`answer-generation.st` is the only one today (M0's retrieve-then-generate prompt);
M1-M4 will each add their own file here rather than growing this one, since they'll
need different shapes (e.g. M3 cache markers, M1 expanded-window context).

### Model catalog
 
 `ModelCatalogProperties` (`infrastructure/config`) binds `rag.models.*` in
 `application.yml` — the three Gemini tiers from the thesis's M4 design (flash-lite /
 flash / pro) and M2's candidate re-ranking backends. Its `vertex-default` entry provides
 the default ranking model (`semantic-ranker-default-004`) for `rag.reranker.model`. The
 catalog's `flash` entry is also the source of truth for `spring.ai.google.genai.chat.model`
 — the one chat model Spring AI actually instantiates a bean for today — via a nested
 `${GOOGLE_GENAI_CHAT_MODEL:${rag.models.google-genai.flash}}` placeholder in
 `application.yml`. `flash` doubles as M0's base model and the tier M4 will treat as its
 medium-complexity/fallback tier once routing exists; `GOOGLE_GENAI_CHAT_MODEL` still
 overrides it independently for ad hoc local runs.

This keeps the thing being benchmarked — the pipeline and its optimization seams — as
plain, framework-light domain/application code that can be tested and reasoned about
without spinning up the web layer, while still being an ordinary Spring Boot app at
the edges.
