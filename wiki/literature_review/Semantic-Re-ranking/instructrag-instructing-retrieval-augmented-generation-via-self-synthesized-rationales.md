# INSTRUCTRAG: INSTRUCTING RETRIEVAL AUGMENTED GENERATION VIA SELF-SYNTHESIZED RATIONALES
**Authors:** Zhepei Wei, Wei-Lin Chen, Yu Meng | **Year:** 2025
**Category:** Semantic Re-ranking

### 📝 Abstract & Relevance Summary
InstructRAG is a novel framework designed to improve the noise robustness of RAG systems by teaching LLMs to explicitly "denoise" retrieved documents. The core idea is to have the model synthesize rationales that explain how a ground-truth answer is derived from a potentially noisy set of documents. These rationales are then used for either in-context learning (ICL) or supervised fine-tuning (SFT), enabling the model to ignore misleading or irrelevant information. This research is critical for RQ1 and RQ2 as it achieves an **8.3% relative improvement** in generation accuracy without human-labeled denoising data, proving that semantic-level document filtering (denoising) is a cost-effective way to handle imperfect retrieval.

### 🚀 Efficiency Metrics (Literature Review / Empirical)
- **RQ1 - Latency (TTFT):** While explicit rationale generation adds some tokens to the output, it significantly reduces the failure rate in noisy contexts, potentially avoiding the need for expensive multi-step retrieval loops.
- **RQ2 - Token Efficiency:** By training the model to articulate which documents are useful, it improves "Information-per-Token" utility, as the model becomes less susceptible to being "distracted" by irrelevant context segments.
- **RQ3 - Platform Context:** Evaluation used **NVIDIA H100 GPUs** and **Llama-3-8B/70B** models.
- **Implementation Note:** A training-free (ICL) version is available, making the denoising logic highly portable to Java/Spring AI pipelines as a pre-generation verification step.

### 📊 Evaluation Data & Bias Note
- **Datasets Used:** PopQA, TriviaQA, Natural Questions (NQ), ASQA, and 2WikiMultiHopQA.
- **AI-Evaluation:** Uses **GPT-4o as a judge** to evaluate semantic equivalence and rationale alignment, bypassing the limitations of lexical pattern matching.
- **Bias Assessment:** Identifies "Self-Preference Bias" risks when using LLMs to generate training data but mitigates this via a consistency sanity check (98% alignment with ground truth).

### 💡 Key Takeaway for Thesis
InstructRAG demonstrates that "Semantic Denoising"—teaching the model to reason about document relevance—is a vital optimization step that transforms noisy RAG inputs into high-precision generative outputs (RQ2).

### 🔗 BibTeX Reference (Target: /bib)
```bibtex
@inproceedings{wei2025instructrag,
  title={INSTRUCTRAG: INSTRUCTING RETRIEVAL AUGMENTED GENERATION VIA SELF-SYNTHESIZED RATIONALES},
  author={Wei, Zhepei and Chen, Wei-Lin and Meng, Yu},
  booktitle={International Conference on Machine Learning},
  year={2025}
}
```
