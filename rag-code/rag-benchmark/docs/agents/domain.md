# Domain Docs

## Before exploring, read these

- Read `CONTEXT.md` at the repository root when it exists.
- Read ADRs under `docs/adr/` that touch the area being changed.
- If a referenced file does not exist, proceed silently.

## File structure

This is a single-context repository:

```text
/
├── CONTEXT.md
├── docs/adr/
└── src/
```

The domain-modeling skills create `CONTEXT.md` and ADRs lazily when terminology or decisions need to be captured.

## Use the glossary vocabulary

When `CONTEXT.md` exists, use its domain terms in issue titles, refactor proposals, hypotheses, and tests. If a needed concept is not defined, treat that as a signal to clarify the domain language.

## Flag ADR conflicts

If proposed work contradicts an existing ADR, surface the conflict explicitly rather than silently overriding it.
