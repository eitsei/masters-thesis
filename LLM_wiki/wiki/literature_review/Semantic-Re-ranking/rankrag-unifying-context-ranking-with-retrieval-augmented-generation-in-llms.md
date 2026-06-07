# RankRAG: Unifying Context Ranking with Retrieval-Augmented Generation in LLMs
**Authors:** Yue Yu, Wei Ping, Zihan Liu, Boxin Wang, Jiaxuan You, Chao Zhang, Mohammad Shoeybi, and Bryan Catanzaro | **Year:** 2024
**Category:** Semantic-Re-ranking

### 📝 Abstract & Relevance Summary
RAG pipelines retrieve the top-$k$ context chunks from a database to augment LLM generation, but embedding-based retrievers have limited capacity, leading to noisy contexts. While separate reranking models (e.g. cross-encoders) improve retrieval quality, they add deployment complexity and lack domain generalizability. The authors propose **RankRAG**, a framework that instruction-tunes a single LLM to perform both context ranking (reranking) and answer generation. By adding a small fraction of ranking data (~1%) into the training blend, RankRAG filters out irrelevant contexts during inference and generates answers based on the refined top-$k$ contexts. This design improves **Output Quality** (accuracy across benchmarks) and **Operational Efficiency** (by simplifying serving architecture and context lengths).

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Reranking retrieved contexts introduces latency. Reranking $N$ candidate passages adds an overhead ratio of $(N * t_{rank}) / (t_{retrieval} + t_{gen})$. In practice, calculating relevance only requires generating a single token ('True' or 'False') over short contexts. For $N=20$, RankRAG adds an additional **0.9x to 6.0x** latency overhead during the ranking phase, which is significantly faster than using separate larger models or processing all $N$ contexts in the final generation.
- **Cost & Token Usage:** Ranking allows RankRAG to utilize a much smaller context window size during the generation phase (e.g., $k=5$), saving generation tokens. The model is highly data-efficient: adding only **5k ranking pairs** (~1% of MS MARCO) achieves excellent ranking accuracy.
- **Throughput:** Fine-tuning RankRAG-8B takes 10 hours on 32 NVIDIA A100 GPUs, and RankRAG-70B takes 16 hours on 128 NVIDIA A100 GPUs. Inference uses vLLM for high-throughput batching.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Exact Match (EM), F1 Score, and retrieval **Recall@5/10/20** before and after ranking.
- **Benchmark Data:** Evaluated on 9 general benchmarks (NQ, TriviaQA, PopQA, HotpotQA, 2WikiMQA, FEVER, Doc2Dial, TopiOCQA, INSCIT) and 5 biomedical benchmarks (MMLU-med, PubmedQA, BioASQ, MedQA, MedMCQA).
- **Judge Setup:** Compared against GPT-4-0613, GPT-4-turbo-2024-0409, and ChatQA-1.5. Evaluated with GPT-4o as a judge on open-domain Natural Questions benchmark.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** On NQ, TriviaQA, and PopQA, Llama3-RankRAG-8B achieves **50.6% EM**, **82.9% EM**, and **57.6% EM** respectively, significantly outperforming Llama3-ChatQA-1.5-8B (which scores 42.4%, 81.0%, 52.6%). Reranking $N=20$ passages adds a small latency overhead but yields a **+5.9% to +9.1% absolute gain** in final EM scores.
- **Pareto Frontier & Combinations:** Traditional RAG accuracy saturates at $k=10$ due to noise. With RankRAG's unified ranking capability, $k=5$ already achieves optimal accuracy, proving that reranking allows a smaller context window during generation.
- **Chunking/Routing Strategy:** Retrieve-Rerank-Generate pipeline. The retriever retrieves $N$ contexts ($N=100$ for 8B, $N=30$ for 70B). RankRAG evaluates the probability of generating "True" vs "False" for each document: `"For the question {question}, access whether the passage is relevant..."`. Contexts are reranked, and the top-$k$ ($k=5$) are fed back into RankRAG for answer generation.
- **Enterprise Suitability:** Extremely high. By consolidating context ranking and generation into a single LLM, it simplifies production deployment, eliminating the need to host and coordinate separate reranker models (e.g. Cohere Rerank, BGE-Reranker).

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Evaluated using KILT benchmark splits and Dragon/BM25 retrievers.
- **Bias Detection:** Performed string-match analysis to ensure no overlap (data contamination) between the instruction fine-tuning datasets and evaluation benchmarks.

### 🔍 Technical Implementation
- **Portability:** Fully portable to a Java/Spring AI stack. Spring AI applications can query the single RankRAG model to perform reranking (by requesting log-probabilities of "True/False" tokens) and then use the same client to generate the final answer.
- **Key References:**
  - *ChatQA:* Liu et al., 2024 (Surpassing GPT-4 on Conversational QA and RAG)
  - *RetRobust:* Yoran et al., 2024 (Making Retrieval-Augmented Language Models Robust to Irrelevant Context)
  - *BEIR Benchmarks:* Thakur et al., 2021 (A Heterogeneous Benchmark for Zero-shot Evaluation of Information Retrieval)

### 💡 Key Takeaway for Thesis
- RankRAG shows that instruction-tuning a single model for both context ranking and generation simplifies the RAG architecture, allows reducing context size to $k=5$ without quality loss, and outperforms expert reranking models.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@inproceedings{yu2024rankrag,
  title={RankRAG: Unifying Context Ranking with Retrieval-Augmented Generation in LLMs},
  author={Yu, Yue and Ping, Wei and Liu, Zihan and Wang, Boxin and You, Jiaxuan and Zhang, Chao and Shoeybi, Mohammad and Catanzaro, Bryan},
  booktitle={38th Conference on Neural Information Processing Systems (NeurIPS 2024)},
  year={2024}
}
```
