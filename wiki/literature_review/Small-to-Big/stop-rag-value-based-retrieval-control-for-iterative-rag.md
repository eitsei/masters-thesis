# Stop-RAG: Value-Based Retrieval Control for Iterative RAG
**Authors:** Jaewan Park, Solbee Cho, Jay-Yoon Lee | **Year:** 2025
**Category:** Small-to-Big

### 📝 Abstract & Relevance Summary
Iterative RAG systems incrementally gather evidence but often suffer from inefficiency due to fixed retrieval iterations or myopic confidence scores, driving up costs and latency. Stop-RAG formulates iterative retrieval as a finite-horizon Markov Decision Process (MDP) and trains a value-based controller (Q-network) to adaptively decide when to stop retrieving. By evaluating forward-looking estimates of whether continued retrieval will improve answer quality, Stop-RAG prevents wasteful LLM calls. This perfectly targets the operational efficiency vs. quality balance by dynamically optimizing token consumption and latency per-query.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Significantly cuts down average Time-to-First-Token (TTFT) and end-to-end latency for simpler queries by halting the retrieve-generate loop early.
- **Token Usage:** Produces massive token savings by skipping unnecessary intermediate generation and retrieval steps.
- **Throughput:** By reducing the average number of iterations executed per query, the system frees up LLM capacity, directly boosting concurrent request throughput.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Exact Match (EM), F1 Score, and Accuracy.
- **Benchmark Data:** Tested on complex multi-hop QA benchmarks requiring iterative reasoning: MuSiQue, HotpotQA, and 2WikiMultihopQA.
- **Judge Setup:** Standard string matching evaluating the final generated prediction against gold answers.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** Stop-RAG dynamically adjusts the equilibrium per-query. It yields a better balance of retrieval depth and precision by stopping early to avoid distractors on simple queries, but continuing the loop for complex queries, ultimately improving overall F1 scores while reducing average iteration counts.
- **Chunking Strategy:** Iteratively retrieves and appends top-1 documents per cycle to build a growing context window, limited to a maximum of 10 iterations.
- **Enterprise Suitability:** Designed specifically as a modular, plug-and-play component. It is highly suitable for cost-sensitive enterprise applications leveraging black-box APIs, as it requires no internal telemetry from the LLM.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Compared against fixed-iteration baselines (CoRAG) and simple prompting-based stopping criteria (LLM-Stop).
- **Bias Detection:** Addressed the bias inherent in greedy, present-focused stopping mechanisms by using full-width forward-view $Q(\lambda)$ targets, ensuring the model accurately predicts long-term retrieval value.

### 🔍 Technical Implementation
- **Portability:** Highly portable. The Q-network acts as an independent routing module (implemented here with DeBERTa). In a Java/Spring AI environment, this lightweight classifier can be integrated as a conditional gateway evaluating the current context state before triggering another LLM API call.
- **Key References:** 
  - Asai et al., 2024 (Self-RAG)
  - Shao et al., 2023 (Iter-RetGen)
  - Trivedi et al., 2023 (IRCoT)

### 💡 Key Takeaway for Thesis
Stop-RAG highlights that a value-based, forward-looking stopping controller is an essential efficiency optimization for Iterative RAG, drastically cutting token costs and latency without sacrificing the quality required for complex multi-hop queries.