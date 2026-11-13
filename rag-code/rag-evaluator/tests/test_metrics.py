"""Unit tests for Chapter 4 thesis evaluation metrics.

Tests:
- RQ1 Latency formulations (T_ret, T_gen, T_total, TTFT, P50, P95)
- RQ2 Operational Cost Model (Thesis Eq. 178 / 181)
- RQ3 Quality Metrics (EM, Token F1, Composite Quality Eq. 238)
- RQ4 Trade-off Ratios (QPD Eq. 250, QPS Eq. 255)
"""

import math
import pytest
from src.metrics import (
    PRICING_CATALOG,
    PricingTier,
    compute_composite_quality,
    compute_cost,
    compute_false_refusal_rate,
    compute_latency_percentiles,
    compute_qpd,
    compute_qps,
    compute_refusal_accuracy,
    compute_total_latency,
    compute_ttft,
    exact_match,
    get_pricing_tier,
    is_refusal_answer,
    is_refusal_ground_truth,
    is_sample_unanswerable,
    normalize_answer,
    token_f1,
)


# =====================================================================
# RQ1 Latency Tests
# =====================================================================


def test_compute_total_latency_simple():
    total = compute_total_latency(retrieval_ms=250.0, generation_ms=1250.0)
    assert total == 1500.0


def test_compute_total_latency_micro_phases():
    total = compute_total_latency(
        retrieval_ms=50.0,
        generation_ms=0.0,
        embed_ms=15.0,
        route_ms=45.0,
        rerank_ms=80.0,
        expand_ms=20.0,
        cache_ms=10.0,
        prefill_ms=300.0,
        stream_ms=700.0,
    )
    assert total == (15 + 45 + 50 + 80 + 20 + 10 + 300 + 700)


def test_compute_ttft():
    ttft = compute_ttft(total_latency_ms=1500.0, stream_ms=600.0)
    assert ttft == 900.0

    # Negative stream guard
    assert compute_ttft(total_latency_ms=500.0, stream_ms=600.0) == 0.0


def test_compute_latency_percentiles():
    latencies = [100.0, 110.0, 120.0, 130.0, 140.0, 150.0, 160.0, 170.0, 180.0, 200.0]
    stats = compute_latency_percentiles(latencies)

    assert stats["median"] == 145.0
    assert stats["p50"] == 145.0
    assert stats["min"] == 100.0
    assert stats["max"] == 200.0
    assert stats["p95"] > stats["p50"]
    assert stats["p90"] <= stats["p95"]


def test_compute_latency_percentiles_empty():
    stats = compute_latency_percentiles([])
    assert stats["p50"] == 0.0
    assert stats["mean"] == 0.0


# =====================================================================
# RQ2 Operational Cost Model (Thesis Eq. 178) Tests
# =====================================================================


def test_compute_cost_gemini_flash():
    # Prompt: 10,000 tokens @ $0.15/1M = $0.0015
    # Completion: 2,000 tokens @ $0.60/1M = $0.0012
    # Cached: 5,000 tokens @ $0.0375/1M = $0.0001875
    # Total = 0.0015 + 0.0012 + 0.0001875 = 0.0028875
    cost = compute_cost(
        prompt_tokens=10000,
        completion_tokens=2000,
        cached_tokens=5000,
        tier="gemini-2.5-flash",
    )
    assert math.isclose(cost, 0.0028875, rel_tol=1e-9)


def test_compute_cost_gemini_38_flash_90pct_discount():
    # Prompt: 10,000 tokens @ $0.75/1M = $0.0075
    # Completion: 2,000 tokens @ $3.75/1M = $0.0075
    # Cached: 5,000 tokens @ $0.075/1M (90% discount off $0.75) = $0.000375
    # Total = 0.0075 + 0.0075 + 0.000375 = 0.015375
    cost = compute_cost(
        prompt_tokens=10000,
        completion_tokens=2000,
        cached_tokens=5000,
        tier="gemini-3.8-flash",
    )
    assert math.isclose(cost, 0.015375, rel_tol=1e-9)


def test_compute_cost_with_rerank_and_embed():
    # Reranking 50 passages @ $0.0001 = $0.005
    # Embedding 1,000 tokens @ $0.025/1M = $0.000025
    cost = compute_cost(
        prompt_tokens=1000,
        completion_tokens=500,
        cached_tokens=0,
        rerank_docs=50,
        embed_tokens=1000,
        tier="gemini-2.5-flash",
    )
    expected_token = (1000 * 0.15 + 500 * 0.60 + 1000 * 0.025) / 1_000_000
    expected_rerank = 50 * 0.0001
    assert math.isclose(cost, expected_token + expected_rerank, rel_tol=1e-9)


def test_compute_cost_ollama_zero():
    cost = compute_cost(
        prompt_tokens=50000,
        completion_tokens=10000,
        cached_tokens=20000,
        tier="ollama",
    )
    assert cost == 0.0


def test_compute_cost_custom_tier():
    custom = PricingTier(
        name="custom-tier",
        prompt_price_per_million=1.0,
        comp_price_per_million=2.0,
        cached_price_per_million=0.5,
    )
    cost = compute_cost(
        prompt_tokens=1000000,
        completion_tokens=500000,
        cached_tokens=200000,
        tier=custom,
    )
    # 1.0 + 1.0 + 0.1 = 2.1
    assert math.isclose(cost, 2.1, rel_tol=1e-9)


def test_compute_cost_prompt_is_total():
    # When prompt_is_uncached=False, uncached = 10000 - 3000 = 7000
    cost = compute_cost(
        prompt_tokens=10000,
        completion_tokens=1000,
        cached_tokens=3000,
        tier="gemini-2.5-flash",
        prompt_is_uncached=False,
    )
    expected = (7000 * 0.15 + 1000 * 0.60 + 3000 * 0.0375) / 1_000_000
    assert math.isclose(cost, expected, rel_tol=1e-9)


def test_compute_cost_cache_write():
    # Cache write tokens must be billed at tier.prompt_price_per_million ($0.75/1M for gemini-3.8-flash)
    # 1,000 prompt @ $0.75/1M = $0.00075
    # 4,000 cache_write @ $0.75/1M = $0.00300
    # 500 comp @ $3.75/1M = $0.001875
    # Total = 0.00075 + 0.00300 + 0.001875 = 0.005625
    cost = compute_cost(
        prompt_tokens=1000,
        completion_tokens=500,
        cache_write_tokens=4000,
        tier="gemini-3.8-flash",
    )
    assert math.isclose(cost, 0.005625, rel_tol=1e-9)

    # Test with custom pricing tier to verify dynamic prompt rate matching
    custom = PricingTier(
        name="custom-cache-tier",
        prompt_price_per_million=2.50,
        comp_price_per_million=5.00,
        cached_price_per_million=0.25,
    )
    cost_custom = compute_cost(
        prompt_tokens=0,
        completion_tokens=0,
        cache_write_tokens=1_000_000,
        tier=custom,
    )
    # 1M cache write tokens * $2.50/1M = $2.50
    assert math.isclose(cost_custom, 2.50, rel_tol=1e-9)


def test_compute_cost_routing_split():
    # Routing classifier always uses gemini-3.5-flash-lite:
    # prompt: $0.30 / 1M tokens
    # completion: $2.50 / 1M tokens
    cost_routing_only = compute_cost(
        prompt_tokens=0,
        completion_tokens=0,
        routing_prompt_tokens=200_000,    # 200k * 0.30 / 1M = 0.060
        routing_completion_tokens=50_000,  # 50k * 2.50 / 1M = 0.125
        tier="gemini-3.8-flash",
    )
    assert math.isclose(cost_routing_only, 0.185, rel_tol=1e-9)

    # Combined with generation tokens on main tier (gemini-3.8-flash):
    # 10,000 prompt @ $0.75/1M = $0.0075
    # 2,000 comp @ $3.75/1M = $0.0075
    # 1,000 router prompt @ $0.30/1M = $0.00030
    # 100 router comp @ $2.50/1M = $0.00025
    # Total = 0.0075 + 0.0075 + 0.00030 + 0.00025 = 0.01555
    cost_combined = compute_cost(
        prompt_tokens=10000,
        completion_tokens=2000,
        routing_prompt_tokens=1000,
        routing_completion_tokens=100,
        tier="gemini-3.8-flash",
    )
    assert math.isclose(cost_combined, 0.01555, rel_tol=1e-9)


def test_get_pricing_tier_gemini_35_flash_vs_lite():
    """Ensures gemini-3.5-flash and gemini-3.5-flash-lite are strictly isolated."""
    tier_flash = get_pricing_tier("gemini-3.5-flash")
    tier_lite = get_pricing_tier("gemini-3.5-flash-lite")

    assert tier_flash.name == "gemini-3.5-flash"
    assert tier_flash.prompt_price_per_million == 1.50
    assert tier_flash.comp_price_per_million == 9.00
    assert tier_flash.cached_price_per_million == 0.15

    assert tier_lite.name == "gemini-3.5-flash-lite"
    assert tier_lite.prompt_price_per_million == 0.30
    assert tier_lite.comp_price_per_million == 2.50
    assert tier_lite.cached_price_per_million == 0.03

    # Prefixed and fuzzy variants must resolve without collision
    assert get_pricing_tier("models/gemini-3.5-flash").prompt_price_per_million == 1.50
    assert get_pricing_tier("models/gemini-3.5-flash-lite").prompt_price_per_million == 0.30
    assert get_pricing_tier("3.5-flash").prompt_price_per_million == 1.50
    assert get_pricing_tier("3.5-flash-lite").prompt_price_per_million == 0.30


# =====================================================================
# RQ3 Quality Metrics Tests (Exact Match, Token F1, Composite Q)
# =====================================================================


def test_normalize_answer():
    assert normalize_answer("The United States of America!") == "united states of america"
    assert normalize_answer("  a   dog,  an elephant. ") == "dog elephant"
    assert normalize_answer("") == ""


def test_exact_match_standard():
    assert exact_match("Marie Curie", "marie curie") == 1.0
    assert exact_match("The Nobel Prize", "Nobel Prize") == 1.0
    assert exact_match("Albert Einstein", "Isaac Newton") == 0.0


def test_exact_match_aliases():
    aliases = ["Madame Curie", "Maria Sklodowska-Curie"]
    assert exact_match("Madame Curie", "Marie Curie", aliases=aliases) == 1.0
    assert exact_match("Maria Sklodowska Curie", "Marie Curie", aliases=aliases) == 1.0
    assert exact_match("Pierre Curie", "Marie Curie", aliases=aliases) == 0.0


def test_token_f1_scores():
    # Exact match
    assert token_f1("Albert Einstein", "Albert Einstein") == 1.0

    # Completely disjoint
    assert token_f1("Paris", "London") == 0.0

    # Partial overlap: "Marie Salomea Curie" vs "Marie Curie"
    # Overlap = 2 ("marie", "curie"). Pred tokens = 3, GT tokens = 2
    # Precision = 2/3, Recall = 2/2 = 1.0
    # F1 = 2 * (2/3 * 1.0) / (2/3 + 1.0) = (4/3) / (5/3) = 0.8
    f1 = token_f1("Marie Salomea Curie", "Marie Curie")
    assert math.isclose(f1, 0.8, rel_tol=1e-6)


def test_token_f1_aliases_selects_max():
    # Ground truth: "Helsinki", Aliases: ["City of Helsinki", "Helsingfors"]
    # Prediction: "City of Helsinki"
    # Against "Helsinki": F1 < 1.0
    # Against "City of Helsinki": F1 = 1.0
    score = token_f1("City of Helsinki", "Helsinki", aliases=["City of Helsinki", "Helsingfors"])
    assert score == 1.0


def test_composite_quality_perfect():
    # All 1.0 -> Q should be 1.0
    q = compute_composite_quality(1.0, 1.0, 1.0, 1.0)
    assert math.isclose(q, 1.0, rel_tol=1e-5)


def test_composite_quality_calculation():
    # CP=0.8, CR=0.7, Faith=0.9, AR=0.85
    # Reciprocal sum approx 1.25 + 1.42857 + 1.11111 + 1.17647 = 4.96615
    # Q = 4 / 4.96615 = 0.80545
    q = compute_composite_quality(0.8, 0.7, 0.9, 0.85)
    assert math.isclose(q, 0.805553, rel_tol=1e-4)


def test_composite_quality_penalizes_single_dimension_failure():
    # When faithfulness is 0.0 (catastrophic hallucination),
    # harmonic mean should plummet near 0
    q_hallucination = compute_composite_quality(0.9, 0.9, 0.0, 0.9, epsilon=1e-6)
    assert q_hallucination < 1e-4


# =====================================================================
# RQ4 Trade-off Ratios Tests (QPD, QPS)
# =====================================================================


def test_compute_qpd():
    q = 0.8
    cost = 0.0002
    qpd = compute_qpd(q, cost)
    assert math.isclose(qpd, 4000.0, rel_tol=1e-6)


def test_compute_qpd_zero_cost():
    assert math.isinf(compute_qpd(0.85, 0.0))
    assert compute_qpd(0.0, 0.0) == 0.0


def test_compute_qps():
    q = 0.8
    latency_ms = 2000.0  # 2.0 seconds
    qps = compute_qps(q, latency_ms)
    assert math.isclose(qps, 0.4, rel_tol=1e-6)


def test_compute_qps_zero_latency():
    assert math.isinf(compute_qps(0.9, 0.0))
    assert compute_qps(0.0, 0.0) == 0.0


# =====================================================================
# Refusal Accuracy & False Refusal Rate (FRR) Tests
# =====================================================================


def test_is_refusal_answer_finnish():
    assert is_refusal_answer("Annettu konteksti ei sisällä riittävästi tietoa kysymykseen vastaamiseksi.")
    assert is_refusal_answer("Valitettavasti annettu konteksti ei sisällä riittävästi tietoa.")
    assert is_refusal_answer("Aineisto ei sisällä riittävästi tietoa kysymykseen vastaamiseksi.")
    assert not is_refusal_answer("Pankki perustettiin vuonna 1820.")


def test_is_refusal_answer_english():
    assert is_refusal_answer("The provided context does not contain sufficient information to answer this query.")
    assert is_refusal_answer("Unfortunately, the context does not contain sufficient information.")
    assert is_refusal_answer("The context does not contain sufficient information to answer this query.")
    assert not is_refusal_answer("Operating profit increased by 14 percent.")


def test_is_refusal_ground_truth():
    assert is_refusal_ground_truth("Annettu konteksti ei sisällä riittävästi tietoa kysymykseen vastaamiseksi.")
    assert is_refusal_ground_truth("The provided context does not contain sufficient information to answer this query.")
    assert not is_refusal_ground_truth("Pääomavaatimus on 15,2 prosenttia.")


def test_is_sample_unanswerable():
    # Explicit answerable flag
    assert is_sample_unanswerable({"answerable": False})
    assert not is_sample_unanswerable({"answerable": True, "groundTruthAnswer": "Vastaus"})

    # OOD ID prefix
    assert is_sample_unanswerable(sample_id="ood__q012")
    assert is_sample_unanswerable(sample_id="ood_financial_01")
    assert not is_sample_unanswerable(sample_id="mhop__q005")

    # Ground truth refusal
    assert is_sample_unanswerable(ground_truth="Annettu konteksti ei sisällä riittävästi tietoa kysymykseen vastaamiseksi.")
    assert not is_sample_unanswerable(ground_truth="Esimerkkipankki Oyj")


def test_compute_refusal_accuracy():
    preds = [
        "Annettu konteksti ei sisällä riittävästi tietoa kysymykseen vastaamiseksi.",
        "Pankki tarjoaa asuntolainoja.",  # failed to refuse
        "The provided context does not contain sufficient information to answer this query.",
    ]
    gts = [
        "Annettu konteksti ei sisällä riittävästi tietoa kysymykseen vastaamiseksi.",
        "Annettu konteksti ei sisällä riittävästi tietoa kysymykseen vastaamiseksi.",
        "The provided context does not contain sufficient information to answer this query.",
    ]
    # 2 out of 3 refused correctly -> 2/3 = 0.6667
    acc = compute_refusal_accuracy(preds, gts)
    assert math.isclose(acc, 2.0 / 3.0, rel_tol=1e-5)


def test_compute_refusal_accuracy_with_flags():
    preds = ["Refusal", "Answer A", "Refusal"]
    gts = ["Answer X", "Answer Y", "Answer Z"]
    flags = [False, True, False]  # 0 and 2 are unanswerable

    # Unanswerable: index 0 (refusal) and index 2 (refusal) -> 2/2 = 1.0
    acc = compute_refusal_accuracy(
        predictions=["Annettu konteksti ei sisällä riittävästi tietoa kysymykseen vastaamiseksi.", "Kyllä", "The provided context does not contain sufficient information to answer this query."],
        ground_truths=gts,
        answerable_flags=flags,
    )
    assert acc == 1.0


def test_compute_refusal_accuracy_no_unanswerable():
    # When all queries are answerable, returns 1.0
    acc = compute_refusal_accuracy(["Ans"], ["Ans"], answerable_flags=[True])
    assert acc == 1.0


def test_compute_false_refusal_rate():
    preds = [
        "Pankki perustettiin vuonna 1820.",
        "Annettu konteksti ei sisällä riittävästi tietoa kysymykseen vastaamiseksi.",  # false refusal!
        "Liikevoitto kasvoi 10 %.",
        "The provided context does not contain sufficient information to answer this query.",  # false refusal!
    ]
    gts = [
        "1820",
        "Pääomavaatimus on 15%",
        "10%",
        "Osinko oli 0.92 euroa",
    ]
    # 4 answerable queries, 2 false refusals -> 2/4 = 0.5 (50%)
    frr = compute_false_refusal_rate(preds, gts)
    assert math.isclose(frr, 0.5, rel_tol=1e-5)


def test_compute_false_refusal_rate_zero():
    preds = ["1820", "15%"]
    gts = ["1820", "15%"]
    assert compute_false_refusal_rate(preds, gts) == 0.0


def test_compute_false_refusal_rate_no_answerable():
    # When no answerable queries exist, returns 0.0
    assert compute_false_refusal_rate([], [], answerable_flags=[]) == 0.0

