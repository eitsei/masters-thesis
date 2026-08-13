# Research Plan & Chapter 4 Outline: Experimental Methods & Benchmarking Setup

**Thesis Title:** *Context is king - Retrieval-Augmented Generation in enterprise-ready systems*  
**Author:** Antti-Jussi Asunmaa  
**Advisor:** Lauri Salminen, MSc | **Supervisor:** Prof. Petri Vuorimaa  
**Target Chapter:** Chapter 4 (*Research Methods & Experimental Setup*)

---

## 1. Executive Summary & Research Alignment

The objective of Chapter 4 is to establish a rigorous, reproducible, and enterprise-grade experimental methodology to evaluate four state-of-the-art Retrieval-Augmented Generation (RAG) optimization techniques:
1. **Model Cascading / Query Routing ($M_4$)**
2. **Small-to-Big Retrieval ($M_1$)**
3. **Semantic Re-ranking ($M_2$)**
4. **Prompt Caching ($M_3$)**

The empirical research directly answers the four core Research Questions (RQs):
* **RQ1 (Latency):** Time to First Token (TTFT) and End-to-End Execution Latency profiles.
* **RQ2 (Cost):** Operational expenditure ($), factoring in token pricing tiers, prompt caching discounts, and model cascading.
* **RQ3 (Quality):** Retrieval and generation quality evaluated via the RAGAS framework (*Context Precision*, *Context Recall*, *Faithfulness*, and *Answer Relevance*).
* **RQ4 (Interaction & Pareto Equilibrium):** Systemic trade-offs, interactive conflicts, and Pareto frontier optimal configurations when combining optimization techniques in high-volume enterprise customer support scenarios.

---

## 2. Methodological Framework: Experimental Quantitative Benchmarking

This study adopts an **Experimental Quantitative Benchmarking** approach. Rather than relying solely on abstract Python scripts, the benchmark suite is built on an enterprise Java/Spring AI stack connected directly to **Google Cloud Platform (GCP)** services to reflect real-world operational performance.

```mermaid
flowchart TD
    subgraph Data Layer
        A1[Enterprise Customer Support KB\n+ Synthetic QA Dataset]
        A2[Public Benchmark\nRouterBench / HotpotQA]
    end

    subgraph System Under Test (SUT) - Incremental Hybrid Architecture
        B[Java 21 / Spring AI Benchmark Runner\n(Local Runner / Containerized)]
        C1[Module 1: Small-to-Big]
        C2[Module 2: Re-ranking]
        C3[Module 3: Prompt Caching]
        C4[Module 4: Model Cascading]
        
        B --> C1 & C2 & C3 & C4
    end

    subgraph Enterprise Infrastructure & GCP Services
        D1[(PostgreSQL + pgvector\nLocal Docker / Cloud SQL)]
        D2[Google Vertex AI API\nGemini 2.5/3 Flash & Pro]
        D3[Cross-Encoder Reranker API]
    end

    subgraph Metrics & Evaluation Infrastructure
        E1[Micrometer / Prometheus Loggers\nTTFT & E2E Latency, Token Counts]
        E2[Offline RAGAS / DeepEval Evaluator\nContext Precision, Recall, Faithfulness, Relevance]
        E3[Pareto Frontier & Cost Calculator\nOperational Expenditure $ Analysis]
    end

    Data Layer --> B
    SUT --> Enterprise Infrastructure & GCP Services
    SUT --> Metrics & Evaluation Infrastructure
```

---

## 3. Technical Architecture & Enterprise GCP Stack

To ensure strict alignment with production enterprise environments and GCP migration goals, the system under test (SUT) is implemented using:

* **Application Framework:** Java 21 & Spring Boot 3.x with **Spring AI** (`spring-ai-bom`).
* **Database Strategy (Hybrid PostgreSQL + `pgvector`):**
  * *Local Development & Benchmark Execution:* Containerized PostgreSQL with `pgvector` in Docker for zero-cost, reproducible, isolated latency benchmarking.
  * *Cloud Production Path:* 100% code-compatible deployment path to **GCP Cloud SQL for PostgreSQL / AlloyDB**.
* **LLM & Embedding Models (Google Cloud Platform Vertex AI):**
  * Generator Models: GCP Vertex AI `gemini-2.5-flash` (lightweight/fast) and `gemini-2.5-pro` (heavyweight/reasoning).
  * Embedding Model: GCP Vertex AI `text-embedding-004` (768-dimensional dense vectors).
* **Reranking Engine:** Cross-Encoder neural reranker (Cohere / Vertex AI Rerank API).
* **Metrics Ingestion:** Spring Boot Actuator with Micrometer export to Prometheus/Grafana and JSON log aggregation.
* **Deployment Model (Incremental Hybrid):** Primary benchmark execution is run locally connecting to GCP Vertex AI APIs, with optional containerized deployment to **GCP Cloud Run** for intra-datacenter latency validation.

---

## 4. Dataset & Benchmark Strategy

To balance enterprise realism with scientific reproducibility, a **Hybrid Dataset Strategy** is implemented:

1. **Enterprise Customer Support Dataset (Primary):**
   * *Knowledge Base:* Realistic technical documentation, knowledge base articles, and SLA policies (500+ document chunks).
   * *QA Set:* 200 synthetic query-answer pairs generated via Gemini Pro with multi-step validation to reflect real customer support workloads (simple informational, multi-hop reasoning, out-of-domain queries).
2. **Public Standard Benchmark (Secondary / Validation):**
   * A subset of **RouterBench** (arXiv:2403.12031) and **HotpotQA** (100 complex multi-hop queries) used to validate generalizability and router accuracy across standard literature benchmarks.

---

## 5. Optimization Implementations & Mechanics

| Module | Strategy | Implementation Details |
| :--- | :--- | :--- |
| **Naive Baseline ($M_0$)** | Standard Flat RAG | Fixed 512-token chunks, bi-encoder cosine top-k ($k=5$), static Gemini Pro model, no caching. |
| **$M_1$** | Small-to-Big Retrieval | 128-token child index chunks mapped to 512–1024 token parent context windows upon retrieval. |
| **$M_2$** | Semantic Re-ranking | Bi-encoder top-$K_1$ candidate retrieval ($K_1=30$) pruned to top-$k$ ($k=5$) via Cross-Encoder. |
| **$M_3$** | Prompt Caching | Model-level GCP Context Caching / prefix KV caching for static system prompts and large document blocks. |
| **$M_4$** | Model Cascading | Lightweight routing classifier evaluating query intent/complexity to route between Gemini Flash (fast/cheap) and Gemini Pro (heavyweight) or bypass RAG. |

---

## 6. Full Experimental Matrix (Test Suite)

To systematically address **RQ4 (Interactions & Pareto Frontier)**, tests are executed across a multi-condition matrix:

```
[Experiment Matrix]
├── 1. Baseline: Naive RAG (M0)
├── 2. Isolated Single Optimizations:
│   ├── M1: Small-to-Big
│   ├── M2: Semantic Re-ranking
│   ├── M3: Prompt Caching
│   └── M4: Model Cascading
├── 3. Targeted Dual Stacks (Conflict Analysis):
│   ├── M1 + M2 (Context Expansion + Re-ranking)
│   ├── M2 + M3 (Re-ranking vs. Cache Hit Rate friction)
│   └── M1 + M4 (Small-to-Big + Cascading)
└── 4. Full Optimization Stack:
    └── M1 + M2 + M3 + M4 (Complete Enterprise Pipeline)
```

Each experimental configuration is executed across 5 iterations to eliminate cold-start noise and guarantee statistical significance (computing mean, standard deviation, and P95 latency).

---

## 7. Metrics & Measurement Infrastructure

### 7.1 Quantitative Efficiency Metrics (RQ1 & RQ2)
* **Time to First Token (TTFT):** Measured in milliseconds ($\text{ms}$) from query invocation until the first streaming token frame is emitted by the API.
* **End-to-End Latency:** Total request execution time ($\text{ms}$).
* **Token Usage & Operational Cost ($):** Calculated via exact token accounting:
  $$\text{Cost} = (N_{\text{prompt}} \times P_{\text{prompt}}) + (N_{\text{cached}} \times P_{\text{cached}}) + (N_{\text{completion}} \times P_{\text{completion}})$$
  using official GCP Vertex AI pricing tiers for Gemini Flash and Pro.

### 7.2 Quality Metrics (RQ3)
Evaluated offline using Python **RAGAS (Retrieval Augmented Generation Assessment)** with an LLM-as-a-judge setup (GPT-4o / Gemini Pro):
* **Context Precision:** Ratio of relevant chunks in the retrieved context window.
* **Context Recall:** Extent to which all required information is retrieved.
* **Faithfulness:** Groundedness of the generated answer against the retrieved context (hallucination check).
* **Answer Relevance:** Semantic similarity of the generated response to the user's intent.

### 7.3 Pareto Frontier & Equilibrium Modeling (RQ4)
Normalized trade-off modeling mapping each configuration on a multi-objective Pareto frontier:
$$\text{Score}_i = w_1 \cdot \text{Quality}_i - w_2 \cdot \overline{\text{Cost}_i} - w_3 \cdot \overline{\text{Latency}_i}$$

---

## 8. Proposed Outline for Chapter 4 (`04_research_methods.tex`)

Below is the proposed section structure to be written into LaTeX:

```latex
\section{Research Methodology and Experimental Setup}
\label{sec:methods}

\subsection{Research Paradigm and Design}
% Describes the Experimental Quantitative Benchmarking approach and alignment with RQs.

\subsection{System Architecture and Tech Stack}
% Enterprise Java 21 / Spring AI, PostgreSQL pgvector (Hybrid Docker/Cloud SQL), GCP Vertex AI Gemini infrastructure.

\subsection{Optimization Module Implementation}
% Mathematical and algorithmic specifications for Small-to-Big (M1), Re-ranking (M2), Caching (M3), Cascading (M4).

\subsection{Dataset Construction and Benchmark Setup}
% Hybrid enterprise customer support QA dataset + RouterBench/HotpotQA integration.

\subsection{Evaluation Infrastructure and Metrics}
% Micrometer/Prometheus TTFT/Latency logging, GCP cost accounting formulas, RAGAS quality triad metrics.

\subsection{Experimental Procedure and Test Matrix}
% Execution protocol, statistical repetition, isolated vs. combined stack test matrix, Pareto frontier analysis.
```

---

## 9. Verification & Advisor Presentation Checklist

> [!TIP]
> **Key Talking Points for Advisor Meeting:**
> 1. **Why Java/Spring AI + GCP Vertex AI?** Demonstrates production-grade enterprise alignment with real-world cloud architectures (GCP) and JVM ecosystems.
> 2. **Why PostgreSQL + pgvector in a Hybrid Setup?** Provides zero-cost local iteration while maintaining 100% compatibility with GCP Cloud SQL / AlloyDB production deployment.
> 3. **How does RQ4 bring novelty?** Existing papers evaluate M1–M4 in isolation. This thesis evaluates their interactive friction (e.g., does Re-ranking destroy Prompt Caching hit rates?).
> 4. **Are results reproducible?** Yes, by combining a standard benchmark (RouterBench) with fixed seed synthetic enterprise data and automated logging.
