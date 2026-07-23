# RAGCache: Efficient Knowledge Caching for Retrieval-Augmented Generation
**Authors:** Chao Jin, Zili Zhang, Xuanlin Jiang, Fangyue Liu, Shufan Liu, Xuanzhe Liu, Xin Jin | **Year:** 2025
**Category:** Prompt-Caching

### 📝 Abstract & Relevance Summary
Retrieval-Augmented Generation (RAG) imposes high end-to-end latency due to long sequence prefill (knowledge injection) and strict data dependencies between vector retrieval and LLM generation. Existing LLM cache managers (e.g., vLLM, SGLang) operate solely in GPU memory and fail to optimize for RAG-specific retrieval skew and document ordering constraints. RAGCache addresses these bottlenecks by organizing retrieved document KV states into a multi-tier knowledge tree across GPU and host memory hierarchy, managed by a Prefix-aware Greedy-Dual-Size-Frequency (PGDSF) replacement policy. Furthermore, it introduces dynamic speculative pipelining to overlap streaming vector search (IVF/HNSW) with LLM prefill. RAGCache directly optimizes operational efficiency (TTFT reduced by 4x, throughput increased by 2.1x) without compromising output quality.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Reduces Time-To-First-Token (TTFT) by up to 4.0x compared to vLLM integrated with Faiss, and up to 3.5x compared to SGLang.
- **Cost & Token Usage:** Substantially lowers redundant GPU compute costs by shifting cold/warm KV cache blocks to host memory (RAM) and maximizing cache hit rates for frequently retrieved documents.
- **Throughput:** Increases system serving throughput by up to 2.1x over vLLM+Faiss and up to 1.8x over SGLang under production request loads.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Evaluated using Exact Match (EM), F1-score, and generation consistency metrics against baseline RAG systems.
- **Benchmark Data:** Open-source datasets (e.g., Natural Questions, MS-MARCO) and real-world production RAG trace datasets.
- **Judge Setup:** Standard automated QA metrics comparing speculative pipelining outputs against full non-pipelined baseline outputs to verify complete generation equivalence.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** Achieves zero quality degradation relative to baseline RAG because speculative pipelining validates intermediate search results before finalizing generation tokens.
- **Pareto Frontier & Combinations:** Combines host-GPU tiering (PGDSF replacement) with algorithm-system co-design for vector search engines (Faiss IVF/HNSW streaming pipelining).
- **Chunking/Routing Strategy:** Organizes document sequences in a prefix-sensitive knowledge tree to preserve KV cache validity under fixed document orderings while handling variable-length chunks.
- **Enterprise Suitability:** Extremely high for enterprise-scale RAG deployments where knowledge bases are too massive to fit entirely in GPU VRAM, enabling cost-effective scaling on host RAM/SSD resources.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Prototyped on vLLM and Faiss, benchmarked against state-of-the-art inference engines (vLLM, SGLang).
- **Bias Detection:** Benchmarked on diverse production workloads to avoid synthetic trace bias and evaluate real-world access distributions.

### 🔍 Technical Implementation
- **Portability:** Serving-level optimization tailored for GPU inference engines (vLLM backend). Java/Spring AI applications benefit directly when connecting to a RAGCache-backed inference endpoint via standard REST/gRPC interfaces.
- **Key References:**
  1. Kwon et al. (2023) - *Efficient Memory Management for Large Language Model Serving with PagedAttention*
  2. Zheng et al. (2023) - *SGLang: Efficient Execution of Structured Language Model Programs*
  3. Johnson et al. (2019) - *Billion-Scale Similarity Search with GPUs*

### 💡 Key Takeaway for Thesis
RAGCache demonstrates that multi-tier host-GPU knowledge tree caching coupled with speculative vector search pipelining cuts TTFT by 4x and boosts throughput by 2.1x while preserving 100% output quality, illustrating the power of algorithm-system co-design for production RAG systems.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@article{jin2025ragcache,
  title={RAGCache: Efficient Knowledge Caching for Retrieval-Augmented Generation},
  author={Jin, Chao and Zhang, Zili and Jiang, Xuanlin and Liu, Fangyue and Liu, Shufan and Liu, Xuanzhe and Jin, Xin},
  journal={ACM Transactions on Computer Systems (TOCS)},
  volume={44},
  number={1},
  pages={2:1--2:27},
  year={2025},
  publisher={ACM}
}
```
