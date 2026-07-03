# Enhancing Financial Report Question-Answering: A Retrieval-Augmented Generation System with Reranking Analysis
**Authors:** Zhiyuan Cheng, Longying Lai, Yue Liu, Kai Cheng, Xiaoxi Qi | **Year:** 2026
**Category:** Semantic-Re-ranking

### 📝 Abstract & Relevance Summary
Financial analysts face significant challenges extracting details from lengthy corporate 10-K filings, which frequently exceed 100 pages. This paper presents a domain-specific RAG system designed to answer complex questions about S&P 500 financial reports and evaluates the impact of neural reranking on answer correctness. The system implements a hybrid search strategy (combining full-text SQLite FTS5 search with semantic vector search) followed by a cross-encoder reranking stage. Using the FinDER benchmark dataset, the authors demonstrate that adding a cross-encoder reranker improves the rate of highly correct answers from 33.5% to 49.0% (a 15.5 percentage point absolute increase). This work is highly relevant to understanding how hybrid retrieval and neural reranking perform on complex, domain-specific documents.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Neural reranking introduces additional runtime latency. To optimize performance, the system implements concurrent document processing using Python's async/await framework, though the paper notes that latency overhead remains a production consideration.
- **Cost & Token Usage:** While reranking adds small API and computational costs, it significantly reduces generative token consumption by filtering out non-essential chunks before passing them to the generator. The system implements a retrieval threshold where chunk ingestion stops once the cumulative probability mass reaches 55% (retaining less than 45% of the retrieved corpus).
- **Throughput:** Concurrent indexing and querying mechanisms are leveraged to maintain acceptable throughput.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Quality is measured using an LLM-as-a-judge correctness score on a scale of 1–10. Neural reranking raises the correctness rate (scores $\ge 8$) to 49.0% compared to 33.5% without reranking, and reduces completely incorrect answers (score = 1) from 35.3% to 22.5%.
- **Benchmark Data:** The FinDER benchmark dataset, comprising 1,500 queries across five experimental groups.
- **Judge Setup:** GPT-4 was employed as a judge to evaluate generated answers against ground-truth answers from the FinDER dataset.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** Neural reranking improves answer correctness by 46.3% relatively (raising the mean score from 4.95 to 6.02) and slashes critical hallucinations by 36.3%. The trade-off is a slight increase in serving latency and API costs.
- **Pareto Frontier & Combinations:** Combining hybrid search (BM25 lexical search + dense vector retrieval) with cross-encoder reranking yields a much stronger recall-precision trade-off than dense-only or sparse-only retrieval pipelines.
- **Chunking/Routing Strategy:** Chunks are configured with a size of 2,500 characters and a 50% overlap (1,250 characters).
- **Enterprise Suitability:** Essential for financial applications where high precision is required, as errors in data interpretation can lead to critical business consequences.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** FinDER benchmark evaluation framework.
- **Bias Detection:** The authors acknowledge that the LLM-as-a-judge approach may prefer verbose answers or penalize valid alternative phrasings. To mitigate variance, they report averages across five random splits of 300 queries each.

### 🔍 Technical Implementation
- **Portability:** Highly portable to a Java/Spring AI stack. The software stack uses SQLite FTS5 for lexical search, FAISS for vector search, and OpenAI's API. A Spring AI application can implement this using PGVector/Elasticsearch (for hybrid search) and a standard cross-encoder model for reranking.
- **Key References:**
  - LinqAlpha (2024) FinDER: A Benchmark for Financial Document Extraction and Reasoning
  - Chen et al. (2023) FrugalGPT: How to Use Large Language Models While Reducing Cost and Improving Performance

### 💡 Key Takeaway for Thesis
- This study proves that neural reranking is crucial for domain-specific RAG on dense text (like financial 10-Ks), increasing high-quality answer correctness by 15.5% absolute and reducing critical errors by 12.8% when layered on top of hybrid retrieval.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@article{cheng2026enhancing,
  title={Enhancing Financial Report Question-Answering: A Retrieval-Augmented Generation System with Reranking Analysis},
  author={Cheng, Zhiyuan and Lai, Longying and Liu, Yue and Cheng, Kai and Qi, Xiaoxi},
  journal={arXiv preprint arXiv:2603.16877},
  year={2026}
}
```
