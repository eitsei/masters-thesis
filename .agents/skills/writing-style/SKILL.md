---
name: writing-style
description: Guidelines and LaTeX conventions matching the author's academic writing style for the Master's thesis on RAG optimization. Use when writing, editing, or expanding thesis chapters, abstracts, or technical literature reviews.
---

# Master's Thesis Writing Style Guide & Guidelines

This skill encodes the author's precise academic writing style, linguistic habits, structural conventions, and LaTeX formatting rules derived from the thesis codebase (*"Context is king - Retrieval-Augmented Generation in enterprise-ready systems"*).

Use these guidelines whenever drafting, editing, or revising LaTeX prose or technical sections for this thesis.

---

## 1. Core Tone & Voice

* **Formal, Authoritative Academic Register:** Tone is objective, analytical, precise, and authoritative. Avoid informal phrasing, speculative language, first-person pronouns (unless explicitly setting scope, e.g., *"This work proposes..."*), or emotional adjectives.
* **Enterprise & Production Focus:** Continuously ground technical concepts in enterprise operational realities—emphasizing high availability, cost scaling, Time to First Token (TTFT), data governance, and production reliability.
* **Standardized US Academic English:** Maintain consistent US English spelling (e.g., *formalize, optimize, behavior, continuous*).

---

## 2. Sentence Construction & Flow

* **Compound-Complex Syntactic Structure:** Sentences are rich and informative, typically joining high-level motivation with specific technical mechanics.
* **Trade-off & Contrast Framing:** Structure paragraphs around explicit trade-offs. 
  * *Pattern:* Start with a capability/advantage $\rightarrow$ introducing the operational friction/trade-off $\rightarrow$ present the architectural optimization that resolves the tension.
  * *Example:* *"While smaller chunks are computationally efficient and exhibit high intrachunk cohesion (ICC), they frequently suffer from low document contextual coherence (DCC) due to semantic fragmentation."*
* **Cohesive Transition Signals:** Frequently use clear academic logical connectors:
  * Contrast: *Conversely*, *However*, *In contrast*, *Nonetheless*, *While [X], [Y]*
  * Cause/Effect: *Consequently*, *Therefore*, *Thereby*, *This highlights*, *As a result*
  * Sequence/Elaboration: *Furthermore*, *In addition*, *To formalize this process systematically*, *For instance*

---

## 3. Mathematical & Technical Formalization

* **Explicit Mathematical Modeling:** Every major technical architecture or optimization paradigm must be formalized with LaTeX `equation` environments.
  * Define explicit symbols for sets, functions, and models:
    * Document corpus: $D = \{d_1, d_2, \ldots, d_N\}$
    * Embedding function: $E(\cdot)$
    * Retrieval function: $\text{Ret}_k(q, D) = R_q$
    * Generator model: $G(q, R_q) = a$
    * Router classifier: $f_{\text{route}}(q) = j$
* **Empirical Quantification:** Always ground comparative assertions in empirical data (percentages, latencies in ms/s, speedup factors, F1/NDCG scores) with precise attribution.
  * *Example:* *"Achieves up to a 98.3\% cost reduction compared to a homogeneous GPT-4 baseline while maintaining comparable response quality \citep{chen2023frugalgpt}."*

---

## 4. Citation & Reference Conventions

* **Natbib Command Usage:**
  * `\citet{key}` when referring to authors as subjects in prose: *"According to \citet{LLMSurvey}..."* or *"\citet{lumer2025rethinking} demonstrated that..."*
  * `\citep{key}` for parenthetical attribution: *"..mitigate critical limitations of modern LLMs \citep{lewis2020rag, wu2025ragnlp}."*
* **Snowballing & Survey Grounding:** Pair specific algorithmic papers (e.g., FrugalGPT, RouteLLM, RankRAG) with foundational background references or survey literature.

---

## 5. Alignment with Research Questions (RQs)

Maintain explicit alignment with the four core thesis research questions:
1. **RQ1 (Latency):** Time to First Token (TTFT), end-to-end execution latency, computational overhead.
2. **RQ2 (Cost):** Operational expenditure ($), token pricing tiers, prompt caching discounts, model cascading savings.
3. **RQ3 (Quality):** RAG evaluation metrics (Context Precision, Context Recall, Faithfulness, Answer Relevance), BLEU, Exact Match, F1 scores.
4. **RQ4 (Interaction & Equilibrium):** Pareto frontier, interaction between combined optimization methods, systemic trade-offs.

---

## 6. Structural & LaTeX Formatting Conventions

* **Structural Previews (Roadmaps):** Always conclude introduction or background sections with a section-by-section breakdown:
  * *Pattern:* *"The rest of the chapter/thesis is structured as follows: Section~\ref{...} reviews..., while Section~\ref{...} examines..."*
* **Footnote Usage:** Reserve `\footnote{...}` for context (e.g., prior publications, empirical survey statistics, platform details).
* **Inline Draft & Outline Notes:** Use `% NOTE:` or `% DRAFT NOTES:` comment blocks in `.tex` files to structure quantitative benchmarks and research notes above written prose.
* **Compilation Artifact Cleanup:** Whenever compiling LaTeX documents, immediately remove all intermediate auxiliary compilation files (`*.aux`, `*.bbl`, `*.blg`, `*.fdb_latexmk`, `*.fls`, `*.log`, `*.lot`, `*.lof`, `*.out`, `*.toc`, `*.xmpdata`, `*.xmpi`, `pdfa.xmpi`, `*.synctex.gz`). Only the final `.pdf` file may be retained.

---

## 7. Signature Vocabulary Checklist

Prefer these domain-specific academic expressions when writing:
* *production-grade enterprise environments*
* *parametric vs. non-parametric knowledge*
* *mitigate critical limitations*
* *formally modeled as / formalize this process systematically*
* *intrachunk cohesion (ICC) vs. document contextual coherence (DCC)*
* *Time to First Token (TTFT) and end-to-end latency*
* *optimal equilibrium / Pareto frontier*
* *systemic instability and escalating operational costs*
* *bypassing redundant prefill computation*
* *decouple the representation for retrieval from generation context*
