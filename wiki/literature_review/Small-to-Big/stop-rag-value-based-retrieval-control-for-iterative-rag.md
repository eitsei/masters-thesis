# Stop-RAG: Value-Based Retrieval Control for Iterative RAG
**Authors:** Jaewan Park, Solbee Cho, Jay-Yoon Lee | **Year:** 2025
**Category:** Small-to-Big

### 📝 Abstract & Relevance Summary
Iterative RAG enables LLMs to solve multi-hop reasoning tasks by accumulating evidence across multiple loops. However, each loop increases latency and token costs, and risks introducing distracting noise. Stop-RAG introduces a value-based controller that adaptively decides when to stop the retrieval process. By framing iterative RAG as a finite-horizon Markov Decision Process (MDP) and training a Q-network with $Q(\lambda)$ targets, Stop-RAG estimates the future utility of continued retrieval. This research is pivotal for RQ1 and RQ2 as it provides a modular strategy to minimize redundant LLM calls and associated costs while maximizing multi-hop QA accuracy.

### 🚀 Efficiency Metrics (Literature Review / Empirical)
- **RQ1 - Latency (TTFT):** Reduces overall response time by identifying optimal stopping points earlier than fixed-iteration baselines.
- **RQ2 - Token Efficiency:** Directly addresses RQ2 by reducing the number of "retrieve-generate" loops, thereby lowering token consumption per query.
- **RQ3 - Platform Context:** The controller is trained using offline trajectories and is compatible with black-box LLM APIs (e.g., GPT-4o, Gemini).
- **Implementation Note:** The Q-network utilizes a DeBERTa-v3-large backbone, which can be deployed as a sidecar microservice in a Spring AI environment to manage agentic RAG workflows.

### 📊 Evaluation Data & Bias Note
- **Datasets Used:** MuSiQue (up to 4-hop), HotpotQA (2-hop), and 2WikiMultihopQA (up to 5-hop).
- **AI-Evaluation:** Uses LLM-based evaluation metrics (EM, F1) to define the reward signal for the Q-network.
- **Bias Assessment:** Highlights that LLM self-assessment (Prompting-based stopping) is often unreliable and biased towards early stopping or over-retrieval.

### 💡 Key Takeaway for Thesis
Stop-RAG demonstrates that value-based control can significantly improve the efficiency of agentic RAG systems by providing a forward-looking estimate of retrieval utility, directly optimizing the trade-off between reasoning depth and token cost (RQ2).

### 🔗 BibTeX Reference (Target: /bib)
```bibtex
@article{park2025stoprag,
  title={Stop-RAG: Value-Based Retrieval Control for Iterative RAG},
  author={Park, Jaewan and Cho, Solbee and Lee, Jay-Yoon},
  journal={arXiv preprint arXiv:2510.14337},
  year={2025}
}
```
