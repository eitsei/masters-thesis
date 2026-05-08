# Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks
**Authors:** Patrick Lewis, Ethan Perez, Aleksandra Piktus, Fabio Petroni, Vladimir Karpukhin, Naman Goyal, Heinrich Küttler, Mike Lewis, Wen-tau Yih, Tim Rocktäschel, Sebastian Riedel, Douwe Kiela | **Year:** 2021
**Category:** Background

### 📝 Abstract & Relevance Summary
This seminal paper introduces the Retrieval-Augmented Generation (RAG) framework, which combines pre-trained parametric memory (BART) with non-parametric memory (a dense vector index of Wikipedia). The authors explore two models: RAG-Sequence and RAG-Token, which marginalize over retrieved documents to generate outputs. The study demonstrates that RAG outperforms pure parametric models and extractive architectures on open-domain QA, fact verification, and abstractive generation. This paper is the foundational baseline for the thesis, establishing the original architecture whose latency (RQ1) and token efficiency (RQ2) subsequent optimization methods aim to improve.

### 🔍 Research Context (Surveys & Background)
- **Foundational Concepts:** Parametric vs. Non-parametric memory, Dense Passage Retrieval (DPR), Marginalization over latent documents.
- **Taxonomy:** Introduces the distinction between RAG-Sequence (document-level conditioning) and RAG-Token (token-level conditioning).
- **Snowballing Leads:** REALM [20], DPR [26], BART [32], T5 [51], FAISS [23].

### 📊 Evaluation Data & Bias Note
- **Datasets Used:** Natural Questions (NQ), TriviaQA, WebQuestions, CuratedTrec, MS-MARCO, Jeopardy, and FEVER.
- **AI-Evaluation:** Uses human pairwise comparative evaluation to assess factuality and specificity.
- **Bias Assessment:** Explicitly warns that external knowledge sources like Wikipedia are not devoid of bias and can lead to the generation of misleading content if the source is compromised.

### 💡 Key Takeaway for Thesis
The "Gold Standard" architecture for RAG; it provides the baseline performance and latency metrics against which all optimization techniques (Small-to-Big, Re-ranking, Caching) must be compared.

### 🔗 BibTeX Reference (Target: /bib)
```bibtex
@inproceedings{lewis2020rag,
  title={Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks},
  author={Lewis, Patrick and Perez, Ethan and Piktus, Aleksandra and Petroni, Fabio and Karpukhin, Vladimir and Goyal, Naman and K{\"u}ttler, Heinrich and Lewis, Mike and Yih, Wen-tau and Rockt{\"a}schel, Tim and others},
  booktitle={Advances in Neural Information Processing Systems},
  volume={33},
  pages={9459--9474},
  year={2020}
}
```
