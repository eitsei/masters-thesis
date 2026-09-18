"""Unit tests for statistical significance testing and Holm-Bonferroni correction (Thesis §4.1).

Tests:
- Paired two-tailed Wilcoxon signed-rank test (median diff, W-stat, p-value)
- Edge case handling (identical distributions, zero differences)
- Holm-Bonferroni step-down correction for multiple comparisons
- Monotonicity preservation and [0.0, 1.0] bounds
- Significance stars (*, **, ***, ns)
- Table 4.2 data format structure
"""

import math
import pytest
from src.stats import (
    WilcoxonComparison,
    adjust_p_values_holm_bonferroni,
    batch_compare_configurations,
    calculate_significance_flag,
    compare_paired_runs,
    wilcoxon_paired_test,
)


def test_wilcoxon_paired_test_normal_shift():
    baseline = [100.0, 110.0, 120.0, 130.0, 140.0, 150.0, 160.0, 170.0, 180.0, 190.0]
    # Shift downward by 15.0 ms
    treatment = [85.0, 95.0, 105.0, 115.0, 125.0, 135.0, 145.0, 155.0, 165.0, 175.0]

    med_diff, w_stat, p_val = wilcoxon_paired_test(baseline, treatment)
    assert med_diff == -15.0
    assert w_stat == 0.0  # Perfect rank shift
    assert p_val < 0.01


def test_wilcoxon_paired_test_identical():
    # Identical arrays should yield 0 diff, 0 W, and p=1.0 without throwing errors
    baseline = [50.0, 50.0, 50.0, 50.0, 50.0]
    treatment = [50.0, 50.0, 50.0, 50.0, 50.0]

    med_diff, w_stat, p_val = wilcoxon_paired_test(baseline, treatment)
    assert med_diff == 0.0
    assert w_stat == 0.0
    assert p_val == 1.0


def test_wilcoxon_paired_test_mismatched_lengths():
    with pytest.raises(ValueError, match="Mismatched sample lengths"):
        wilcoxon_paired_test([1.0, 2.0], [1.0])


def test_significance_flags():
    assert calculate_significance_flag(0.0001) == "***"
    assert calculate_significance_flag(0.005) == "**"
    assert calculate_significance_flag(0.03) == "*"
    assert calculate_significance_flag(0.05) == "ns"
    assert calculate_significance_flag(0.50) == "ns"
    assert calculate_significance_flag(float("nan")) == "ns"


def test_holm_bonferroni_correction_step_down():
    # 4 hypotheses with raw p-values: [0.001, 0.02, 0.04, 0.06]
    # m = 4
    # rank 0: 0.001 * 4 = 0.004
    # rank 1: 0.02 * 3 = 0.06
    # rank 2: 0.04 * 2 = 0.08
    # rank 3: 0.06 * 1 = 0.06 -> adjusted max = 0.08
    comparisons = [
        WilcoxonComparison("RQ1", "Metric A", "C1", 10.0, 8.0, -2.0, 0.0, 0.001),
        WilcoxonComparison("RQ1", "Metric B", "C1", 10.0, 8.0, -2.0, 0.0, 0.02),
        WilcoxonComparison("RQ2", "Metric C", "C1", 10.0, 8.0, -2.0, 0.0, 0.04),
        WilcoxonComparison("RQ3", "Metric D", "C1", 10.0, 8.0, -2.0, 0.0, 0.06),
    ]

    adjusted = adjust_p_values_holm_bonferroni(comparisons)
    assert len(adjusted) == 4
    assert math.isclose(adjusted[0].p_adjusted, 0.004, rel_tol=1e-5)
    assert math.isclose(adjusted[1].p_adjusted, 0.06, rel_tol=1e-5)
    assert math.isclose(adjusted[2].p_adjusted, 0.08, rel_tol=1e-5)
    assert math.isclose(adjusted[3].p_adjusted, 0.08, rel_tol=1e-5)

    # Check flags
    assert adjusted[0].significance == "**"
    assert adjusted[1].significance == "ns"
    assert adjusted[2].significance == "ns"
    assert adjusted[3].significance == "ns"


def test_holm_bonferroni_capped_at_one():
    comparisons = [
        WilcoxonComparison("RQ1", "Metric A", "C1", 10.0, 8.0, -2.0, 0.0, 0.8),
        WilcoxonComparison("RQ1", "Metric B", "C1", 10.0, 8.0, -2.0, 0.0, 0.9),
    ]
    adjusted = adjust_p_values_holm_bonferroni(comparisons)
    assert adjusted[0].p_adjusted <= 1.0
    assert adjusted[1].p_adjusted <= 1.0


def test_compare_paired_runs_structure():
    # 10 synthetic queries
    baseline_records = [
        {
            "questionId": f"q_{i}",
            "total_latency_ms": 200.0 + i * 5,
            "cost_usd": 0.0005,
            "composite_quality": 0.75,
            "exact_match": 1.0 if i % 2 == 0 else 0.0,
        }
        for i in range(10)
    ]
    treatment_records = [
        {
            "questionId": f"q_{i}",
            "total_latency_ms": 150.0 + i * 5,
            "cost_usd": 0.0003,
            "composite_quality": 0.85,
            "exact_match": 1.0,
        }
        for i in range(10)
    ]

    comps = compare_paired_runs(
        baseline_records,
        treatment_records,
        config_name="C1: S2B",
        metrics_to_test=["total_latency_ms", "cost_usd", "composite_quality"],
    )

    assert len(comps) == 3
    for c in comps:
        assert isinstance(c, WilcoxonComparison)
        assert c.config_name == "C1: S2B"
        assert c.target_rq in ("RQ1", "RQ2", "RQ3")
        assert c.p_adjusted >= c.p_raw
        assert c.p_adjusted <= 1.0


def test_batch_compare_configurations():
    baseline_records = [
        {"questionId": f"q_{i}", "total_latency_ms": 200.0 + i} for i in range(10)
    ]
    c1_records = [
        {"questionId": f"q_{i}", "total_latency_ms": 180.0 + i} for i in range(10)
    ]
    c2_records = [
        {"questionId": f"q_{i}", "total_latency_ms": 150.0 + i} for i in range(10)
    ]

    treatments = {"C1: S2B": c1_records, "C2: SR": c2_records}
    batch_comps = batch_compare_configurations(
        baseline_records, treatments, metrics_to_test=["total_latency_ms"]
    )

    assert len(batch_comps) == 2
    c_names = [c.config_name for c in batch_comps]
    assert "C1: S2B" in c_names
    assert "C2: SR" in c_names


def test_extract_metric_series_ttft_and_tokens():
    from src.stats import _extract_metric_series

    records = [
        {
            "timings": {"ttftMs": 450.0, "generationMs": 800.0},
            "tokenUsage": {"totalTokens": 1250, "promptTokens": 1000},
            "metrics": {"faithfulness": 0.95},
            "cost_usd": 0.0004,
        },
        {
            "ttft_ms": 320.0,
            "total_tokens": 800,
            "faithfulness": 0.80,
            "cost": 0.0002,
        },
    ]

    ttft = _extract_metric_series(records, "ttft_ms")
    assert ttft == [450.0, 320.0]

    tokens = _extract_metric_series(records, "total_tokens")
    assert tokens == [1250.0, 800.0]

    costs = _extract_metric_series(records, "cost_usd")
    assert costs == [0.0004, 0.0002]

    faith = _extract_metric_series(records, "faithfulness")
    assert faith == [0.95, 0.80]


def test_wilcoxon_paired_test_nan_handling():
    # 10 pairs with some NaN values
    baseline = [100.0, 110.0, float("nan"), 130.0, 140.0, 150.0, 160.0, 170.0, 180.0, 190.0]
    treatment = [85.0, float("nan"), 105.0, 115.0, 125.0, 135.0, 145.0, 155.0, 165.0, 175.0]

    med_diff, w_stat, p_val = wilcoxon_paired_test(baseline, treatment)
    assert not math.isnan(med_diff)
    assert not math.isnan(w_stat)
    assert not math.isnan(p_val)
    assert med_diff == -15.0


def test_compare_paired_runs_default_metrics_and_n_pairs():
    baseline_records = [
        {
            "questionId": f"q_{i}",
            "total_latency_ms": 200.0 + i * 5,
            "timings": {"ttftMs": 50.0 + i},
            "cost_usd": 0.0005,
            "tokenUsage": {"totalTokens": 1000 + i * 10},
            "composite_quality": 0.75,
            "exact_match": 1.0 if i % 2 == 0 else 0.0,
            "token_f1": 0.70,
            "faithfulness": 0.80,
            "answer_relevancy": 0.85,
            "context_precision": 0.90,
            "context_recall": 0.88,
        }
        for i in range(10)
    ]
    treatment_records = [
        {
            "questionId": f"q_{i}",
            "total_latency_ms": 150.0 + i * 5,
            "timings": {"ttftMs": 40.0 + i},
            "cost_usd": 0.0003,
            "tokenUsage": {"totalTokens": 800 + i * 10},
            "composite_quality": 0.85,
            "exact_match": 1.0,
            "token_f1": 0.85,
            "faithfulness": 0.90,
            "answer_relevancy": 0.92,
            "context_precision": 0.95,
            "context_recall": 0.94,
        }
        for i in range(10)
    ]

    comps = compare_paired_runs(baseline_records, treatment_records, config_name="C1: S2B")
    # All 11 metrics tested by default
    assert len(comps) == 11
    metric_names = [c.metric_name for c in comps]
    assert "Time to First Token (ms)" in metric_names
    assert "Total Token Usage" in metric_names
    assert "End-to-End Latency (ms)" in metric_names
    assert "Operational Cost ($)" in metric_names
    for c in comps:
        assert c.n_pairs == 10


def test_batch_compare_configurations_family_scope():
    baseline_records = [
        {
            "questionId": f"q_{i}",
            "total_latency_ms": 200.0 + i,
            "cost_usd": 0.005,
        }
        for i in range(10)
    ]
    c1_records = [
        {
            "questionId": f"q_{i}",
            "total_latency_ms": 150.0 + i,
            "cost_usd": 0.001,
        }
        for i in range(10)
    ]
    c2_records = [
        {
            "questionId": f"q_{i}",
            "total_latency_ms": 160.0 + i,
            "cost_usd": 0.002,
        }
        for i in range(10)
    ]

    treatments = {"C1": c1_records, "C2": c2_records}

    # Under family_scope="metric", m = 2 (2 treatments per metric)
    comps_metric = batch_compare_configurations(
        baseline_records, treatments, metrics_to_test=["total_latency_ms", "cost_usd"], family_scope="metric"
    )
    # Under family_scope="global", m = 4 (2 treatments * 2 metrics = 4)
    comps_global = batch_compare_configurations(
        baseline_records, treatments, metrics_to_test=["total_latency_ms", "cost_usd"], family_scope="global"
    )

    assert len(comps_metric) == 4
    assert len(comps_global) == 4

    # The global adjusted p-value for the best test should be larger than metric-family adjusted
    min_metric = min(c.p_adjusted for c in comps_metric)
    min_global = min(c.p_adjusted for c in comps_global)
    assert min_global >= min_metric
