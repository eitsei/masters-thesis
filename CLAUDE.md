## Agent skills

### Issue tracker

Issues and specs live as local markdown files under `.scratch/`. See `docs/agents/issue-tracker.md`.

### Domain docs

Single-context layout: `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.

### RAG Benchmark Testbed

When implementing, modifying, or testing the RAG benchmark harness, coding agents must follow [`rag-benchmark/AGENTS.md`](file:///home/eitsei/dippa/rag-benchmark/AGENTS.md) which formalizes all architectural, telemetry, module interaction, and evaluation decisions from Chapter 4 of the thesis.

### Thesis Compilation & Artifact Cleanup

Whenever compiling the LaTeX thesis (e.g., `thesis/_main.tex`), always immediately delete all intermediate auxiliary compilation files (`*.aux`, `*.bbl`, `*.blg`, `*.fdb_latexmk`, `*.fls`, `*.log`, `*.lot`, `*.lof`, `*.out`, `*.toc`, `*.xmpdata`, `*.xmpi`, `pdfa.xmpi`, `*.synctex.gz`, etc.). Only the compiled `.pdf` file (e.g., `_main.pdf`) may be left behind.
