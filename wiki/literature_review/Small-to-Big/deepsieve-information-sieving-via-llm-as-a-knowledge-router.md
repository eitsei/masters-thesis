# DeepSieve: Information Sieving via LLM-as-a-Knowledge-Router
**Authors:** Minghao Guo, Qingcheng Zeng, Xujiang Zhao, et al. | **Year:** 2026
**Category:** Small-to-Big

### 📝 Abstract & Relevance Summary
DeepSieve introduces a modular RAG framework designed for multi-stage information sieving across both query and source spaces. It utilizes an LLM as a "knowledge router" to dynamically decompose complex queries into a sub-query Directed Acyclic Graph (DAG) and dispatch them to heterogeneous sources (e.g., SQL databases, RAG corpora, web APIs). It features a recursive reflexion loop to correct retrieval failures. This paper is foundational for **Operational Efficiency** by optimizing source selection to avoid token waste and **Output Quality** by preventing "context pollution" in heterogeneous knowledge environments.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Adds a slight overhead (~0.03s) for decomposition and routing but remains negligible compared to total generation time. Average latency is 0.058s per query.
- **Token Usage:** Uses significantly fewer tokens (up to 9.7x fewer than Reflexion) by isolating retrieval to specific sources and avoiding the processing of massive, irrelevant merged indices.
- **Throughput:** By dispatching queries to structured sources (SQL) when appropriate, it reduces the reasoning burden on the generative LLM, potentially increasing system-wide throughput.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Exact Match (EM) and F1 Score.
- **Benchmark Data:** MuSiQue, 2WikiMultiHopQA, and HotpotQA.
- **Judge Setup:** Uses LLM-based global inference to resolve contradictions across valid reasoning paths.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** DeepSieve achieves a +50% improvement in EM over naive RAG in cross-domain (Medical/Legal) tests. It trades architectural complexity for a dramatic reduction in "context pollution," providing a high-precision equilibrium for enterprise-scale source heterogeneity.
- **Chunking Strategy:** Decomposes monolithic queries into structured sub-questions, routing them to specialized tool-corpus pairs.
- **Enterprise Suitability:** Specifically addresses real-world enterprise needs where knowledge is siloed across incompatible formats (SQL, JSON, RAG) and cannot be merged into a flat index.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Uses DeepSeek-V3 and GPT-4o as backbones across multi-source simulated environments.
- **Bias Detection:** Explicitly designs router-visible profiles to guide model selection, reducing self-preference bias towards specific indices.

### 🔍 Technical Implementation
- **Portability:** The router architecture is highly portable to Java/Spring AI as a task-routing state machine, integrating diverse data clients (JDBC for SQL, Vector clients for RAG).
- **Key References:** 
  - Gutierrez et al., 2024 (HippoRAG)
  - Asai et al., 2024 (Self-RAG)

### 💡 Key Takeaway for Thesis
DeepSieve demonstrates that information sieving via knowledge-aware routing is the superior strategy for production RAG, isolating domain contexts to maximize quality while aggressively minimizing token overhead.

### 🔗 BibTeX Reference (Target: /bib)
```bibtex
@inproceedings{guo2026deepsieve,
  title={DeepSieve: Information Sieving via {LLM}-as-a-Knowledge-Router},
  author={Guo, Minghao and Zeng, Qingcheng and Zhao, Xujiang and Liu, Yanchi and Yu, Wenchao and Du, Mengnan and Chen, Haifeng and Cheng, Wei},
  booktitle={Findings of the Association for Computational Linguistics: EACL 2026},
  pages={3054--3077},
  year={2026}
}
```