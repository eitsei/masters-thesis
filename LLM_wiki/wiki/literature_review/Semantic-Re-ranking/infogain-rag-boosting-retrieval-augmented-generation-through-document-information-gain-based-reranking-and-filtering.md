# InfoGain-RAG: Boosting Retrieval-Augmented Generation through Document Information Gain-based Reranking and Filtering
**Authors:** Zihan Wang, Zihan Liang, Zhou Shao, Yufei Ma, Huangyu Dai, Ben Chen, Lingtao Mao, Chenyi Lei, Yuqing Ding, Han Li | **Year:** 2025
**Category:** Semantic-Re-ranking

### 📝 Abstract & Relevance Summary
Current RAG frameworks often struggle to evaluate whether retrieved documents contribute productively to final answer generation, leading to the ingestion of irrelevant or misleading contexts. This paper proposes Document Information Gain (DIG), a metric that quantifies document utility by computing the difference in an LLM’s generation confidence with and without the document augmented. Building on DIG, the authors introduce InfoGain-RAG, which trains a specialized plug-and-play reranking module using a multi-task objective (absolute relevance classification and relative margin ranking). This approach is highly relevant to both operational efficiency and output quality, as it filters out non-contributing documents to reduce token overhead and prevent LLM hallucinations.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Unlike self-reflective RAG methods that require multiple LLM calls at runtime, InfoGain-RAG runs as a single-pass reranker. It processes candidate documents and filters them prior to the LLM generation stage, resulting in extremely low computational latency and overhead during serving.
- **Cost & Token Usage:** By filtering out low-DIG documents, InfoGain-RAG minimizes prompt token counts. It reduces the context window footprint, yielding substantial token cost savings when using commercial APIs like GPT-4o.
- **Throughput:** By decreasing input context size, it improves overall generation throughput (Tokens Per Second) and reduces Time to First Token (TTFT).

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Evaluated using Exact Match (EM) accuracy on question-answering tasks and verification accuracy on fact-checking tasks. InfoGain-RAG improves Qwen2.5-72B's accuracy on NaturalQA by 17.8% and PopQA by 17.4% over naive RAG.
- **Benchmark Data:** TriviaQA, NaturalQA, PopQA, and FM2 (Fact Verification).
- **Judge Setup:** The training data is annotated by measuring LLM generation confidence changes (via token logits smoothing and weighting). The evaluations span Claude-Sonnet, GPT-4o, LLaMA3.1, Qwen2.5, Gemma-2, and DeepSeek-V3.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** InfoGain-RAG achieves a superior trade-off compared to complex agentic RAG structures. For instance, using LLaMA2-13B, it achieves 76.2% accuracy on TriviaQA and 51.9% on NaturalQA, outperforming Self-RAG (69.3%, 49.5%) and CRAG (74.5%, 48.2%) while avoiding multiple generative LLM calls.
- **Pareto Frontier & Combinations:** Multi-task training (combining absolute classification and relative ranking) achieves 76.8% accuracy on TriviaQA compared to 73.0% for cross-entropy and 71.4% for margin-only loss, illustrating that joint optimization establishes a higher accuracy ceiling.
- **Chunking/Routing Strategy:** Documents are retrieved, passed to the DIG reranker, and only those exceeding a predefined DIG threshold ($\beta$) are routed to the LLM generator.
- **Enterprise Suitability:** Highly suitable for production environments because it is a plug-and-play module that separates document scoring from generation.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Standard QA accuracy (Exact Match) and factual consistency checks.
- **Bias Detection:** A sliding window smoothing and token importance weighting mechanism is introduced during logit-confidence calculation to overcome length bias (where longer answers naturally receive lower raw joint probabilities).

### 🔍 Technical Implementation
- **Portability:** Fully portable to a Java/Spring AI stack. The reranking module is trained on top of a standard encoder model (e.g., cross-encoder or DeBERTa) and can be converted to ONNX for local execution without Python dependencies.
- **Key References:**
  - Asai et al. (2024) Self-RAG: Learning to Retrieve, Generate, and Critique through Self-Reflection
  - Yan et al. (2024) Corrective Retrieval Augmented Generation (CRAG)
  - Shi et al. (2023) REPLUG: Retrieval-Augmented Black-Box Language Models

### 💡 Key Takeaway for Thesis
- InfoGain-RAG represents a major efficiency optimization for RAG, proving that document relevance can be parameterized using offline LLM confidence deltas to train a fast, single-pass reranker that boosts generation accuracy by up to 17.8% while reducing input token lengths.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@inproceedings{wang2025infogain,
  title={InfoGain-RAG: Boosting Retrieval-Augmented Generation through Document Information Gain-based Reranking and Filtering},
  author={Wang, Zihan and Liang, Zihan and Shao, Zhou and Ma, Yufei and Dai, Huangyu and Chen, Ben and Mao, Lingtao and Lei, Chenyi and Ding, Yuqing and Li, Han},
  booktitle={Proceedings of the 2025 Conference on Empirical Methods in Natural Language Processing (EMNLP)},
  pages={7190--7204},
  year={2025},
  publisher={Association for Computational Linguistics}
}
```
