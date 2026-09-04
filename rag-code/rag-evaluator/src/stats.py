"""Statistical analysis and significance testing for RAG benchmarking.

Implements:
- Non-parametric two-tailed Wilcoxon signed-rank test across paired query observations (Thesis §4.1).
- Holm-Bonferroni step-down correction controlling family-wise error rates (FWER) across multiple comparisons.
- Statistical significance formatting matching Table 4.2 of the Master's thesis.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Any, Mapping, Sequence

import numpy as np
from scipy import stats


@dataclass
class WilcoxonComparison:
    """Represents a single hypothesis test comparison between treatment C_k and baseline C_0."""

    target_rq: str  # e.g. "RQ1", "RQ2", "RQ3"
    metric_name: str  # e.g. "Total Latency (ms)", "Cost ($)", "Composite Q"
    config_name: str  # e.g. "C1: S2B"
    baseline_median: float  # Median of baseline C0
    treatment_median: float  # Median of treatment Ck
    median_diff: float  # \tilde{\mu}_\Delta = median(treatment - baseline)
    w_statistic: float  # Wilcoxon signed-rank W statistic
    p_raw: float  # Unadjusted two-tailed p-value
    p_adjusted: float = 1.0  # Holm-Bonferroni step-down adjusted p-value
    significance: str = "ns"  # Significance flag: ***, **, *, ns


# Standard mapping of evaluation metrics to Research Questions (Table 4.2)
METRIC_TARGET_MAPPING: dict[str, tuple[str, str]] = {
    # Key in records -> (Target RQ, Display Metric Name)
    "total_latency_ms": ("RQ1", "End-to-End Latency (ms)"),
    "retrieval_ms": ("RQ1", "Retrieval Latency (ms)"),
    "generation_ms": ("RQ1", "Generation Latency (ms)"),
    "ttft_ms": ("RQ1", "Time to First Token (ms)"),
    "cost_usd": ("RQ2", "Operational Cost ($)"),
    "total_tokens": ("RQ2", "Total Token Usage"),
    "exact_match": ("RQ3", "Exact Match (EM)"),
    "token_f1": ("RQ3", "Token F1 Score"),
    "faithfulness": ("RQ3", "Faithfulness"),
    "answer_relevancy": ("RQ3", "Answer Relevancy"),
    "context_precision": ("RQ3", "Context Precision"),
    "context_recall": ("RQ3", "Context Recall"),
    "composite_quality": ("RQ3", "Composite Quality Q"),
}


def calculate_significance_flag(p_value: float) -> str:
    """Returns standard academic significance stars:

    ***: p < 0.001
    **:  p < 0.01
    *:   p < 0.05
    ns:  p >= 0.05
    """
    if math.isnan(p_value):
        return "ns"
    if p_value < 0.001:
        return "***"
    if p_value < 0.01:
        return "**"
    if p_value < 0.05:
        return "*"
    return "ns"


def wilcoxon_paired_test(
    baseline: Sequence[float],
    treatment: Sequence[float],
) -> tuple[float, float, float]:
    """Performs two-tailed Wilcoxon signed-rank test between paired observations.

    Args:
        baseline: Sequence of observations for C_0.
        treatment: Sequence of observations for C_k.

    Returns:
        tuple of (median_paired_diff, w_statistic, p_raw)
    """
    base_arr = np.asarray(baseline, dtype=float)
    treat_arr = np.asarray(treatment, dtype=float)

    if len(base_arr) != len(treat_arr):
        raise ValueError(
            f"Mismatched sample lengths: baseline={len(base_arr)}, treatment={len(treat_arr)}"
        )
    if len(base_arr) == 0:
        return 0.0, 0.0, 1.0

    diffs = treat_arr - base_arr
    median_diff = float(np.median(diffs))

    # Guard against identical arrays where all paired differences are 0
    if np.all(diffs == 0):
        return 0.0, 0.0, 1.0

    try:
        res = stats.wilcoxon(
            treat_arr,
            base_arr,
            zero_method="wilcox",
            alternative="two-sided",
        )
        w_stat = float(res.statistic)
        p_val = float(res.pvalue)
    except Exception:
        # Fallback in edge cases (e.g. very small sample or zero-rank ties)
        w_stat = 0.0
        p_val = 1.0

    if math.isnan(p_val):
        p_val = 1.0

    return median_diff, w_stat, p_val


def adjust_p_values_holm_bonferroni(
    comparisons: list[WilcoxonComparison],
) -> list[WilcoxonComparison]:
    """Applies the Holm-Bonferroni step-down procedure across a family of comparisons.

    Enforces monotonicity: p_adj[i] = min(1.0, max(p_adj[i-1], (m - i) * p_raw[i]))
    """
    if not comparisons:
        return []

    m = len(comparisons)
    # Sort comparisons by raw p-value ascending
    sorted_pairs = sorted(enumerate(comparisons), key=lambda x: x[1].p_raw)

    adjusted: list[WilcoxonComparison] = [None] * m  # type: ignore

    running_max = 0.0
    for rank, (original_idx, comp) in enumerate(sorted_pairs):
        multiplier = m - rank
        adj_p = min(1.0, comp.p_raw * multiplier)
        running_max = max(running_max, adj_p)
        bounded_adj = min(1.0, running_max)

        adjusted_comp = WilcoxonComparison(
            target_rq=comp.target_rq,
            metric_name=comp.metric_name,
            config_name=comp.config_name,
            baseline_median=comp.baseline_median,
            treatment_median=comp.treatment_median,
            median_diff=comp.median_diff,
            w_statistic=comp.w_statistic,
            p_raw=comp.p_raw,
            p_adjusted=bounded_adj,
            significance=calculate_significance_flag(bounded_adj),
        )
        adjusted[original_idx] = adjusted_comp

    return adjusted


def _extract_metric_series(
    records: Sequence[Mapping[str, Any]],
    metric_key: str,
) -> list[float]:
    """Extracts a numeric series for a given metric key, handling nested structures."""
    series: list[float] = []
    for r in records:
        val = None
        if metric_key in r:
            val = r[metric_key]
        elif "metrics" in r and isinstance(r["metrics"], dict) and metric_key in r["metrics"]:
            val = r["metrics"][metric_key]
        elif "timings" in r and isinstance(r["timings"], dict) and metric_key in r["timings"]:
            val = r["timings"][metric_key]
        elif "tokenUsage" in r and isinstance(r["tokenUsage"], dict):
            tu = r["tokenUsage"]
            if metric_key == "total_tokens":
                val = tu.get("totalTokens", 0)
            elif metric_key == "prompt_tokens":
                val = tu.get("promptTokens", 0)
            elif metric_key == "completion_tokens":
                val = tu.get("completionTokens", 0)

        # Fallbacks for alias names
        if val is None:
            aliases = {
                "total_latency_ms": ["totalLatencyMs", "latency_ms"],
                "retrieval_ms": ["retrievalMs"],
                "generation_ms": ["generationMs"],
                "cost_usd": ["cost", "costUsd"],
                "composite_quality": ["composite_q", "quality"],
                "exact_match": ["em"],
                "token_f1": ["f1"],
            }
            for alias in aliases.get(metric_key, []):
                if alias in r:
                    val = r[alias]
                    break

        try:
            series.append(float(val if val is not None else 0.0))
        except (ValueError, TypeError):
            series.append(0.0)
    return series


def compare_paired_runs(
    baseline_records: list[dict[str, Any]],
    treatment_records: list[dict[str, Any]],
    config_name: str = "Ck",
    metrics_to_test: list[str] | None = None,
) -> list[WilcoxonComparison]:
    """Compares a treatment configuration against a baseline across paired queries.

    Aligns queries by questionId (or id) if present; otherwise pairs by sequential index.
    """
    if metrics_to_test is None:
        metrics_to_test = [
            "total_latency_ms",
            "cost_usd",
            "exact_match",
            "token_f1",
            "faithfulness",
            "answer_relevancy",
            "context_precision",
            "context_recall",
            "composite_quality",
        ]

    # Align records by ID if possible
    base_dict = {}
    for i, r in enumerate(baseline_records):
        qid = r.get("questionId") or r.get("id") or str(i)
        base_dict[qid] = r

    paired_base: list[dict[str, Any]] = []
    paired_treat: list[dict[str, Any]] = []

    for i, r in enumerate(treatment_records):
        qid = r.get("questionId") or r.get("id") or str(i)
        if qid in base_dict:
            paired_base.append(base_dict[qid])
            paired_treat.append(r)

    if not paired_base:
        # Fallback to sequential pairing up to minimum length
        min_len = min(len(baseline_records), len(treatment_records))
        paired_base = baseline_records[:min_len]
        paired_treat = treatment_records[:min_len]

    comparisons: list[WilcoxonComparison] = []

    for metric_key in metrics_to_test:
        base_vals = _extract_metric_series(paired_base, metric_key)
        treat_vals = _extract_metric_series(paired_treat, metric_key)

        if not base_vals or not treat_vals:
            continue

        base_med = float(np.median(base_vals))
        treat_med = float(np.median(treat_vals))
        med_diff, w_stat, p_raw = wilcoxon_paired_test(base_vals, treat_vals)

        rq, display_name = METRIC_TARGET_MAPPING.get(
            metric_key, ("RQ3", metric_key.replace("_", " ").title())
        )

        comparisons.append(
            WilcoxonComparison(
                target_rq=rq,
                metric_name=display_name,
                config_name=config_name,
                baseline_median=base_med,
                treatment_median=treat_med,
                median_diff=med_diff,
                w_statistic=w_stat,
                p_raw=p_raw,
            )
        )

    return adjust_p_values_holm_bonferroni(comparisons)


def batch_compare_configurations(
    baseline_records: list[dict[str, Any]],
    treatment_runs: dict[str, list[dict[str, Any]]],
    metrics_to_test: list[str] | None = None,
) -> list[WilcoxonComparison]:
    """Compares multiple treatments (C1..C15) against baseline C0.

    Applies Holm-Bonferroni correction globally across all comparisons.
    """
    raw_comparisons: list[WilcoxonComparison] = []

    for config_name, treatment_records in treatment_runs.items():
        # Get unadjusted comparisons for this config
        comps = compare_paired_runs(
            baseline_records,
            treatment_records,
            config_name=config_name,
            metrics_to_test=metrics_to_test,
        )
        # Add without individual adjustment to correct across entire family
        for c in comps:
            raw_comparisons.append(
                WilcoxonComparison(
                    target_rq=c.target_rq,
                    metric_name=c.metric_name,
                    config_name=c.config_name,
                    baseline_median=c.baseline_median,
                    treatment_median=c.treatment_median,
                    median_diff=c.median_diff,
                    w_statistic=c.w_statistic,
                    p_raw=c.p_raw,
                )
            )

    return adjust_p_values_holm_bonferroni(raw_comparisons)
