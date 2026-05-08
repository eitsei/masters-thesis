# QPaug: Question and Passage Augmentation for Open-Domain Question Answering of LLMs
**Authors:** Minsang Kim, Cheoneum Park, Seungjun Baek | **Year:** 2024
**Category:** Small-to-Big

### 📝 Abstract & Relevance Summary
QPaug (Question and Passage Augmentation) is a dual-augmentation framework designed to enhance Open-Domain Question Answering (ODQA) by combining parametric and non-parametric knowledge. The method involves: (1) **Question Augmentation**, which uses zero-shot Chain-of-Thought (CoT) to decompose complex queries into detailed plans, making retrieval more specific; and (2) **Passage Augmentation**, where the LLM self-generates a passage to complement potentially noisy or incomplete retrieved context. This research is highly relevant to RQ1 and RQ2 as it demonstrates up to a **30% improvement** in retrieval recall and significant performance gains on multi-hop benchmarks using a compact set of passages ($K=10$), effectively optimizing information density per token.

### 🚀 Efficiency Metrics (Literature Review / Empirical)
- **RQ1 - Latency (TTFT):** By improving retrieval recall by **30%**, QPaug reduces the "miss" rate that often triggers expensive multi-step retrieval loops in iterative RAG.
- **RQ2 - Token Efficiency:** Demonstrates that a single high-quality LLM-generated passage, when combined with only **9** retrieved passages, outperforms baseline RAG using **10** retrieved passages, supporting more concise context windows.
- **RQ3 - Platform Context:** Evaluation included proprietary models (GPT-4, GPT-3.5) and open-source models (**Llama-2-7b/70b**).
- **Implementation Note:** Implemented via **LangChain**; the logic is highly portable to Spring AI using specialized prompts for query planning and "Internal Knowledge" synthesis.

### 📊 Evaluation Data & Bias Note
- **Datasets Used:** Natural Questions (NQ), 2wiki hop questions, and HotpotQA.
- **AI-Evaluation:** Employs "LLM-as-a-generator" for both query expansion and context augmentation.
- **Bias Assessment:** To prevent hallucinations, the model is explicitly instructed to generate **[NONE]** if knowledge is missing. Self-generated context is labeled "Your Knowledge:" to mitigate its bias against external retrieved facts.

### 💡 Key Takeaway for Thesis
QPaug proves that "Small-to-Big" transitions—expanding a query into a detailed plan and augmenting context with synthesized knowledge—can dramatically improve RAG precision while keeping the context window (and token costs) manageable (RQ2).

### 🔗 BibTeX Reference (Target: /bib)
```bibtex
@article{kim2024qpaug,
  title={QPaug: Question and Passage Augmentation for Open-Domain Question Answering of LLMs},
  author={Kim, Minsang and Park, Cheoneum and Baek, Seungjun},
  journal={arXiv preprint arXiv:2406.14277},
  year={2024}
}
```
