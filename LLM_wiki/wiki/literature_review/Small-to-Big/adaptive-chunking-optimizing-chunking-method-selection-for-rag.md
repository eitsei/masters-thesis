# Adaptive Chunking: Optimizing Chunking-Method Selection for RAG
**Authors:** Paulo Roberto de Moura Júnior, Jean Lelong, Annabelle Blangero | **Year:** 2026
**Category:** Small-to-Big

### 📝 Abstract & Relevance Summary
This paper introduces the Adaptive Chunking framework, which challenges the "one-size-fits-all" monolithic chunking approaches by dynamically selecting the most suitable chunking method for each document. The selection is guided by a suite of five intrinsic, document-based metrics: References Completeness (RC), Intrachunk Cohesion (ICC), Document Contextual Coherence (DCC), Block Integrity (BI), and Size Compliance (SC). The framework is highly relevant to balancing operational efficiency and output quality, demonstrating that tailoring the chunking strategy to individual document structures raises downstream answer correctness to 72% (up from 62–64%) and increases successful answers by over 30% without modifying downstream models or prompts.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Computational overhead varies widely by chunking strategy. The LLM regex splitter (using GPT-5) requires 146.85 seconds, the recursive splitter (s=1100) takes 31.42 seconds, and semantic chunking requires 389.25 seconds. The Adaptive Chunking framework dynamically selects the chunker, averaging 210.66 seconds for execution (including API wait times) across the corpus.
- **Cost & Token Usage:** Two post-processing passes—oversized-chunk splitting (limit 1,100 tokens) and tiny-chunk merging (minimum 100 tokens)—regularize chunk sizes. Merging tiny chunks prevents context-poor segments from occupying retrieval slots, maximizing the value of dense retrieval and reducing token wastage.
- **Throughput:** While LLM-based chunking is slow, the post-processed splitters execute quickly (e.g., recursive splitters under 32s), preserving offline ingestion throughput.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Retrieval Completeness (scored 0 to 2 by an LLM judge and normalized to [0,1], verifying if context supports ground truth answers) and Answer Correctness (G-Eval 0-1 scale assessing factual alignment).
- **Benchmark Data:** Evaluated on the CLAIR corpus comprising 33 diverse PDFs (1.18 million tokens) spanning legal, technical, and social science documents.
- **Judge Setup:** GPT-4.1 was used to generate ground truth QA pairs (3 per document) and served as the judge model for Retrieval Completeness and G-Eval scoring.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** Adaptive chunking improves answer correctness from 62-64% (recursive baseline) to 72% and increases successfully answered questions from 49 to 65. This gain is achieved with no changes to LLM prompts or weights, shifting the Pareto frontier upwards.
- **Pareto Frontier & Combinations:** A key trade-off is observed between Intrachunk Cohesion (ICC) and Document Contextual Coherence (DCC): smaller chunks have high ICC but low DCC (topical focus at the expense of surrounding context), while larger chunks show the reverse. Similarly, high Block Integrity (BI) can drag down ICC by forcing mixed tables and text into the same chunk.
- **Chunking/Routing Strategy:** The Adaptive selection policy picks *page (post-processed)* for 48% of documents, *recursive (s=1100)* for 42%, *LLM-regex* for 6%, and *recursive (s=600)* for 3%. Target chunk sizes are bounded between 100 and 1,100 tokens.
- **Enterprise Suitability:** Tailoring splitters to specific document hierarchies (such as legal text articles vs. narrative pages) ensures production RAG pipelines remain both accurate and cost-effective.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Implemented using DeepEval (G-Eval for correctness, custom Retrieval Completeness for recall).
- **Bias Detection:** The References Completeness (RC) metric utilizes the Maverick coreference resolution model, which is limited to English-language texts, introducing a language bias in the evaluation.

### 🔍 Technical Implementation
- **Portability:** Highly portable to Java/Spring AI. The regex splitters, recursive separators, and greedy merging steps are straightforward to write in Java. The coreference-based RC metric relies on Maverick (Python), but the remaining metrics and splitters are fully language-agnostic.
- **Key References:**
  - Duarte et al., 2024 (LumberChunker)
  - Günther et al., 2025 (Late Chunking)
  - Qu et al., 2025 (Is semantic chunking worth the cost?)

### 💡 Key Takeaway for Thesis
- Rather than optimizing a single global chunk size, enterprise-grade RAG systems must utilize document-aware, adaptive chunking policies to resolve the semantic-cohesion vs. context-preservation trade-off dynamically.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@article{demourajunior2026adaptive,
  author    = {Paulo Roberto de Moura J{\'u}nior and Jean Lelong and Annabelle Blangero},
  title     = {Adaptive Chunking: Optimizing Chunking-Method Selection for RAG},
  journal   = {arXiv preprint arXiv:2603.25333},
  year      = {2026}
}
```
