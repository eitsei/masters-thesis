# Rethinking Retrieval: From Traditional Retrieval Augmented Generation to Agentic and Non-Vector Reasoning Systems in the Financial Domain for Large Language Models

**Authors:** Elias Lumer, Matt Melich, Olivia Zino, Elena Kim, Sara Dieter, Pradeep Honaganahalli Basavaraju, Vamse Kumar Subbiah, James A. Burke, Roberto Hernandez | **Year:** 2025
**Category:** Small-to-Big (also covers Semantic-Re-ranking / Routing / Background)

### 📝 Abstract & Relevance Summary
This paper presents the first systematic evaluation comparing vector-based agentic RAG (using hybrid search and metadata filtering) against hierarchical node-based systems (which traverse document table-of-contents without embeddings) in the financial domain. Additionally, it evaluates two advanced enhancement techniques: cross-encoder reranking for retrieval precision and small-to-big retrieval for context completeness. The study is highly relevant to both **Operational Efficiency** and **Output Quality** because it measures the specific latency and cost trade-offs of these techniques on a 150-question benchmark of SEC filings. Small-to-big retrieval is shown to significantly improve answer quality (65% win rate) with negligible latency overhead (0.2s) and zero cost increase, demonstrating a highly optimized equilibrium for production environments.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** 
  - **Vector Agentic RAG vs Hierarchical Node RAG:** Vector-based agentic RAG has a latency of **5.20s** compared to **5.98s** for hierarchical systems.
  - **Small-to-Big Retrieval:** Introduces only **0.20 seconds** of additional latency. Using **asynchronous retrieval** (parallel queries for neighboring chunks) reduces this overhead to **0.17s**, compared to **0.34s** for synchronous retrieval.
  - **Cross-Encoder Reranking:** Increases latency from 0.22s (baseline) to **2.02s** at the optimal `(10, 5)` configuration.
- **Cost & Token Usage:**
  - **Small-to-Big Retrieval:** Results in zero per-query cost increase, maintaining a constant cost of **$0.000078** per query.
  - **Preprocessing/Node Generation:** OpenAI GPT-4o node-level summary generation cost is **$6.12** per average filing with summaries, compared to **$1.19** without summaries.
- **Throughput:** Fetching neighboring chunks in parallel (asynchronously) dramatically improves response throughput and system resource utilization under load.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** LLM-as-a-judge pairwise win rates (accuracy, completeness, clarity, conciseness, relevance, and style) and retrieval accuracy (MRR@5, Recall@5).
  - **Vector-based Agentic RAG:** Achieved a **68% win rate** over hierarchical node-based systems.
  - **Small-to-Big Retrieval:** Achieved a **65% win rate** over baseline chunking.
  - **Cross-Encoder Reranking:** Delivered a **59% absolute improvement** in MRR@5 (increasing from 0.160 to 0.750) and perfect Recall@5 (1.00 compared to 0.50 baseline).
- **Benchmark Data:** A 150-question benchmark dataset evaluated across 1,200 SEC filings (10-K, 10-Q, and 8-K).
- **Judge Setup:** Anthropic Claude 4.5 Sonnet was utilized as the pairwise LLM-as-a-judge.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** Small-to-big retrieval offers a massive quality upgrade (65% win rate) for a minor 0.2s latency increase, proving to be an exceptionally high-value optimization.
- **Pareto Frontier & Combinations:** While cross-encoder reranking increases latency significantly (~1.8s overhead), it optimizes MRR@5. Small-to-big retrieval, by contrast, focuses on semantic completeness. Combining the two ensures that the top-ranked vector chunks are expanded with immediate neighbors, forming an optimal quality-latency Pareto frontier.
- **Chunking/Routing Strategy:** Target chunk indexing (`idx`) is retrieved via dense vector search. For generation, the context window is expanded to include its immediate neighbors (one preceding chunk `idx - 1` and one following chunk `idx + 1` or up to a window size of `idx - 2` to `idx + 2`). 
- **Enterprise Suitability:** Vector-based agentic RAG is highly suitable for production systems. In contrast, hierarchical node trees suffer from context window limitations and failure rates at the LLM table-of-contents selection stage.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Custom pairwise benchmarking suite using Claude 4.5 Sonnet as the judge.
- **Bias Detection:** The evaluation addresses context window limitations (Primacy/Recency bias) and documents the failure cases of LLM traversal on hierarchical structures.

### 🔍 Technical Implementation
- **Portability:** Language-agnostic. Highly portable to a Java/Spring AI stack by implementing an asynchronous database fetch for sibling chunk IDs after the primary vector search returns the target chunk IDs.
- **Key References:**
  - *Lewis et al., 2020* (RAG foundation)
  - *Nogueira and Cho, 2019* (Cross-encoder reranking)
  - *VectifyAI, 2024/2025* (Hierarchical node-based traversal)

### 💡 Key Takeaway for Thesis
- Small-to-big retrieval is a highly effective, low-overhead optimization method that yields a 65% improvement in answer quality with just 0.17s (asynchronous) latency overhead, making it a critical baseline configuration for enterprise-grade RAG.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@article{lumer2025rethinking,
  title={Rethinking Retrieval: From Traditional Retrieval Augmented Generation to Agentic and Non-Vector Reasoning Systems in the Financial Domain for Large Language Models},
  author={Lumer, Elias and Melich, Matt and Zino, Olivia and Kim, Elena and Dieter, Sara and Basavaraju, Pradeep Honaganahalli and Subbiah, Vamse Kumar and Burke, James A. and Hernandez, Roberto},
  journal={arXiv preprint arXiv:2511.18177},
  year={2025}
}
```
