# rag-benchmark

A local-first Spring Boot application for benchmarking Retrieval-Augmented Generation
(RAG) optimization techniques, built as the implementation artifact for a master's
thesis. It exposes a small REST API to ingest documents into a pgvector-backed store
and to run a RAG query pipeline against them, against either a fully local model stack
(Ollama) or a cloud one (Google Gemini via Spring AI's Google GenAI integration),
switchable with a single environment variable. The current version implements only the
**M0 baseline** pipeline (retrieve top-K chunks, generate an answer); the four
optimization modules studied in the thesis (M1-M4, see below) exist as named seams in
the domain model but are not implemented yet. The design and research questions this
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

Requesting an optimization module that isn't implemented yet (`modules` accepts any of
`M1`, `M2`, `M3`, `M4`) returns `501 Not Implemented` with a body describing which
module(s) were rejected, instead of silently ignoring the request:

```
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{"query": "What is the capital of France?", "modules": ["M2"]}'
```

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

For local development you need something realistic in `vector_store_test` to query against.
`com.dippa.ragbenchmark.ingest.nordea` is a one-off admin tool that crawls
[nordea.fi/private-banking](https://www.nordea.fi/private-banking/) (bounded to that path prefix,
same host), extracts text from any linked PDFs, chunks everything with the same 512-token
splitter `DocumentIngestionService` uses for the M0 baseline, and writes the chunks into
`vector_store_test`.

It's disabled by default, so a normal `mvn spring-boot:run` never triggers a live crawl. Run it
explicitly with:

```
mvn spring-boot:run -Dspring-boot.run.arguments=--ingest.nordea.enabled=true
```

The app starts normally, runs the crawl/ingest once on startup, logs a summary (pages crawled,
PDFs extracted, chunks written), and then keeps running as usual — stop it with Ctrl-C once the
summary line appears if you don't need the rest of the app up.

Notes:

- **Data persists across restarts already** — `vector_store_test` lives in the `postgres`
  service's named Docker volume (`rag-benchmark-pgdata` in `docker-compose.yml`), which survives
  `mvn spring-boot:run` being stopped/restarted. It's only gone if you explicitly run
  `docker compose down -v` or delete the volume.
- **Safe to re-run.** Each chunk's id is deterministic (derived from its source URL + chunk
  index), so re-running this tool overwrites the same rows instead of duplicating them.
- **Only extracted plain text ever touches disk.** Each page/PDF's cleaned text is staged in a
  temp file under the OS temp dir before chunking; raw HTML/PDF bytes are never written to disk,
  and the whole staging directory is deleted once ingestion finishes (success or failure).
- Crawl scope, page/PDF caps, request delay, and the crawler's `User-Agent` string are all
  configurable under `ingest.nordea.*` in `application.yml`.
- Nordea's exact page DOM wasn't available to hand-verify ahead of time, so the nav/footer/
  cookie-banner stripping in `NordeaCrawler` is a best-effort heuristic — spot-check a few
  ingested chunks after a run and tune the CSS selectors there if you see leftover site chrome.

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

## What's implemented (M0) vs. stubbed (M1-M4)

| Module | Status |
|---|---|
| M0 — baseline retrieve-then-generate pipeline | Implemented (`RagQueryService`) |
| M1 — small-to-big retrieval expansion | Not implemented; requesting it returns `501` |
| M2 — re-ranking | Not implemented; a `Reranker` port exists with a `NoOpReranker` default; requesting it via the API returns `501` |
| M3 — prompt / context caching | Not implemented; requesting it returns `501` |
| M4 — model routing / cascading | Not implemented; a `ModelRouter` port exists but has no implementation wired up; requesting it returns `501` |

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
flash / pro) and M2's two candidate re-ranking backends. The typed record itself isn't
consumed by any bean yet, since M2 and M4 are unimplemented (see table above), but the
catalog's `flash` entry is the source of truth for `spring.ai.google.genai.chat.model`
— the one model Spring AI actually instantiates a bean for today — via a nested
`${GOOGLE_GENAI_CHAT_MODEL:${rag.models.google-genai.flash}}` placeholder in
`application.yml`. `flash` doubles as M0's base model and the tier M4 will treat as its
medium-complexity/fallback tier once routing exists; `GOOGLE_GENAI_CHAT_MODEL` still
overrides it independently for ad hoc local runs.

This keeps the thing being benchmarked — the pipeline and its optimization seams — as
plain, framework-light domain/application code that can be tested and reasoned about
without spinning up the web layer, while still being an ordinary Spring Boot app at
the edges.
