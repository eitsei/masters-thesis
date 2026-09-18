"""Terminal reporting and Rich table formatting for RAG benchmark results.

Provides:
- Overall Summary table (Mean, Median P50, P95 latency, tokens, cost, EM, F1, RAGAS scores, Composite Q, QPD, QPS).
- Hop Breakdown table (stratified by 2-hop, 3-hop, and 4-hop query complexity).
- Statistical Comparison table (paired Wilcoxon test results and Holm-Bonferroni significance).
"""

from __future__ import annotations

import math
from typing import Any, Mapping, Sequence

import numpy as np
from rich import box
from rich.console import Console
from rich.table import Table

from src.metrics import (
    compute_composite_quality,
    compute_latency_percentiles,
    compute_qpd,
    compute_qps,
)
from src.stats import WilcoxonComparison


def _safe_float(val: Any, default: float = 0.0) -> float:
    try:
        if val is None:
            return default
        f = float(val)
        return default if math.isnan(f) else f
    except (ValueError, TypeError):
        return default


def _extract_metric_values(records: Sequence[Mapping[str, Any]], key: str) -> list[float]:
    """Extracts a list of float values for a given metric key from a sequence of records."""
    vals: list[float] = []
    for r in records:
        v = None
        if key in r:
            v = r[key]
        elif "metrics" in r and isinstance(r["metrics"], dict) and key in r["metrics"]:
            v = r["metrics"][key]
        elif "timings" in r and isinstance(r["timings"], dict) and key in r["timings"]:
            v = r["timings"][key]
        elif "tokenUsage" in r and isinstance(r["tokenUsage"], dict):
            tu = r["tokenUsage"]
            if key in tu:
                v = tu[key]
            elif key == "total_tokens":
                v = tu.get("totalTokens", 0)
            elif key == "prompt_tokens":
                v = tu.get("promptTokens", 0)
            elif key == "completion_tokens":
                v = tu.get("completionTokens", 0)
            elif key == "cached_tokens":
                v = tu.get("cachedTokens", 0)

        # Common aliases
        if v is None:
            alias_map = {
                "total_latency_ms": ["totalLatencyMs", "latency_ms", "latency"],
                "retrieval_ms": ["retrievalMs"],
                "generation_ms": ["generationMs"],
                "cost_usd": ["cost", "costUsd"],
                "exact_match": ["em"],
                "token_f1": ["f1"],
                "composite_quality": ["composite_q", "quality", "Q"],
            }
            for alias in alias_map.get(key, []):
                if alias in r:
                    v = r[alias]
                    break

        vals.append(_safe_float(v, 0.0))
    return vals


def summarize_run_records(records: Sequence[Mapping[str, Any]]) -> dict[str, float]:
    """Computes aggregate summary metrics for a collection of query records."""
    if not records:
        return {}

    latencies = _extract_metric_values(records, "total_latency_ms")
    lat_stats = compute_latency_percentiles(latencies)

    costs = _extract_metric_values(records, "cost_usd")
    ems = _extract_metric_values(records, "exact_match")
    f1s = _extract_metric_values(records, "token_f1")
    faiths = _extract_metric_values(records, "faithfulness")
    ars = _extract_metric_values(records, "answer_relevancy")
    cps = _extract_metric_values(records, "context_precision")
    crs = _extract_metric_values(records, "context_recall")
    tokens = _extract_metric_values(records, "total_tokens")

    mean_cost = float(np.mean(costs)) if costs else 0.0
    mean_em = float(np.mean(ems)) if ems else 0.0
    mean_f1 = float(np.mean(f1s)) if f1s else 0.0
    mean_faith = float(np.mean(faiths)) if faiths else 0.0
    mean_ar = float(np.mean(ars)) if ars else 0.0
    mean_cp = float(np.mean(cps)) if cps else 0.0
    mean_cr = float(np.mean(crs)) if crs else 0.0
    mean_tokens = float(np.mean(tokens)) if tokens else 0.0

    # Composite Q from aggregate means or average of individual composite Q
    comp_qs = _extract_metric_values(records, "composite_quality")
    if comp_qs and any(q > 0 for q in comp_qs):
        mean_comp_q = float(np.mean(comp_qs))
    else:
        mean_comp_q = compute_composite_quality(mean_cp, mean_cr, mean_faith, mean_ar)

    mean_lat = lat_stats["mean"]
    qpd = compute_qpd(mean_comp_q, mean_cost)
    qps = compute_qps(mean_comp_q, mean_lat)

    return {
        "count": float(len(records)),
        "latency_mean": lat_stats["mean"],
        "latency_p50": lat_stats["p50"],
        "latency_p95": lat_stats["p95"],
        "cost_mean": mean_cost,
        "tokens_mean": mean_tokens,
        "em_mean": mean_em,
        "f1_mean": mean_f1,
        "faithfulness_mean": mean_faith,
        "answer_relevancy_mean": mean_ar,
        "context_precision_mean": mean_cp,
        "context_recall_mean": mean_cr,
        "composite_q_mean": mean_comp_q,
        "qpd": qpd,
        "qps": qps,
    }


def display_summary_table(
    data: Sequence[Mapping[str, Any]] | dict[str, Sequence[Mapping[str, Any]]],
    console: Console | None = None,
    title: str = "Benchmark Evaluation Summary (Chapter 4)",
) -> Table:
    """Renders the Overall Summary table comparing configurations or displaying a single run."""
    c = console or Console()

    table = Table(
        title=f"🏆 [bold cyan]{title}[/bold cyan]",
        box=box.ROUNDED,
        header_style="bold magenta",
        title_justify="center",
    )

    table.add_column("Config / Run", style="cyan", justify="left", no_wrap=True)
    table.add_column("N", justify="right")
    table.add_column("P50 Lat (ms)", justify="right")
    table.add_column("P95 Lat (ms)", justify="right")
    table.add_column("Avg Tokens", justify="right")
    table.add_column("Cost ($/1k)", justify="right")
    table.add_column("EM (%)", justify="right", style="green")
    table.add_column("F1 (%)", justify="right", style="green")
    table.add_column("Faith", justify="right")
    table.add_column("AR", justify="right")
    table.add_column("CP", justify="right")
    table.add_column("CR", justify="right")
    table.add_column("Comp Q", justify="right", style="bold yellow")
    table.add_column("QPD", justify="right")
    table.add_column("QPS", justify="right")

    # Normalize data into a dict of {name: list_of_records}
    run_dict: dict[str, Sequence[Mapping[str, Any]]]
    if isinstance(data, dict):
        run_dict = data
    else:
        run_dict = {"Benchmark Run": data}

    for name, records in run_dict.items():
        s = summarize_run_records(records)
        if not s:
            continue

        qpd_str = "∞" if math.isinf(s["qpd"]) else f"{s['qpd']:.1f}"
        qps_str = "∞" if math.isinf(s["qps"]) else f"{s['qps']:.3f}"

        table.add_row(
            name,
            str(int(s["count"])),
            f"{s['latency_p50']:.1f}",
            f"{s['latency_p95']:.1f}",
            f"{s['tokens_mean']:.0f}",
            f"${s['cost_mean'] * 1000.0:.3f}",
            f"{s['em_mean'] * 100:.1f}%",
            f"{s['f1_mean'] * 100:.1f}%",
            f"{s['faithfulness_mean']:.3f}",
            f"{s['answer_relevancy_mean']:.3f}",
            f"{s['context_precision_mean']:.3f}",
            f"{s['context_recall_mean']:.3f}",
            f"{s['composite_q_mean']:.4f}",
            qpd_str,
            qps_str,
        )

    c.print(table)
    return table


def display_hop_breakdown_table(
    records: Sequence[Mapping[str, Any]],
    console: Console | None = None,
    title: str = "Query Complexity Breakdown (2-Hop vs 3-Hop vs 4-Hop)",
) -> Table:
    """Renders performance and quality metrics stratified by MuSiQue hop counts."""
    c = console or Console()

    table = Table(
        title=f"📊 [bold green]{title}[/bold green]",
        box=box.ROUNDED,
        header_style="bold blue",
        title_justify="center",
    )

    table.add_column("Reasoning Depth", style="cyan", justify="left")
    table.add_column("N", justify="right")
    table.add_column("P50 Lat (ms)", justify="right")
    table.add_column("P95 Lat (ms)", justify="right")
    table.add_column("Avg Tokens", justify="right")
    table.add_column("Cost ($/1k)", justify="right")
    table.add_column("EM (%)", justify="right", style="green")
    table.add_column("F1 (%)", justify="right", style="green")
    table.add_column("Faith", justify="right")
    table.add_column("AR", justify="right")
    table.add_column("CP", justify="right")
    table.add_column("CR", justify="right")
    table.add_column("Comp Q", justify="right", style="bold yellow")

    # Partition by hopCount
    hop_groups: dict[int, list[Mapping[str, Any]]] = {}
    for r in records:
        hops = r.get("hopCount")
        if hops is None:
            hops = r.get("hop_count", 0)
        try:
            h_int = int(hops)
        except (ValueError, TypeError):
            h_int = 0
        hop_groups.setdefault(h_int, []).append(r)

    # Sort keys (e.g. 2, 3, 4)
    for h in sorted(hop_groups.keys()):
        h_records = hop_groups[h]
        s = summarize_run_records(h_records)
        label = f"{h}-Hop Questions" if h > 0 else "Unknown / Out-of-Domain"

        table.add_row(
            label,
            str(int(s["count"])),
            f"{s['latency_p50']:.1f}",
            f"{s['latency_p95']:.1f}",
            f"{s['tokens_mean']:.0f}",
            f"${s['cost_mean'] * 1000.0:.3f}",
            f"{s['em_mean'] * 100:.1f}%",
            f"{s['f1_mean'] * 100:.1f}%",
            f"{s['faithfulness_mean']:.3f}",
            f"{s['answer_relevancy_mean']:.3f}",
            f"{s['context_precision_mean']:.3f}",
            f"{s['context_recall_mean']:.3f}",
            f"{s['composite_q_mean']:.4f}",
        )

    # Overall Row
    overall_s = summarize_run_records(records)
    if overall_s:
        table.add_section()
        table.add_row(
            "[bold]All Queries (Total)[/bold]",
            str(int(overall_s["count"])),
            f"{overall_s['latency_p50']:.1f}",
            f"{overall_s['latency_p95']:.1f}",
            f"{overall_s['tokens_mean']:.0f}",
            f"${overall_s['cost_mean'] * 1000.0:.3f}",
            f"{overall_s['em_mean'] * 100:.1f}%",
            f"{overall_s['f1_mean'] * 100:.1f}%",
            f"{overall_s['faithfulness_mean']:.3f}",
            f"{overall_s['answer_relevancy_mean']:.3f}",
            f"{overall_s['context_precision_mean']:.3f}",
            f"{overall_s['context_recall_mean']:.3f}",
            f"{overall_s['composite_q_mean']:.4f}",
        )

    c.print(table)
    return table


def display_comparison_table(
    comparisons: Sequence[WilcoxonComparison],
    console: Console | None = None,
    title: str = "Wilcoxon Signed-Rank Hypothesis Tests (Table 4.2 Format)",
) -> Table:
    """Renders paired statistical hypothesis tests comparing treatment against baseline."""
    c = console or Console()

    table = Table(
        title=f"🔬 [bold yellow]{title}[/bold yellow]",
        box=box.ROUNDED,
        header_style="bold cyan",
        title_justify="center",
    )

    table.add_column("RQ", style="magenta", justify="center")
    table.add_column("Config", style="cyan", justify="left")
    table.add_column("Target Metric", justify="left")
    table.add_column("C0 Med", justify="right")
    table.add_column("Ck Med", justify="right")
    table.add_column("μ̃Δ (Diff)", justify="right", style="bold")
    table.add_column("W-Stat", justify="right")
    table.add_column("p-raw", justify="right")
    table.add_column("p-adj (Holm)", justify="right")
    table.add_column("Sig", justify="center", style="bold red")

    for comp in comparisons:
        # Format values cleanly
        sig_color = "[green]" if comp.significance != "ns" else "[dim]"
        sig_formatted = f"{sig_color}{comp.significance}[/]"

        table.add_row(
            comp.target_rq,
            comp.config_name,
            comp.metric_name,
            f"{comp.baseline_median:.4f}" if abs(comp.baseline_median) < 10 else f"{comp.baseline_median:.1f}",
            f"{comp.treatment_median:.4f}" if abs(comp.treatment_median) < 10 else f"{comp.treatment_median:.1f}",
            f"{comp.median_diff:+.4f}" if abs(comp.median_diff) < 10 else f"{comp.median_diff:+.1f}",
            f"{comp.w_statistic:.1f}",
            f"{comp.p_raw:.4e}" if comp.p_raw < 0.001 else f"{comp.p_raw:.4f}",
            f"{comp.p_adjusted:.4e}" if comp.p_adjusted < 0.001 else f"{comp.p_adjusted:.4f}",
            sig_formatted,
        )

    c.print(table)
    return table
