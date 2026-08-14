Status: open
Recommendation strength: Worth exploring

# DocumentIngestionService narrates a partial-failure mode that GlobalExceptionHandler never catches

## Files
- `rag-benchmark/src/main/java/com/dippa/ragbenchmark/application/DocumentIngestionService.java` (lines 61-69)
- `rag-benchmark/src/main/java/com/dippa/ragbenchmark/api/GlobalExceptionHandler.java`

## Problem
`DocumentIngestionService.ingest` wraps a `vectorStore.add` failure into a
hand-crafted `IllegalStateException` naming exactly which `documentId`/`chunkIds` may
have been partially persisted (chunk ids are deliberately generated before insertion
so this diagnostic is possible). `GlobalExceptionHandler` has no handler for
`IllegalStateException`, so it becomes an anonymous Spring Boot default 500 and the
diagnostic never reaches the HTTP caller — only server logs. The module that detects
and describes the failure is not the module responsible for presenting it, and the two
are out of sync. Also untested: no test makes `vectorStore.add` throw.

## Proposed solution
Add a dedicated `ChunkPersistenceException` (carrying `documentId`/`chunkIds`) thrown
from `DocumentIngestionService`, and a matching `@ExceptionHandler` in
`GlobalExceptionHandler` that surfaces it in the response body. Add a test that stubs
`vectorStore.add` to throw and asserts the exception carries the right fields.

## Benefits
Locality (failure-description and presentation live as a matched pair), leverage —
relevant once TODO.md's MuSiQue batch ingestion lands and partial failures during a
batch become far more likely than with today's single-document endpoint.
