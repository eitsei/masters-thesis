# Prompt Cache: Modular Attention Reuse for Low-Latency Inference
**Authors:** In Gim, Guojun Chen, Seung-seob Lee, Nikhil Sarda, Anurag Khandelwal, Lin Zhong | **Year:** 2024
**Category:** Prompt Caching

### 📝 Abstract & Relevance Summary
Prompt Cache accelerates Large Language Model (LLM) inference by reusing attention states across different prompts. The core problem addressed is the redundant computation of overlapping text segments common in RAG pipelines, such as system prompts, templates, and retrieved contexts. By introducing a schema-based approach to precompute and store these "prompt modules," the system achieves substantial latency reduction. This is highly relevant to optimizing operational efficiency in enterprise systems, as it drastically cuts down processing time without compromising the quality of the generated output.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Time-to-first-token (TTFT) latency is reduced by 1.5× to 10× for GPU-based inference (when prompt modules are in GPU memory) and from 20× up to 70× for CPU-based inference.
- **Token Usage:** While it does not explicitly reduce the number of tokens sent to the API, it significantly reduces the FLOPs and computation time for those tokens by bypassing attention computation for cached text segments.
- **Throughput:** By reducing the memory footprint of precomputed attention states and allowing sharing across concurrent requests, Prompt Cache can enable larger batch sizes, thereby improving overall throughput in production environments.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** The paper evaluates output quality using deterministic sampling and standard metrics like F1 Score, ROUGE-L, and Accuracy. Results confirm negligible quality degradation compared to baseline non-cached inference.
- **Benchmark Data:** Evaluated on the LongBench suite (comprising 21 datasets across 6 categories, including multi-document QA, summarization, and code completion) with context lengths ranging from 4K to 10K.
- **Judge Setup:** Standard quantitative metrics (F1, ROUGE) were used; an LLM-as-a-judge was not specifically highlighted in this context.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** The approach yields massive latency reductions (up to 70x TTFT improvement on CPU) while maintaining accuracy fully comparable to baseline models, indicating a near-perfect efficiency-quality equilibrium.
- **Chunking Strategy:** Context is organized into "prompt modules" via a Prompt Markup Language (PML), allowing specific chunks (e.g., individual documents in RAG) to be independently cached and reused.
- **Enterprise Suitability:** Highly suitable for enterprise scale. It leverages abundant CPU memory (host DRAM) to store terabytes of precomputed states, making it viable for large-scale, resource-constrained, or cloud server deployments.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Extensive evaluation using Llama2 (7B, 13B), MPT 7B, and Falcon 7B on the LongBench benchmark.
- **Bias Detection:** The evaluation relied on established open-source benchmarks with fixed metrics, reducing the risk of self-preference bias.

### 🔍 Technical Implementation
- **Portability:** The optimization is implemented at the lower-level KV cache/attention computation layer (e.g., modifying HuggingFace Transformers in PyTorch). It is not directly portable to a pure Java/Spring AI stack unless the backend inference engine (like vLLM or a custom model server) explicitly supports this level of attention caching.
- **Key References:** 
  - Pope et al., 2022 (Efficiently scaling transformer inference)
  - Kwon et al., 2023 (PagedAttention)

### 💡 Key Takeaway for Thesis
Prompt Cache proves that modular, structural reuse of attention states in the KV cache can drastically slash time-to-first-token latency for repetitive RAG contexts, serving as a critical foundational optimization for high-efficiency enterprise deployments.
