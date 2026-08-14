Status: open
Recommendation strength: Speculative

# Zero-adapter domain ports and a duplicated inline timing pattern

## Files
- `rag-benchmark/src/main/java/com/dippa/ragbenchmark/domain/ModelRouter.java`
- `rag-benchmark/src/main/java/com/dippa/ragbenchmark/domain/PromptCache.java`
- `rag-benchmark/src/main/java/com/dippa/ragbenchmark/domain/RetrievalExpander.java`
- `rag-benchmark/src/main/java/com/dippa/ragbenchmark/application/RagQueryService.java` (lines 63-66, 70-72)

## Problem
`ModelRouter`, `PromptCache`, `RetrievalExpander` have zero adapters, zero injection
points, zero callers — deletion test confirms deleting them today changes nothing.
This is intentional per README/TODO.md (forward declarations for M1/M3/M4), so it's
*not* flagged as premature — contrast with `Reranker`, which is a real seam already
(one adapter wired, called unconditionally, tested).

One latent risk worth flagging while cheap to fix: `ModelRouter.routeModel(String)`
returns a bare `String` tier id with no connection to
`ModelCatalogProperties.GoogleGenAiTiers` — a second independently-typed "which tier"
representation, echoing issue 01's original problem. Worth typing it against the
catalog (or a shared enum) when M4 is actually implemented, rather than a bare String.

Separately, unrelated to the ports: `RagQueryService.query()` already repeats a
four-line `Timer.Sample start / do work / stop` pattern twice in one method
(`rag.pipeline.retrieval`, `rag.pipeline.generation`). M1/M2/M3 will each plausibly
want their own timed stage — likely 3-4 more copies of the same four lines.

## Proposed solution
- Ports: no action needed now — just remember the typing note when M4 lands.
- Timer: extract a small `timed(String metricName, Supplier<T> work)` helper in
  `RagQueryService` before M1-M4 add more copies.

## Benefits
Ports: none needed yet, correctly deferred. Timer: locality (one place defines how a
pipeline stage is timed), less copy-paste risk.
