"""Publication-ready vector PDF figures for Master's thesis.

Generates:
- thesis/figures/pareto_frontier.pdf (Quality vs Latency, Quality vs Cost with empirical Pareto frontier)
- thesis/figures/pareto_frontier_3d.pdf (Empirical 3D Pareto frontier across QPS, QPD, Composite Q)
- thesis/figures/latency_breakdown.pdf (Full 9-phase stacked micro-breakdown, 45° angled labels)
- thesis/figures/cost_breakdown.pdf (Full 7-phase stacked cost breakdown per 1,000 queries, 45° angled labels)
- thesis/figures/quality_lollipop.pdf [PRIMARY] (2x2 horizontal lollipop plot with tier pastel
    backgrounds, reinforced grid, and dynamic adaptive broken axes per metric)
- thesis/figures/quality_delta_matrix.pdf (Executive performance delta heatmap vs. C0 baseline)
- thesis/figures/quality_radar.pdf [LEGACY] (4-panel radar / spider chart, kept for backwards compat)

Visual Standards (adopted permanently from October 2026 evaluation runs):
- Quality profiles: 4-panel horizontal lollipop with dynamic broken x-axes when metric values cluster
  above 0.40 (e.g. Context Precision, Context Recall). Each architectural tier (k=0..4) highlighted
  by a distinct pastel background band and separated by subtle boundary lines.
- Latency breakdown: All 9 micro-phases stacked (route, embed, ret, rerank, expand, cache, prefill,
  stream, other), total label rotated 45°, y-limit = 1.35 * max_total.
- Cost breakdown: All 7 cost components stacked (prompt, cache, comp, rerank, embed, route, other),
  total label rotated 45°, y-limit = 1.35 * max_total.
- Pareto frontier: Only Pareto-optimal points annotated, dynamic Y bounds, anti-collision offsets.
"""

from __future__ import annotations

import math
import re
from pathlib import Path
from typing import Any, Mapping, Optional, Sequence

import matplotlib
matplotlib.use("Agg")  # Headless backend for Linux environments
import matplotlib.gridspec as gridspec
import matplotlib.pyplot as plt
from matplotlib.patches import Patch
from mpl_toolkits.mplot3d import Axes3D  # noqa: F401 - registers 3d projection
import numpy as np

from src.metrics import PRICING_CATALOG, get_pricing_tier
from src.reporter import summarize_run_records

# ── Tier colour palettes ──────────────────────────────────────────────────────
_TIER_COLORS: dict[int, str] = {
    0: "#2c3e50",  # Baseline: dark slate
    1: "#1f77b4",  # k=1 Isolated: blue
    2: "#2ca02c",  # k=2 Pairwise: green
    3: "#ff7f0e",  # k=3 Triplets: orange
    4: "#d62728",  # k=4 Full-Stack: red
}

_TIER_BG_COLORS: dict[int, str] = {
    0: "#f1f5f9",  # Baseline: soft slate
    1: "#eff6ff",  # k=1: soft light blue
    2: "#f0fdf4",  # k=2: soft light green
    3: "#fff7ed",  # k=3: soft light orange/peach
    4: "#fef2f2",  # k=4: soft light red/rose
}

_CONFIG_FRIENDLY_NAMES: dict[str, str] = {
    "C0": "$C_0$ (Baseline)",
    "C1": "$C_1$ (M1)",   "C2": "$C_2$ (M2)",   "C3": "$C_3$ (M3)",   "C4": "$C_4$ (M4)",
    "C5": "$C_5$ (M1+M2)", "C6": "$C_6$ (M1+M3)", "C7": "$C_7$ (M1+M4)",
    "C8": "$C_8$ (M2+M3)", "C9": "$C_9$ (M2+M4)", "C10": "$C_{10}$ (M3+M4)",
    "C11": "$C_{11}$ (M1+M2+M3)", "C12": "$C_{12}$ (M1+M2+M4)",
    "C13": "$C_{13}$ (M1+M3+M4)", "C14": "$C_{14}$ (M2+M3+M4)",
    "C15": "$C_{15}$ (Full-Stack)",
}


def _get_config_key(name: str) -> str:
    m = re.search(r"c_?(\d+)", name.lower())
    if m:
        return f"C{m.group(1)}"
    if "base" in name.lower():
        return "C0"
    return name


def _get_friendly_name_pr(name: str) -> str:
    key = _get_config_key(name)
    return _CONFIG_FRIENDLY_NAMES.get(key, name.replace("run_", "").replace("_", " "))


def _get_tier_pr(name: str) -> int:
    key = _get_config_key(name)
    m = re.search(r"C(\d+)", key)
    if not m:
        return 0
    c = int(m.group(1))
    if c == 0:
        return 0
    if 1 <= c <= 4:
        return 1
    if 5 <= c <= 10:
        return 2
    if 11 <= c <= 14:
        return 3
    return 4


def _draw_tier_bands_pr(ax: plt.Axes, y_pos: "np.ndarray", tiers: list[int], total: int) -> None:
    """Draws soft tinted horizontal tier bands with boundary divider lines."""
    for t in range(5):
        ys = [y for y, tier in zip(y_pos, tiers) if tier == t]
        if not ys:
            continue
        min_y, max_y = min(ys), max(ys)
        ax.axhspan(min_y - 0.5, max_y + 0.5, facecolor=_TIER_BG_COLORS[t], alpha=0.85, zorder=0)
        if max_y + 0.5 < total - 0.5:
            ax.axhline(max_y + 0.5, color="#cbd5e1", linewidth=0.85, linestyle="-", zorder=1)
        if min_y - 0.5 > -0.5:
            ax.axhline(min_y - 0.5, color="#cbd5e1", linewidth=0.85, linestyle="-", zorder=1)


def _add_axis_break_slashes(ax_l: plt.Axes, ax_r: plt.Axes, d: float = 0.025) -> None:
    """Adds '//' diagonal break marks on the shared spine between two sub-axes."""
    ax_l.spines["right"].set_visible(False)
    ax_r.spines["left"].set_visible(False)
    ax_r.tick_params(left=False, labelleft=False)
    kw = dict(color="#475569", clip_on=False, linewidth=1.1)
    ax_l.plot((1 - d, 1 + d), (-d, +d), transform=ax_l.transAxes, **kw)
    ax_l.plot((1 - d, 1 + d), (1 - d, 1 + d), transform=ax_l.transAxes, **kw)
    d_r = d * 0.20
    ax_r.plot((-d_r, +d_r), (-d, +d), transform=ax_r.transAxes, **kw)
    ax_r.plot((-d_r, +d_r), (1 - d, 1 + d), transform=ax_r.transAxes, **kw)




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
    dataset_name: Optional[str] = None,
) -> Path:
    """Generates dual-panel Quality vs Latency and Quality vs Cost Pareto trade-off figure.

    Visual Standards (Pareto Improved):
    - Tier colors used for all scatter points.
    - Only Pareto-optimal points are annotated with friendly names and offset labels to eliminate clutter.
    - Dynamic Y-limits based on actual data range.
    - Red dashed curve delineating the empirical Pareto frontier.
    - Bottom legend identifying tiers and the Pareto frontier.
    """
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
        cost = s["cost_mean"] * 1000.0
        q = s["composite_q_mean"]
        latency_points.append((lat, q, name))
        cost_points.append((cost, q, name))

    if not latency_points:
        fig, ax = plt.subplots(figsize=(8, 4))
        ax.text(0.5, 0.5, "No data available", ha="center", va="center", transform=ax.transAxes)
        plt.savefig(out_file, bbox_inches="tight", dpi=300)
        plt.close(fig)
        return out_file

    frontier_lat = _compute_pareto_frontier(latency_points, minimize_x=True, maximize_y=True)
    pareto_lat_names = set(p[2] for p in frontier_lat)

    frontier_cost = _compute_pareto_frontier(cost_points, minimize_x=True, maximize_y=True)
    pareto_cost_names = set(p[2] for p in frontier_cost)

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(11.5, 5.0), dpi=300)

    # Dynamic Y limits
    all_q = [p[1] for p in latency_points]
    y_min = max(0.0, min(all_q) - 0.08)
    y_max = min(1.02, max(all_q) + 0.10)

    # ── Panel 1: Quality vs Latency ───────────────────────────────────────────
    for lat, q, name in latency_points:
        t = _get_tier_pr(name)
        color = _TIER_COLORS.get(t, "#444444")
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

        if is_pareto:
            friendly = _get_friendly_name_pr(name)
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

    # ── Panel 2: Quality vs Cost ──────────────────────────────────────────────
    for cost, q, name in cost_points:
        t = _get_tier_pr(name)
        color = _TIER_COLORS.get(t, "#444444")
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
            friendly = _get_friendly_name_pr(name)
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

    # ── Combined legend below subplots ────────────────────────────────────────
    legend_elements = [
        plt.Line2D([0], [0], color="#d62728", linestyle="--", linewidth=1.8, label="Pareto Frontier ($\\mathcal{P}$)"),
        Patch(facecolor=_TIER_COLORS[0], edgecolor="k", label="$C_0$ Baseline"),
        Patch(facecolor=_TIER_COLORS[1], edgecolor="k", label="Single-opt ($k=1$)"),
        Patch(facecolor=_TIER_COLORS[2], edgecolor="k", label="Dual-opt ($k=2$)"),
        Patch(facecolor=_TIER_COLORS[3], edgecolor="k", label="Triple-opt ($k=3$)"),
        Patch(facecolor=_TIER_COLORS[4], edgecolor="k", label="Full-Stack ($k=4$)"),
    ]
    fig.legend(
        handles=legend_elements,
        loc="upper center",
        bbox_to_anchor=(0.5, 0.04),
        ncol=6,
        fontsize=8.5,
        frameon=True,
    )

    if dataset_name:
        fig.suptitle(f"Multi-Objective Pareto Frontier for {dataset_name}", fontsize=12.5, fontweight="bold", y=0.98)
        plt.tight_layout(rect=[0, 0.08, 1, 0.94])
    else:
        plt.tight_layout(rect=[0, 0.08, 1, 0.94])

    plt.savefig(out_file, bbox_inches="tight", dpi=300)
    plt.close(fig)
    return out_file


def plot_latency_breakdown(
    data: dict[str, Sequence[Mapping[str, Any]]],
    output_path: Path | str = "thesis/figures/latency_breakdown.pdf",
    dataset_name: Optional[str] = None,
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

            if stream == 0.0 and prefill > 0.0 and gen >= prefill:
                stream = max(0.0, gen - prefill)

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

    # Label total latency on top of bars, rotated 45° for readability
    max_tot = max(totals) if totals else 1.0
    for i, tot in enumerate(totals):
        ax.annotate(
            f"{tot:.0f} ms",
            xy=(x[i], tot),
            xytext=(4, 4),
            textcoords="offset points",
            ha="left",
            va="bottom",
            fontsize=7.5,
            fontweight="bold",
            rotation=45,
        )

    ax.set_ylabel("Execution Latency (ms)")
    title_suffix = f" for {dataset_name}" if dataset_name else ""
    ax.set_title(f"End-to-End Latency Micro-Phase Breakdown{title_suffix}", fontweight="bold")
    ax.set_xticks(x)

    display_labels = [c.replace("_", " ") for c in configs]
    ax.set_xticklabels(display_labels, rotation=45, ha="right", fontsize=9)

    ax.set_ylim(0, max_tot * 1.35)
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


def _categorize_radar_tiers(
    configs: Sequence[str],
) -> tuple[list[str], list[str], list[str], list[str], list[str]]:
    """Categorizes configuration names into (baseline, singulars, duos, trios, quads).

    Classification rules:
    - Baseline: 'C0' or 'C_0' or 'base' in name.
    - If C-number pattern (e.g. C1..C15 or C_1..C_15) is present:
        - 0: baseline
        - 1..4: singulars (k=1)
        - 5..10: duos (k=2)
        - 11..14: trios (k=3)
        - >=15: full-stack (k=4)
    - Fallback heuristic based on module separators:
        - Count number of module tokens (M1, M2, M3, M4, S2B, SR, PC, ROUTE, etc.):
        - 1 module -> singular
        - 2 modules -> duo
        - 3 modules -> trio
        - >=4 modules -> quad
    """
    baseline_list: list[str] = []
    singulars: list[str] = []
    duos: list[str] = []
    trios: list[str] = []
    quads: list[str] = []

    for name in configs:
        lower = name.lower()
        # Check for C<number> or C_<number>
        m = re.search(r"c_?(\d+)", lower)
        if m:
            c_num = int(m.group(1))
            if c_num == 0:
                baseline_list.append(name)
            elif 1 <= c_num <= 4:
                singulars.append(name)
            elif 5 <= c_num <= 10:
                duos.append(name)
            elif 11 <= c_num <= 14:
                trios.append(name)
            else:
                quads.append(name)
            continue

        if "base" in lower or "c0" in lower:
            baseline_list.append(name)
            continue

        # Heuristic fallback: count '+' or count 'm[1-4]' occurrences
        m_matches = re.findall(r"m[1-4]", lower)
        plus_count = name.count("+")
        count = max(len(m_matches), plus_count + 1 if plus_count > 0 else 1)
        if count >= 4:
            quads.append(name)
        elif count == 3:
            trios.append(name)
        elif count == 2:
            duos.append(name)
        else:
            singulars.append(name)

    return baseline_list, singulars, duos, trios, quads


def plot_ragas_metrics_radar(
    data: dict[str, Sequence[Mapping[str, Any]]],
    output_path: Path | str = "thesis/figures/ragas_metrics_radar.pdf",
    dataset_name: Optional[str] = None,
) -> Path:
    """Generates radar chart across the 4 core RAGAS metrics.

    If >= 5 configurations are provided, generates a 2x2 (4-panel) figure comparing each
    architectural tier against baseline (k=0):
    1. Single-Factor Isolated (k=1) vs. Baseline
    2. Pairwise Combinations (k=2) vs. Baseline
    3. Multi-Module Triplets (k=3) vs. Baseline
    4. Full-Stack Integration (k=4) vs. Baseline
    """
    _set_publication_style()
    out_file = Path(output_path)
    out_file.parent.mkdir(parents=True, exist_ok=True)

    categories = ["Context Precision", "Context Recall", "Faithfulness", "Answer Relevancy"]
    num_vars = len(categories)

    angles = np.linspace(0, 2 * np.pi, num_vars, endpoint=False).tolist()
    angles += angles[:1]  # Close the radar polygon

    palette = [
        "#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd",
        "#8c564b", "#e377c2", "#17becf",
    ]

    base_names, sing_names, duo_names, trio_names, quad_names = _categorize_radar_tiers(list(data.keys()))

    # Fallback to single radar plot if fewer than 5 configs or tiers cannot be formed
    if len(data) < 5 or (not sing_names and not duo_names and not trio_names and not quad_names):
        fig, ax = plt.subplots(figsize=(6.2, 5.8), subplot_kw=dict(polar=True), dpi=300)

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

        title_suffix = f" for {dataset_name}" if dataset_name else ""
        plt.title(f"RAGAS Four Core Quality Dimensions{title_suffix}", fontweight="bold", y=1.08)
        plt.legend(loc="upper right", bbox_to_anchor=(1.3, 1.1), fontsize=8)

        plt.tight_layout()
        plt.savefig(out_file, bbox_inches="tight", dpi=300)
        plt.close(fig)
        return out_file

    # 2x2 (4-Panel) Grid Radar Chart for publication readability
    fig, axes = plt.subplots(2, 2, figsize=(11.0, 10.5), subplot_kw=dict(polar=True), dpi=300)
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

        # 1. Plot Baseline in distinct dashed style
        if base_values is not None:
            ax.plot(
                angles,
                base_values,
                color="#2c3e50",
                linestyle="--",
                linewidth=2.0,
                label="$C_0$ (Baseline)",
                zorder=4,
            )
            ax.fill(angles, base_values, color="#2c3e50", alpha=0.08)

        # 2. Plot configurations in this tier
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
            clean_lbl = name.replace("run_", "").replace("_evaluated", "").replace("_", " ")
            ax.plot(angles, values, color=color, linewidth=1.8, label=clean_lbl, zorder=5)
            ax.fill(angles, values, color=color, alpha=0.12)

        # Polar axis styling
        ax.set_theta_offset(np.pi / 2)
        ax.set_theta_direction(-1)
        ax.set_xticks(angles[:-1])
        ax.set_xticklabels(categories, fontsize=9.0, fontweight="bold")
        ax.set_ylim(0.0, 1.0)
        ax.set_yticks([0.2, 0.4, 0.6, 0.8, 1.0])
        ax.set_yticklabels(["0.2", "0.4", "0.6", "0.8", "1.0"], fontsize=7.0, color="gray")
        ax.set_title(panel_title, fontweight="bold", pad=22, fontsize=10.5)

        ax.legend(
            loc="upper center",
            bbox_to_anchor=(0.5, -0.14),
            ncol=min(3, len(tier_configs) + 1),
            fontsize=8.0,
            framealpha=0.9,
        )

    title_suffix = f" for {dataset_name}" if dataset_name else ""
    fig.suptitle(
        f"Multi-Dimensional Quality Trade-offs across Architectural Tiers (RAGAS){title_suffix}",
        fontsize=12.5,
        fontweight="bold",
        y=0.995,
    )
    plt.tight_layout()
    plt.subplots_adjust(hspace=0.38, wspace=0.25)
    plt.savefig(out_file, bbox_inches="tight", dpi=300)
    plt.close(fig)
    return out_file


def plot_quality_lollipop(
    data: Mapping[str, Any],
    out_file: Path,
    dataset_name: str = "",
) -> Path:
    """4-panel horizontal lollipop plot — the primary quality figure standard.

    Replaced the radar chart as the default quality export from October 2026.
    Each panel shows one RAGAS metric across all 16 configurations, ordered
    bottom-to-top by tier (C0 baseline → C15 full-stack).

    Features
    --------
    - **Tier pastel bands**: Each of the 5 architectural tiers (k=0..4) is
      highlighted with a distinct pastel horizontal band and labelled on the
      y-axis (C0 … C15).
    - **Reinforced grid**: Stronger dashed vertical grid lines (alpha=0.65).
    - **Dynamic broken x-axis**: When metric values cluster above 0.40 and
      span ≤ 0.45, the x-axis is split into a short [0.0–0.06] anchor segment
      and a zoomed [zoom_min, zoom_max] segment, joined by '//' break marks.
      Otherwise the full [0, 1] scale is used.
    - **Per-panel tick & spine styling**: Only left spine visible; horizontal
      lollipop stems with circular markers.

    Parameters
    ----------
    data:
        ``{run_name: [record, ...]}`` mapping as returned by the CLI loader.
    out_file:
        Destination path for the PDF/PNG output.
    dataset_name:
        Human-readable dataset label used in the figure title.

    Returns
    -------
    Path
        The ``out_file`` path after writing.
    """
    _set_publication_style()

    # ── Metrics ────────────────────────────────────────────────────────────────
    METRICS = [
        ("context_precision_mean",  "Context Precision"),
        ("context_recall_mean",     "Context Recall"),
        ("faithfulness_mean",       "Faithfulness"),
        ("answer_relevancy_mean",   "Answer Relevancy"),
    ]

    # ── Build sorted config list ───────────────────────────────────────────────
    summarised: dict[str, dict[str, float]] = {}
    for run_name, records in data.items():
        if not records:
            continue
        s = summarize_run_records(records)
        if s:
            summarised[run_name] = s

    if not summarised:
        fig, ax = plt.subplots(figsize=(8, 4))
        ax.text(0.5, 0.5, "No data available", ha="center", va="center", transform=ax.transAxes)
        plt.savefig(out_file, bbox_inches="tight", dpi=300)
        plt.close(fig)
        return out_file

    def _sort_key(name: str) -> int:
        m = re.search(r"c_?(\d+)", name.lower())
        if m:
            return int(m.group(1))
        if "base" in name.lower():
            return 0
        return 999

    sorted_runs = sorted(summarised.keys(), key=_sort_key, reverse=True)
    n = len(sorted_runs)
    labels = [_get_friendly_name_pr(r) for r in sorted_runs]
    tiers = [_get_tier_pr(r) for r in sorted_runs]
    y_pos = np.arange(n)
    colors = [_TIER_COLORS[t] for t in tiers]

    # ── Figure layout (2x2 grid with continuous [0.0, 1.0] scale) ──────────────
    # Top row: Retrieval metrics (Context Precision, Context Recall)
    # Bottom row: Synthesis metrics (Faithfulness, Answer Relevancy)
    fig_w = 8.8
    fig_h = 10.2

    fig = plt.figure(figsize=(fig_w, fig_h), dpi=300)
    title_suffix = f" — {dataset_name}" if dataset_name else ""
    fig.suptitle(
        f"RAGAS Quality Metrics by Configuration{title_suffix}",
        fontsize=12.5, fontweight="bold", y=0.985,
    )

    outer_gs = gridspec.GridSpec(
        2, 2, figure=fig,
        hspace=0.28, wspace=0.38,
        left=0.14, right=0.96, top=0.94, bottom=0.06,
    )

    panel_prefixes = ["(a)", "(b)", "(c)", "(d)"]

    for idx, (mkey, mtitle) in enumerate(METRICS):
        row = idx // 2
        col = idx % 2
        cell = outer_gs[row, col]
        ax = fig.add_subplot(cell)

        # Soft tier background bands
        _draw_tier_bands_pr(ax, y_pos, tiers, n)

        vals = [summarised[r].get(mkey, float("nan")) for r in sorted_runs]

        for i, (v, c) in enumerate(zip(vals, colors)):
            if math.isnan(v):
                continue
            ax.hlines(y_pos[i], 0.0, v, colors=c, linewidth=1.5, alpha=0.85, zorder=3)
            ax.scatter(v, y_pos[i], color=c, s=45, edgecolors="k", linewidth=0.6, zorder=4)
            ax.annotate(
                f"{v:.3f}",
                (v, y_pos[i]),
                textcoords="offset points",
                xytext=(8, 0),
                va="center",
                ha="left",
                fontsize=7.2,
                fontweight="bold",
                color=c,
            )

        ax.set_title(f"{panel_prefixes[idx]} {mtitle}", fontweight="bold", fontsize=10.5, pad=8, loc="left")
        ax.set_xlim(-0.02, 1.14)
        ax.set_xticks([0.0, 0.2, 0.4, 0.6, 0.8, 1.0])
        ax.set_xticklabels(["0.0", "0.2", "0.4", "0.6", "0.8", "1.0"], fontsize=8.0)
        ax.xaxis.set_tick_params(labelsize=8.0)
        ax.set_yticks(y_pos)
        ax.set_yticklabels(labels, fontsize=8.2, fontweight="medium")
        ax.set_ylim(-0.7, n - 0.3)
        ax.spines["top"].set_visible(False)
        ax.spines["right"].set_visible(False)
        ax.grid(True, axis="x", linestyle="--", linewidth=0.8, color="#94a3b8", alpha=0.65, zorder=1)

    # ── Tier legend below the figure ──────────────────────────────────────────
    tier_labels = {
        0: "Baseline ($C_0$)",
        1: "Single-opt ($C_1$–$C_4$)",
        2: "Dual-opt ($C_5$–$C_{10}$)",
        3: "Triple-opt ($C_{11}$–$C_{14}$)",
        4: "Full-Stack ($C_{15}$)",
    }
    handles = [
        Patch(facecolor=_TIER_COLORS[t], edgecolor="k", label=tier_labels[t])
        for t in sorted(tier_labels)
    ]
    fig.legend(
        handles=handles,
        loc="lower center",
        bbox_to_anchor=(0.5, 0.01),
        ncol=5,
        fontsize=9.0,
        framealpha=0.9,
        edgecolor="#cbd5e1",
    )

    plt.savefig(out_file, bbox_inches="tight", dpi=300)
    plt.close(fig)
    return out_file


def plot_quality_delta_matrix(
    data: Mapping[str, Any],
    out_file: Path,
    dataset_name: str = "",
) -> Path:
    """Executive Performance Delta Heatmap comparing all treatments (C1..C15) to C0 Baseline.

    Displays relative percentage improvements/regressions across all 4 RAGAS metrics,
    Composite Quality (Q), median latency (P50), and operational cost per 1k queries.
    """
    _set_publication_style()
    out_file = Path(out_file)
    out_file.parent.mkdir(parents=True, exist_ok=True)

    summaries: dict[str, dict[str, float]] = {}
    for run_name, records in data.items():
        if not records:
            continue
        s = summarize_run_records(records)
        if s:
            summaries[run_name] = s

    if not summaries:
        fig, ax = plt.subplots(figsize=(8, 4))
        ax.text(0.5, 0.5, "No data available", ha="center", va="center", transform=ax.transAxes)
        plt.savefig(out_file, bbox_inches="tight", dpi=300)
        plt.close(fig)
        return out_file

    configs = list(summaries.keys())
    base_name = next((c for c in configs if _get_config_key(c) == "C0"), None)
    if not base_name or base_name not in summaries:
        base_name = configs[0]
    sb = summaries[base_name]

    treatment_configs = [c for c in configs if _get_config_key(c) != "C0"]
    def _c_sort(c: str) -> int:
        m = re.search(r"c_?(\d+)", c.lower())
        return int(m.group(1)) if m else 999
    treatment_configs.sort(key=_c_sort)

    if not treatment_configs:
        treatment_configs = configs

    metrics = [
        ("Context Precision", "context_precision_mean", True),
        ("Context Recall", "context_recall_mean", True),
        ("Faithfulness", "faithfulness_mean", True),
        ("Answer Relevancy", "answer_relevancy_mean", True),
        ("Composite Quality $Q$", "composite_q_mean", True),
        ("Latency $P_{50}$", "latency_p50", False),
        ("Cost / 1k", "cost_mean", False),
    ]

    delta_matrix = np.zeros((len(treatment_configs), len(metrics)))
    raw_matrix = np.zeros((len(treatment_configs), len(metrics)))

    for r_idx, c_name in enumerate(treatment_configs):
        s = summaries[c_name]
        for c_idx, (m_label, m_key, higher_is_better) in enumerate(metrics):
            val = s.get(m_key, 0.0)
            base_v = sb.get(m_key, 1.0)
            raw_matrix[r_idx, c_idx] = val

            if base_v > 0:
                pct = ((val - base_v) / base_v) * 100.0
            else:
                pct = 0.0

            oriented_delta = pct if higher_is_better else -pct
            delta_matrix[r_idx, c_idx] = oriented_delta

    fig, ax = plt.subplots(figsize=(10.5, 8.0), dpi=300)
    cmap = plt.cm.RdYlGn
    vlim = max(30.0, float(np.percentile(np.abs(delta_matrix), 90)))
    im = ax.imshow(delta_matrix, cmap=cmap, vmin=-vlim, vmax=vlim, aspect="auto")

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

            text_color = "white" if abs(d) > (vlim * 0.60) else "black"
            ax.text(
                c, r,
                f"{raw_str}\n({sign}{actual_pct:.1f}%)",
                ha="center", va="center",
                fontsize=7.5,
                color=text_color,
                fontweight="bold" if abs(d) > 20 else "normal",
            )

    y_labels = [_get_friendly_name_pr(c) for c in treatment_configs]
    ax.set_yticks(np.arange(len(treatment_configs)))
    ax.set_yticklabels(y_labels, fontsize=8.5)

    x_labels = [m[0] for m in metrics]
    ax.set_xticks(np.arange(len(metrics)))
    ax.set_xticklabels(x_labels, fontsize=9.0, fontweight="bold", rotation=20, ha="right")

    cbar = fig.colorbar(im, ax=ax, fraction=0.03, pad=0.04)
    cbar.set_label("Relative Performance vs. Baseline ($C_0$)\n[Green = Improvement, Red = Regression]", fontsize=8.5)
    cbar.ax.tick_params(labelsize=8.0)

    title_suffix = f" for {dataset_name}" if dataset_name else ""
    ax.set_title(
        f"Executive Performance Delta vs. Baseline ($C_0$){title_suffix}",
        fontsize=11.5,
        fontweight="bold",
        pad=15,
    )
    plt.tight_layout()
    plt.savefig(out_file, bbox_inches="tight", dpi=300)
    plt.close(fig)
    return out_file


def _compute_pareto_frontier_3d(
    points: list[tuple[float, float, float, str]],
) -> list[int]:
    """Identifies indices of non-dominated Pareto frontier points in 3D (QPS, QPD, Q).

    All three objectives are to be MAXIMIZED:
    - QPS: Throughput Efficiency (higher is better)
    - QPD: Cost Efficiency (higher is better)
    - Q: Composite Generative Quality (higher is better)

    Point A is dominated by Point B iff:
        B.x >= A.x and B.y >= A.y and B.z >= A.z
        and (B.x > A.x or B.y > A.y or B.z > A.z)

    Returns:
        List of indices corresponding to non-dominated points in `points`.
    """
    n = len(points)
    if n == 0:
        return []

    pareto_indices: list[int] = []
    for i in range(n):
        xi, yi, zi, _ = points[i]
        dominated = False
        for j in range(n):
            if i == j:
                continue
            xj, yj, zj, _ = points[j]
            if (xj >= xi and yj >= yi and zj >= zi) and (
                xj > xi or yj > yi or zj > zi
            ):
                dominated = True
                break
        if not dominated:
            pareto_indices.append(i)

    return pareto_indices


def plot_pareto_frontier_3d(
    data: dict[str, Sequence[Mapping[str, Any]]],
    output_path: Path | str = "thesis/figures/pareto_frontier_3d.pdf",
    subtitle: str | None = None,
    dataset_name: Optional[str] = None,
) -> Path:
    """Generates empirical 3D Pareto frontier surface figure across QPS, QPD, and Composite Q."""
    _set_publication_style()
    out_file = Path(output_path)
    out_file.parent.mkdir(parents=True, exist_ok=True)

    points: list[tuple[float, float, float, str]] = []

    for name, records in data.items():
        s = summarize_run_records(records)
        if not s:
            continue
        q = float(s.get("composite_q_mean", 0.0))
        qps = float(s.get("qps", 0.0))
        qpd = float(s.get("qpd", 0.0))

        # Avoid inf or negative
        qps = max(0.0, qps) if math.isfinite(qps) else 0.0
        qpd = max(0.0, qpd) if math.isfinite(qpd) else 0.0

        clean_name = name.replace("run_", "").replace("_evaluated", "")
        points.append((qps, qpd, q, clean_name))

    if not points:
        fig = plt.figure(figsize=(8.8, 7.2), dpi=300)
        fig.savefig(out_file, bbox_inches="tight", dpi=300)
        plt.close(fig)
        return out_file

    pareto_idx_set = set(_compute_pareto_frontier_3d(points))

    xs = np.array([p[0] for p in points])
    ys = np.array([p[1] for p in points])
    zs = np.array([p[2] for p in points])

    z_min_floor = max(0.0, float(np.min(zs)) * 0.85) if len(zs) > 0 else 0.0
    z_max_ceil = min(1.0, max(0.1, float(np.max(zs)) * 1.12)) if len(zs) > 0 else 1.0
    if z_max_ceil <= z_min_floor:
        z_max_ceil = z_min_floor + 0.1

    fig = plt.figure(figsize=(8.8, 7.2), dpi=300)
    ax = fig.add_subplot(111, projection="3d")
    ax.view_init(elev=20, azim=-65)

    dom_x, dom_y, dom_z, dom_labels = [], [], [], []
    par_x, par_y, par_z, par_labels = [], [], [], []

    for idx, (x, y, z, lbl) in enumerate(points):
        if idx in pareto_idx_set:
            par_x.append(x)
            par_y.append(y)
            par_z.append(z)
            par_labels.append(lbl)
        else:
            dom_x.append(x)
            dom_y.append(y)
            dom_z.append(z)
            dom_labels.append(lbl)

    # Plot dominated points
    if dom_x:
        ax.scatter(
            dom_x,
            dom_y,
            dom_z,
            c="#7f7f7f",
            s=65,
            alpha=0.65,
            edgecolors="k",
            linewidth=0.5,
            label="Dominated Configurations ($C_i \\notin \\mathcal{P}$)",
            zorder=3,
        )

    # Plot Pareto-optimal points
    par_scatter = None
    if par_x:
        par_scatter = ax.scatter(
            par_x,
            par_y,
            par_z,
            c=par_z,
            cmap="viridis",
            vmin=z_min_floor,
            vmax=z_max_ceil,
            s=95,
            alpha=0.95,
            edgecolors="#d62728",
            linewidth=1.6,
            label="Pareto-Optimal Frontier ($\\mathcal{P}_{3D}$)",
            zorder=5,
        )

    # Vertical drop lines from points to floor
    for idx, (x, y, z, _) in enumerate(points):
        is_par = idx in pareto_idx_set
        line_color = "#d62728" if is_par else "#aaaaaa"
        line_alpha = 0.55 if is_par else 0.30
        line_width = 1.1 if is_par else 0.8
        ax.plot(
            [x, x],
            [y, y],
            [z_min_floor, z],
            linestyle="--",
            color=line_color,
            alpha=line_alpha,
            linewidth=line_width,
            zorder=2,
        )

    # Floor shadow points
    if len(xs) > 0:
        ax.scatter(
            xs,
            ys,
            np.full_like(xs, z_min_floor),
            c="#bbbbbb",
            s=25,
            alpha=0.35,
            zorder=1,
        )

    # Triangulated surface mesh for Pareto points
    if len(par_x) >= 3:
        try:
            ax.plot_trisurf(
                np.array(par_x),
                np.array(par_y),
                np.array(par_z),
                color="#ff7f0e",
                alpha=0.35,
                edgecolor="#d62728",
                linewidth=0.8,
                shade=True,
                zorder=4,
            )
        except Exception:
            # Fallback if points are collinear or 2D Delaunay projection fails
            pass

    # Point annotations
    for idx, (x, y, z, lbl) in enumerate(points):
        display_lbl = lbl.replace("_", " ")
        if "C0" in display_lbl:
            display_lbl = "C0 (Base)"
        elif "C11" in display_lbl:
            display_lbl = "C11 (All)"
        elif "C1" in display_lbl and "M1" in display_lbl:
            display_lbl = "C1 (M1)"
        elif "C2" in display_lbl and "M2" in display_lbl:
            display_lbl = "C2 (M2)"
        elif "C3" in display_lbl and "M3" in display_lbl:
            display_lbl = "C3 (M3)"
        elif "C5" in display_lbl:
            display_lbl = "C5 (M1+M2)"
        elif "C6" in display_lbl:
            display_lbl = "C6 (M1+M3)"
        elif "C8" in display_lbl:
            display_lbl = "C8 (M2+M3)"

        is_par = idx in pareto_idx_set
        ax.text(
            x,
            y,
            z + (0.01 if is_par else 0.005),
            f" {display_lbl}",
            fontsize=8.5 if is_par else 7.5,
            fontweight="bold" if is_par else "normal",
            alpha=0.95 if is_par else 0.75,
            zorder=6,
        )

    ax.set_xlabel("Throughput Efficiency: Quality / Second (QPS)", labelpad=10)
    ax.set_ylabel("Cost Efficiency: Quality / Dollar (QPD)", labelpad=10)
    ax.set_zlabel("Composite Generative Quality $Q(q)$", labelpad=8)
    ax.set_zlim(z_min_floor, z_max_ceil)

    title_text = "Empirical 3D Pareto Frontier ($\\mathcal{P}_{3D}$): Multi-Objective Trade-off Surface"
    if subtitle:
        title_text += f"\n{subtitle}"
    elif dataset_name:
        title_text += f"\nfor {dataset_name}"
    ax.set_title(title_text, fontweight="bold", pad=15)

    if par_scatter is not None:
        cbar = fig.colorbar(par_scatter, ax=ax, pad=0.1, shrink=0.65, aspect=20)
        cbar.set_label("Composite Quality $Q(q) \\in [0, 1]$", fontsize=9)

    ax.legend(loc="upper left", bbox_to_anchor=(0.02, 0.95), framealpha=0.92, fontsize=8.5)

    plt.tight_layout()
    plt.savefig(out_file, bbox_inches="tight", dpi=300)
    plt.close(fig)
    return out_file


def plot_cost_breakdown(
    data: dict[str, Sequence[Mapping[str, Any]]],
    output_path: Path | str = "thesis/figures/cost_breakdown.pdf",
    pricing_tier: str = "gemini-3.8-flash",
    dataset_name: Optional[str] = None,
) -> Path:
    """Generates stacked bar chart isolating all micro-phase operational cost components across configurations.

    Costs are normalized to USD per 1,000 queries ($) following Thesis Eq. 190:
        Cost_1k = 1000 * mean(Cost(q))
    """
    _set_publication_style()
    out_file = Path(output_path)
    out_file.parent.mkdir(parents=True, exist_ok=True)

    tier = get_pricing_tier(pricing_tier)

    configs: list[str] = []
    prompt_means: list[float] = []
    cache_means: list[float] = []
    comp_means: list[float] = []
    rerank_means: list[float] = []
    embed_means: list[float] = []
    route_means: list[float] = []
    other_means: list[float] = []
    totals: list[float] = []

    for name, records in data.items():
        configs.append(name)
        c_prompt_list = []
        c_cache_list = []
        c_comp_list = []
        c_rerank_list = []
        c_embed_list = []
        c_route_list = []
        c_other_list = []
        c_tot_list = []

        for r in records:
            tu = r.get("tokenUsage") if isinstance(r.get("tokenUsage"), dict) else {}
            timings = r.get("timings") if isinstance(r.get("timings"), dict) else {}
            modules = r.get("modulesApplied") or r.get("modulesRequested") or []
            has_m3 = (
                "M3" in modules
                or "PC" in modules
                or r.get("cacheStatus") in ("EXPLICIT_HIT", "EXPLICIT_MISS")
                or "m3" in str(name).lower()
                or (isinstance(r.get("runName"), str) and "m3" in r["runName"].lower())
            )

            # Per-record pricing tier resolution
            model_used = r.get("modelUsed") or r.get("model_used")
            record_tier = get_pricing_tier(model_used) if model_used else tier

            # 1. Prompt tokens (uncached) and Context Cache tokens (read vs write)
            p_tok = float(tu.get("promptTokens") or tu.get("prompt_tokens") or r.get("prompt_tokens") or 0.0)
            comp_tok = float(tu.get("completionTokens") or tu.get("completion_tokens") or r.get("completion_tokens") or 0.0)

            if has_m3:
                cached_read_tok = float(
                    tu.get("cachedTokens")
                    or tu.get("cached_tokens")
                    or r.get("cached_tokens")
                    or r.get("cachedTokens")
                    or 0.0
                )
                cache_write_tok = float(
                    tu.get("cacheWriteTokens")
                    or tu.get("cache_write_tokens")
                    or r.get("cache_write_tokens")
                    or r.get("cacheWriteTokens")
                    or 0.0
                )
                if cached_read_tok == 0.0 and cache_write_tok == 0.0:
                    cache_meta = (r.get("cacheMetadata") or tu.get("cacheMetadata")) if isinstance(r.get("cacheMetadata") or tu.get("cacheMetadata"), dict) else {}
                    fallback_count = float(cache_meta.get("tokenCount") or cache_meta.get("token_count") or 0.0)
                    cache_status = str(r.get("cacheStatus") or r.get("cache_status") or tu.get("cacheStatus") or tu.get("cache_status") or "").upper()
                    if "HIT" in cache_status:
                        cached_read_tok = fallback_count
                    elif "MISS" in cache_status:
                        cache_write_tok = fallback_count
            else:
                # Without M3, pipeline operates without context caching; ignore server internal prefix cache
                cached_read_tok = 0.0
                cache_write_tok = 0.0

            c_prompt = ((p_tok + cache_write_tok) * record_tier.prompt_price_per_million) / 1_000_000.0
            c_cache = (cached_read_tok * record_tier.cached_price_per_million) / 1_000_000.0
            c_comp = (comp_tok * record_tier.comp_price_per_million) / 1_000_000.0

            # 2. Semantic Re-ranking (if explicit rerank candidate count or price is recorded)
            rerank_docs = float(
                r.get("rerankDocs")
                or r.get("rerank_docs")
                or tu.get("rerankDocs")
                or tu.get("rerank_docs")
                or 0.0
            )
            if rerank_docs == 0.0 and (
                "M2" in modules
                or "SR" in modules
                or "m2" in str(name).lower()
                or (isinstance(r.get("runName"), str) and "m2" in r["runName"].lower())
            ):
                rerank_docs = 50.0  # Candidate pool size per Thesis Table 4.1

            c_rerank = rerank_docs * record_tier.rerank_price_per_doc

            # 3. Query Vectorization Embedding (if explicit embed tokens recorded)
            embed_tok = float(
                r.get("embedTokens")
                or r.get("embed_tokens")
                or tu.get("embedTokens")
                or tu.get("embed_tokens")
                or 0.0
            )
            if embed_tok == 0.0:
                q_text = str(r.get("question") or r.get("query") or "")
                embed_tok = float(max(1, len(q_text.split()))) if q_text else 20.0

            c_embed = (embed_tok * record_tier.embed_price_per_million) / 1_000_000.0

            # 4. Dynamic Routing Classifier (if explicit routing tokens recorded)
            route_tok = float(
                r.get("routingTokens")
                or r.get("route_tokens")
                or r.get("routing_tokens")
                or tu.get("routingTokens")
                or tu.get("route_tokens")
                or tu.get("routing_tokens")
                or 0.0
            )
            route_prompt_tok = float(
                tu.get("routingPromptTokens")
                or tu.get("routing_prompt_tokens")
                or r.get("routingPromptTokens")
                or r.get("routing_prompt_tokens")
                or 0.0
            )
            route_comp_tok = float(
                tu.get("routingCompletionTokens")
                or tu.get("routing_completion_tokens")
                or r.get("routingCompletionTokens")
                or r.get("routing_completion_tokens")
                or 0.0
            )
            if route_prompt_tok == 0.0 and route_comp_tok == 0.0 and route_tok > 0.0:
                route_prompt_tok = route_tok

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
        {"key": "prompt", "label": "Prompt ($C_{\\text{prompt}}$)", "color": "#2b5c8f", "values": prompt_means},
        {"key": "cache", "label": "Context Cache ($C_{\\text{cache}}$)", "color": "#27ae60", "values": cache_means},
        {"key": "comp", "label": "Generation ($C_{\\text{comp}}$)", "color": "#e67e22", "values": comp_means},
        {"key": "rerank", "label": "Re-ranking ($C_{\\text{rerank}}$)", "color": "#9b59b6", "values": rerank_means},
        {"key": "embed", "label": "Embedding ($C_{\\text{embed}}$)", "color": "#5dade2", "values": embed_means},
        {"key": "route", "label": "Routing ($C_{\\text{route}}$)", "color": "#8c564b", "values": route_means},
        {"key": "other", "label": "Overhead ($C_{\\text{other}}$)", "color": "#95a5a6", "values": other_means},
    ]

    active_components = [
        c for c in component_defs if any(val > 0.0001 for val in c["values"])
    ]
    if not active_components:
        active_components = [
            {"key": "prompt", "label": "Prompt ($C_{\\text{prompt}}$)", "color": "#2b5c8f", "values": prompt_means},
            {"key": "comp", "label": "Generation ($C_{\\text{comp}}$)", "color": "#e67e22", "values": comp_means},
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

    actual_heights = [float(bottom[i]) for i in range(len(configs))]
    max_tot = max(actual_heights) if actual_heights else 1.0
    for i, tot in enumerate(actual_heights):
        ax.annotate(
            f"${tot:.3f}",
            xy=(x[i], tot),
            xytext=(4, 4),
            textcoords="offset points",
            ha="left",
            va="bottom",
            fontsize=7.5,
            fontweight="bold",
            rotation=45,
        )

    ax.set_ylabel("Operational Cost per 1,000 Queries (USD)")
    title_suffix = f" for {dataset_name}" if dataset_name else ""
    ax.set_title(f"Operational Cost Micro-Phase Breakdown (per 1,000 Queries){title_suffix}", fontweight="bold")
    ax.set_xticks(x)

    display_labels = [c.replace("run_", "").replace("_", " ") for c in configs]
    ax.set_xticklabels(display_labels, rotation=45, ha="right", fontsize=9)

    ax.set_ylim(0, max(max_tot * 1.35, 0.01))
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

