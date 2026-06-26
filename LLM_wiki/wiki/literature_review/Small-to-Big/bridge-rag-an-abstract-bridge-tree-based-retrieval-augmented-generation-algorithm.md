# Bridge-RAG: An Abstract Bridge Tree Based Retrieval Augmented Generation Algorithm
**Authors:** Zihang Li, Wenjun Liu, Yikun Zong, Jiawen Tao, Siying Dai, Songcheng Ren, Zirui Liu, Yuhang Wang, Yanbing Jiang, Tong Yang | **Year:** 2026
**Category:** Small-to-Big

### 📝 Abstract & Relevance Summary
This paper introduces Bridge-RAG, a novel retrieval-augmented generation framework designed to resolve the key challenges of retrieval accuracy and computational efficiency in structured RAG. By organizing deterministic fixed-window abstracts (groups of consecutive chunks) into a hierarchical bridge forest, the system maps high-level concepts down to granular text details. To maintain efficiency under deep hierarchies, the Cuckoo Filter from CFT-RAG is integrated to provide O(1) entity-to-abstract lookups. The study demonstrates significant relevance to both operational efficiency and output quality, achieving up to 1.9× faster retrieval times and substantial improvements in answer accuracy over structured baselines.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Provides up to 1.9× faster retrieval compared to structured RAG baselines. On MedQA, Bridge-RAG (Depth=3) retrieval latency averages 5.06 seconds per query compared to 8.72 seconds for Tree RAG (1.72× speedup) and 9.68 seconds for Graph RAG (1.91× speedup). On AALCR, Bridge-RAG averages 5.76 seconds per query compared to 9.64 seconds for Tree RAG.
- **Cost & Token Usage:** Constant-time entity lookup via the Cuckoo Filter bypasses complex graph search algorithms. The filter requires only ~4 bytes per entity entry (12-bit fingerprint, temperature counter, pointer), minimizing operational RAM costs.
- **Throughput:** Dramatically outperforms other structured options since set membership lookup scale does not depend on database size, enabling high query throughput on massive corpora.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Automated answer quality scores including ROUGE-1, ROUGE-2, ROUGE-L, BLEU, and BERTScore.
- **Benchmark Data:** Evaluated on MedQA (long-context medical QA exams, 1,000 sampled questions) and AALCR (Artificial Analysis Long Context Reasoning benchmark, 100 questions).
- **Judge Setup:** Doubao Seed 1.6 was used as the generator; results were evaluated using standard automated text matching metrics compared to golden answers.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** Tracing parent-child lineages inside the abstract tree improves BLEU by up to 44% over Tree RAG on AALCR while reducing latency by 40%.
- **Pareto Frontier & Combinations:** A trade-off exists regarding depth selection. Depth=3 achieves the best overall quality, but Depth=2 captures the vast majority of the context with lower traversal costs, representing the optimal efficiency-quality sweet spot for production.
- **Chunking/Routing Strategy:** Chunks are grouped deterministically in fixed windows of n=5 consecutive chunks to generate abstracts (bridge nodes).
- **Enterprise Suitability:** The Cuckoo Filter-based O(1) indexing scale provides a highly robust, low-latency, and memory-efficient pathway for real-world enterprise RAG deployment.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Evaluated across 100 independent trials to establish statistical relevance.
- **Bias Detection:** RAG hallucination risks inherit from the static backbone generator when retrieved context is conflicting.

### 🔍 Technical Implementation
- **Portability:** Highly compatible with a Java/Spring AI stack. The Cuckoo Filter and hierarchical tree construction can be easily written in Java, offering language-agnostic portability.
- **Key References:**
  - Li et al., 2026 (CFT-RAG)
  - Sarthi et al., 2024 (RAPTOR)
  - Edge et al., 2025 (Graph RAG)

### 💡 Key Takeaway for Thesis
- Integrating O(1) membership lookup structures (such as Cuckoo Filters) with deterministic hierarchical abstracts allows systems to serve global contextual answers with faster retrieval times than flat search spaces.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@article{li2026bridgerag,
  author    = {Zihang Li and Wenjun Liu and Yikun Zong and Jiawen Tao and Siying Dai and Songcheng Ren and Zirui Liu and Yuhang Wang and Yanbing Jiang and Tong Yang},
  title     = {Bridge-RAG: An Abstract Bridge Tree Based Retrieval Augmented Generation Algorithm},
  journal   = {arXiv preprint arXiv:2603.26668},
  year      = {2026}
}
```
