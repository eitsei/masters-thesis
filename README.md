# Context is King: Retrieval-Augmented Generation in Enterprise-Ready Systems

This repository contains the source code, evaluation harness, and LaTeX manuscript for the Master's Thesis *"Context is king - Retrieval-Augmented Generation in enterprise-ready systems"* (Aalto University, School of Science, 2026).

The project empirically evaluates a $2^4 = 16$ full-factorial design across four RAG optimization modules—Small-to-Big Hierarchical Retrieval ($M_1$), Two-Stage Neural Re-ranking ($M_2$), Prompt & Context Caching ($M_3$), and Dynamic Complexity Model Routing ($M_4$)—analyzing the trade-offs between operational efficiency (latency, cost) and output quality.

## Repository Structure

```text
.
├── rag-code/
│   ├── rag-benchmark/       # Java 21 / Spring Boot 3.3 / Spring AI RAG backend & ingestion pipelines
│   └── rag-evaluator/       # Python evaluation harness (RAGAS, statistical tests, LaTeX/figure exporters)
├── thesis/
│   ├── _main.tex            # Master's thesis LaTeX entrypoint
│   ├── _main.pdf            # Compiled Master's thesis manuscript
│   ├── chapters/            # Thesis chapters and appendices
│   ├── figures/             # Generated empirical figures (PDF)
│   ├── tables/              # Generated empirical tables (LaTeX)
│   └── references/          # Bibliography (BibTeX)
├── CSC_SETUP.md             # HPC environment & Slurm batch execution guide (CSC Roihu)
└── context_inspector.py     # CLI utility for inspecting retrieved contexts and benchmark JSONL runs
```

## Components & Usage

Detailed setup and execution instructions are provided in each component's documentation:

- **RAG Benchmark Backend ([`rag-code/rag-benchmark/`](rag-code/rag-benchmark/README.md)):** Spring Boot service implementing the modular RAG pipeline ($M_1$–$M_4$), PostgreSQL/pgvector storage, and corpus ingestion endpoints.
- **Evaluation & Analysis Suite ([`rag-code/rag-evaluator/`](rag-code/rag-evaluator/scripts/README.md)):** Python CLI and benchmark orchestration scripts for running the 16-configuration factorial matrix, LLM-as-a-Judge evaluation, Wilcoxon signed-rank tests, and generating publication figures and tables.
- **HPC Evaluation Guide ([`CSC_SETUP.md`](CSC_SETUP.md)):** Instructions for provisioning local LLM/embedding weights and running GPU evaluation jobs on the CSC Roihu supercomputer.
- **Thesis Manuscript (`thesis/`):** LaTeX source files built using the Aalto University thesis class (`latexmk -pdf _main.tex`).

## Citation

```bibtex
@mastersthesis{asunmaa2026context,
  author  = {Asunmaa, Antti-Jussi},
  title   = {Context is king - Retrieval-Augmented Generation in enterprise-ready systems},
  school  = {Aalto University, School of Science},
  year    = {2026},
  address = {Espoo, Finland},
  type    = {Master's thesis}
}
```
