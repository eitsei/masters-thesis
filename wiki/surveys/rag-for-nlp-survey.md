# Retrieval-Augmented Generation for Natural Language Processing: A Survey
**Authors:** Shangyu Wu, Ying Xiong, Yufei Cui, Haolun Wu, Can Chen, Ye Yuan, Lianming Huang, Xue Liu, Tei-Wei Kuo, Nan Guan, Chun Jason Xue | **Year:** 2025
**Category:** Survey

### 📝 Abstract & Relevance Summary
This systematic survey reviews the core components of RAG: the retriever, generator, and retrieval fusions. It distinguishes between three major fusion types: query-based (text/feature concatenation), latent (attention/weighted addition), and logits-based (ensemble/calibration). The paper also covers RAG training strategies and datastore updates. For the thesis, this paper is highly relevant as it explicitly addresses RAG efficiency (RQ1) by identifying latency bottlenecks in encoding versus ANN searching and proposing mitigation strategies like model quantization and distillation.

### 🔍 Research Context (Surveys & Background)
- **Foundational Concepts:** Retriever (Encoder, Indexing, Datastore), Generator (Default vs. Retrieval-Augmented), and Retrieval Fusions.
- **Taxonomy:** Categorizes fusions into Query-based (e.g., FiD), Logits-based (e.g., kNN-LM), and Latent-based (e.g., RETRO).
- **Snowballing Leads:** REALM [53], RAG [95], RETRO [11], FiD [72], kNN-LM [88], Atlas [73].

### 📊 Evaluation Data & Bias Note
- **Datasets Used:** NQ, TriviaQA, MS MARCO, and domain-specific benchmarks (Legal, Medical).
- **AI-Evaluation:** Introduces ARES (Automated RAG Evaluation System) for assessing faithfulness and relevance.
- **Bias Assessment:** Notes that "imperfect retrieval" (contradictory or unanswerable documents) is a primary driver of hallucinations in RAG systems.

### 💡 Key Takeaway for Thesis
This paper provides a detailed technical breakdown of where latency occurs in the RAG pipeline, allowing for a targeted analysis of optimization methods (RQ1) based on the scale of the external knowledge base.

### 🔗 BibTeX Reference (Target: /bib)
```bibtex
@article{wu2025rag,
  title={Retrieval-Augmented Generation for Natural Language Processing: A Survey},
  author={Wu, Shangyu and Xiong, Ying and Cui, Yufei and Wu, Haolun and Chen, Can and Yuan, Ye and Huang, Lianming and Liu, Xue and Kuo, Tei-Wei and Guan, Nan and Xue, Chun Jason},
  journal={arXiv preprint arXiv:2407.13193},
  year={2025}
}
```
