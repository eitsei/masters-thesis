# ARAGOG: Advanced RAG Output Grading
**Authors:** Matouš Eibich, Shivay Nagpal, Alexander Fred-Ojala | **Year:** 2024
**Category:** Survey

### 📝 Abstract & Relevance Summary
This paper presents a systematic evaluation of various advanced Retrieval-Augmented Generation (RAG) techniques, including Sentence-window retrieval, Document summary index, HyDE, Multi-query, MMR, and rerankers (Cohere, LLM-based). It addresses the lack of comparative experimental studies in advanced retrieval methods, measuring their direct impacts on retrieval precision and answer similarity. The study holds significant relevance to the operational efficiency vs. output quality balance, showing that techniques that optimize retrieval precision often come with severe latency and cost trade-offs, and that high retrieval precision does not always translate to improved end-to-end answer quality.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Utilizing advanced methods like Hypothetical Document Embedding (HyDE) and LLM-based reranking significantly enhances retrieval precision but incurs higher latency due to the additional LLM calls required.
- **Cost & Token Usage:** Advanced methods (HyDE, LLM-based reranking) increase token usage and financial costs due to multiple reasoning and generation steps per query. Multi-query approaches vectorized and processed multiple sub-queries, increasing database query count and reranking overhead.
- **Throughput:** Not directly measured, but highly complex pipelines containing sequential LLM invocations (e.g., query expansion followed by LLM-based reranking) introduce processing bottlenecks.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Retrieval Precision (percentage of retrieved context relevant to the query, graded by an LLM evaluator) and Answer Similarity (assessed on a scale of 0 to 5 comparing generated answers to reference responses).
- **Benchmark Data:** Evaluated on a dataset comprising 107 challenging, human-validated question-answer pairs derived from the AI ArXiv collection (423 research papers).
- **Judge Setup:** GPT-3.5-turbo was employed as the evaluator/judge via the Tonic Validate package.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** The combination of HyDE and LLM Rerank emerged as the most potent configuration for enhancing retrieval precision. However, this comes at the cost of higher latency and token expenses. A close second is HyDE alone, which avoids the additional reranking LLM overhead.
- **Pareto Frontier & Combinations:**
  - *Sentence-window retrieval:* Emerged as the overall leading technique for retrieval precision, statistically outperforming the best Classic VDB configurations. However, it showed a disparity: high retrieval precision did not translate to high Answer Similarity, indicating that the generation phase struggled to leverage the sentence-window context.
  - *MMR & Cohere Rerank:* Did not show statistically significant improvements in retrieval precision over the Naive RAG baseline.
  - *Multi-query:* Underperformed compared to the Naive RAG baseline, indicating negative transfer in precision.
- **Chunking/Routing Strategy:**
  - *Classic VDB:* TokenTextSplitter with chunk size of 512 tokens and overlap of 50 tokens.
  - *Sentence-window:* SentenceWindowNodeParser with a window size of 3 sentences.
  - *Document summary index:* TokenTextSplitter with chunk size of 3072 tokens and overlap of 100 tokens.
- **Enterprise Suitability:** Document Summary Index is highlighted as a competent, scalable alternative to classic vector databases, although it requires an upfront computational investment to pre-generate document summaries.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Evaluated using the Tonic Validate package. 10 independent runs were conducted per experiment to mitigate LLM output variability, with significance verified via ANOVA and Tukey's HSD tests.
- **Bias Detection:** Using GPT-3.5-turbo instead of GPT-4 as an LLM judge due to budget constraints potentially limits the grading depth.

### 🔍 Technical Implementation
- **Portability:** Fully portable to Java/Spring AI since the architectures (sentence window, summaries, cuckoo filtering entry points) are language-agnostic.
- **Key References:**
  - Gao et al., 2022 (HyDE)
  - Carbonell and Goldstein, 1998 (MMR)
  - Liu, 2023a (Document Summary Index)

### 💡 Key Takeaway for Thesis
- The paper highlights a crucial disparity where higher retrieval precision (specifically in Sentence Window Retrieval) does not guarantee better answer similarity, proving that generation prompts and model capability must be co-optimized with retrieval granularity.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@article{eibich2024aragog,
  author    = {Matou{\v{s}} Eibich and Shivay Nagpal and Alexander Fred-Ojala},
  title     = {ARAGOG: Advanced RAG Output Grading},
  journal   = {arXiv preprint arXiv:2404.01037},
  year      = {2024}
}
```
