# Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks
**Authors:** Patrick Lewis, Ethan Perez, Aleksandra Piktus, Fabio Petroni, Vladimir Karpukhin, Naman Goyal, Heinrich Küttler, Mike Lewis, Wen-tau Yih, Tim Rocktäschel, Sebastian Riedel, Douwe Kiela | **Year:** 2020
**Category:** Background

### 📝 Abstract & Relevance Summary
This seminal paper introduces Retrieval-Augmented Generation (RAG), a general-purpose fine-tuning recipe that combines pre-trained parametric memory (a seq2seq model) with non-parametric memory (a dense vector index of a text corpus). The authors compare two RAG formulations: RAG-Sequence, which conditions generation on the same retrieved passage across the entire sequence, and RAG-Token, which can select different passages for each generated token. This paper is highly relevant to thesis research as it establishes the foundational RAG framework, demonstrating that decoupling memory from parameters allows smaller networks to achieve state-of-the-art quality on knowledge-intensive tasks while enabling test-time database updates without model retraining.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Discusses the decoding latency of RAG-Sequence, which requires running beam search over each of the top-K documents. Proposes "Fast Decoding" to bypass redundant forward passes for documents where candidates did not appear in the beam, optimizing latency. Note that increasing the number of retrieved documents (K) at test time linearly increases generation latency.
- **Cost & Token Usage:** Demonstrates that RAG-Sequence/Token models (totaling ~620M parameters) outperform massive parametric-only models like T5-11B (11 billion parameters) on Open-Domain QA, representing a dramatic reduction in training, hosting, and query token costs.
- **Throughput:** Utilizes FAISS with a Hierarchical Navigable Small World (HNSW) approximation to achieve sub-linear query retrieval throughput over 21 million Wikipedia passages.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Evaluates quality via Exact Match (EM) for QA, BLEU/Rouge-L for NLG, Q-BLEU-1 (entity-weighted BLEU) for question generation, and label accuracy for classification. Factuality and specificity are validated using comparative human evaluation.
- **Benchmark Data:** Evaluated on Natural Questions (NQ), TriviaQA (TQA), WebQuestions (WQ), CuratedTrec (CT), MS-MARCO NLG, Jeopardy question generation, and FEVER fact verification.
- **Judge Setup:** Employs comparative human judges to evaluate RAG-Token against a BART baseline.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** Fine-tuning the query encoder (DPR) improves performance across all tasks but requires backpropagating through MIPS. To avoid the high cost of rebuilding the index of 21M chunks during training, RAG keeps the document encoder and index fixed, trading a minor retrieval gain for a massive reduction in training latency.
- **Pareto Frontier & Combinations:** Shows that RAG-Token is superior for generation tasks requiring the synthesis of multiple disjoint facts (e.g., Jeopardy), while RAG-Sequence excels in extractive QA where the answer is contained in a single source. RAG can generate correct answers (11.8% accuracy on NQ) even when the correct answer is not present verbatim in any retrieved document.
- **Chunking/Routing Strategy:** Splits Wikipedia articles into disjoint 100-word passages. Focuses on dense retrieval (DPR) but notes that fixed BM25 retrieval outperforms DPR on FEVER fact verification due to its entity-centric, keyword-matching nature.
- **Enterprise Suitability:** Allows "hot-swapping" the index at test time to update the model's world knowledge (e.g., swapping a 2016 Wikipedia index for a 2018 index) without retraining parameters, which is highly suited for enterprise systems with frequent data updates.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Uses Hugging Face Transformers RAG implementation.
- **Bias Detection:** Analyzes article title overlap between RAG retrieval and FEVER gold annotations to ensure retrieval validity.

### 🔍 Technical Implementation
- **Portability:** Fully portable. RAG is the standard architecture of modern retrieval-generation systems. The bi-encoder retriever + seq2seq generator pipeline is natively supported by Java and Spring AI with standard vector databases (Neo4j, Pgvector) and LLM connectors.
- **Key References:**
  1. Guu et al., 2020 - *REALM: Retrieval-Augmented Language Model Pre-Training* (Latent retrieval pre-training)
  2. Karpukhin et al., 2020 - *Dense Passage Retrieval for Open-Domain Question Answering* (DPR bi-encoder)
  3. Lewis et al., 2019 - *BART: Denoising Sequence-to-Sequence Pre-training, Fine-tuning, and Paths for Natural Language Generation* (BART generator)

### 💡 Key Takeaway for Thesis
- The RAG paper shows that combining parametric and non-parametric memory allows small, resource-efficient models to match the generation quality of models 30x larger, while enabling instantaneous knowledge updates via index swapping.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@inproceedings{lewis2020rag,
  title={Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks},
  author={Lewis, Patrick and Perez, Ethan and Piktus, Aleksandra and Petroni, Fabio and Karpukhin, Vladimir and Goyal, Naman and K{\"u}ttler, Heinrich and Lewis, Mike and Yih, Wen-tau and Rockt{\"a}schel, Tim and Riedel, Sebastian and Kiela, Douwe},
  booktitle={Advances in Neural Information Processing Systems},
  volume={33},
  pages={9459--9474},
  year={2020}
}
```
