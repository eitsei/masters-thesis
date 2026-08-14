Status: resolved
Recommendation strength: Strong

# Config catalog silently duplicated the live model config

## Files
- `rag-benchmark/src/main/resources/application.yml` (lines 36-37, 105-119)
- `rag-benchmark/src/main/java/com/dippa/ragbenchmark/infrastructure/config/ModelCatalogProperties.java`

## Problem
`application.yml` set the one model Spring AI actually instantiates
(`spring.ai.google.genai.chat.model`) as an independently-hardcoded literal from
`rag.models.google-genai.flash` (bound into `ModelCatalogProperties`, consumed by zero
beans). Nothing tied the two together — they agreed today by author discipline, not by
construction. Deletion test: deleting `ModelCatalogProperties` and the `rag.models.*`
block changed nothing else in the app.

## Resolution (commit `b594a76`)
- `chat.model` now reads `${GOOGLE_GENAI_CHAT_MODEL:${rag.models.google-genai.flash}}`
  — catalog is the source of truth for the default; env var override still works
  independently for ad hoc local runs.
- `flash` bumped `gemini-3.6-flash` → `gemini-3.7-flash` (real model, released
  2026-08-14 per web search; `flash-lite`/`pro` checked and still current).
- Comments in `application.yml`, `README.md`'s "Model catalog" section, and the
  `ModelCatalogProperties` javadoc updated to describe the actual link.
- Verified live: booted the app against `google-genai` with a fake API key; confirmed
  via `/actuator/env` that the property resolves to `gemini-3.7-flash` with no override
  set, and to the override value when `GOOGLE_GENAI_CHAT_MODEL` is set.

## Design decisions made during grilling
- Catalog is authoritative (not the reverse), because `flash` doubles as M0's base
  model *and* the tier M4's routing/cascading will treat as its medium-complexity /
  fallback tier (per user's stated M4 design: flash-lite for easy tasks + the router
  itself, flash 3.7 as medium/fallback, gemini-3.1-pro for heavy lifting; possible
  future "thinking-effort routing" stretch goal on top of flash 3.7).
- `GoogleGenAiTiers.flash` stays a plain `String` — no widening for thinking-effort
  routing now; M4 isn't built yet, that's the right moment to reshape the catalog.
