Status: open
Recommendation strength: Worth exploring

# The HTTP translation seam has zero test coverage, and its exception handler is broader than its intent

## Files
- `rag-benchmark/src/main/java/com/dippa/ragbenchmark/api/GlobalExceptionHandler.java` (lines 31-35)
- `rag-benchmark/src/main/java/com/dippa/ragbenchmark/api/QueryController.java` (lines 53-71: `parseModules`/`toModuleId`)
- `rag-benchmark/src/main/java/com/dippa/ragbenchmark/api/DocumentController.java`
- (absence of) any `*ControllerTest` under `rag-benchmark/src/test`

## Problem
All three existing tests (`DocumentIngestionServiceTest`, `RagQueryServiceTest`,
`NoOpRerankerTest`) sit in `application`/`infrastructure` — nothing under `api/` is
exercised. `GlobalExceptionHandler.handleIllegalArgument` catches the generic
`java.lang.IllegalArgumentException`, not a project-specific type, so it will also
swallow any unrelated future `IllegalArgumentException` — its interface (which errors
it claims responsibility for) is wider than its actual intent.

## Proposed solution
Add `@WebMvcTest`/MockMvc tests for `QueryController`, `DocumentController`, and
`GlobalExceptionHandler` (unknown module → 400, any-case module name → 501, ingest
happy path → 201). Consider a narrower `InvalidModuleException` thrown from
`QueryController.toModuleId` instead of the generic exception, so the handler's scope
matches its job.

## Benefits
Testability (closes the one zero-coverage seam), locality (narrower exception keeps
"bad module name" handling from absorbing unrelated bugs later).
