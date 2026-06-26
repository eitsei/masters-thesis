# Chunking Methods on Retrieval-Augmented Generation – Effectiveness Evaluation Against Computational Cost and Limitations
**Authors:** Mateusz Śmigielski, Michał Rajkowski, Mateusz Zbrocki, Michał Bernacki-Janson, Karol Kunicki, Julianna Godziszewska, Maciej Piasecki, Konrad Wojtasik | **Year:** 2026
**Category:** Small-to-Big

### 📝 Abstract & Relevance Summary
This study systematically evaluates the effectiveness of a wide range of document chunking methods (Fixed Size, TextTiling, Recursive Semantic, Sequential HAC, Max Min, GraphSeg, Lumberchunker, DenseX) against their computational costs and execution limitations in RAG systems. It directly addresses the balance between operational efficiency and output quality, demonstrating that highly complex or model-based chunking approaches do not yield consistent performance improvements over simpler baselines. Instead, they introduce significant computational overhead, pipeline latency, and engineering fragility.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Fixed-size chunking is by far the most computationally efficient method (often executing in `<1s`). Recursive Semantic, Max-min, and Sequential HAC represent moderate chunking runtimes. In contrast, DenseX is extremely slow (e.g., 30.79 hours on GutenQA merged). GraphSeg (15.05 hours on TriviaQA merged) and Lumberchunker (8.37 hours on TriviaQA merged) incur massive ingestion delays.
- **Cost & Token Usage:** Lumberchunker and other LLM-driven methods require intensive model API calls or large local GPU setups (evaluated using a 20B LLM), which dramatically scales up preprocessing dollar costs.
- **Throughput:** Complex chunkers cause severe pipeline blockages, with multiple runs triggering a 48-hour timeout limit ("T" marker in experiments) on long texts.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Retrieval Accuracy@5, Recall@10, and end-to-end generated answer quality evaluated on a 5-point Likert scale (1-5 range).
- **Benchmark Data:** Evaluated on the Polish Information Retrieval Benchmark (PIRB) and several long narrative/scientific/structured QA corpuses (GutenQA, LiteraryQA, NovelQA, Qasper, SQuAD, TriviaQA, Natural Questions, PoQuAD).
- **Judge Setup:** GPT-OSS-20B was utilized as the generative model and served as the LLM judge.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** Simpler methods like Fixed-size chunking (chunk size 512, overlap 50) and Recursive Semantic splitting achieve highly competitive results (e.g., Fixed-size average Accuracy@5 of 84.96% vs. 86.85% for Max-min, and 85.75% for GraphSeg). The high computational costs of complex methods are not justified by their marginal performance gains.
- **Pareto Frontier & Combinations:** Layout-aware or semantic clustering chunkers (such as GraphSeg) show strong recall on narrative datasets but suffer from high fragility and execution times.
- **Chunking/Routing Strategy:** Evaluates the trade-off of chunk count vs. quality. While fewer, more coherent chunks (GraphSeg, Lumberchunker) perform well, simpler over-segmented baselines remain robust alternatives.
- **Enterprise Suitability:** LLM-based and layout-based splitters are frequently unsuitable for enterprise ingestion due to high latency, out-of-memory errors on documents with >1M characters, and API timeout failures.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** PIRB benchmark and custom evaluation code repository.
- **Bias Detection:** Highlights that prior chunking comparisons in literature are often limited to narrow datasets or specific domains, leading to biased conclusions that favor complex methods.

### 🔍 Technical Implementation
- **Portability:** Algorithmic splitters (Fixed size, TextTiling, Recursive Semantic) are highly portable to Java/Spring AI stacks. Lumberchunker and GraphSeg require external model APIs or Python-specific vision libraries, making them fragile and harder to deploy natively in Spring AI.
- **Key References:**
  - Duarte et al., 2024 (LumberChunker)
  - Qu et al., 2025 (Is semantic chunking worth the cost?)
  - Zhou et al., 2026 (Document chunking taxonomy)

### 💡 Key Takeaway for Thesis
- Monolithic fixed-size or recursive semantic chunking remains the most robust choice for enterprise-grade ingestion systems due to the severe execution latency and implementation fragility of layout- or LLM-based chunkers.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@article{smigielski2026chunking,
  author    = {Mateusz {\'{S}}migielski and Micha{\l} Rajkowski and Mateusz Zbrocki and Micha{\l} Bernacki-Janson and Karol Kunicki and Julianna Godziszewska and Maciej Piasecki and Konrad Wojtasik},
  title     = {Chunking Methods on Retrieval-Augmented Generation -- Effectiveness Evaluation Against Computational Cost and Limitations},
  journal   = {arXiv preprint arXiv:2606.00881},
  year      = {2026}
}
```
