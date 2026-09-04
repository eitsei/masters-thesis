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
    """Generates stacked bar chart isolating retrieval vs. generation latencies."""
    _set_publication_style()
    out_file = Path(output_path)
    out_file.parent.mkdir(parents=True, exist_ok=True)

    configs: list[str] = []
    retrievals: list[float] = []
    generations: list[float] = []
    totals: list[float] = []

    for name, records in data.items():
        configs.append(name)
        ret_vals = []
        gen_vals = []
        tot_vals = []
        for r in records:
            # Check timings object
            t = r.get("timings", {})
            ret = t.get("retrievalMs", r.get("retrieval_ms", 0.0))
            gen = t.get("generationMs", r.get("generation_ms", 0.0))
            tot = r.get("totalLatencyMs", r.get("total_latency_ms", ret + gen))
            ret_vals.append(float(ret))
            gen_vals.append(float(gen))
            tot_vals.append(float(tot))

        retrievals.append(float(np.mean(ret_vals)) if ret_vals else 0.0)
        generations.append(float(np.mean(gen_vals)) if gen_vals else 0.0)
        totals.append(float(np.mean(tot_vals)) if tot_vals else 0.0)

    fig, ax = plt.subplots(figsize=(8.5, 4.5), dpi=300)
    x = np.arange(len(configs))
    width = 0.55

    color_ret = "#2b5c8f"  # Deep blue for retrieval
    color_gen = "#e67e22"  # Amber orange for generation

    rects1 = ax.bar(x, retrievals, width, label="Retrieval ($T_{\\text{ret}}$)", color=color_ret, edgecolor="black", linewidth=0.5)
    rects2 = ax.bar(x, generations, width, bottom=retrievals, label="Generation ($T_{\\text{gen}}$)", color=color_gen, edgecolor="black", linewidth=0.5)

    # Label total latency on top of bars
    for i, tot in enumerate(totals):
        ax.annotate(
            f"{tot:.0f} ms",
            xy=(x[i], tot),
            xytext=(0, 3),
            textcoords="offset points",
            ha="center",
            va="bottom",
            fontsize=8,
            fontweight="bold",
        )

    ax.set_ylabel("Execution Latency (ms)")
    ax.set_title("End-to-End Latency Micro-Phase Breakdown", fontweight="bold")
    ax.set_xticks(x)
    ax.set_xticklabels(configs, rotation=20, ha="right", fontsize=9)
    ax.legend(loc="upper left")
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
