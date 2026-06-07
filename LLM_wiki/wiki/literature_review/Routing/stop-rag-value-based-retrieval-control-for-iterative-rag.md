# Stop-RAG: Value-Based Retrieval Control for Iterative RAG
**Authors:** Jaewan Park, Solbee Cho, Jay-Yoon Lee | **Year:** 2025
**Category:** Routing

### 📝 Abstract & Relevance Summary
Iterative retrieval-augmented generation (RAG) enables large language models to answer complex multi-hop questions by repeatedly retrieving new evidence. However, each additional retrieval loop increases end-to-end latency, operational cost, and the risk of introducing distracting context, which underscores the need for an efficient stopping strategy. To address this, the authors introduce Stop-RAG, a value-based controller that adaptively decides when to stop retrieving. By casting iterative RAG as a finite-horizon Markov Decision Process (MDP), Stop-RAG trains a Q-network using Q(λ) targets from complete offline trajectories. This allows the system to balance **Operational Efficiency** (minimizing redundant retrieval steps) and **Output Quality** (mitigating distractor injection while ensuring sufficient evidence is retrieved to generate correct answers).

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Average latency is reduced by dynamically stopping retrieval early when further steps do not add value. On MuSiQue, Stop-RAG cuts average iterations from a fixed budget of 10 down to 8.6 steps. On 2WikiMultihopQA, it reduces the average iterations from 10 to 5.1 steps. The inference overhead of the controller itself is minor (a DeBERTa-v3-large forward pass).
- **Cost & Token Usage:** Directly decreases token consumption and API costs by bypassing unnecessary retrieve-generate loops. Rather than running the maximum 10 loops, Stop-RAG executes only the necessary steps (average 8.6 steps on MuSiQue and 5.1 steps on 2WikiMultihopQA).
- **Throughput:** By reducing the total number of iterations per query, Stop-RAG increases overall system throughput and query-processing capacity.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Exact Match (EM), F1 score, and Accuracy (Acc - gold answer appearing anywhere in the generated output).
- **Benchmark Data:** MuSiQue, HotpotQA, and 2WikiMultihopQA.
- **Judge Setup:** Evaluated directly against dataset gold answers. During training, the expected reward at a state is estimated using the average F1 score across 8 independent trials (N=8). No LLM-as-a-judge was used for final evaluation.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** Stop-RAG improves both accuracy and efficiency. By stopping retrieval once the necessary evidence is found, it prevents the model from being distracted by noisy context. On MuSiQue (using "Our Pipeline"), Stop-RAG yields 36.8 EM / 47.0 F1 in 8.6 average steps, outperforming the fixed 10-step baseline (34.5 EM / 44.8 F1) and prompting-based stopping (LLM-Stop: 34.2 EM / 44.6 F1 in 7.6 steps).
- **Pareto Frontier & Combinations:** Stop-RAG operates as a plug-and-play controller on top of existing iterative RAG pipelines (such as CoRAG or standard LLM-based systems). It uses a margin-based decision rule: stop retrieval when `Q_theta(s, STOP) - Q_theta(s, CONT)` exceeds a tuned validation threshold.
- **Chunking/Routing Strategy:** Operates as a binary router (STOP vs CONT) at each step of the iterative retrieve-generate loop (maximum horizon T = 10).
- **Enterprise Suitability:** Highly suitable for production environments. Unlike confidence-based methods that rely on black-box LLM internal telemetry (entropy/attention weights), Stop-RAG only requires the state representation (the concatenated question and retrieved documents), making it language-agnostic and robust to model swaps.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Evaluated using custom partitions of standard multi-hop datasets.
- **Bias Detection:** The ablation study evaluates different training targets, showing that the forward-looking Q(λ) target (with λ annealed from 1.0 to 0.1) performs better than Monte Carlo targets (high variance),TD(0) / Q(0) targets (myopic), or binary classification (lacks value magnitude).

### 🔍 Technical Implementation
- **Portability:** Highly compatible with a Java/Spring AI stack. The Q-network (DeBERTa-v3-large) can be exported to ONNX and executed via the ONNX Runtime library directly within Java. The inputs to the model are simple text strings (Question + SEP + Document Context).
- **Key References:**
  - Trivedi, H., et al. (2023). *Interleaving Retrieval with Chain-of-Thought Reasoning for Knowledge-Intensive Multi-Step Questions.* ACL 2023.
  - Asai, A., et al. (2024). *Self-RAG: Learning to Retrieve, Generate, and Critique through Self-Reflection.* ICLR 2024.
  - Jeong, S., et al. (2024). *Adaptive-RAG: Learning to Adapt Retrieval-Augmented Large Language Models through Question Complexity.* NAACL 2024.

### 💡 Key Takeaway for Thesis
- Stop-RAG demonstrates that modeling retrieval control as a finite-horizon MDP and utilizing a value-based controller (trained via Q(λ)) optimizes iterative RAG by dynamically halting retrieval when additional context would lead to distractions or diminishing returns.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@inproceedings{park2025stoprag,
  title={Stop-RAG: Value-Based Retrieval Control for Iterative RAG},
  author={Park, Jaewan and Cho, Solbee and Lee, Jay-Yoon},
  booktitle={39th Conference on Neural Information Processing Systems (NeurIPS 2025) Workshop: Multi-Turn Interactions in Large Language Models},
  year={2025}
}
```
