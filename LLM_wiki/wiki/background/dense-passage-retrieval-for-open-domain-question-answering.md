# Dense Passage Retrieval for Open-Domain Question Answering
**Authors:** Vladimir Karpukhin, Barlas Oğuz, Sewon Min, Patrick Lewis, Ledell Wu, Sergey Edunov, Danqi Chen, Wen-tau Yih | **Year:** 2020
**Category:** Background

### 📝 Abstract & Relevance Summary
Open-domain question answering relies on efficient passage retrieval to select candidate contexts. Traditionally, sparse vector space models like TF-IDF or BM25 have been the de facto standard. This paper introduces Dense Passage Retrieval (DPR), demonstrating that retrieval can be practically implemented using dense representations alone, where embeddings are learned from a small number of questions and passages via a simple dual-encoder framework. DPR outperforms a strong Lucene-BM25 system by 9%–19% absolute in terms of top-20 passage retrieval accuracy. This foundational work is highly relevant to establishing baseline retrieval quality and understanding dense retrieval latency compared to sparse models in enterprise-grade RAG.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** DPR retrieval is highly efficient. Given a question at run-time, query embedding takes approximately 20 ms. Retrieval of the top 100 passages from a 21-million passage index is completed in milliseconds using FAISS (approximately 995 questions per second on a single thread).
- **Cost & Token Usage:** In contrast to sparse indices which are cheap to build, DPR requires computing dense embeddings on a 21-million passage corpus offline, which is computationally expensive (requiring GPU cluster hours). However, the resulting representations are compact (768 dimensions using BERT-base), reducing memory footprints.
- **Throughput:** DPR achieves high throughput during inference, serving 995.0 questions per second per CPU thread using a pre-built FAISS index.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Evaluated primarily using Top-K Retrieval Accuracy (percentage of top K retrieved passages that contain the answer) and End-to-End QA Exact Match (EM) score. Top-20 retrieval accuracy on Natural Questions (NQ) reaches 78.4% compared to 59.1% for BM25.
- **Benchmark Data:** Evaluated on five QA datasets: Natural Questions (NQ), TriviaQA, WebQuestions (WQ), TREC, and SQuAD.
- **Judge Setup:** Standard exact match (EM) scoring against ground truth answer spans; no LLM-as-judge setup was used due to the pre-LLM publication date.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** DPR shifts the Pareto frontier by significantly increasing retrieval accuracy (e.g., top-5 accuracy is 65.2% vs. 42.9% for BM25) at the cost of higher indexing time and memory consumption compared to BM25.
- **Pareto Frontier & Combinations:** When combined with BM25 (hybrid dense-sparse retrieval), it can achieve even higher retrieval accuracy, capturing both semantic similarities (DPR) and exact keyword matches (BM25).
- **Chunking/Routing Strategy:** Evaluated using fixed-length passages (100 tokens), which performed better than variable-length or paragraph-based chunking.
- **Enterprise Suitability:** Production-grade deployments must balance the high initial indexing cost of dense retrieval with its superior retrieval precision and sub-20 ms latency.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Standard academic Information Retrieval (IR) evaluation pipelines and exact span matching.
- **Bias Detection:** Analyzed different types of negative passage selection (random, BM25-based hard negatives, and gold negatives) to show that BM25 hard negatives are crucial for training high-quality encoders.

### 🔍 Technical Implementation
- **Portability:** Highly portable to a Java/Spring AI stack. The bi-encoder model (BERT-base) can be converted to ONNX and run via ONNX Runtime in Java, and embeddings can be indexed and queried in production vector databases (e.g., Qdrant, PGVector, Milvus).
- **Key References:** 
  - Lee et al. (2019) Latent Retrieval for Weakly Supervised Open-Domain Question Answering (ORQA)
  - Guu et al. (2020) REALM: Retrieval-Augmented Language Model Pre-Training
  - Robertson & Zaragoza (2009) BM25

### 💡 Key Takeaway for Thesis
- DPR establishes the viability of dense passage retrieval as a superior alternative to sparse BM25 retrieval, showing that dual-encoder BERT models can achieve >15% higher recall with sub-20 ms query latencies, acting as the foundation for modern RAG.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@inproceedings{karpukhin2020dense,
  title={Dense Passage Retrieval for Open-Domain Question Answering},
  author={Karpukhin, Vladimir and O{\u{g}}uz, Barlas and Min, Sewon and Lewis, Patrick and Wu, Ledell and Edunov, Sergey and Chen, Danqi and Yih, Wen-tau},
  booktitle={Proceedings of the 2020 Conference on Empirical Methods in Natural Language Processing (EMNLP)},
  pages={6769--6781},
  year={2020},
  publisher={Association for Computational Linguistics}
}
```
