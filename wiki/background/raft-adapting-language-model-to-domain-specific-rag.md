# RAFT: Adapting Language Model to Domain Specific RAG
**Authors:** Tianjun Zhang, Shishir G. Patil, Naman Jain, et al. | **Year:** 2024
**Category:** Background

### 📝 Abstract & Relevance Summary
RAFT (Retrieval Augmented Fine Tuning) is a training strategy designed to improve an LLM's ability to answer questions in domain-specific "open-book" RAG settings. Unlike standard fine-tuning which may just "memorize" facts, RAFT trains the model to understand the dynamics between a question, relevant documents, and distracting irrelevant documents (distractors). The model is trained to cite verbatim the correct sequences from relevant text while ignoring noise. This is critical for **Output Quality**, as it significantly reduces hallucination and improves reasoning through RAFT's chain-of-thought response style. It also impacts **Operational Efficiency** by enabling smaller local models to achieve high accuracy in specialized domains.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** By improving the model's robustness to distractors, it reduces the need for multiple corrective retrieval cycles, potentially lowering end-to-end latency in noisy environments.
- **Token Usage:** The model is trained to generate concise, chain-of-thought style answers with direct citations, ensuring token consumption is focused on verifiable evidence.
- **Throughput:** RAFT-7B (fine-tuned Llama-2) outperforms much larger models like GPT-3.5 in specialized domains, proving that smaller, efficiently fine-tuned models are a viable high-throughput alternative.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Exact Match (EM) and Accuracy.
- **Benchmark Data:** Evaluated on PubMed, HotpotQA, and Gorilla API Bench.
- **Judge Setup:** Uses GPT-4-1106 to generate Chain-of-Thought prompts and evaluate the reasoning robustness of the fine-tuned model.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** RAFT achieves a significant accuracy leap (e.g., +35.25% on HotpotQA compared to zero-shot Llama-2 with RAG) by investing in a domain-specific fine-tuning step. It provides a superior quality equilibrium for "open-book" scenarios compared to standard Supervised Fine Tuning (SFT).
- **Chunking Strategy:** Training data utilizes a mix of "golden" documents (relevant) and "distractor" documents (irrelevant), typically using 1 relevant document and 4 distractors per question.
- **Enterprise Suitability:** Ideal for production-grade RAG in specialized fields (legal, medical, or private codebases) where high-precision reasoning is non-negotiable.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Extensive benchmark across varied domains (Wikipedia, medical, API docs).
- **Bias Detection:** Analyzes the "reversal curse" and the impact of varying the percentage (P%) of training data with golden documents to optimize in-domain robustness.

### 🔍 Technical Implementation
- **Portability:** The training recipe is applicable to any fine-tunable model (e.g., Llama, Mistral) and can be orchestrated in a Spring AI environment once the fine-tuned adapter is deployed.
- **Key References:** 
  - Lewis et al., 2020 (Original RAG)
  - Brown et al., 2020 (Few-shot learning)
  - Wei et al., 2022 (Chain-of-thought)

### 💡 Key Takeaway for Thesis
RAFT proves that training models to explicitly ignore distractor documents and cite relevant evidence is the foundational "study guide" for optimizing domain-specific RAG quality and reliability.

### 🔗 BibTeX Reference (Target: /bib)
```bibtex
@article{zhang2024raft,
  title={{RAFT}: Adapting Language Model to Domain Specific {RAG}},
  author={Zhang, Tianjun and Patil, Shishir G. and Jain, Naman and Shen, Sheng and Zaharia, Matei and Stoica, Ion and Gonzalez, Joseph E.},
  journal={arXiv preprint arXiv:2403.10131},
  year={2024}
}
```