# rag-benchmark architecture review — candidate tracker

Source: `/improve-codebase-architecture` run on 2026-08-14 against `rag-benchmark`
(M0 baseline, two commits at review time). Full exploration was done by a sub-agent
reading the whole module (no clear git-history hot spot yet — repo too young).

Vocabulary used throughout: module, interface, implementation, depth, deep, shallow,
seam, adapter, leverage, locality — see `/codebase-design`.

## Candidates, strongest first

1. `issues/01-config-catalog-duplication.md` — **Strong** — RESOLVED (commit `b594a76`)
2. `issues/02-shallow-dto-pass-through.md` — Worth exploring — RESOLVED
3. `issues/03-untested-api-seam.md` — Worth exploring — RESOLVED
4. `issues/04-ingestion-partial-failure-handler.md` — Worth exploring — RESOLVED
5. `issues/05-moduleid-scattered-conversion.md` — Worth exploring — RESOLVED
6. `issues/06-speculative-ports-and-timer-duplication.md` — Speculative

## Original top recommendation

Candidate 1 (config catalog vs. live model config duplication) — smallest fix,
already live today rather than speculative, invisible drift risk. Done.

## Status

Review wrapped up at 5/6 resolved (2026-08-14). Candidate 6 (speculative ports + timer
duplication) stays open/untouched — user confirmed it's not worth pursuing right now
given its Speculative rating. Revisit if a second real adapter/timer use case shows up
that would make the speculative seam concrete (see "one adapter = hypothetical seam,
two = real" in `/codebase-design`).

## Not yet a tracked candidate

Error-response shape inconsistency (validation failures / malformed JSON return a
different JSON shape than domain errors) — discovered while resolving candidate 3,
explicitly scoped out as its own separate concern. See "Explicitly deferred" in
`issues/03-untested-api-seam.md` if this comes up again.
