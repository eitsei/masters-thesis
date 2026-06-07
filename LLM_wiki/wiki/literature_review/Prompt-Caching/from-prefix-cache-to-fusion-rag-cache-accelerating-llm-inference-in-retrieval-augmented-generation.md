# From Prefix Cache to Fusion RAG Cache: Accelerating LLM Inference in Retrieval-Augmented Generation
**Authors:** Jiahao Wang, Weiyu Xie, Mingxing Zhang, Boxing Zhang, Jianwei Dong, Yuening Zhu, Chen Lin, Jingqi Tang, Yaochen Han, Zhiyuan Ai, Xianglin Chen, Yongwei Wu, and Congfeng Jiang | **Year:** 2026
**Category:** Prompt-Caching

### 📝 Abstract & Relevance Summary
Retrieval-Augmented Generation (RAG) integrates external knowledge but increases prompt length, leading to high Time to First Token (TTFT) and computational cost. While previous KVCache reuse methods (e.g., CacheBlend, Cache-Craft) address this, they suffer from quality degradation because of KV deviation and lack of cross-chunk attention. To address this, the authors propose **FusionRAG**, a two-stage framework that optimizes both preprocessing and online recomputation. In the offline preprocessing stage, related text chunks are grouped, and their cross-attention is precomputed. In the online reprocessing stage, a query-guided selection algorithm identifies and recomputes only a critical subset of tokens (<15%). This approach balances operational efficiency (reducing TTFT) and output quality (preserving semantic fidelity closer to full attention).

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Reduces TTFT by **2.66--3.94x** compared to Full Attention (FA) at a 15% recomputation ratio for a 27K token context. Under a 0% recomputation ratio (Full Reuse), TTFT is reduced by **5.22--9.39x**. The Triton-based sparse attention operator (Q-Sparse-Attn) yields a **4.2x speedup** compared to FlashAttention and **2.69x speedup** compared to PyTorch's SDPA at 15% recomputation. The Asynchronous KVCache Scheduler provides an additional **11% TTFT reduction** by overlapping host-GPU memory transfer and inference.
- **Cost & Token Usage:** Recomputes fewer than **15%** of context tokens. Eliminates **82.3%** of redundant computations through the Alternative Path matching mechanism, which achieves a **77.9% cache hit rate** and reduces total KVCache storage overhead by **71.0%** by keeping only a single cache copy per unique chunk.
- **Throughput:** Achieves a **1.2--4.3x throughput improvement** compared to baselines under concurrent request testing with continuous batching.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Evaluated using **Faithfulness** (derived from the RAGAS framework to measure context fidelity), **Exact Match (EM)**, **F1 Score**, and **Normalized F1 Score** (relative to Full Attention and naive Full Reuse).
- **Benchmark Data:** TriviaQA (1-hop), HotpotQA (2-hop), Musique (up to 4-hop), and 2WikiMultiHopQA (up to 5-hop).
- **Judge Setup:** The evaluation of Faithfulness utilizes the RAGAS framework, and the base models tested include Mistral-7B-Instruct-v0.3, Qwen2.5-7B/14B-Instruct, Qwen3-32B, and GLM4-32B. A proof-of-concept is validated on OpenPangu-1B.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** At a 15% recomputation ratio, FusionRAG recovers **over 80%** of the quality loss across 60% of test scenarios (12/20), and achieves EM and F1 scores comparable to Full Attention. Query-guided selection alone recovers 72% of quality, and adding similarity-guided preprocessing reaches **99% quality recovery**, effectively bridging the efficiency-quality gap.
- **Pareto Frontier & Combinations:** Combines offline **Similarity-Guided Preprocessing** with online **Query-Guided Selection**. The combination allows the system to outperform online-only recomputation methods operating at the same budget; even at 0% recomputation, preprocessed FusionRAG outperforms baseline recomputation at 15%.
- **Chunking/Routing Strategy:** In the offline stage, an embedding model (BGEM3) finds the **top-10** (n=10) similar text chunks for each document, which is identified as the optimal number of co-occurring contexts to precompute. The online stage uses an attention-weight-based query-guided selection of the top 15% critical tokens.
- **Enterprise Suitability:** Highly suitable for stable, frequently-queried enterprise databases where the 0.218s per chunk preprocessing cost is easily amortized over subsequent queries. Less suitable for "write-once, read-once" or real-time news feeds due to continuous update overhead.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Evaluated using the RAGAS framework for faithfulness and standard precision/recall overlaps (ROUGE-1) for F1.
- **Bias Detection:** Analyzed position and index bias in recomputation. Naive CacheBlend is shown to be biased towards later-retrieved chunks and token heads, whereas FusionRAG's query-guided selection distributes token selection uniformly across chunks based on semantic focus.

### 🔍 Technical Implementation
- **Portability:** The high-level pipeline (offline similarity matching, prefix cache with backtracking, and query-guided attention scoring) is language-agnostic and implementable in Java/Spring AI. However, the low-level sparse attention optimization relies on custom Triton kernels (Python-based GPU programming), which are not directly portable to Spring AI without C++ JNI bindings.
- **Key References:** 
  - *CacheBlend:* Yao et al., 2025 (Fast LLM Serving for RAG with Cached Knowledge Fusion)
  - *Cache-Craft:* Agarwal et al., 2025 (Managing Chunk-Caches for Efficient Retrieval-Augmented Generation)
  - *PromptCache:* Gim et al., 2024 (Modular Attention Reuse for Low-Latency Inference)

### 💡 Key Takeaway for Thesis
- FusionRAG demonstrates that shifting the cross-attention computation of co-occurring RAG context chunks to an offline preprocessing stage allows online queries to achieve up to a 9.3x TTFT speedup while recovering 99% of Full Attention quality.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@article{wang2026fusionrag,
  title={From Prefix Cache to Fusion RAG Cache: Accelerating LLM Inference in Retrieval-Augmented Generation},
  author={Wang, Jiahao and Xie, Weiyu and Zhang, Mingxing and Zhang, Boxing and Dong, Jianwei and Zhu, Yuening and Lin, Chen and Tang, Jingqi and Han, Yaochen and Ai, Zhiyuan and Chen, Xianglin and Wu, Yongwei and Jiang, Congfeng},
  journal={Proc. ACM Manag. Data},
  volume={4},
  number={1},
  pages={41:1--41:28},
  year={2026},
  publisher={ACM},
  doi={10.1145/3786655}
}
```
