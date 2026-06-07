# Prompt Cache: Modular Attention Reuse for Low-Latency Inference
**Authors:** In Gim, Guojun Chen, Seung-seob Lee, Nikhil Sarda, Anurag Khandelwal, and Lin Zhong | **Year:** 2024
**Category:** Prompt-Caching

### 📝 Abstract & Relevance Summary
Large language models (LLMs) often receive prompts with overlapping segments, such as system messages, prompt templates, and reference documents. Reusing key-value (KV) attention states across different requests is challenging because transformers rely on position-dependent embeddings. The authors propose **Prompt Cache**, a schema-driven approach that enables modular attention reuse for low-latency inference. By defining reusable segments (prompt modules) using a specialized markup language (PML), Prompt Cache precomputes and caches their attention states, then stitches them together dynamically for new queries. This system addresses **Operational Efficiency** by bypassing prefill computation and reducing Time to First Token (TTFT) without compromising the **Output Quality** or requiring model parameter fine-tuning.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Reduces Time to First Token (TTFT) latency by **1.5x to 10x** for GPU-based inference (when prompt modules are loaded from GPU HBM memory), and by **20x to 70x** for CPU-based inference (AMD Ryzen Ryzen 9 7950X achieves 20x, Intel i9-13900K achieves 70x). On a 3K token context using RTX 4090 and Llama-7B, Prompt Cache reduces TTFT from 900ms to 90ms.
- **Cost & Token Usage:** Memory consumption per token scales linearly: BERT: 0.03 MB/token, Falcon 1B: 0.18 MB/token, Llama 7B: 0.50 MB/token, Llama 13B: 0.78 MB/token, MPT 30B: 1.31 MB/token, Falcon 40B: 1.87 MB/token, Llama 70B: 2.50 MB/token, Falcon 180B: 4.53 MB/token. For Llama 70B, caching a 1K token document requires **2.5 GB** of memory. When combined with PagedAttention, sharing a 1K token prompt module across a batch of 100 concurrent requests reduces the memory footprint by **50%**.
- **Throughput:** Sharing attention states across concurrent requests within the same batch significantly improves system throughput by reclaiming GPU memory.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Accuracy (for passage retrieval), ROUGE-L (for summarization tasks), and F1 Score (for multi-document QA).
- **Benchmark Data:** Curated subsamples from the **LongBench** suite (Narrative QA, 2WikiQA, MuSiQue, GovReport, QMSum, MultiNews, TriviaQA, Passage Retrieval), with context lengths ranging from 4K to 10K tokens.
- **Judge Setup:** Standard deterministic sampling (greedy decoding) was used to compare output accuracy directly against the default KV cache baseline. No LLM-as-a-judge was required.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** Across all datasets, the accuracy of outputs generated using Prompt Cache is comparable to the baseline, introducing negligible quality degradation.
- **Pareto Frontier & Combinations:** Memory copy overhead grows linearly, whereas computing attention from scratch grows quadratically. Thus, the latency reduction advantage of Prompt Cache scales quadratically with sequence length. Copying cached attention states from CPU host DRAM to GPU memory adds a small latency overhead (~5.34ms for 5K tokens), which is easily offset by the prefill calculation savings.
- **Chunking/Routing Strategy:** Operates on user-defined prompt modules in **Prompt Markup Language (PML)**. Dynamic parameters (e.g. `<param name="duration" len=2/>`) act as placeholders represented by `<unk>` tokens during caching. Mutually exclusive modules are defined using `<union>` tags and share the same starting position ID.
- **Enterprise Suitability:** Extremely well-suited for production systems processing structured prompt templates, long system instructions, or static context documents (e.g. legal and healthcare text corpuses).

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Evaluated using LongBench benchmarks and implemented on top of the Hugging Face transformers library.
- **Bias Detection:** Masking attention span to the boundaries of each prompt module acts as a local attention mask. This can introduce beneficial inductive bias by filtering out irrelevant context, but it can also cause quality issues for semantically dependent modules. The authors propose "scaffolding" (encoding dependent modules together) to mitigate this at the expense of memory.

### 🔍 Technical Implementation
- **Portability:** The PML schema, backtracking cache matching, and modular prompt structure are language-agnostic and fully portable to a Java/Spring AI stack. However, the custom PyTorch implementation requires approximately 20 lines of modification per LLM architecture to support discontinuous position IDs in RoPE/ALiBi positional encodings.
- **Key References:**
  - *PagedAttention:* Kwon et al., 2023 (Efficient Memory Management for Large Language Model Serving)
  - *FlashAttention:* Dao et al., 2022 (Fast and Memory-Efficient Exact Attention with IO-Awareness)
  - *Attention Complexities:* Tay et al., 2023 (Efficient Transformers: A Survey)

### 💡 Key Takeaway for Thesis
- Prompt Cache demonstrates that precomputing and reuse of modular, position-aligned prompt KVCaches yields up to a 10x GPU TTFT reduction with zero accuracy degradation, highlighting the value of structured prompt design.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@inproceedings{gim2024promptcache,
  title={Prompt Cache: Modular Attention Reuse for Low-Latency Inference},
  author={Gim, In and Chen, Guojun and Lee, Seung-seob and Sarda, Nikhil and Khandelwal, Anurag and Zhong, Lin},
  booktitle={Proceedings of the 5th MLSys Conference},
  year={2024}
}
```
