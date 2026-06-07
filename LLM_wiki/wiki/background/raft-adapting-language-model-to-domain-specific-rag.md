# RAFT: Adapting Language Model to Domain Specific RAG
**Authors:** Tianjun Zhang, Shishir G. Patil, Naman Jain, Sheng Shen, Matei Zaharia, Ion Stoica, Joseph E. Gonzalez | **Year:** 2024
**Category:** Background

### 📝 Abstract & Relevance Summary
RAFT (Retrieval-Augmented Fine-Tuning) is a post-training recipe designed to adapt Large Language Models for domain-specific open-book settings. Traditional RAG systems face test-time noise from imperfect retrievers, while standard supervised fine-tuning (SFT) fails to utilize context documents or robustly ignore distractor/irrelevant text. RAFT solves this by training models on question-answer pairs alongside a mixture of relevant (golden) and irrelevant (distractor) documents, enforcing Chain-of-Thought (CoT) reasoning that directly cites relevant source segments. This paper is highly relevant to thesis research as it provides a practical method for balancing internal model memorization with context reading robustness, optimizing accuracy in domain-specific RAG configurations without expanding parameter scales.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** By adapting smaller open-source models (e.g., Llama-2-7B) to specialized domains, RAFT-7B matches or exceeds the performance of larger proprietary models like GPT-3.5, reducing API call latencies and serving costs.
- **Cost & Token Usage:** Evaluates the trade-offs of varying context sizes. Standard SFT models break down when test-time context expands (due to "distractor" noise), whereas RAFT remains robust to larger top-k retrieval sets. This allows developers to use smaller context windows (e.g., k=3 instead of k=10) to save tokens and inference costs without degrading quality.
- **Throughput:** By fine-tuning the model's instruction alignment, it generates concise, cited answers directly, improving overall token throughput compared to verbose, uncited generation.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Evaluates accuracy based on exact match (EM) for QA and functional correctness for code generation. Incorporating Chain-of-Thought (CoT) with verbatim citations prevents overfitting and significantly boosts accuracy.
- **Benchmark Data:** Validates across PubMed QA (medical), HotpotQA (multi-hop Wikipedia), and Gorilla APIBench (HuggingFace, Torch Hub, TensorFlow Hub).
- **Judge Setup:** Uses GPT-4-1106-preview to generate logical reasoning chains and reference answers for training and evaluation.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** Explores the trade-off of the hyperparameter P% (the proportion of training data containing the golden document). Setting P < 100% (e.g., P = 80%) forces the model to balance memorization (answering from parameters when context is missing) with extraction (filtering context when distractors are present), yielding the highest accuracy.
- **Pareto Frontier & Combinations:** Demonstrates a significant quality gain when combining RAG with domain-specific fine-tuning (RAFT) compared to naive RAG or standard SFT alone: on HotpotQA, RAFT-7B scores 35.28% EM compared to 5.03% for Llama-2-7B+RAG and 4.41% for DSF+RAG.
- **Chunking/Routing Strategy:** Robust to varying numbers of distractor documents (k) at test time. Training with 1 golden + 4 distractor documents generalizes best across variable test-time document counts.
- **Enterprise Suitability:** Particularly suitable for enterprise applications with fixed, proprietary document vaults (e.g., internal wikis, APIs, legal/medical corpuses) where the model must answer queries accurately with low latency and high trust.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Evaluates exact match compared to golden references, employing validation subsets for hyperparameter selection.
- **Bias Detection:** Highlights that standard SFT without context causes models to become confused by complex questions, failing to extract context and falling back to parameter bias.

### 🔍 Technical Implementation
- **Portability:** RAFT is a data preprocessing and training pipeline. The recipe can be implemented in any data pipeline stack (including Java tools), and the resulting fine-tuned model (e.g., standard Llama or Mistral weights) is fully compatible with Java/Spring AI frameworks.
- **Key References:**
  1. Guu et al., 2020 - *REALM: Retrieval-Augmented Language Model Pre-Training* (Foundational retrieval model pre-training)
  2. Patil et al., 2023 - *Gorilla: Large Language Model Connected with Massive APIs* (API calling benchmark)
  3. Wei et al., 2022 - *Chain-of-Thought Prompting Elicits Reasoning in Large Language Models* (CoT baselines)

### 💡 Key Takeaway for Thesis
- RAFT proves that training the LLM to ignore distractor context is critical; fine-tuning the model on imperfect retrieval simulations allows small models to maintain high RAG quality under noisy context.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@article{zhang2024raft,
  title={{RAFT}: Adapting Language Model to Domain Specific {RAG}},
  author={Zhang, Tianjun and Patil, Shishir G. and Jain, Naman and Shen, Sheng and Zaharia, Matei and Stoica, Ion and Gonzalez, Joseph E.},
  journal={arXiv preprint arXiv:2403.10131},
  year={2024}
}
```
