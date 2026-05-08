# From Prefix Cache to Fusion RAG Cache: Accelerating LLM Inference in Retrieval-Augmented Generation
**Authors:** Jiahao Wang, Weiyu Xie, Mingxing Zhang, Boxing Zhang, Jianwei Dong, Yuening Zhu, Chen Lin, Jingqi Tang, Yaochen Han, Zhiyuan Ai, Xianglin Chen, Yongwei Wu, Congfeng Jiang | **Year:** 2026
**Category:** Prompt Caching

### 📝 Abstract & Relevance Summary
FusionRAG is a novel inference framework that optimizes KVCache reuse to accelerate RAG pipelines. Traditional prefix caching fails in RAG because multiple chunks can appear in $n!$ possible orders, leading to low cache hit ratios and quality degradation due to lost cross-chunk attention. FusionRAG solves this using a two-stage approach: (1) **Similarity-Guided offline preprocessing**, which precomputes cross-attention among similar chunks, and (2) **Query-Guided online selection**, which recomputes KVCache for only the most critical tokens identified by the query. This research is directly relevant to RQ1 and RQ2 as it reduces Time to First Token (TTFT) by up to 9.39x and eliminates over 80% of redundant computations while maintaining generation quality comparable to full attention.

### 🚀 Efficiency Metrics (Literature Review / Empirical)
- **RQ1 - Latency (TTFT):** Reduces TTFT by **2.66–9.39×** compared to Full Attention. The Q-Sparse-Attn operator reduces computation time by **77%** on 32K context.
- **RQ2 - Token Efficiency:** Recomputing fewer than **15%** of tokens achieves up to **70%** higher normalized-F1 scores than baselines. Eliminates **82.3%** of redundant prefix context computations via the "Alternative Path" mechanism.
- **RQ3 - Platform Context:** Tested on **NVIDIA L20 GPUs**. Implements an **Asynchronous KVCache Scheduler** to hide DISK-CPU-GPU I/O latency.
- **Implementation Note:** Portable logic for Spring AI via the "Alternative Path" mechanism, which identifies unique chunks regardless of prefix context.

### 📊 Evaluation Data & Bias Note
- **Datasets Used:** TriviaQA (1-hop), HotpotQA (2-hop), Musique (4-hop), and 2WikiMultiHopQA (5-hop).
- **AI-Evaluation:** Evaluates "Faithfulness" to the retrieved context to ensure no hallucinations are induced by sparse attention.
- **Bias Assessment:** Critiques "position-dependent" selection biases (favoring chunk starts) and proposes "content-dependent" selection via query-guided attention weights.

### 💡 Key Takeaway for Thesis
FusionRAG identifies the prefill stage as the primary latency bottleneck and provides a robust, training-free solution to recover $99\%$ of generation quality with only $15\%$ recomputation, making it a state-of-the-art method for high-efficiency RAG.

### 🔗 BibTeX Reference (Target: /bib)
```bibtex
@article{wang2026fusionrag,
  title={From Prefix Cache to Fusion RAG Cache: Accelerating LLM Inference in Retrieval-Augmented Generation},
  author={Wang, Jiahao and Xie, Weiyu and Zhang, Mingxing and Zhang, Boxing and Dong, Jianwei and Zhu, Yuening and Lin, Chen and Tang, Jingqi and Han, Yaochen and Ai, Zhiyuan and others},
  journal={Proceedings of the ACM on Management of Data},
  volume={4},
  number={1},
  pages={Article 41},
  year={2026},
  publisher={ACM}
}
```
