# HiSem-RAG: A Hierarchical Semantic-Driven Retrieval-Augmented Generation Method
**Authors:** Dongju Yang, Junming Wang | **Year:** 2026
**Category:** Indexing / Hierarchical Retrieval

### 📝 Abstract & Relevance Summary
Traditional RAG methods struggle with hierarchical documents, often causing semantic fragmentation and structural loss. HiSem-RAG addresses these issues through three key modules: hierarchical semantic indexing to preserve document boundaries, bidirectional semantic enhancement using titles and summaries for better context, and a distribution-aware adaptive threshold strategy for dynamic retrieval. Evaluation on EleQA and LongQA datasets shows that HiSem-RAG outperforms baseline methods in accuracy and resource efficiency. The method is particularly effective for long-document scenarios, mitigating the "lost in the middle" problem by ensuring retrieved fragments carry full hierarchical context. This paper is highly relevant for optimizing token efficiency in complex, structured knowledge bases.

### 🚀 Latency Optimization (Speed)
- **Method:** Distribution-Aware Adaptive Threshold Retrieval.
- **Technical Implementation:** Replacing fixed top-k strategies with a dynamic threshold calculated based on the similarity distribution of retrieved candidates at each level of the document tree.
- **Results:** Reduced token consumption by approximately 11% while maintaining or improving accuracy (82.00% overall accuracy on EleQA).

### 📉 Token Efficiency (Cost & Context)
- **Method:** Hierarchical Semantic Indexing and Bidirectional Semantic Enhancement.
- **Token Reduction:** Saves about 11% computation resources by narrowing retrieval scope when information is concentrated; prevents redundant retrieval of irrelevant fragments through structure-aware filtering.
- **Impact on Accuracy:** Improves overall accuracy by 5.04% and 3.98% compared to HyDE and RAPTOR respectively; significantly reduces hallucinations by grounding retrieval in actual document structure.

### 🛠 Architecture Overview
- **Pipeline Stage:** Indexing / Retrieval
- **Models Used:** GLM-4-flash, BGE-M3 (text embedding model).
- **Hardware Context:** Tested on NVIDIA A800 80 GB graphics processor.

### 💡 Key Takeaway for Thesis
HiSem-RAG proves that hierarchical document awareness and adaptive thresholding are superior to flat chunking for long-context RAG, offering a significant reduction in token waste while increasing factual correctness.

### 🔗 BibTeX Reference (Target: /bib)
```bibtex
@article{yang2026hisem,
  title={HiSem-RAG: A Hierarchical Semantic-Driven Retrieval-Augmented Generation Method},
  author={Yang, Dongju and Wang, Junming},
  journal={Applied Sciences},
  volume={16},
  number={903},
  year={2026},
  publisher={MDPI}
}
```
