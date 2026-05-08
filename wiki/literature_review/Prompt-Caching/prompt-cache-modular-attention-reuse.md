# Prompt Cache: Modular Attention Reuse for Low-Latency Inference
**Authors:** In Gim, Guojun Chen, Seung-seob Lee, Nikhil Sarda, Anurag Khandelwal, Lin Zhong | **Year:** 2024
**Category:** Prompt Caching

### 📝 Abstract & Relevance Summary
Prompt Cache is a modular attention reuse framework designed to accelerate LLM inference by precomputing and caching the attention states (KVCache) of frequently used prompt segments (e.g., system messages, templates, and reference documents). By reusing these states, the system avoids redundant quadratic self-attention computations for the prompt's prefill stage. This research is vital for RQ1 and RQ2 as it demonstrates TTFT reductions of up to 8× on GPUs and 60× on CPUs for long-context tasks. It establishes that attention states can be reused even with discontinuous position IDs, enabling a "lego-like" assembly of prompts from cached modules.

### 🚀 Efficiency Metrics (Literature Review / Empirical)
- **RQ1 - Latency (TTFT):** Achieves **1.5× to 10×** reduction for GPU inference and **20× to 70×** for CPU inference. TTFT for a 3K context on an RTX 4090 dropped from **900ms to 90ms**.
- **RQ2 - Token Efficiency:** Reuses precomputed attention states for the majority of the prompt; computation is only required for the new user query and non-cached segments.
- **RQ3 - Platform Context:** Evaluation covers **NVIDIA RTX 4090, A40, A100** and high-end CPUs (Intel i9-13900K). Explores host-to-device memory copy trade-offs.
- **Implementation Note:** Requires approximately 20 lines of additional code per LLM to support discontinuous position IDs, making it highly portable to Java/Spring AI-based inference servers.

### 📊 Evaluation Data & Bias Note
- **Datasets Used:** LongBench suite (NarrativeQA, 2WikiMultiHopQA, MuSiQue, TriviaQA, etc.) with contexts from 4K to 10K tokens.
- **AI-Evaluation:** Deterministic sampling used to ensure accuracy is comparable to baseline Full Attention.
- **Bias Assessment:** Identifies "Attention Masking" as a beneficial inductive bias that can filter irrelevant noise from context documents, potentially improving output faithfulness.

### 💡 Key Takeaway for Thesis
Prompt Cache proves that the "Quadratic Bottleneck" of self-attention can be bypassed by treating prompt context as a collection of modular, reusable precomputed states, which is essential for high-throughput, low-latency RAG (RQ1).

### 🔗 BibTeX Reference (Target: /bib)
```bibtex
@inproceedings{gim2024prompt,
  title={Prompt Cache: Modular Attention Reuse for Low-Latency Inference},
  author={Gim, In and Chen, Guojun and Lee, Seung-seob and Sarda, Nikhil and Khandelwal, Anurag and Zhong, Lin},
  booktitle={Proceedings of the 5th MLSys Conference},
  year={2024}
}
```
