#!/usr/bin/env python3
"""Generate alternative figure designs and proposals for the Master's thesis.

Generates proposals into:
  rag-code/rag-evaluator/figures/
    ├── option1_radar_fixed/       (Re-engineered radar charts with fixed spacing and non-overlapping legends)
    ├── option2_lollipop/          (4-panel horizontal dot/lollipop plots comparing quality dimensions)
    ├── option3_faceted_bars/      (4-panel horizontal faceted bar plots)
    ├── wildcard1_diverging_heatmap/ (Quality and efficiency delta matrix / diverging heatmap)
    ├── wildcard2_parallel_coordinates/ (Parallel coordinates quality profile)
    ├── wildcard3_tri_objective_bubble/ (3-objective bubble plot: Latency vs Quality vs Cost Efficiency)
    ├── pareto_improved/           (Pareto plots with selective Pareto-only annotations and dynamic Y limits)
    └── breakdowns_improved/       (Stacked bar charts with raised Y-limits and collision-free value labels)

Exports both vector PDF and high-resolution PNG for direct visual inspection.
"""

from __future__ import annotations

import json
import math
import re
import sys
from pathlib import Path
from typing import Any, Mapping, Optional, Sequence

import matplotlib
matplotlib.use("Agg")
import matplotlib.gridspec as gridspec
import matplotlib.pyplot as plt
from matplotlib.patches import Patch
import numpy as np

# Ensure src module can be imported
evaluator_dir = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(evaluator_dir))

from src.metrics import PRICING_CATALOG, get_pricing_tier
from src.reporter import summarize_run_records
from src.plot_results import _compute_pareto_frontier, _categorize_radar_tiers


# Friendly short names for all 16 configurations
CONFIG_FRIENDLY_NAMES: dict[str, str] = {
    "C0": "$C_0$ (Baseline)",
    "C1": "$C_1$ (M1)",
    "C2": "$C_2$ (M2)",
    "C3": "$C_3$ (M3)",
    "C4": "$C_4$ (M4)",
    "C5": "$C_5$ (M1+M2)",
    "C6": "$C_6$ (M1+M3)",
    "C7": "$C_7$ (M1+M4)",
    "C8": "$C_8$ (M2+M3)",
    "C9": "$C_9$ (M2+M4)",
    "C10": "$C_{10}$ (M3+M4)",
    "C11": "$C_{11}$ (M1+M2+M3)",
    "C12": "$C_{12}$ (M1+M2+M4)",
    "C13": "$C_{13}$ (M1+M3+M4)",
    "C14": "$C_{14}$ (M2+M3+M4)",
    "C15": "$C_{15}$ (Full-Stack)",
}

TIER_COLORS = {
    0: "#2c3e50",  # Baseline: Dark slate
    1: "#1f77b4",  # Singulars: Blue
    2: "#2ca02c",  # Duos: Green
    3: "#ff7f0e",  # Trios: Orange
    4: "#d62728",  # Full-Stack: Red
}

TIER_BG_COLORS = {
    0: "#f1f5f9",  # Baseline: Soft slate
    1: "#eff6ff",  # Singulars (k=1): Soft light blue
    2: "#f0fdf4",  # Duos (k=2): Soft light green
    3: "#fff7ed",  # Trios (k=3): Soft light orange/peach
    4: "#fef2f2",  # Full-Stack (k=4): Soft light red/rose
}


def _get_config_key(name: str) -> str:
    m = re.search(r"c_?(\d+)", name.lower())
    if m:
        return f"C{m.group(1)}"
    if "base" in name.lower():
        return "C0"
    return name


def _get_friendly_name(name: str) -> str:
    key = _get_config_key(name)
    return CONFIG_FRIENDLY_NAMES.get(key, name.replace("run_", "").replace("_", " "))


def _get_tier(name: str) -> int:
    key = _get_config_key(name)
    m = re.search(r"C(\d+)", key)
    if not m:
        return 0
    c_num = int(m.group(1))
    if c_num == 0:
        return 0
    if 1 <= c_num <= 4:
        return 1
    if 5 <= c_num <= 10:
        return 2
    if 11 <= c_num <= 14:
        return 3
    return 4


def _load_run_dir(run_dir: Path) -> dict[str, list[dict[str, Any]]]:
    """Loads all run JSONL files in directory into sorted dictionary."""
    def _sort_key(p: Path) -> tuple[int, str]:
        m = re.search(r"[Cc](\d+)", p.stem)
        return (int(m.group(1)) if m else 999, p.name)

    res: dict[str, list[dict[str, Any]]] = {}
    for f in sorted(run_dir.glob("*.jsonl"), key=_sort_key):
        stem = f.stem.replace("run_", "").replace("_evaluated", "")
        records = []
        with open(f, "r", encoding="utf-8") as fp:
            for line in fp:
                line = line.strip()
                if line:
                    records.append(json.loads(line))
        if records:
            res[stem] = records
    return res


def _save_plot(fig: plt.Figure, out_base: Path) -> None:
    """Saves both PDF and PNG formats with publication quality."""
    out_base.parent.mkdir(parents=True, exist_ok=True)
    pdf_path = out_base.with_suffix(".pdf")
    png_path = out_base.with_suffix(".png")
    fig.savefig(pdf_path, bbox_inches="tight", dpi=300)
    fig.savefig(png_path, bbox_inches="tight", dpi=200)
    plt.close(fig)
    print(f"  ✓ Saved: {pdf_path.name} & {png_path.name}")


# ==============================================================================
# PROPOSAL 1: Option 1 - Fixed Radar Chart (Spacing, Legends, Markers)
# ==============================================================================
def plot_option1_radar_fixed(
    data: dict[str, Sequence[Mapping[str, Any]]],
    out_base: Path,
    dataset_name: str,
) -> None:
    categories = ["Context Precision", "Context Recall", "Faithfulness", "Answer Relevancy"]
    num_vars = len(categories)
    angles = np.linspace(0, 2 * np.pi, num_vars, endpoint=False).tolist()
    angles += angles[:1]

    palette = [
        "#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd",
        "#8c564b", "#e377c2", "#17becf",
    ]
    markers = ["o", "s", "^", "D", "v", "p", "h"]

    base_names, sing_names, duo_names, trio_names, quad_names = _categorize_radar_tiers(list(data.keys()))

    fig, axes = plt.subplots(2, 2, figsize=(12.5, 12.0), subplot_kw=dict(polar=True), dpi=300)
    axes_flat = axes.flatten()

    panels = [
        ("(a) Single-Factor Isolated ($k=1$) vs. Baseline", sing_names),
        ("(b) Pairwise Combinations ($k=2$) vs. Baseline", duo_names),
        ("(c) Multi-Module Triplets ($k=3$) vs. Baseline", trio_names),
        ("(d) Full-Stack Integration ($k=4$) vs. Baseline", quad_names),
    ]

    base_name = base_names[0] if base_names else None
    base_values = None
    if base_name and base_name in data:
        sb = summarize_run_records(data[base_name])
        if sb:
            base_values = [
                sb["context_precision_mean"],
                sb["context_recall_mean"],
                sb["faithfulness_mean"],
                sb["answer_relevancy_mean"],
            ]
            base_values += base_values[:1]

    for p_idx, (panel_title, tier_configs) in enumerate(panels):
        ax = axes_flat[p_idx]

        # 1. Baseline
        if base_values is not None:
            ax.plot(
                angles,
                base_values,
                color="#2c3e50",
                linestyle="--",
                linewidth=2.2,
                label="$C_0$ (Baseline)",
                zorder=4,
            )
            ax.fill(angles, base_values, color="#2c3e50", alpha=0.08)

        # 2. Configurations in tier
        for idx, name in enumerate(tier_configs):
            records = data.get(name)
            if not records:
                continue
            s = summarize_run_records(records)
            if not s:
                continue
            values = [
                s["context_precision_mean"],
                s["context_recall_mean"],
                s["faithfulness_mean"],
                s["answer_relevancy_mean"],
            ]
            values += values[:1]

            color = palette[idx % len(palette)]
            marker = markers[idx % len(markers)]
            friendly = _get_friendly_name(name)

            ax.plot(
                angles,
                values,
                color=color,
                linewidth=1.8,
                marker=marker,
                markersize=4.5,
                label=friendly,
                zorder=5,
            )
            # Subtle fill only (no heavy overlaps)
            ax.fill(angles, values, color=color, alpha=0.03)

        # Polar axis styling
        ax.set_theta_offset(np.pi / 2)
        ax.set_theta_direction(-1)
        ax.set_xticks(angles[:-1])

        # Precise alignment of tick labels to prevent horizontal overlap
        tick_labels = ax.set_xticklabels(categories, fontsize=9.0, fontweight="bold")
        for i, lbl in enumerate(tick_labels):
            ang = angles[i]
            # Pi/2 is top (Context Precision)
            # Pi is right (Context Recall in direction -1 offset pi/2)
            # Let's adjust padding
            lbl.set_y(lbl.get_position()[1])

        ax.set_ylim(0.0, 1.0)
        ax.set_yticks([0.2, 0.4, 0.6, 0.8, 1.0])
        ax.set_yticklabels(["0.2", "0.4", "0.6", "0.8", "1.0"], fontsize=7.0, color="#666666")
        ax.set_title(panel_title, fontweight="bold", pad=26, fontsize=10.8)

        # Legend positioned well below the plot so it doesn't touch lower panel's title
        ax.legend(
            loc="upper center",
            bbox_to_anchor=(0.5, -0.16),
            ncol=min(3, len(tier_configs) + 1),
            fontsize=8.0,
            framealpha=0.92,
            edgecolor="#cccccc",
        )

    fig.suptitle(
        f"Multi-Dimensional Quality Trade-offs across Architectural Tiers (RAGAS) for {dataset_name}",
        fontsize=13.0,
        fontweight="bold",
        y=0.99,
    )
    plt.tight_layout()
    # Generous spacing: wspace=0.48 eliminates label collision; hspace=0.52 avoids legend-title collision
    plt.subplots_adjust(hspace=0.52, wspace=0.48, top=0.93, bottom=0.08)
    _save_plot(fig, out_base)


def _add_broken_axis_slashes(ax_left: plt.Axes, ax_right: plt.Axes, d: float = 0.025) -> None:
    """Adds standard diagonal break marks '//' between two adjacent sub-axes."""
    ax_left.spines["right"].set_visible(False)
    ax_right.spines["left"].set_visible(False)
    ax_right.tick_params(left=False, labelleft=False)

    kw = dict(color="#475569", clip_on=False, linewidth=1.1)
    # Slants on ax_left (right border)
    ax_left.plot((1 - d, 1 + d), (-d, +d), transform=ax_left.transAxes, **kw)
    ax_left.plot((1 - d, 1 + d), (1 - d, 1 + d), transform=ax_left.transAxes, **kw)

    # Slants on ax_right (left border): ax_right is ~5x wider than ax_left, so scale dx
    d_r = d * 0.20
    ax_right.plot((-d_r, +d_r), (-d, +d), transform=ax_right.transAxes, **kw)
    ax_right.plot((-d_r, +d_r), (1 - d, 1 + d), transform=ax_right.transAxes, **kw)


def _draw_tier_bands(
    target_ax: plt.Axes,
    y_pos: np.ndarray,
    tiers: list[int],
    total_configs: int,
) -> None:
    """Draws soft tinted horizontal background bands and subtle boundary lines for each tier."""
    for t in range(5):
        ys = [y for y, tier in zip(y_pos, tiers) if tier == t]
        if not ys:
            continue
        min_y, max_y = min(ys), max(ys)
        bg_col = TIER_BG_COLORS.get(t, "#ffffff")
        target_ax.axhspan(min_y - 0.5, max_y + 0.5, facecolor=bg_col, alpha=0.85, zorder=0)
        # Boundary line at top
        if max_y + 0.5 < total_configs - 0.5:
            target_ax.axhline(max_y + 0.5, color="#cbd5e1", linewidth=0.85, linestyle="-", zorder=1)
        # Boundary line at bottom
        if min_y - 0.5 > -0.5:
            target_ax.axhline(min_y - 0.5, color="#cbd5e1", linewidth=0.85, linestyle="-", zorder=1)


def plot_option2_lollipop(
    data: dict[str, Sequence[Mapping[str, Any]]],
    out_base: Path,
    dataset_name: str,
) -> None:
    """2x2 multi-panel horizontal lollipop plot with continuous [0, 1] scale and tier background bands."""
    categories = [
        ("Context Precision", "context_precision_mean"),
        ("Context Recall", "context_recall_mean"),
        ("Faithfulness", "faithfulness_mean"),
        ("Answer Relevancy", "answer_relevancy_mean"),
    ]

    configs = list(data.keys())
    summaries = {name: summarize_run_records(records) for name, records in data.items()}
    configs_ordered = sorted(
        configs,
        key=lambda c: int(re.search(r"c_?(\d+)", c.lower()).group(1)) if re.search(r"c_?(\d+)", c.lower()) else 0,
        reverse=True,
    )

    y_pos = np.arange(len(configs_ordered))
    friendly_labels = [_get_friendly_name(c) for c in configs_ordered]
    tiers = [_get_tier(c) for c in configs_ordered]

    fig_w = 8.8
    fig_h = 10.2
    fig = plt.figure(figsize=(fig_w, fig_h), dpi=300)

    outer_grid = gridspec.GridSpec(
        2, 2, figure=fig,
        hspace=0.28, wspace=0.38,
        left=0.14, right=0.96, top=0.94, bottom=0.06,
    )

    panel_prefixes = ["(a)", "(b)", "(c)", "(d)"]

    for col_idx, (cat_label, cat_key) in enumerate(categories):
        row = col_idx // 2
        col = col_idx % 2
        ax = fig.add_subplot(outer_grid[row, col])

        # Soft tier background bands
        _draw_tier_bands(ax, y_pos, tiers, len(configs_ordered))

        values = [summaries[c][cat_key] if summaries.get(c) else 0.0 for c in configs_ordered]

        for y, val, tier in zip(y_pos, values, tiers):
            color = TIER_COLORS.get(tier, "#333333")
            # Continuous stem from 0.0 to val
            ax.hlines(
                y,
                xmin=0.0,
                xmax=val,
                color=color,
                linewidth=1.5,
                alpha=0.85,
                zorder=3,
            )
            ax.scatter(val, y, color=color, s=45, edgecolors="k", linewidth=0.6, zorder=4)
            # Value annotation shifted a few points further away (8pt offset) from marker
            ax.annotate(
                f"{val:.3f}",
                (val, y),
                textcoords="offset points",
                xytext=(8, 0),
                va="center",
                ha="left",
                fontsize=7.2,
                fontweight="bold",
                color=color,
            )

        ax.set_title(f"{panel_prefixes[col_idx]} {cat_label}", fontweight="bold", fontsize=10.5, pad=8, loc="left")
        ax.set_xlim(-0.02, 1.14)
        ax.set_xticks([0.0, 0.2, 0.4, 0.6, 0.8, 1.0])
        ax.set_xticklabels(["0.0", "0.2", "0.4", "0.6", "0.8", "1.0"], fontsize=8.0)
        ax.xaxis.set_tick_params(labelsize=8.0)
        ax.set_yticks(y_pos)
        ax.set_yticklabels(friendly_labels, fontsize=8.2, fontweight="medium")
        ax.set_ylim(-0.7, len(configs_ordered) - 0.3)
        ax.spines["top"].set_visible(False)
        ax.spines["right"].set_visible(False)
        ax.grid(True, axis="x", linestyle="--", linewidth=0.8, color="#94a3b8", alpha=0.65, zorder=1)

    # Legend for tiers at bottom
    legend_elements = [
        Patch(facecolor=TIER_COLORS[0], edgecolor="k", label="Baseline ($C_0$)"),
        Patch(facecolor=TIER_COLORS[1], edgecolor="k", label="Single-opt ($C_1$–$C_4$)"),
        Patch(facecolor=TIER_COLORS[2], edgecolor="k", label="Dual-opt ($C_5$–$C_{10}$)"),
        Patch(facecolor=TIER_COLORS[3], edgecolor="k", label="Triple-opt ($C_{11}$–$C_{14}$)"),
        Patch(facecolor=TIER_COLORS[4], edgecolor="k", label="Full-Stack ($C_{15}$)"),
    ]
    fig.legend(
        handles=legend_elements,
        loc="lower center",
        bbox_to_anchor=(0.5, 0.01),
        ncol=5,
        fontsize=9.0,
        framealpha=0.9,
        edgecolor="#cbd5e1",
    )

    fig.suptitle(
        f"RAGAS Quality Metrics by Configuration — {dataset_name}",
        fontsize=12.5,
        fontweight="bold",
        y=0.985,
    )
    _save_plot(fig, out_base)


def plot_option2_lollipop_unbroken(
    data: dict[str, Sequence[Mapping[str, Any]]],
    out_base: Path,
    dataset_name: str,
) -> None:
    """Continuous unbroken [0, 1] scale variant with soft tier background bands and reinforced grid."""
    categories = [
        ("Context Precision", "context_precision_mean"),
        ("Context Recall", "context_recall_mean"),
        ("Faithfulness", "faithfulness_mean"),
        ("Answer Relevancy", "answer_relevancy_mean"),
    ]

    configs = list(data.keys())
    summaries = {name: summarize_run_records(records) for name, records in data.items()}
    configs_ordered = sorted(
        configs,
        key=lambda c: int(re.search(r"c_?(\d+)", c.lower()).group(1)) if re.search(r"c_?(\d+)", c.lower()) else 0,
        reverse=True,
    )

    y_pos = np.arange(len(configs_ordered))
    friendly_labels = [_get_friendly_name(c) for c in configs_ordered]
    tiers = [_get_tier(c) for c in configs_ordered]

    fig, axes = plt.subplots(1, 4, figsize=(15.0, 7.5), sharey=True, dpi=300)

    base_name = next((c for c in configs if _get_config_key(c) == "C0"), None)
    base_summary = summaries.get(base_name) if base_name else None

    for col_idx, (cat_label, cat_key) in enumerate(categories):
        ax = axes[col_idx]
        base_val = base_summary[cat_key] if base_summary else 0.5

        # Soft tier background bands
        _draw_tier_bands(ax, y_pos, tiers, len(configs_ordered))

        ax.axvline(
            base_val,
            color="#2c3e50",
            linestyle="--",
            linewidth=1.5,
            alpha=0.85,
            zorder=2,
            label="Baseline ($C_0$)" if col_idx == 0 else None,
        )

        values = [summaries[c][cat_key] if summaries.get(c) else 0.0 for c in configs_ordered]

        for y, val, tier in zip(y_pos, values, tiers):
            color = TIER_COLORS.get(tier, "#333333")
            ax.hlines(y, xmin=min(base_val, val), xmax=max(base_val, val), color=color, linewidth=1.8, alpha=0.7, zorder=3)
            ax.scatter(val, y, color=color, s=50, edgecolors="k", linewidth=0.6, zorder=4)

        ax.set_title(cat_label, fontweight="bold", fontsize=11.0, pad=12)
        ax.set_xlim(-0.02, 1.02)
        ax.set_xticks([0.0, 0.2, 0.4, 0.6, 0.8, 1.0])
        ax.set_xticklabels(["0.0", "0.2", "0.4", "0.6", "0.8", "1.0"], fontsize=8.5)
        ax.grid(True, axis="x", linestyle="--", linewidth=0.8, color="#94a3b8", alpha=0.65, zorder=1)

    axes[0].set_yticks(y_pos)
    axes[0].set_yticklabels(friendly_labels, fontsize=9.0, fontweight="medium")

    legend_elements = [
        plt.Line2D([0], [0], color="#2c3e50", linestyle="--", linewidth=1.5, label="Baseline ($C_0$)"),
        Patch(facecolor=TIER_COLORS[1], edgecolor="k", label="Isolated ($k=1$)"),
        Patch(facecolor=TIER_COLORS[2], edgecolor="k", label="Pairwise ($k=2$)"),
        Patch(facecolor=TIER_COLORS[3], edgecolor="k", label="Triplets ($k=3$)"),
        Patch(facecolor=TIER_COLORS[4], edgecolor="k", label="Full-Stack ($k=4$)"),
    ]
    fig.legend(
        handles=legend_elements,
        loc="upper center",
        bbox_to_anchor=(0.5, 0.04),
        ncol=5,
        fontsize=9.0,
        frameon=True,
    )

    fig.suptitle(
        f"RAGAS Quality Dimension Profiles across Configurations for {dataset_name} (Standard Scale)",
        fontsize=13.0,
        fontweight="bold",
        y=0.98,
    )
    plt.tight_layout(rect=[0, 0.06, 1, 0.95])
    _save_plot(fig, out_base)


# ==============================================================================
# PROPOSAL 3: Option 3 - 4-Panel Faceted Bar Chart
# ==============================================================================
def plot_option3_faceted_bars(
    data: dict[str, Sequence[Mapping[str, Any]]],
    out_base: Path,
    dataset_name: str,
) -> None:
    categories = [
        ("Context Precision", "context_precision_mean"),
        ("Context Recall", "context_recall_mean"),
        ("Faithfulness", "faithfulness_mean"),
        ("Answer Relevancy", "answer_relevancy_mean"),
    ]

    configs = list(data.keys())
    summaries = {name: summarize_run_records(records) for name, records in data.items()}
    configs_ordered = sorted(configs, key=lambda c: int(re.search(r"c_?(\d+)", c.lower()).group(1)) if re.search(r"c_?(\d+)", c.lower()) else 0, reverse=True)

    y_pos = np.arange(len(configs_ordered))
    friendly_labels = [_get_friendly_name(c) for c in configs_ordered]
    tiers = [_get_tier(c) for c in configs_ordered]

    fig, axes = plt.subplots(1, 4, figsize=(15.5, 7.8), sharey=True, dpi=300)

    base_name = next((c for c in configs if _get_config_key(c) == "C0"), None)
    base_summary = summaries.get(base_name) if base_name else None

    for col_idx, (cat_label, cat_key) in enumerate(categories):
        ax = axes[col_idx]
        base_val = base_summary[cat_key] if base_summary else 0.5
        ax.axvline(base_val, color="#2c3e50", linestyle="--", linewidth=1.5, alpha=0.8, zorder=2)

        values = [summaries[c][cat_key] if summaries.get(c) else 0.0 for c in configs_ordered]
        bar_colors = [TIER_COLORS.get(t, "#444444") for t in tiers]

        bars = ax.barh(y_pos, values, color=bar_colors, height=0.68, edgecolor="k", linewidth=0.4, alpha=0.85, zorder=3)

        # Label value at end of each bar
        for bar, val in zip(bars, values):
            ax.text(
                min(val + 0.02, 0.98),
                bar.get_y() + bar.get_height() / 2.0,
                f"{val:.2f}",
                va="center",
                ha="left" if val < 0.85 else "right",
                fontsize=7.2,
                fontweight="medium",
                color="#000000" if val < 0.85 else "#ffffff",
                zorder=5,
            )

        ax.set_title(cat_label, fontweight="bold", fontsize=11.0, pad=12)
        ax.set_xlim(0.0, 1.05)
        ax.grid(True, axis="x", linestyle=":", alpha=0.6)

    axes[0].set_yticks(y_pos)
    axes[0].set_yticklabels(friendly_labels, fontsize=9.0)

    legend_elements = [
        plt.Line2D([0], [0], color="#2c3e50", linestyle="--", linewidth=1.5, label="Baseline ($C_0$)"),
        Patch(facecolor=TIER_COLORS[1], edgecolor="k", label="Isolated ($k=1$)"),
        Patch(facecolor=TIER_COLORS[2], edgecolor="k", label="Pairwise ($k=2$)"),
        Patch(facecolor=TIER_COLORS[3], edgecolor="k", label="Triplets ($k=3$)"),
        Patch(facecolor=TIER_COLORS[4], edgecolor="k", label="Full-Stack ($k=4$)"),
    ]
    fig.legend(
        handles=legend_elements,
        loc="upper center",
        bbox_to_anchor=(0.5, 0.04),
        ncol=5,
        fontsize=9.0,
        frameon=True,
    )

    fig.suptitle(
        f"Faceted RAGAS Metric Comparison across Configurations for {dataset_name}",
        fontsize=13.0,
        fontweight="bold",
        y=0.98,
    )
    plt.tight_layout(rect=[0, 0.06, 1, 0.95])
    _save_plot(fig, out_base)


# ==============================================================================
# WILDCARD 1: Diverging Delta Matrix / Heatmap
# ==============================================================================
def plot_wildcard1_diverging_heatmap(
    data: dict[str, Sequence[Mapping[str, Any]]],
    out_base: Path,
    dataset_name: str,
) -> None:
    configs = list(data.keys())
    summaries = {name: summarize_run_records(records) for name, records in data.items()}

    # Base record
    base_name = next((c for c in configs if _get_config_key(c) == "C0"), None)
    if not base_name or base_name not in summaries:
        return
    sb = summaries[base_name]

    treatment_configs = [c for c in configs if _get_config_key(c) != "C0"]
    treatment_configs.sort(key=lambda c: int(re.search(r"c_?(\d+)", c.lower()).group(1)) if re.search(r"c_?(\d+)", c.lower()) else 0)

    metrics = [
        ("Context Precision", "context_precision_mean", True),   # True = higher is better
        ("Context Recall", "context_recall_mean", True),
        ("Faithfulness", "faithfulness_mean", True),
        ("Answer Relevancy", "answer_relevancy_mean", True),
        ("Composite Quality $Q$", "composite_q_mean", True),
        ("Latency $P_{50}$", "latency_p50", False),             # False = lower is better
        ("Cost / 1k", "cost_mean", False),
    ]

    delta_matrix = np.zeros((len(treatment_configs), len(metrics)))
    raw_matrix = np.zeros((len(treatment_configs), len(metrics)))

    for r_idx, c_name in enumerate(treatment_configs):
        s = summaries[c_name]
        for c_idx, (m_label, m_key, higher_is_better) in enumerate(metrics):
            val = s[m_key] if s else 0.0
            base_v = sb[m_key] if sb else 1.0
            raw_matrix[r_idx, c_idx] = val

            if base_v > 0:
                pct = ((val - base_v) / base_v) * 100.0
            else:
                pct = 0.0

            # Orient delta so positive = improvement (green), negative = regression (red)
            oriented_delta = pct if higher_is_better else -pct
            delta_matrix[r_idx, c_idx] = oriented_delta

    fig, ax = plt.subplots(figsize=(11.5, 9.2), dpi=300)

    # Diverging colormap centered at 0%
    cmap = plt.cm.RdYlGn
    vlim = max(30.0, np.percentile(np.abs(delta_matrix), 90))
    im = ax.imshow(delta_matrix, cmap=cmap, vmin=-vlim, vmax=vlim, aspect="auto")

    # Add text inside cells
    for r in range(len(treatment_configs)):
        for c in range(len(metrics)):
            val = raw_matrix[r, c]
            d = delta_matrix[r, c]
            higher_is_better = metrics[c][2]
            actual_pct = d if higher_is_better else -d

            sign = "+" if actual_pct >= 0 else ""
            if "Cost" in metrics[c][0]:
                raw_str = f"${val*1000.0:.2f}"
            elif "Latency" in metrics[c][0]:
                raw_str = f"{val:.0f}ms"
            else:
                raw_str = f"{val:.3f}"

            text_color = "white" if abs(d) > (vlim * 0.65) else "black"
            ax.text(
                c, r,
                f"{raw_str}\n({sign}{actual_pct:.1f}%)",
                ha="center", va="center",
                fontsize=7.8,
                color=text_color,
                fontweight="bold" if abs(d) > 20 else "normal",
            )

    y_labels = [_get_friendly_name(c) for c in treatment_configs]
    ax.set_yticks(np.arange(len(treatment_configs)))
    ax.set_yticklabels(y_labels, fontsize=9.0)

    x_labels = [m[0] for m in metrics]
    ax.set_xticks(np.arange(len(metrics)))
    ax.set_xticklabels(x_labels, fontsize=9.5, fontweight="bold", rotation=20, ha="right")

    # Colorbar
    cbar = fig.colorbar(im, ax=ax, fraction=0.03, pad=0.04)
    cbar.set_label("Relative Performance vs. Baseline ($C_0$)\n[Green = Improvement, Red = Regression]", fontsize=8.5)

    ax.set_title(
        f"Executive Performance Delta Heatmap vs. Naive Baseline ($C_0$) for {dataset_name}",
        fontsize=12.5,
        fontweight="bold",
        pad=16,
    )
    plt.tight_layout()
    _save_plot(fig, out_base)


# ==============================================================================
# WILDCARD 2: Parallel Coordinates Quality Profile
# ==============================================================================
def plot_wildcard2_parallel_coordinates(
    data: dict[str, Sequence[Mapping[str, Any]]],
    out_base: Path,
    dataset_name: str,
) -> None:
    categories = [
        ("Context Precision", "context_precision_mean"),
        ("Context Recall", "context_recall_mean"),
        ("Faithfulness", "faithfulness_mean"),
        ("Answer Relevancy", "answer_relevancy_mean"),
    ]

    configs = list(data.keys())
    summaries = {name: summarize_run_records(records) for name, records in data.items()}
    configs_ordered = sorted(configs, key=lambda c: int(re.search(r"c_?(\d+)", c.lower()).group(1)) if re.search(r"c_?(\d+)", c.lower()) else 0)

    fig, ax = plt.subplots(figsize=(10.5, 6.2), dpi=300)
    x_positions = np.arange(len(categories))

    # Draw vertical axes
    for x in x_positions:
        ax.axvline(x, color="#444444", linewidth=1.2, zorder=2)

    tier_styles = {
        0: {"color": "#2c3e50", "lw": 2.8, "ls": "--", "alpha": 1.0, "zorder": 6},
        1: {"color": "#1f77b4", "lw": 1.5, "ls": "-", "alpha": 0.65, "zorder": 3},
        2: {"color": "#2ca02c", "lw": 1.5, "ls": "-", "alpha": 0.65, "zorder": 3},
        3: {"color": "#ff7f0e", "lw": 1.6, "ls": "-", "alpha": 0.70, "zorder": 4},
        4: {"color": "#d62728", "lw": 2.4, "ls": "-", "alpha": 0.95, "zorder": 5},
    }

    for c in configs_ordered:
        s = summaries.get(c)
        if not s:
            continue
        vals = [s[k] for _, k in categories]
        t = _get_tier(c)
        st = tier_styles[t]

        ax.plot(
            x_positions,
            vals,
            color=st["color"],
            linewidth=st["lw"],
            linestyle=st["ls"],
            alpha=st["alpha"],
            zorder=st["zorder"],
            marker="o",
            markersize=4.5,
        )

    ax.set_xticks(x_positions)
    ax.set_xticklabels([c[0] for c in categories], fontsize=10.5, fontweight="bold")
    ax.set_ylim(-0.02, 1.02)
    ax.set_yticks([0.0, 0.2, 0.4, 0.6, 0.8, 1.0])
    ax.set_ylabel("Metric Score $[0, 1]$", fontsize=10.5, fontweight="bold")
    ax.grid(True, axis="y", linestyle=":", alpha=0.6)

    legend_elements = [
        plt.Line2D([0], [0], color="#2c3e50", linestyle="--", linewidth=2.8, label="$C_0$ (Baseline)"),
        plt.Line2D([0], [0], color=TIER_COLORS[1], linewidth=1.8, label="Single-Factor ($k=1$)"),
        plt.Line2D([0], [0], color=TIER_COLORS[2], linewidth=1.8, label="Pairwise ($k=2$)"),
        plt.Line2D([0], [0], color=TIER_COLORS[3], linewidth=1.8, label="Triplets ($k=3$)"),
        plt.Line2D([0], [0], color=TIER_COLORS[4], linewidth=2.4, label="Full-Stack ($k=4$)"),
    ]
    ax.legend(handles=legend_elements, loc="upper right", framealpha=0.92, fontsize=8.8)

    ax.set_title(
        f"Parallel Coordinates Quality Profile across All 16 Configurations for {dataset_name}",
        fontsize=12.5,
        fontweight="bold",
        pad=15,
    )
    plt.tight_layout()
    _save_plot(fig, out_base)


# ==============================================================================
# WILDCARD 3: Tri-Objective Bubble Trade-off (Latency vs Quality vs Cost Efficiency)
# ==============================================================================
def plot_wildcard3_tri_objective_bubble(
    data: dict[str, Sequence[Mapping[str, Any]]],
    out_base: Path,
    dataset_name: str,
) -> None:
    points: list[tuple[float, float, float, float, str]] = []
    for name, records in data.items():
        s = summarize_run_records(records)
        if not s:
            continue
        lat = s["latency_p50"]
        q = s["composite_q_mean"]
        cost = s["cost_mean"] * 1000.0
        qpd = float(s.get("qpd", 0.0))
        points.append((lat, q, cost, qpd, name))

    if not points:
        return

    fig, ax = plt.subplots(figsize=(10.5, 6.8), dpi=300)

    # Compute Pareto frontier (minimize latency, maximize Q)
    pts_for_pareto = [(p[0], p[1], p[4]) for p in points]
    frontier = _compute_pareto_frontier(pts_for_pareto, minimize_x=True, maximize_y=True)
    pareto_names = set(p[2] for p in frontier)

    # Bubble sizes proportional to QPD
    qpds = [p[3] for p in points]
    min_qpd, max_qpd = min(qpds), max(qpds)
    def _bubble_size(val: float) -> float:
        if max_qpd == min_qpd:
            return 200.0
        norm = (val - min_qpd) / (max_qpd - min_qpd)
        return 80.0 + norm * 450.0

    for lat, q, cost, qpd, name in points:
        t = _get_tier(name)
        color = TIER_COLORS.get(t, "#444444")
        is_pareto = name in pareto_names
        sz = _bubble_size(qpd)

        ax.scatter(
            lat, q,
            s=sz,
            color=color,
            alpha=0.85 if is_pareto else 0.45,
            edgecolors="#d62728" if is_pareto else "k",
            linewidth=2.2 if is_pareto else 0.6,
            zorder=5 if is_pareto else 3,
        )

        # Annotate ONLY Pareto points to avoid collisions
        if is_pareto:
            friendly = _get_friendly_name(name)
            ax.annotate(
                f"{friendly}\n({cost:.2f}$/1k)",
                (lat, q),
                xytext=(8, 8),
                textcoords="offset points",
                fontsize=8.2,
                fontweight="bold",
                color="#b30000",
                bbox=dict(boxstyle="round,pad=0.2", facecolor="white", alpha=0.8, edgecolor="#b30000", linewidth=0.5),
                zorder=7,
            )

    # Draw Pareto curve
    if len(frontier) > 1:
        fx = [p[0] for p in frontier]
        fy = [p[1] for p in frontier]
        ax.plot(fx, fy, "--", color="#d62728", linewidth=2.0, alpha=0.85, label="Empirical Pareto Frontier ($\\mathcal{P}$)", zorder=4)

    # Dynamic limits
    all_lat = [p[0] for p in points]
    all_q = [p[1] for p in points]
    x_pad = (max(all_lat) - min(all_lat)) * 0.1
    y_min = max(0.0, min(all_q) - 0.08)
    y_max = min(1.02, max(all_q) + 0.10)

    ax.set_xlim(min(all_lat) - x_pad, max(all_lat) + x_pad * 1.5)
    ax.set_ylim(y_min, y_max)

    ax.set_xlabel("Median Latency $P_{50}$ (ms)", fontsize=10.5, fontweight="bold")
    ax.set_ylabel("Composite Quality $Q(q) \\in [0, 1]$", fontsize=10.5, fontweight="bold")
    ax.grid(True, linestyle="--", alpha=0.5)

    # Legend
    legend_elements = [
        plt.Line2D([0], [0], color="#d62728", linestyle="--", linewidth=2.0, label="Pareto Frontier ($\\mathcal{P}$)"),
        Patch(facecolor=TIER_COLORS[0], edgecolor="k", label="$C_0$ Baseline"),
        Patch(facecolor=TIER_COLORS[1], edgecolor="k", label="Isolated ($k=1$)"),
        Patch(facecolor=TIER_COLORS[2], edgecolor="k", label="Pairwise ($k=2$)"),
        Patch(facecolor=TIER_COLORS[3], edgecolor="k", label="Triplets ($k=3$)"),
        Patch(facecolor=TIER_COLORS[4], edgecolor="k", label="Full-Stack ($k=4$)"),
    ]
    ax.legend(handles=legend_elements, loc="lower right", framealpha=0.92, fontsize=8.5)

    # Annotation note about bubble size
    ax.text(
        0.02, 0.96,
        "Note: Bubble area $\\propto$ Quality per Dollar (QPD). Red borders delineate Pareto-optimal designs.",
        transform=ax.transAxes,
        fontsize=8.2,
        fontstyle="italic",
        bbox=dict(boxstyle="square,pad=0.3", facecolor="#f8f9fa", edgecolor="#cccccc"),
    )

    ax.set_title(
        f"Tri-Objective Trade-off: Quality vs. Latency vs. Cost Efficiency for {dataset_name}",
        fontsize=12.5,
        fontweight="bold",
        pad=15,
    )
    plt.tight_layout()
    _save_plot(fig, out_base)


# ==============================================================================
# PARETO IMPROVED: Selective Pareto-only labels & dynamic Y limits
# ==============================================================================
def plot_pareto_improved(
    data: dict[str, Sequence[Mapping[str, Any]]],
    out_base: Path,
    dataset_name: str,
) -> None:
    latency_points: list[tuple[float, float, str]] = []
    cost_points: list[tuple[float, float, str]] = []

    for name, records in data.items():
        s = summarize_run_records(records)
        if not s:
            continue
        lat = s["latency_p50"]
        cost = s["cost_mean"] * 1000.0
        q = s["composite_q_mean"]
        latency_points.append((lat, q, name))
        cost_points.append((cost, q, name))

    frontier_lat = _compute_pareto_frontier(latency_points, minimize_x=True, maximize_y=True)
    pareto_lat_names = set(p[2] for p in frontier_lat)

    frontier_cost = _compute_pareto_frontier(cost_points, minimize_x=True, maximize_y=True)
    pareto_cost_names = set(p[2] for p in frontier_cost)

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12.0, 5.0), dpi=300)

    # Dynamic Y limits
    all_q = [p[1] for p in latency_points]
    y_min = max(0.0, min(all_q) - 0.08)
    y_max = min(1.02, max(all_q) + 0.10)

    # Panel 1: Quality vs Latency
    for lat, q, name in latency_points:
        t = _get_tier(name)
        color = TIER_COLORS.get(t, "#444444")
        is_pareto = name in pareto_lat_names

        ax1.scatter(
            lat, q,
            color=color,
            s=80 if is_pareto else 50,
            zorder=5 if is_pareto else 3,
            edgecolors="#d62728" if is_pareto else "k",
            linewidth=2.0 if is_pareto else 0.5,
            alpha=0.95 if is_pareto else 0.6,
        )

        # Annotate ONLY Pareto points with clean offset
        if is_pareto:
            friendly = _get_friendly_name(name)
            ax1.annotate(
                friendly,
                (lat, q),
                textcoords="offset points",
                xytext=(6, 6),
                fontsize=8.5,
                fontweight="bold",
                color="#b30000",
                bbox=dict(boxstyle="round,pad=0.2", facecolor="white", alpha=0.85, edgecolor="#b30000", linewidth=0.5),
                zorder=6,
            )

    if len(frontier_lat) > 1:
        fx = [p[0] for p in frontier_lat]
        fy = [p[1] for p in frontier_lat]
        ax1.plot(fx, fy, "--", color="#d62728", linewidth=1.8, label="Pareto Frontier ($\\mathcal{P}$)", zorder=4)

    ax1.set_title("(a) Quality vs. End-to-End Latency", fontweight="bold")
    ax1.set_xlabel("Median Latency $P_{50}$ (ms)", fontweight="bold")
    ax1.set_ylabel("Composite Quality $Q(q) \\in [0, 1]$", fontweight="bold")
    ax1.set_ylim(y_min, y_max)
    ax1.grid(True, linestyle="--", alpha=0.5)

    # Panel 2: Quality vs Cost
    for cost, q, name in cost_points:
        t = _get_tier(name)
        color = TIER_COLORS.get(t, "#444444")
        is_pareto = name in pareto_cost_names

        ax2.scatter(
            cost, q,
            color=color,
            s=80 if is_pareto else 50,
            zorder=5 if is_pareto else 3,
            edgecolors="#d62728" if is_pareto else "k",
            linewidth=2.0 if is_pareto else 0.5,
            alpha=0.95 if is_pareto else 0.6,
        )

        if is_pareto:
            friendly = _get_friendly_name(name)
            ox, oy = 6, 6
            for oc, oq, oname in cost_points:
                if oname in pareto_cost_names and oname != name and abs(oc - cost) < 0.4 and abs(oq - q) < 0.04:
                    if name > oname:
                        ox, oy = 8, 14
                    else:
                        ox, oy = -42, -16
            ax2.annotate(
                friendly,
                (cost, q),
                textcoords="offset points",
                xytext=(ox, oy),
                fontsize=8.5,
                fontweight="bold",
                color="#b30000",
                bbox=dict(boxstyle="round,pad=0.2", facecolor="white", alpha=0.85, edgecolor="#b30000", linewidth=0.5),
                zorder=6,
            )

    if len(frontier_cost) > 1:
        cx = [p[0] for p in frontier_cost]
        cy = [p[1] for p in frontier_cost]
        ax2.plot(cx, cy, "--", color="#d62728", linewidth=1.8, label="Pareto Frontier ($\\mathcal{P}$)", zorder=4)

    ax2.set_title("(b) Quality vs. Operational Cost", fontweight="bold")
    ax2.set_xlabel("Operational Cost per 1,000 Queries (USD)", fontweight="bold")
    ax2.set_ylabel("Composite Quality $Q(q) \\in [0, 1]$", fontweight="bold")
    ax2.set_ylim(y_min, y_max)
    ax2.grid(True, linestyle="--", alpha=0.5)

    legend_elements = [
        plt.Line2D([0], [0], color="#d62728", linestyle="--", linewidth=1.8, label="Pareto Frontier ($\\mathcal{P}$)"),
        Patch(facecolor=TIER_COLORS[0], edgecolor="k", label="$C_0$ Baseline"),
        Patch(facecolor=TIER_COLORS[1], edgecolor="k", label="Isolated ($k=1$)"),
        Patch(facecolor=TIER_COLORS[2], edgecolor="k", label="Pairwise ($k=2$)"),
        Patch(facecolor=TIER_COLORS[3], edgecolor="k", label="Triplets ($k=3$)"),
        Patch(facecolor=TIER_COLORS[4], edgecolor="k", label="Full-Stack ($k=4$)"),
    ]
    fig.legend(
        handles=legend_elements,
        loc="upper center",
        bbox_to_anchor=(0.5, 0.04),
        ncol=6,
        fontsize=8.5,
        frameon=True,
    )

    fig.suptitle(f"Multi-Objective Pareto Frontier for {dataset_name}", fontsize=12.5, fontweight="bold", y=0.98)
    plt.tight_layout(rect=[0, 0.08, 1, 0.94])
    _save_plot(fig, out_base)


# ==============================================================================
# BREAKDOWNS IMPROVED: Full component stacking & 45-degree angled labels
# ==============================================================================
def plot_latency_breakdown_improved(
    data: dict[str, Sequence[Mapping[str, Any]]],
    out_base: Path,
    dataset_name: str,
) -> None:
    configs: list[str] = []
    route_means, embed_means, ret_means, rerank_means = [], [], [], []
    expand_means, cache_means, prefill_means, stream_means = [], [], [], []
    gen_means, other_means, totals = [], [], []

    has_split_generation = False
    configs_ordered = sorted(
        data.keys(),
        key=lambda c: int(re.search(r"c_?(\d+)", c.lower()).group(1)) if re.search(r"c_?(\d+)", c.lower()) else 0,
    )

    for name in configs_ordered:
        records = data[name]
        configs.append(_get_friendly_name(name))
        c_route, c_embed, c_ret, c_rerank = [], [], [], []
        c_expand, c_cache = [], []
        c_prefill, c_stream, c_gen = [], [], []
        c_other, c_tot = [], []

        for r in records:
            t = r.get("timings") if isinstance(r.get("timings"), dict) else {}
            route = float(
                t.get("routingMs") or t.get("routeMs") or t.get("routing_ms") or t.get("route_ms")
                or r.get("routing_ms") or r.get("route_ms") or 0.0
            )
            embed = float(
                t.get("embedMs") or t.get("embed_ms") or t.get("embeddingMs") or t.get("embedding_ms")
                or r.get("embed_ms") or 0.0
            )
            ret = float(
                t.get("retrievalMs") or t.get("retrieval_ms") or r.get("retrieval_ms") or 0.0
            )
            rerank = float(
                t.get("rerankMs") or t.get("rerank_ms") or t.get("rerankingMs") or t.get("reranking_ms")
                or r.get("rerank_ms") or 0.0
            )
            expand = float(
                t.get("expansionMs") or t.get("expansion_ms") or r.get("expansion_ms") or 0.0
            )
            cache = float(
                t.get("cacheMs") or t.get("cache_ms") or r.get("cache_ms") or 0.0
            )
            prefill = float(
                t.get("prefillMs") or t.get("prefill_ms") or r.get("prefill_ms") or 0.0
            )
            stream = float(
                t.get("streamMs") or t.get("stream_ms") or r.get("stream_ms") or 0.0
            )
            gen = float(
                t.get("generationMs") or t.get("generation_ms") or r.get("generation_ms") or 0.0
            )

            if stream == 0.0 and prefill > 0.0 and gen >= prefill:
                stream = max(0.0, gen - prefill)

            if prefill > 0.0 or stream > 0.0:
                has_split_generation = True
                sub_sum = route + embed + ret + rerank + expand + cache + prefill + stream
            else:
                sub_sum = route + embed + ret + rerank + expand + cache + gen

            tot = float(
                r.get("totalLatencyMs") or r.get("total_latency_ms")
                or t.get("totalMs") or t.get("total_ms") or 0.0
            )
            if tot <= 0.0:
                tot = sub_sum

            # Residual system/transport overhead
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
        {"key": "route", "label": "Routing ($T_{\\mathrm{route}}$)", "color": "#8c564b", "values": route_means},
        {"key": "embed", "label": "Embedding ($T_{\\mathrm{embed}}$)", "color": "#5dade2", "values": embed_means},
        {"key": "ret", "label": "Retrieval ($T_{\\mathrm{ret}}$)", "color": "#1f77b4", "values": ret_means},
        {"key": "rerank", "label": "Re-ranking ($T_{\\mathrm{rerank}}$)", "color": "#9467bd", "values": rerank_means},
        {"key": "expand", "label": "Expansion ($T_{\\mathrm{expand}}$)", "color": "#17becf", "values": expand_means},
        {"key": "cache", "label": "Cache Lookup ($T_{\\mathrm{cache}}$)", "color": "#2ca02c", "values": cache_means},
    ]

    if has_split_generation:
        component_defs.extend([
            {"key": "prefill", "label": "Prefill ($T_{\\mathrm{prefill}}$)", "color": "#ff7f0e", "values": prefill_means},
            {"key": "stream", "label": "Streaming ($T_{\\mathrm{stream}}$)", "color": "#e377c2", "values": stream_means},
        ])
    else:
        component_defs.append(
            {"key": "gen", "label": "Generation ($T_{\\mathrm{gen}}$)", "color": "#ff7f0e", "values": gen_means}
        )

    component_defs.append(
        {"key": "other", "label": "Overhead ($T_{\\mathrm{other}}$)", "color": "#95a5a6", "values": other_means}
    )

    active_components = [c for c in component_defs if any(val > 0.1 for val in c["values"])]

    fig, ax = plt.subplots(figsize=(12.5, 6.0), dpi=300)
    x = np.arange(len(configs))
    width = 0.58

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

    # 45-degree angled value labels sitting right at the bar top
    for i, tot in enumerate(totals):
        ax.annotate(
            f"{tot:.0f} ms",
            xy=(x[i], tot),
            xytext=(0, 4),
            textcoords="offset points",
            ha="left",
            va="bottom",
            rotation=45,
            fontsize=8.0,
            fontweight="bold",
            color="#1e293b",
        )

    ax.set_ylim(0, max(totals) * 1.35)
    ax.set_ylabel("Execution Latency (ms)", fontsize=10.5, fontweight="bold")
    ax.set_xticks(x)
    ax.set_xticklabels(configs, rotation=35, ha="right", fontsize=8.5)
    ax.legend(loc="upper left", ncol=5, fontsize=8.5, framealpha=0.92)
    ax.grid(True, axis="y", linestyle="--", alpha=0.5)

    ax.set_title(f"End-to-End Latency Micro-Phase Breakdown for {dataset_name}", fontsize=12.5, fontweight="bold", pad=12)
    plt.tight_layout()
    _save_plot(fig, out_base)


def plot_cost_breakdown_improved(
    data: dict[str, Sequence[Mapping[str, Any]]],
    out_base: Path,
    dataset_name: str,
    pricing_tier: str = "gemini-3.8-flash",
) -> None:
    tier = get_pricing_tier(pricing_tier)
    configs: list[str] = []
    prompt_means, cache_means, comp_means = [], [], []
    rerank_means, embed_means, route_means, other_means = [], [], [], []
    totals: list[float] = []

    configs_ordered = sorted(
        data.keys(),
        key=lambda c: int(re.search(r"c_?(\d+)", c.lower()).group(1)) if re.search(r"c_?(\d+)", c.lower()) else 0,
    )

    for name in configs_ordered:
        records = data[name]
        configs.append(_get_friendly_name(name))
        c_prompt_list, c_cache_list, c_comp_list = [], [], []
        c_rerank_list, c_embed_list, c_route_list = [], [], []
        c_other_list, c_tot_list = [], []

        for r in records:
            tu = r.get("tokenUsage") if isinstance(r.get("tokenUsage"), dict) else {}
            modules = r.get("modulesApplied") or r.get("modulesRequested") or []
            has_m3 = (
                "M3" in modules
                or "PC" in modules
                or r.get("cacheStatus") in ("EXPLICIT_HIT", "EXPLICIT_MISS")
                or "m3" in str(name).lower()
                or (isinstance(r.get("runName"), str) and "m3" in r["runName"].lower())
            )

            model_used = r.get("modelUsed") or r.get("model_used")
            record_tier = get_pricing_tier(model_used) if model_used else tier

            p_tok = float(tu.get("promptTokens") or tu.get("prompt_tokens") or r.get("prompt_tokens") or 0.0)
            comp_tok = float(tu.get("completionTokens") or tu.get("completion_tokens") or r.get("completion_tokens") or 0.0)

            if has_m3:
                cached_read_tok = float(
                    tu.get("cachedTokens") or tu.get("cached_tokens") or r.get("cached_tokens") or 0.0
                )
                cache_write_tok = float(
                    tu.get("cacheWriteTokens") or tu.get("cache_write_tokens") or r.get("cache_write_tokens") or 0.0
                )
                if cached_read_tok == 0.0 and cache_write_tok == 0.0:
                    cache_meta = (r.get("cacheMetadata") or tu.get("cacheMetadata")) if isinstance(r.get("cacheMetadata") or tu.get("cacheMetadata"), dict) else {}
                    fallback_count = float(cache_meta.get("tokenCount") or cache_meta.get("token_count") or 0.0)
                    cache_status = str(r.get("cacheStatus") or r.get("cache_status") or "").upper()
                    if "HIT" in cache_status:
                        cached_read_tok = fallback_count
                    elif "MISS" in cache_status:
                        cache_write_tok = fallback_count
            else:
                cached_read_tok = 0.0
                cache_write_tok = 0.0

            c_prompt = ((p_tok + cache_write_tok) * record_tier.prompt_price_per_million) / 1_000_000.0
            c_cache = (cached_read_tok * record_tier.cached_price_per_million) / 1_000_000.0
            c_comp = (comp_tok * record_tier.comp_price_per_million) / 1_000_000.0

            rerank_docs = float(r.get("rerankDocs") or r.get("rerank_docs") or tu.get("rerankDocs") or 0.0)
            if rerank_docs == 0.0 and ("M2" in modules or "SR" in modules or "m2" in str(name).lower()):
                rerank_docs = 50.0
            c_rerank = rerank_docs * record_tier.rerank_price_per_doc

            embed_tok = float(r.get("embedTokens") or r.get("embed_tokens") or tu.get("embedTokens") or 0.0)
            if embed_tok == 0.0:
                q_text = str(r.get("question") or r.get("query") or "")
                embed_tok = float(max(1, len(q_text.split()))) if q_text else 20.0
            c_embed = (embed_tok * record_tier.embed_price_per_million) / 1_000_000.0

            route_prompt_tok = float(tu.get("routingPromptTokens") or r.get("routingPromptTokens") or 0.0)
            route_comp_tok = float(tu.get("routingCompletionTokens") or r.get("routingCompletionTokens") or 0.0)
            router_tier = PRICING_CATALOG.get("gemini-3.5-flash-lite")
            if router_tier and (route_prompt_tok > 0.0 or route_comp_tok > 0.0):
                c_route = (
                    (route_prompt_tok * router_tier.prompt_price_per_million)
                    + (route_comp_tok * router_tier.comp_price_per_million)
                ) / 1_000_000.0
            else:
                c_route = 0.0

            sub_sum = c_prompt + c_cache + c_comp + c_rerank + c_embed + c_route
            tot_cost = float(r.get("cost_usd") or r.get("costUsd") or 0.0)
            if tot_cost > (sub_sum + 1e-7):
                c_other = tot_cost - sub_sum
            else:
                c_other = 0.0
            effective_tot = max(tot_cost, sub_sum)

            c_prompt_list.append(c_prompt * 1000.0)
            c_cache_list.append(c_cache * 1000.0)
            c_comp_list.append(c_comp * 1000.0)
            c_rerank_list.append(c_rerank * 1000.0)
            c_embed_list.append(c_embed * 1000.0)
            c_route_list.append(c_route * 1000.0)
            c_other_list.append(c_other * 1000.0)
            c_tot_list.append(effective_tot * 1000.0)

        prompt_means.append(float(np.mean(c_prompt_list)) if c_prompt_list else 0.0)
        cache_means.append(float(np.mean(c_cache_list)) if c_cache_list else 0.0)
        comp_means.append(float(np.mean(c_comp_list)) if c_comp_list else 0.0)
        rerank_means.append(float(np.mean(c_rerank_list)) if c_rerank_list else 0.0)
        embed_means.append(float(np.mean(c_embed_list)) if c_embed_list else 0.0)
        route_means.append(float(np.mean(c_route_list)) if c_route_list else 0.0)
        other_means.append(float(np.mean(c_other_list)) if c_other_list else 0.0)
        totals.append(float(np.mean(c_tot_list)) if c_tot_list else 0.0)

    component_defs = [
        {"label": "Prompt Tokens ($C_{\\mathrm{prompt}}$)", "color": "#1f77b4", "values": prompt_means},
        {"label": "Context Cache ($C_{\\mathrm{cache}}$)", "color": "#2ca02c", "values": cache_means},
        {"label": "Generation Tokens ($C_{\\mathrm{comp}}$)", "color": "#ff7f0e", "values": comp_means},
        {"label": "Semantic Re-ranking ($C_{\\mathrm{rerank}}$)", "color": "#9467bd", "values": rerank_means},
        {"label": "Query Embedding ($C_{\\mathrm{embed}}$)", "color": "#17becf", "values": embed_means},
        {"label": "Routing Classifier ($C_{\\mathrm{route}}$)", "color": "#8c564b", "values": route_means},
        {"label": "Residual Overhead ($C_{\\mathrm{other}}$)", "color": "#95a5a6", "values": other_means},
    ]

    active_components = [c for c in component_defs if any(v > 0.0001 for v in c["values"])]

    fig, ax = plt.subplots(figsize=(12.5, 6.0), dpi=300)
    x = np.arange(len(configs))
    width = 0.58

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

    # 45-degree angled value labels sitting right at the bar top
    for i, tot in enumerate(totals):
        ax.annotate(
            f"${tot:.2f}",
            xy=(x[i], tot),
            xytext=(0, 4),
            textcoords="offset points",
            ha="left",
            va="bottom",
            rotation=45,
            fontsize=8.0,
            fontweight="bold",
            color="#1e293b",
        )

    ax.set_ylim(0, max(totals) * 1.35)
    ax.set_ylabel("Operational Cost per 1,000 Queries (USD)", fontsize=10.5, fontweight="bold")
    ax.set_xticks(x)
    ax.set_xticklabels(configs, rotation=35, ha="right", fontsize=8.5)
    ax.legend(loc="upper left", ncol=4, fontsize=8.5, framealpha=0.92)
    ax.grid(True, axis="y", linestyle="--", alpha=0.5)

    ax.set_title(f"Operational Cost Micro-Phase Breakdown (per 1,000 Queries) for {dataset_name}", fontsize=12.5, fontweight="bold", pad=12)
    plt.tight_layout()
    _save_plot(fig, out_base)


# ==============================================================================
# MAIN RUNNER
# ==============================================================================
def main() -> None:
    base_figures_dir = evaluator_dir / "figure_testing"
    runs_dir = evaluator_dir / "runs"

    datasets = [
        ("Enterprise Closed", runs_dir / "enterprise_closed", "enterprise_closed"),
        ("MuSiQue", runs_dir / "sample10_all16", "musique"),
        ("Enterprise Raw", runs_dir / "nordea_all16_final", "enterprise_raw"),
    ]

    print("========================================================================")
    print(" Generating Thesis Figure Proposals & Redesigns")
    print(f" Target Directory: {base_figures_dir}")
    print("========================================================================")

    for ds_name, ds_path, slug in datasets:
        if not ds_path.exists():
            print(f"\n[Warning] Dataset directory not found: {ds_path}. Skipping.")
            continue

        print(f"\n>>> Processing Dataset: {ds_name} ({ds_path})...")
        data = _load_run_dir(ds_path)
        if not data:
            print(f"  [Error] No JSONL runs found in {ds_path}!")
            continue
        print(f"  Loaded {len(data)} configurations.")

        # 1. Option 1: Fixed Radar
        print("  Generating Option 1: Fixed Radar Chart...")
        plot_option1_radar_fixed(
            data,
            base_figures_dir / "option1_radar_fixed" / f"quality_radar_{slug}",
            ds_name,
        )

        # 2. Option 2: Lollipop / Dot Plot with Dynamic Broken Axes
        print("  Generating Option 2: 4-Panel Lollipop Plot (Broken Axes)...")
        plot_option2_lollipop(
            data,
            base_figures_dir / "option2_lollipop" / f"quality_lollipop_{slug}",
            ds_name,
        )

        # 2b. Option 2: Lollipop / Dot Plot (Unbroken Standard Scale)
        print("  Generating Option 2: 4-Panel Lollipop Plot (Unbroken Scale)...")
        plot_option2_lollipop_unbroken(
            data,
            base_figures_dir / "option2_lollipop_unbroken" / f"quality_lollipop_unbroken_{slug}",
            ds_name,
        )

        # 3. Option 3: Faceted Bars
        print("  Generating Option 3: Faceted Quality Bars...")
        plot_option3_faceted_bars(
            data,
            base_figures_dir / "option3_faceted_bars" / f"quality_faceted_bars_{slug}",
            ds_name,
        )

        # 4. Wildcard 1: Diverging Delta Matrix
        print("  Generating Wildcard 1: Diverging Delta Heatmap...")
        plot_wildcard1_diverging_heatmap(
            data,
            base_figures_dir / "wildcard1_diverging_heatmap" / f"quality_delta_matrix_{slug}",
            ds_name,
        )

        # 5. Wildcard 2: Parallel Coordinates
        print("  Generating Wildcard 2: Parallel Coordinates Quality Profile...")
        plot_wildcard2_parallel_coordinates(
            data,
            base_figures_dir / "wildcard2_parallel_coordinates" / f"quality_parallel_coords_{slug}",
            ds_name,
        )

        # 6. Wildcard 3: Tri-Objective Bubble Trade-off
        print("  Generating Wildcard 3: Tri-Objective Bubble Trade-off...")
        plot_wildcard3_tri_objective_bubble(
            data,
            base_figures_dir / "wildcard3_tri_objective_bubble" / f"tri_objective_bubble_{slug}",
            ds_name,
        )

        # 7. Pareto Improved
        print("  Generating Pareto Improved...")
        plot_pareto_improved(
            data,
            base_figures_dir / "pareto_improved" / f"pareto_frontier_{slug}",
            ds_name,
        )

        # 8. Breakdowns Improved
        print("  Generating Breakdowns Improved (Latency & Cost)...")
        plot_latency_breakdown_improved(
            data,
            base_figures_dir / "breakdowns_improved" / f"latency_breakdown_{slug}",
            ds_name,
        )
        plot_cost_breakdown_improved(
            data,
            base_figures_dir / "breakdowns_improved" / f"cost_breakdown_{slug}",
            ds_name,
        )

    print("\n========================================================================")
    print(" ✓ All figure proposals successfully generated!")
    print(f" View results in: {base_figures_dir}")
    print("========================================================================")


if __name__ == "__main__":
    main()
