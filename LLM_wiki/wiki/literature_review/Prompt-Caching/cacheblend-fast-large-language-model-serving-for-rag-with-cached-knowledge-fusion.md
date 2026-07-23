# CacheBlend: Fast Large Language Model Serving for RAG with Cached Knowledge Fusion
**Authors:** Jiayi Yao, Hanchen Li, Yuhan Liu, Siddhant Ray, Yihua Cheng, Qizheng Zhang, Kuntai Du, Shan Lu, Junchen Jiang | **Year:** 2025
**Category:** Prompt-Caching

### 📝 Abstract & Relevance Summary
In Retrieval-Augmented Generation (RAG), retrieved document chunks appear in arbitrary orders and positions, preventing direct reuse of precomputed key-value (KV) caches due to uncaptured cross-attention between chunks. Full KV recomputation eliminates accuracy loss but causes severe prefill latency, while naive full KV cache reuse causes drastic quality degradation. CacheBlend resolves this dilemma by reusing precomputed chunk KV caches regardless of prefix position and selectively recomputing KV values for only a small subset of tokens sensitive to cross-attention. By pipelining this lightweight recomputation with KV cache retrieval from host RAM/SSD storage, CacheBlend achieves significant operational efficiency gains (2.2–3.3x TTFT reduction, up to 5x throughput gain) while maintaining output quality within 0.01–0.03 of full prefill.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Reduces Time-To-First-Token (TTFT) by 2.2x to 3.3x compared to full KV recomputation across Mistral-7B, Yi-34B, and Llama-70B.
- **Cost & Token Usage:** Drastically lowers prefill FLOPs by selectively recomputing cross-attention for only a small fraction of tokens (e.g., ~15–20%). Enables storing KV caches on cost-effective NVMe SSD storage without incurring extra latency penalties.
- **Throughput:** Increases throughput by up to 5.0x compared to full KV recomputation and up to 3.3x compared to standard prefix caching at equal TTFT thresholds.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Evaluated using F1-score (multi-hop QA) and ROUGE-L (summarization tasks).
- **Benchmark Data:** 2WikiMQA, Musique (including synthetic extended multi-query datasets), SAMSum, and MultiNews evaluated on Mistral-7B, Yi-34B, and 8-bit quantized Llama-70B.
- **Judge Setup:** Standard automated scoring (F1, ROUGE-L) and GPT-4 generated multi-query variations to simulate real-world document reuse.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** Naive full KV reuse degrades accuracy by 0.15–0.35 in F1/ROUGE-L. CacheBlend restores quality to within 0.01–0.03 of full KV recomputation while delivering 2.2–3.3x TTFT speedup.
- **Pareto Frontier & Combinations:** Combines selective token-level cross-attention recomputation with NVMe storage retrieval pipelining (hiding transfer latency behind computation).
- **Chunking/Routing Strategy:** Evaluated on 512-token chunks (LangChain chunking) across top-6 retrieved context chunks.
- **Enterprise Suitability:** High for production RAG systems with massive document libraries where storing full KV caches in expensive GPU VRAM is cost-prohibitive.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Built on top of vLLM, evaluated on dual NVIDIA A40 GPU servers with NVMe SSD storage.
- **Bias Detection:** Tested across both multi-hop reasoning (Musique, 2WikiMQA) and abstractive summarization (MultiNews) to detect potential positional and domain biases.

### 🔍 Technical Implementation
- **Portability:** Implemented directly within the vLLM engine prefill pipeline. Language-agnostic at the application level; Java/Spring AI systems interface with CacheBlend via vLLM REST endpoints seamlessly.
- **Key References:**
  1. Gim et al. (2024) - *Prompt Cache: Modular Attention Reuse for Low-Latency Inference*
  2. Kwon et al. (2023) - *Efficient Memory Management for Large Language Model Serving with PagedAttention*
  3. Liu et al. (2024) - *CacheGen: Fast Context Loading for Language Model Applications*

### 💡 Key Takeaway for Thesis
CacheBlend proves that selective cross-attention recomputation of a small token subset enables order-agnostic KV cache reuse from host storage, achieving a 3.3x TTFT reduction and 5x throughput increase with virtually zero quality loss (<0.03 drop).

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@inproceedings{yao2025cacheblend,
  title={CacheBlend: Fast Large Language Model Serving for RAG with Cached Knowledge Fusion},
  author={Yao, Jiayi and Li, Hanchen and Liu, Yuhan and Ray, Siddhant and Cheng, Yihua and Zhang, Qizheng and Du, Kuntai and Lu, Shan and Jiang, Junchen},
  booktitle={Proceedings of the 20th European Conference on Computer Systems (EuroSys '25)},
  pages={95--110},
  year={2025},
  publisher={ACM}
}
```
