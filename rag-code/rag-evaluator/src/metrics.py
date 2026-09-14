"""Evaluation metrics for RAG benchmark analysis.

Implements all Chapter 4 thesis equations:
- RQ1 Latency: T_ret, T_gen, T_total, TTFT, P50, P95
- RQ2 Operational Cost Model (Thesis Eq. 178 / 181):
    Cost(q) = N_prompt * P_prompt + N_comp * P_comp + N_cached * P_cached (+ N_rerank * P_rerank + N_embed * P_embed)
- RQ3 Quality Metrics:
    - Deterministic lexical: exact_match, token_f1
    - RAGAS 4 core metrics: Context Precision, Context Recall, Faithfulness, Answer Relevancy
    - Composite Quality Score Q(q) (Thesis Eq. 238):
        Q(q) = 4 / (1/(CP + eps) + 1/(CR + eps) + 1/(Faith + eps) + 1/(AR + eps))
- RQ4 Trade-off Ratios:
    - Quality per Dollar (QPD, Eq. 250): QPD = Q / Cost
    - Quality per Second (QPS, Eq. 255): QPS = Q / (T_total / 1000)
"""

from __future__ import annotations

import collections
import math
import re
import string
from dataclasses import dataclass
from typing import Any, Iterable, Mapping, Sequence

import numpy as np


# =====================================================================
# RQ1: Latency Formulations & Percentile Statistics
# =====================================================================


def compute_total_latency(
    retrieval_ms: float = 0.0,
    generation_ms: float = 0.0,
    embed_ms: float = 0.0,
    route_ms: float = 0.0,
    rerank_ms: float = 0.0,
    expand_ms: float = 0.0,
    cache_ms: float = 0.0,
    prefill_ms: float = 0.0,
    stream_ms: float = 0.0,
) -> float:
    """Computes total end-to-end latency (Thesis Eq. 156-160).

    T_total(q) = T_embed + T_route + T_ret + T_rerank + T_expand + T_cache + T_prefill + T_stream
    When micro-phases prefill/stream are absent, falls back to generation_ms.
    """
    gen_time = (prefill_ms + stream_ms) if (prefill_ms + stream_ms) > 0 else generation_ms
    return float(
        embed_ms
        + route_ms
        + retrieval_ms
        + rerank_ms
        + expand_ms
        + cache_ms
        + gen_time
    )


def compute_ttft(total_latency_ms: float, stream_ms: float = 0.0) -> float:
    """Computes Time to First Token (TTFT, Thesis Eq. 166).

    TTFT(q) = T_total(q) - T_stream(q)
    """
    return float(max(0.0, total_latency_ms - stream_ms))


def compute_latency_percentiles(latencies: Sequence[float]) -> dict[str, float]:
    """Computes distribution statistics including median (P50) and 95th percentile (P95)."""
    if not latencies:
        return {
            "mean": 0.0,
            "std": 0.0,
            "median": 0.0,
            "p50": 0.0,
            "p90": 0.0,
            "p95": 0.0,
            "p99": 0.0,
            "min": 0.0,
            "max": 0.0,
        }
    arr = np.asarray(latencies, dtype=float)
    return {
        "mean": float(np.mean(arr)),
        "std": float(np.std(arr, ddof=1 if len(arr) > 1 else 0)),
        "median": float(np.percentile(arr, 50)),
        "p50": float(np.percentile(arr, 50)),
        "p90": float(np.percentile(arr, 90)),
        "p95": float(np.percentile(arr, 95)),
        "p99": float(np.percentile(arr, 99)),
        "min": float(np.min(arr)),
        "max": float(np.max(arr)),
    }


# =====================================================================
# RQ2: Operational Cost Accounting (Thesis Eq. 178)
# =====================================================================


@dataclass(frozen=True)
class PricingTier:
    """Pricing configuration for an LLM provider and auxiliary services.

    Rates are specified in USD per 1,000,000 tokens unless otherwise noted.
    """

    name: str
    prompt_price_per_million: float  # Uncached prompt token rate ($/1M)
    comp_price_per_million: float  # Generated completion token rate ($/1M)
    cached_price_per_million: float  # Cached prompt token rate ($/1M)
    rerank_price_per_doc: float = 0.0  # Cross-encoder re-ranking rate ($/doc)
    embed_price_per_million: float = 0.0  # Query vectorization rate ($/1M)


PRICING_CATALOG: dict[str, PricingTier] = {
    # Gemini 2.5 Flash standard enterprise tier
    "gemini-2.5-flash": PricingTier(
        name="gemini-2.5-flash",
        prompt_price_per_million=0.15,
        comp_price_per_million=0.60,
        cached_price_per_million=0.0375,
        rerank_price_per_doc=0.0001,
        embed_price_per_million=0.025,
    ),
    # Gemini 3.5 Flash-Lite (Low-latency / tier-1 router)
    "gemini-3.5-flash-lite": PricingTier(
        name="gemini-3.5-flash-lite",
        prompt_price_per_million=0.075,
        comp_price_per_million=0.30,
        cached_price_per_million=0.01875,
        rerank_price_per_doc=0.0001,
        embed_price_per_million=0.025,
    ),
    # Gemini 3.7 Flash (Generation workhorse)
    "gemini-3.7-flash": PricingTier(
        name="gemini-3.7-flash",
        prompt_price_per_million=0.15,
        comp_price_per_million=0.60,
        cached_price_per_million=0.0375,
        rerank_price_per_doc=0.0001,
        embed_price_per_million=0.025,
    ),
    # Gemini 3.8 Flash (Latest generation workhorse)
    # Official pricing through Dec 31, 2026:
    # Input: $0.75/1M, Output: $3.75/1M, Context Cache Read: $0.075/1M (90% discount)
    # Storage: $0.50/1M tokens/hr ($1.50/$7.50/$0.15 starting Jan 1, 2027)
    "gemini-3.8-flash": PricingTier(
        name="gemini-3.8-flash",
        prompt_price_per_million=0.75,
        comp_price_per_million=3.75,
        cached_price_per_million=0.075,
        rerank_price_per_doc=0.0001,
        embed_price_per_million=0.025,
    ),
    # Gemini 3.1 Pro (High-capacity multi-hop tier)
    "gemini-3.1-pro": PricingTier(
        name="gemini-3.1-pro",
        prompt_price_per_million=1.25,
        comp_price_per_million=5.00,
        cached_price_per_million=0.3125,
        rerank_price_per_doc=0.0001,
        embed_price_per_million=0.025,
    ),
    # Anthropic Claude 3.5 Sonnet / Sonnet 5 (Evaluation Judge)
    "claude-sonnet-5": PricingTier(
        name="claude-sonnet-5",
        prompt_price_per_million=3.00,
        comp_price_per_million=15.00,
        cached_price_per_million=0.30,
        rerank_price_per_doc=0.0,
        embed_price_per_million=0.0,
    ),
    "claude-5-sonnet": PricingTier(
        name="claude-sonnet-5",
        prompt_price_per_million=3.00,
        comp_price_per_million=15.00,
        cached_price_per_million=0.30,
        rerank_price_per_doc=0.0,
        embed_price_per_million=0.0,
    ),
    # GPT-4o-mini
    "gpt-4o-mini": PricingTier(
        name="gpt-4o-mini",
        prompt_price_per_million=0.15,
        comp_price_per_million=0.60,
        cached_price_per_million=0.075,
        rerank_price_per_doc=0.0,
        embed_price_per_million=0.02,
    ),
    # Local / Ollama zero-cost execution
    "ollama": PricingTier(
        name="ollama",
        prompt_price_per_million=0.0,
        comp_price_per_million=0.0,
        cached_price_per_million=0.0,
        rerank_price_per_doc=0.0,
        embed_price_per_million=0.0,
    ),
    "local": PricingTier(
        name="local",
        prompt_price_per_million=0.0,
        comp_price_per_million=0.0,
        cached_price_per_million=0.0,
        rerank_price_per_doc=0.0,
        embed_price_per_million=0.0,
    ),
}


def get_pricing_tier(tier: PricingTier | str | None = "gemini-3.8-flash") -> PricingTier:
    """Resolves a pricing tier name or object to a PricingTier instance."""
    if tier is None:
        return PRICING_CATALOG["gemini-3.8-flash"]
    if isinstance(tier, PricingTier):
        return tier
    tier_key = str(tier).strip().lower()
    if tier_key in PRICING_CATALOG:
        return PRICING_CATALOG[tier_key]
    # Fuzzy alias match
    for k, v in PRICING_CATALOG.items():
        if tier_key in k or k in tier_key:
            return v
    # Fallback to default
    return PRICING_CATALOG["gemini-3.8-flash"]


def compute_cost(
    prompt_tokens: int,
    completion_tokens: int,
    cached_tokens: int = 0,
    rerank_docs: int = 0,
    embed_tokens: int = 0,
    tier: PricingTier | str | None = "gemini-3.8-flash",
    prompt_is_uncached: bool = True,
) -> float:
    """Computes query operational execution cost (Thesis Eq. 178 / 181).

    Cost(q) = N_prompt * P_prompt + N_comp * P_comp + N_cached * P_cached
              + N_rerank * P_rerank + N_embed * P_embed

    Args:
        prompt_tokens: Count of prompt tokens. If prompt_is_uncached is True (default),
                       treated as N_prompt directly. If False, uncached tokens = prompt_tokens - cached_tokens.
        completion_tokens: Count of generated completion tokens N_comp.
        cached_tokens: Count of cached context tokens N_cached.
        rerank_docs: Count of candidate passages scored by cross-encoder N_rerank.
        embed_tokens: Count of query tokens embedded N_embed.
        tier: PricingTier instance or string identifier from PRICING_CATALOG.
        prompt_is_uncached: Whether prompt_tokens represents uncached tokens.
    """
    resolved_tier = get_pricing_tier(tier)

    uncached_tokens = (
        prompt_tokens
        if prompt_is_uncached
        else max(0, prompt_tokens - cached_tokens)
    )

    token_cost = (
        (uncached_tokens * resolved_tier.prompt_price_per_million)
        + (completion_tokens * resolved_tier.comp_price_per_million)
        + (cached_tokens * resolved_tier.cached_price_per_million)
        + (embed_tokens * resolved_tier.embed_price_per_million)
    ) / 1_000_000.0

    rerank_cost = rerank_docs * resolved_tier.rerank_price_per_doc

    return float(token_cost + rerank_cost)


# =====================================================================
# RQ3: Quality Metrics (Deterministic Lexical & Composite Quality)
# =====================================================================


def normalize_answer(s: str) -> str:
    """Standard QA text normalization (SQuAD / HotpotQA / MuSiQue conventions).

    - Lowercase text
    - Strip punctuation (replace with spaces so hyphenated words split cleanly)
    - Strip English articles ('a', 'an', 'the')
    - Normalize whitespace
    """
    if not s:
        return ""

    text = s.lower()
    # Replace punctuation with spaces so hyphenated words split cleanly
    text = "".join(ch if ch not in set(string.punctuation) else " " for ch in text)
    # Remove articles
    text = re.sub(r"\b(a|an|the)\b", " ", text)
    # Normalize whitespace
    return " ".join(text.split())


def exact_match(
    prediction: str,
    ground_truth: str,
    aliases: list[str] | None = None,
) -> float:
    """Computes exact match (EM) score (1.0 or 0.0) against ground truth and aliases."""
    norm_pred = normalize_answer(prediction)
    candidates = [ground_truth]
    if aliases:
        candidates.extend(aliases)

    for candidate in candidates:
        if norm_pred == normalize_answer(candidate):
            return 1.0
    return 0.0


def token_f1(
    prediction: str,
    ground_truth: str,
    aliases: list[str] | None = None,
) -> float:
    """Computes maximum token-level F1 score against ground truth and aliases."""
    candidates = [ground_truth]
    if aliases:
        candidates.extend(aliases)

    best_f1 = 0.0
    for candidate in candidates:
        score = _compute_single_token_f1(prediction, candidate)
        if score > best_f1:
            best_f1 = score
        if math.isclose(best_f1, 1.0):
            break
    return float(best_f1)


def _compute_single_token_f1(prediction: str, ground_truth: str) -> float:
    """Calculates token-level F1 between two individual strings."""
    pred_tokens = normalize_answer(prediction).split()
    gt_tokens = normalize_answer(ground_truth).split()

    if not pred_tokens and not gt_tokens:
        return 1.0
    if not pred_tokens or not gt_tokens:
        return 0.0

    common = collections.Counter(pred_tokens) & collections.Counter(gt_tokens)
    num_same = sum(common.values())
    if num_same == 0:
        return 0.0

    precision = num_same / len(pred_tokens)
    recall = num_same / len(gt_tokens)
    return (2.0 * precision * recall) / (precision + recall)


def compute_composite_quality(
    context_precision: float,
    context_recall: float,
    faithfulness: float,
    answer_relevancy: float,
    epsilon: float = 1e-4,
) -> float:
    """Computes regularized harmonic Composite Quality Score Q(q) (Thesis Eq. 238).

    Q(q) = 4 / (1/(CP + eps) + 1/(CR + eps) + 1/(Faith + eps) + 1/(AR + eps))

    Guarantees numerical stability and severely penalizes single-dimension failure modes.
    """
    cp = max(0.0, float(context_precision))
    cr = max(0.0, float(context_recall))
    faith = max(0.0, float(faithfulness))
    ar = max(0.0, float(answer_relevancy))
    eps = max(1e-12, float(epsilon))

    denominator = (
        (1.0 / (cp + eps))
        + (1.0 / (cr + eps))
        + (1.0 / (faith + eps))
        + (1.0 / (ar + eps))
    )

    score = 4.0 / denominator
    return float(min(1.0, max(0.0, score)))


# =====================================================================
# RQ4: Trade-off Ratios (Pareto Frontiers)
# =====================================================================


def compute_qpd(composite_quality: float, cost: float) -> float:
    """Computes Quality per Dollar (QPD, Thesis Eq. 250).

    QPD = Q / Cost
    """
    q = max(0.0, float(composite_quality))
    if cost <= 0.0:
        return float("inf") if q > 0 else 0.0
    return float(q / cost)


def compute_qps(composite_quality: float, total_latency_ms: float) -> float:
    """Computes Quality per Second (QPS, Thesis Eq. 255).

    QPS = Q / (T_total / 1000)
    """
    q = max(0.0, float(composite_quality))
    if total_latency_ms <= 0.0:
        return float("inf") if q > 0 else 0.0
    seconds = total_latency_ms / 1000.0
    return float(q / seconds)


# =====================================================================
# RAGAS 4 Core Wrappers Integration
# =====================================================================


class RagasEvaluatorWrapper:
    """Wrapper encapsulating the 4 core RAGAS metrics:

    1. Context Precision (retriever ranking quality)
    2. Context Recall (ground-truth sentence attribution)
    3. Faithfulness (factual consistency against context)
    4. Answer Relevancy (semantic directness to query)
    """

    def __init__(
        self,
        llm: Any = None,
        embeddings: Any = None,
        delay_seconds: float = 0.0,
    ) -> None:
        self.llm = llm
        self.embeddings = embeddings
        self.delay_seconds = delay_seconds

    def evaluate_sample(
        self,
        question: str,
        response: str,
        retrieved_contexts: list[str],
        ground_truth: str,
    ) -> dict[str, float]:
        """Evaluates a single sample using RAGAS single-turn evaluation."""
        from ragas.dataset_schema import SingleTurnSample

        sample = SingleTurnSample(
            user_input=question,
            response=response,
            retrieved_contexts=retrieved_contexts or [""],
            reference=ground_truth,
        )

        scores: dict[str, float] = {}

        try:
            from ragas.metrics.collections import (
                answer_relevancy,
                context_precision,
                context_recall,
                faithfulness,
            )
            # Evaluate using instances
            from ragas.metrics import (
                AnswerRelevancy,
                ContextPrecision,
                ContextRecall,
                Faithfulness,
            )

            metrics = [
                Faithfulness(llm=self.llm),
                AnswerRelevancy(llm=self.llm, embeddings=self.embeddings),
                ContextPrecision(llm=self.llm),
                ContextRecall(llm=self.llm),
            ]
            for idx, m in enumerate(metrics):
                try:
                    score = m.single_turn_score(sample)
                    scores[m.name] = float(score) if score is not None else 0.0
                except Exception:
                    scores[m.name] = 0.0
                if self.delay_seconds > 0 and idx < len(metrics) - 1:
                    import time
                    time.sleep(self.delay_seconds)
        except Exception:
            scores = {
                "faithfulness": 0.0,
                "answer_relevancy": 0.0,
                "context_precision": 0.0,
                "context_recall": 0.0,
            }

        return scores
