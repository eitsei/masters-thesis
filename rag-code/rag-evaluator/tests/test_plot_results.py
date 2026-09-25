"""Unit tests for figure plotting functions in src/plot_results.py.

Tests:
- plot_latency_breakdown: verifying stream_ms derivation (generationMs - prefillMs) and other=0
- plot_cost_breakdown: verifying per-record modelUsed pricing tier resolution (Pro vs Flash)
"""

from pathlib import Path
from typing import Any
from unittest.mock import patch
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pytest

from src.plot_results import plot_cost_breakdown, plot_latency_breakdown


def test_plot_latency_breakdown(tmp_path: Path):
    out_pdf = tmp_path / "latency_breakdown.pdf"

    # Mock records with generationMs=1000, prefillMs=200
    # Expected: stream is derived as 800 (1000.0 - 200.0), and other is 0
    data = {
        "C0: Baseline": [
            {
                "timings": {
                    "generationMs": 1000.0,
                    "prefillMs": 200.0,
                },
                "totalLatencyMs": 1000.0,
            }
        ]
    }

    plotted_bars: dict[str, Any] = {}
    orig_subplots = plt.subplots

    def spied_subplots(*args, **kwargs):
        fig, ax = orig_subplots(*args, **kwargs)
        orig_bar = ax.bar

        def capture_bar(*b_args, **b_kwargs):
            label = b_kwargs.get("label", "")
            vals = b_args[1] if len(b_args) > 1 else b_kwargs.get("height")
            plotted_bars[label] = vals
            return orig_bar(*b_args, **b_kwargs)

        ax.bar = capture_bar
        return fig, ax

    with patch("matplotlib.pyplot.subplots", side_effect=spied_subplots):
        result_path = plot_latency_breakdown(data, output_path=out_pdf)

    assert result_path.exists()
    assert result_path.stat().st_size > 500

    # Verify stream is derived as 800
    stream_keys = [k for k in plotted_bars if "stream" in k.lower()]
    assert len(stream_keys) == 1, f"Expected 1 streaming component, got {list(plotted_bars.keys())}"
    assert plotted_bars[stream_keys[0]][0] == pytest.approx(800.0)

    # Verify prefill is 200
    prefill_keys = [k for k in plotted_bars if "prefill" in k.lower()]
    assert len(prefill_keys) == 1
    assert plotted_bars[prefill_keys[0]][0] == pytest.approx(200.0)

    # Verify other is 0 (either omitted from active components because < 0.1 ms or value is 0.0)
    other_keys = [k for k in plotted_bars if "other" in k.lower() or "overhead" in k.lower()]
    assert len(other_keys) == 0 or plotted_bars[other_keys[0]][0] == pytest.approx(0.0)


def test_plot_cost_breakdown(tmp_path: Path):
    out_pdf = tmp_path / "cost_breakdown.pdf"

    # Mock records with modelUsed="gemini-3.1-pro-preview"
    # Pro pricing tier: prompt $1.25/1M, comp $5.00/1M
    # Default Flash tier: prompt $0.75/1M, comp $3.75/1M
    # With 1,000 prompt tokens and 100 completion tokens:
    # Pro prompt cost per 1k queries: (1,000 * 1.25 / 1e6) * 1000 = $1.25
    # Pro comp cost per 1k queries: (100 * 5.00 / 1e6) * 1000 = $0.50
    # Flash would have been: prompt $0.75, comp $0.375
    data = {
        "C4: Routed": [
            {
                "modelUsed": "gemini-3.1-pro-preview",
                "tokenUsage": {
                    "promptTokens": 1000,
                    "completionTokens": 100,
                    "cachedTokens": 0,
                },
                "question": "What is multi-hop reasoning?",
            }
        ]
    }

    plotted_bars: dict[str, Any] = {}
    orig_subplots = plt.subplots

    def spied_subplots(*args, **kwargs):
        fig, ax = orig_subplots(*args, **kwargs)
        orig_bar = ax.bar

        def capture_bar(*b_args, **b_kwargs):
            label = b_kwargs.get("label", "")
            vals = b_args[1] if len(b_args) > 1 else b_kwargs.get("height")
            plotted_bars[label] = vals
            return orig_bar(*b_args, **b_kwargs)

        ax.bar = capture_bar
        return fig, ax

    with patch("matplotlib.pyplot.subplots", side_effect=spied_subplots):
        result_path = plot_cost_breakdown(data, output_path=out_pdf, pricing_tier="gemini-3.8-flash")

    assert result_path.exists()
    assert result_path.stat().st_size > 500

    # Verify that Pro pricing tier ($1.25/$5.00) is applied rather than default Flash ($0.75/$3.75)
    prompt_keys = [k for k in plotted_bars if "prompt" in k.lower()]
    assert len(prompt_keys) == 1
    assert plotted_bars[prompt_keys[0]][0] == pytest.approx(1.25, rel=1e-5)

    comp_keys = [k for k in plotted_bars if "generation" in k.lower() or "comp" in k.lower()]
    assert len(comp_keys) == 1
    assert plotted_bars[comp_keys[0]][0] == pytest.approx(0.50, rel=1e-5)


def test_categorize_radar_tiers():
    from src.plot_results import _categorize_radar_tiers

    configs = [
        "run_C0_baseline",
        "run_C1_M1", "run_C2_M2", "run_C3_M3", "run_C4_M4",
        "run_C5_M1_M2", "run_C6_M1_M3", "run_C7_M1_M4", "run_C8_M2_M3", "run_C9_M2_M4", "run_C10_M3_M4",
        "run_C11_M1_M2_M3", "run_C12_M1_M2_M4", "run_C13_M1_M3_M4", "run_C14_M2_M3_M4", "run_C15_M1_M2_M3_M4",
    ]
    base, sing, duo, trio, quad = _categorize_radar_tiers(configs)

    assert len(base) == 1 and base[0] == "run_C0_baseline"
    assert len(sing) == 4
    assert len(duo) == 6
    assert len(trio) == 4
    assert len(quad) == 1 and quad[0] == "run_C15_M1_M2_M3_M4"


def test_plot_ragas_metrics_radar_four_panel(tmp_path: Path):
    from src.plot_results import plot_ragas_metrics_radar

    out_pdf = tmp_path / "quality_radar_enterprise.pdf"
    configs = [
        "run_C0_baseline",
        "run_C1_M1", "run_C2_M2", "run_C3_M3", "run_C4_M4",
        "run_C5_M1_M2", "run_C6_M1_M3", "run_C7_M1_M4", "run_C8_M2_M3", "run_C9_M2_M4", "run_C10_M3_M4",
        "run_C11_M1_M2_M3", "run_C12_M1_M2_M4", "run_C13_M1_M3_M4", "run_C14_M2_M3_M4", "run_C15_M1_M2_M3_M4",
    ]
    data = {
        cfg: [
            {
                "context_precision": 0.85,
                "context_recall": 0.80,
                "faithfulness": 0.90,
                "answer_relevancy": 0.88,
            }
        ]
        for cfg in configs
    }

    result = plot_ragas_metrics_radar(data, output_path=out_pdf, dataset_name="Enterprise")
    assert result.exists()
    assert result.stat().st_size > 1000
