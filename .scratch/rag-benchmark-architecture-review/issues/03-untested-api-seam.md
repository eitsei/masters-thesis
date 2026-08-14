Status: resolved
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

## Resolution
Scoped narrow, per user decision (see "Explicitly deferred" below). Added
`InvalidModuleException` (`domain` package, next to `ModuleNotImplementedException`)
thrown from `QueryController.toModuleId`; `GlobalExceptionHandler` now handles that
narrow type instead of generic `IllegalArgumentException`. Added `QueryControllerTest`
(3 cases: unknown module → 400, case-insensitive valid module → 501, baseline happy
path → 200) and `DocumentControllerTest` (ingest happy path → 201), both `@WebMvcTest`
so they exercise the real `GlobalExceptionHandler` bean, not a mock of it.

**Discovered during implementation:** Spring Boot 4 split `@WebMvcTest` out of
`spring-boot-test-autoconfigure` into a new `spring-boot-webmvc-test` module (package
also moved to `org.springframework.boot.webmvc.test.autoconfigure`) — had to add that
dependency to `pom.xml`, it wasn't pulled in by `spring-boot-starter-test` alone.

**Explicitly deferred (scope discussion with user):** the fact that validation
failures (blank `text`/`query`) and malformed JSON return a *different* JSON error
shape (Spring's default `{"timestamp","status","error","path"}`) than domain errors
(`{"error","message"}`) was discovered live while testing this, but ruled out of scope
— it's a separate, bigger finding ("error responses aren't consistent") that would
mean adding handlers for `MethodArgumentNotValidException`/`HttpMessageNotReadableException`
too. Not tracked as its own numbered candidate yet; worth raising if this comes up
again.

