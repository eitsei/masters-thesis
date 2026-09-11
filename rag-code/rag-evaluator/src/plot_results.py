"""Publication-ready vector PDF figures for Master's thesis.

Generates:
- thesis/figures/pareto_frontier.pdf (Quality vs Latency, Quality vs Cost with empirical Pareto frontier)
- thesis/figures/latency_breakdown.pdf (Stacked bar chart of retrieval vs. generation latencies)
- thesis/figures/ragas_metrics_radar.pdf (Radar chart comparing 4 core RAGAS metrics)
"""

from __future__ import annotations

from pathlib import Path
from typing import Any, Mapping, Sequence

import matplotlib
matplotlib.use("Agg")  # Headless backend for Linux environments
import matplotlib.pyplot as plt
import numpy as np

from src.reporter import summarize_run_records


def _set_publication_style() -> None:
    """Configures matplotlib rcParams for IEEE/ACM academic publication figures."""
    plt.rcParams.update(
        {
            "font.size": 10,
            "axes.labelsize": 11,
            "axes.titlesize": 12,
            "xtick.labelsize": 9,
            "ytick.labelsize": 9,
            "legend.fontsize": 9,
            "figure.titlesize": 13,
            "lines.linewidth": 1.8,
            "lines.markersize": 6,
            "grid.alpha": 0.4,
            "grid.linestyle": "--",
            "pdf.fonttype": 42,
            "ps.fonttype": 42,
        }
    )


def _compute_pareto_frontier(
    points: list[tuple[float, float, str]],
    minimize_x: bool = True,
    maximize_y: bool = True,
) -> list[tuple[float, float, str]]:
    """Identifies the non-dominated Pareto frontier points.

    Args:
        points: list of (x, y, label) tuples.
        minimize_x: True if lower x is better (e.g. latency, cost).
        maximize_y: True if higher y is better (e.g. quality Q).
    """
    if not points:
        return []

    # Sort primarily by x (ascending if minimizing)
    sorted_pts = sorted(points, key=lambda p: (p[0] if minimize_x else -p[0], -p[1] if maximize_y else p[1]))

    pareto_pts: list[tuple[float, float, str]] = []
    best_y = -float("inf") if maximize_y else float("inf")

    for x, y, label in sorted_pts:
        if maximize_y:
            if y >= best_y:
                pareto_pts.append((x, y, label))
                best_y = y
        else:
            if y <= best_y:
                pareto_pts.append((x, y, label))
                best_y = y

    return pareto_pts


def plot_pareto_frontier(
    data: dict[str, Sequence[Mapping[str, Any]]],
    output_path: Path | str = "thesis/figures/pareto_frontier.pdf",
) -> Path:
    """Generates dual-panel Quality vs Latency and Quality vs Cost Pareto trade-off figure."""
    _set_publication_style()
    out_file = Path(output_path)
    out_file.parent.mkdir(parents=True, exist_ok=True)

    latency_points: list[tuple[float, float, str]] = []
    cost_points: list[tuple[float, float, str]] = []

    for name, records in data.items():
        s = summarize_run_records(records)
        if not s:
            continue
        lat = s["latency_p50"]  # Median latency
        cost = s["cost_mean"]
        q = s["composite_q_mean"]
        latency_points.append((lat, q, name))
        cost_points.append((cost, q, name))

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(11, 4.8), dpi=300)

    palette = [
        "#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd",
        "#8c564b", "#e377c2", "#7f7f7f", "#bcbd22", "#17becf",
    ]

    # Panel 1: Quality vs Latency
    for i, (lat, q, name) in enumerate(latency_points):
        color = palette[i % len(palette)]
        ax1.scatter(lat, q, color=color, s=70, zorder=4, edgecolors="k", linewidth=0.6, label=name)
        ax1.annotate(
            name,
            (lat, q),
            textcoords="offset points",
            xytext=(5, 5),
            fontsize=8,
            alpha=0.85,
        )

    # Compute and plot Pareto frontier for Latency
    frontier_lat = _compute_pareto_frontier(latency_points, minimize_x=True, maximize_y=True)
    if len(frontier_lat) > 1:
        fx = [p[0] for p in frontier_lat]
        fy = [p[1] for p in frontier_lat]
        ax1.plot(fx, fy, "--", color="#d62728", alpha=0.75, linewidth=1.5, label="Pareto Frontier ($\\mathcal{P}$)")

    ax1.set_title("(a) Quality vs. End-to-End Latency", fontweight="bold")
    ax1.set_xlabel("Median Latency $P_{50}$ (ms)")
    ax1.set_ylabel("Composite Quality $Q(q) \\in [0, 1]$")
    ax1.set_ylim(-0.05, 1.05)
    ax1.grid(True)

    # Panel 2: Quality vs Cost
    for i, (cost, q, name) in enumerate(cost_points):
        color = palette[i % len(palette)]
        ax2.scatter(cost, q, color=color, s=70, zorder=4, edgecolors="k", linewidth=0.6)
        ax2.annotate(
            name,
            (cost, q),
            textcoords="offset points",
            xytext=(5, 5),
            fontsize=8,
            alpha=0.85,
        )

    # Compute and plot Pareto frontier for Cost
    frontier_cost = _compute_pareto_frontier(cost_points, minimize_x=True, maximize_y=True)
    if len(frontier_cost) > 1:
        cx = [p[0] for p in frontier_cost]
        cy = [p[1] for p in frontier_cost]
        ax2.plot(cx, cy, "--", color="#d62728", alpha=0.75, linewidth=1.5, label="Pareto Frontier ($\\mathcal{P}$)")

    ax2.set_title("(b) Quality vs. Operational Cost", fontweight="bold")
    ax2.set_xlabel("Average Cost per Query (USD)")
    ax2.set_ylabel("Composite Quality $Q(q) \\in [0, 1]$")
    ax2.set_ylim(-0.05, 1.05)
    ax2.grid(True)

    # Place combined legend below subplots
    handles, labels = ax1.get_legend_handles_labels()
    fig.legend(
        handles,
        labels,
        loc="upper center",
        bbox_to_anchor=(0.5, 0.04),
        ncol=min(5, len(labels)),
        frameon=True,
    )

    plt.tight_layout(rect=[0, 0.08, 1, 1])
    plt.savefig(out_file, bbox_inches="tight", dpi=300)
    plt.close(fig)
    return out_file


def plot_latency_breakdown(
    data: dict[str, Sequence[Mapping[str, Any]]],
    output_path: Path | str = "thesis/figures/latency_breakdown.pdf",
) -> Path:
    """Generates stacked bar chart isolating all micro-phase latency components across configurations."""
    _set_publication_style()
    out_file = Path(output_path)
    out_file.parent.mkdir(parents=True, exist_ok=True)

    configs: list[str] = []
    route_means: list[float] = []
    embed_means: list[float] = []
    ret_means: list[float] = []
    rerank_means: list[float] = []
    expand_means: list[float] = []
    cache_means: list[float] = []
    prefill_means: list[float] = []
    stream_means: list[float] = []
    gen_means: list[float] = []
    other_means: list[float] = []
    totals: list[float] = []

    has_split_generation = False

    for name, records in data.items():
        configs.append(name)
        c_route, c_embed, c_ret, c_rerank = [], [], [], []
        c_expand, c_cache = [], []
        c_prefill, c_stream, c_gen = [], [], []
        c_other, c_tot = [], []

        for r in records:
            t = r.get("timings") if isinstance(r.get("timings"), dict) else {}

            # 1. Routing
            route = float(
                t.get("routingMs") or t.get("routeMs") or t.get("routing_ms") or t.get("route_ms")
                or r.get("routing_ms") or r.get("route_ms") or r.get("routingMs") or r.get("routeMs") or 0.0
            )
            # 2. Query Vector Embedding
            embed = float(
                t.get("embedMs") or t.get("embed_ms") or t.get("embeddingMs") or t.get("embedding_ms")
                or r.get("embed_ms") or r.get("embedMs") or 0.0
            )
            # 3. Vector Retrieval
            ret = float(
                t.get("retrievalMs") or t.get("retrieval_ms")
                or r.get("retrieval_ms") or r.get("retrievalMs") or 0.0
            )
            # 4. Semantic Re-ranking
            rerank = float(
                t.get("rerankMs") or t.get("rerank_ms") or t.get("rerankingMs") or t.get("reranking_ms")
                or r.get("rerank_ms") or r.get("rerankMs") or 0.0
            )
            # 5. Context Expansion (Small-to-Big)
            expand = float(
                t.get("expansionMs") or t.get("expansion_ms")
                or r.get("expansion_ms") or r.get("expansionMs") or 0.0
            )
            # 6. Context Cache Lookup
            cache = float(
                t.get("cacheMs") or t.get("cache_ms")
                or r.get("cache_ms") or r.get("cacheMs") or 0.0
            )
            # 7. Generation (Prefill + Streaming if available, else Generation)
            prefill = float(
                t.get("prefillMs") or t.get("prefill_ms")
                or r.get("prefill_ms") or r.get("prefillMs") or 0.0
            )
            stream = float(
                t.get("streamMs") or t.get("stream_ms")
                or r.get("stream_ms") or r.get("streamMs") or 0.0
            )
            gen = float(
                t.get("generationMs") or t.get("generation_ms")
                or r.get("generation_ms") or r.get("generationMs") or 0.0
            )

            if prefill > 0.0 or stream > 0.0:
                has_split_generation = True

            # Total reported latency
            tot = float(
                r.get("totalLatencyMs") or r.get("total_latency_ms")
                or t.get("totalMs") or t.get("total_ms") or 0.0
            )

            if prefill > 0.0 or stream > 0.0:
                sub_sum = route + embed + ret + rerank + expand + cache + prefill + stream
            else:
                sub_sum = route + embed + ret + rerank + expand + cache + gen

            if tot <= 0.0:
                tot = sub_sum

            # Any residual pipeline/transport overhead
            other = max(0.0, tot - sub_sum) if tot > (sub_sum + 0.5) else 0.0

            c_route.append(route)
            c_embed.append(embed)
            c_ret.append(ret)
            c_rerank.append(rerank)
            c_expand.append(expand)
            c_cache.append(cache)
            c_prefill.append(prefill)
            c_stream.append(stream)
            c_gen.append(gen)
            c_other.append(other)
            c_tot.append(tot)

        route_means.append(float(np.mean(c_route)) if c_route else 0.0)
        embed_means.append(float(np.mean(c_embed)) if c_embed else 0.0)
        ret_means.append(float(np.mean(c_ret)) if c_ret else 0.0)
        rerank_means.append(float(np.mean(c_rerank)) if c_rerank else 0.0)
        expand_means.append(float(np.mean(c_expand)) if c_expand else 0.0)
        cache_means.append(float(np.mean(c_cache)) if c_cache else 0.0)
        prefill_means.append(float(np.mean(c_prefill)) if c_prefill else 0.0)
        stream_means.append(float(np.mean(c_stream)) if c_stream else 0.0)
        gen_means.append(float(np.mean(c_gen)) if c_gen else 0.0)
        other_means.append(float(np.mean(c_other)) if c_other else 0.0)
        totals.append(float(np.mean(c_tot)) if c_tot else 0.0)

    component_defs = [
        {"key": "route", "label": "Routing ($T_{\\text{route}}$)", "color": "#8c564b", "values": route_means},
        {"key": "embed", "label": "Embedding ($T_{\\text{embed}}$)", "color": "#5dade2", "values": embed_means},
        {"key": "ret", "label": "Retrieval ($T_{\\text{ret}}$)", "color": "#2b5c8f", "values": ret_means},
        {"key": "rerank", "label": "Re-ranking ($T_{\\text{rerank}}$)", "color": "#9b59b6", "values": rerank_means},
        {"key": "expand", "label": "Expansion ($T_{\\text{expand}}$)", "color": "#1abc9c", "values": expand_means},
        {"key": "cache", "label": "Cache Lookup ($T_{\\text{cache}}$)", "color": "#27ae60", "values": cache_means},
    ]

    if has_split_generation:
        component_defs.extend([
            {"key": "prefill", "label": "Prefill ($T_{\\text{prefill}}$)", "color": "#f39c12", "values": prefill_means},
            {"key": "stream", "label": "Streaming ($T_{\\text{stream}}$)", "color": "#e67e22", "values": stream_means},
        ])
    else:
        component_defs.append(
            {"key": "gen", "label": "Generation ($T_{\\text{gen}}$)", "color": "#e67e22", "values": gen_means}
        )

    component_defs.append(
        {"key": "other", "label": "Overhead ($T_{\\text{other}}$)", "color": "#95a5a6", "values": other_means}
    )

    # Filter out components that are negligible (< 0.1 ms) across all configurations
    active_components = [
        c for c in component_defs if any(val > 0.1 for val in c["values"])
    ]
    if not active_components:
        active_components = [
            {"key": "ret", "label": "Retrieval ($T_{\\text{ret}}$)", "color": "#2b5c8f", "values": ret_means},
            {"key": "gen", "label": "Generation ($T_{\\text{gen}}$)", "color": "#e67e22", "values": gen_means},
        ]

    fig, ax = plt.subplots(figsize=(9.0, 4.8), dpi=300)
    x = np.arange(len(configs))
    width = 0.55

    bottom = np.zeros(len(configs))
    for comp in active_components:
        vals = np.array(comp["values"])
        ax.bar(
            x,
            vals,
            width,
            bottom=bottom,
            label=comp["label"],
            color=comp["color"],
            edgecolor="black",
            linewidth=0.5,
        )
        bottom += vals

    # Label total latency on top of bars
    max_tot = max(totals) if totals else 1.0
    for i, tot in enumerate(totals):
        ax.annotate(
            f"{tot:.0f} ms",
            xy=(x[i], tot),
            xytext=(0, 4),
            textcoords="offset points",
            ha="center",
            va="bottom",
            fontsize=8,
            fontweight="bold",
        )

    ax.set_ylabel("Execution Latency (ms)")
    ax.set_title("End-to-End Latency Micro-Phase Breakdown", fontweight="bold")
    ax.set_xticks(x)

    display_labels = [c.replace("_", " ") for c in configs]
    ax.set_xticklabels(display_labels, rotation=20, ha="right", fontsize=9)

    ax.set_ylim(0, max_tot * 1.22)
    ax.legend(
        loc="upper left",
        framealpha=0.92,
        facecolor="white",
        edgecolor="#cccccc",
        fontsize=8.5,
    )
    ax.grid(axis="y", alpha=0.4)

    plt.tight_layout()
    plt.savefig(out_file, bbox_inches="tight", dpi=300)
    plt.close(fig)
    return out_file


def plot_ragas_metrics_radar(
    data: dict[str, Sequence[Mapping[str, Any]]],
    output_path: Path | str = "thesis/figures/ragas_metrics_radar.pdf",
) -> Path:
    """Generates radar chart across the 4 core RAGAS metrics."""
    _set_publication_style()
    out_file = Path(output_path)
    out_file.parent.mkdir(parents=True, exist_ok=True)

    categories = ["Context Precision", "Context Recall", "Faithfulness", "Answer Relevancy"]
    num_vars = len(categories)

    angles = np.linspace(0, 2 * np.pi, num_vars, endpoint=False).tolist()
    angles += angles[:1]  # Close the radar polygon

    fig, ax = plt.subplots(figsize=(6.2, 5.8), subplot_kw=dict(polar=True), dpi=300)

    palette = ["#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd", "#8c564b"]

    for idx, (name, records) in enumerate(data.items()):
        s = summarize_run_records(records)
        if not s:
            continue

        values = [
            s["context_precision_mean"],
            s["context_recall_mean"],
            s["faithfulness_mean"],
            s["answer_relevancy_mean"],
        ]
        values += values[:1]  # Close polygon

        color = palette[idx % len(palette)]
        ax.plot(angles, values, color=color, linewidth=2.0, label=name)
        ax.fill(angles, values, color=color, alpha=0.15)

    ax.set_theta_offset(np.pi / 2)
    ax.set_theta_direction(-1)

    ax.set_xticks(angles[:-1])
    ax.set_xticklabels(categories, fontsize=9, fontweight="bold")
    ax.set_ylim(0.0, 1.0)
    ax.set_yticks([0.2, 0.4, 0.6, 0.8, 1.0])
    ax.set_yticklabels(["0.2", "0.4", "0.6", "0.8", "1.0"], fontsize=7, color="gray")

    plt.title("RAGAS Four Core Quality Dimensions", fontweight="bold", y=1.08)
    plt.legend(loc="upper right", bbox_to_anchor=(1.3, 1.1), fontsize=8)

    plt.tight_layout()
    plt.savefig(out_file, bbox_inches="tight", dpi=300)
    plt.close(fig)
    return out_file
