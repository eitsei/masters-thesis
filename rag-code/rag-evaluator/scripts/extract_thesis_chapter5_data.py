#!/usr/bin/env python3
"""Extracts exact Chapter 5 data for both MuSiQue and Enterprise datasets.

Computes:
- Latency: TTFT P50, TTFT P95, T_total P50, T_total P95, Delta T_total (%) + Wilcoxon significance vs C0
- Cost: Uncached Prompt Tokens, Cached Prompt Tokens, Output Tokens, Cost_1k ($), Delta Cost (%) + Wilcoxon significance vs C0
- Quality MuSiQue: CP, CR, Faithfulness, AR, Token F1, EM, Composite Q (Eq. 20) + Wilcoxon significance vs C0
- Quality Enterprise: CP, CR, Faithfulness, AR, Rejection Accuracy / Refusal F1, Composite Q (Eq. 20) + Wilcoxon significance vs C0
- Pareto: Composite Q, Cost_1k ($), T_total P50 (s), QPD, QPS, Pareto Optimal flag
"""

import json
from pathlib import Path
import numpy as np
from scipy import stats

CONFIGS = [
    ("C_0", "run_C0_baseline.jsonl", "Naive RAG Baseline", 0),
    ("C_1", "run_C1_M1.jsonl", "S2B", 1),
    ("C_2", "run_C2_M2.jsonl", "SR", 1),
    ("C_3", "run_C3_M3.jsonl", "PC", 1),
    ("C_4", "run_C4_M4.jsonl", "MC", 1),
    ("C_5", "run_C5_M1_M2.jsonl", "S2B + SR", 2),
    ("C_6", "run_C6_M1_M3.jsonl", "S2B + PC", 2),
    ("C_7", "run_C7_M1_M4.jsonl", "S2B + MC", 2),
    ("C_8", "run_C8_M2_M3.jsonl", "SR + PC", 2),
    ("C_9", "run_C9_M2_M4.jsonl", "SR + MC", 2),
    ("C_{10}", "run_C10_M3_M4.jsonl", "PC + MC", 2),
    ("C_{11}", "run_C11_M1_M2_M3.jsonl", "S2B + SR + PC", 3),
    ("C_{12}", "run_C12_M1_M2_M4.jsonl", "S2B + SR + MC", 3),
    ("C_{13}", "run_C13_M1_M3_M4.jsonl", "S2B + PC + MC", 3),
    ("C_{14}", "run_C14_M2_M3_M4.jsonl", "SR + PC + MC", 3),
    ("C_{15}", "run_C15_M1_M2_M3_M4.jsonl", "S2B + SR + PC + MC (Full-Stack)", 4),
]

def load_dataset(run_dir):
    p = Path(run_dir)
    res = {}
    for cid, fname, name, tier in CONFIGS:
        f = p / fname
        if not f.exists():
            continue
        recs = [json.loads(l) for l in f.read_text().splitlines() if l.strip()]
        res[cid] = {"recs": recs, "name": name, "tier": tier}
    return res

def holm_bonferroni(raw_p_values):
    # returns dict mapping index to adjusted p-value
    m = len(raw_p_values)
    sorted_indices = sorted(range(m), key=lambda i: raw_p_values[i])
    adj_p = [1.0] * m
    for rank, idx in enumerate(sorted_indices):
        p = raw_p_values[idx]
        adj = p * (m - rank)
        adj_p[idx] = min(1.0, adj)
    # enforce monotonicity
    for i in range(1, m):
        idx = sorted_indices[i]
        prev_idx = sorted_indices[i - 1]
        adj_p[idx] = max(adj_p[idx], adj_p[prev_idx])
    return adj_p

def get_sig_flag(p):
    if p < 0.001:
        return "***"
    elif p < 0.01:
        return "**"
    elif p < 0.05:
        return "*"
    return "ns"

def compute_wilcoxon(baseline_vals, treat_vals):
    # paired test on shared questions
    n = min(len(baseline_vals), len(treat_vals))
    b = np.array(baseline_vals[:n], dtype=float)
    t = np.array(treat_vals[:n], dtype=float)
    diff = t - b
    # if all diffs are zero
    if np.all(diff == 0):
        return 0.0, 1.0
    try:
        res = stats.wilcoxon(diff, alternative="two-sided")
        return float(res.statistic), float(res.pvalue)
    except Exception:
        return 0.0, 1.0

def process_corpus(corpus_name, run_dir):
    data = load_dataset(run_dir)
    c0_recs = data["C_0"]["recs"]
    
    # 1. Latency metrics
    c0_total_lat = [r.get("total_latency_ms", r.get("totalLatencyMs", 0)) for r in c0_recs]
    c0_ttft = [r.get("ttft_ms", r.get("timings", {}).get("ttftMs", 0)) for r in c0_recs]
    c0_lat_med = float(np.median(c0_total_lat))

    # Collect raw p-values for total latency vs C0
    raw_lat_p = []
    lat_keys = [cid for cid, _, _, _ in CONFIGS if cid != "C_0"]
    for cid in lat_keys:
        t_lat = [r.get("total_latency_ms", r.get("totalLatencyMs", 0)) for r in data[cid]["recs"]]
        _, p_val = compute_wilcoxon(c0_total_lat, t_lat)
        raw_lat_p.append(p_val)
    adj_lat_p = holm_bonferroni(raw_lat_p)
    lat_sig = {cid: (raw_lat_p[i], adj_lat_p[i], get_sig_flag(adj_lat_p[i])) for i, cid in enumerate(lat_keys)}

    latency_rows = []
    for cid, _, name, tier in CONFIGS:
        recs = data[cid]["recs"]
        ttft_vals = [r.get("ttft_ms", r.get("timings", {}).get("ttftMs", 0)) for r in recs]
        tot_vals = [r.get("total_latency_ms", r.get("totalLatencyMs", 0)) for r in recs]
        
        ttft_p50 = float(np.percentile(ttft_vals, 50))
        ttft_p95 = float(np.percentile(ttft_vals, 95))
        tot_p50 = float(np.percentile(tot_vals, 50))
        tot_p95 = float(np.percentile(tot_vals, 95))
        
        if cid == "C_0":
            delta_pct = 0.0
            sig = ""
        else:
            delta_pct = ((tot_p50 - c0_lat_med) / c0_lat_med) * 100.0
            sig = lat_sig[cid][2]
            
        latency_rows.append({
            "cid": cid,
            "name": name,
            "tier": tier,
            "ttft_p50": ttft_p50,
            "ttft_p95": ttft_p95,
            "tot_p50": tot_p50,
            "tot_p95": tot_p95,
            "delta_pct": delta_pct,
            "sig": sig
        })

    # 2. Cost metrics
    c0_costs = [r.get("cost_usd", r.get("cost", 0)) for r in c0_recs]
    c0_cost_mean = float(np.mean(c0_costs))
    
    raw_cost_p = []
    cost_keys = [cid for cid, _, _, _ in CONFIGS if cid != "C_0"]
    for cid in cost_keys:
        t_costs = [r.get("cost_usd", r.get("cost", 0)) for r in data[cid]["recs"]]
        _, p_val = compute_wilcoxon(c0_costs, t_costs)
        raw_cost_p.append(p_val)
    adj_cost_p = holm_bonferroni(raw_cost_p)
    cost_sig = {cid: (raw_cost_p[i], adj_cost_p[i], get_sig_flag(adj_cost_p[i])) for i, cid in enumerate(cost_keys)}

    cost_rows = []
    for cid, _, name, tier in CONFIGS:
        recs = data[cid]["recs"]
        # token counts
        uncached_prompt = []
        cached_prompt = []
        output_tokens = []
        costs = []
        for r in recs:
            tu = r.get("tokenUsage", {})
            pt = tu.get("promptTokens", r.get("prompt_tokens", 0))
            ct = tu.get("cachedTokens", r.get("cached_tokens", 0))
            opt = tu.get("completionTokens", r.get("completion_tokens", 0))
            uncached = max(0, pt - ct)
            uncached_prompt.append(uncached)
            cached_prompt.append(ct)
            output_tokens.append(opt)
            costs.append(r.get("cost_usd", r.get("cost", 0)))
            
        avg_uncached = float(np.mean(uncached_prompt))
        avg_cached = float(np.mean(cached_prompt))
        avg_output = float(np.mean(output_tokens))
        cost_1k = float(np.mean(costs)) * 1000.0
        
        if cid == "C_0":
            delta_cost = 0.0
            sig = ""
        else:
            delta_cost = ((float(np.mean(costs)) - c0_cost_mean) / c0_cost_mean) * 100.0
            sig = cost_sig[cid][2]
            
        cost_rows.append({
            "cid": cid,
            "name": name,
            "tier": tier,
            "uncached_tokens": avg_uncached,
            "cached_tokens": avg_cached,
            "output_tokens": avg_output,
            "cost_1k": cost_1k,
            "delta_cost": delta_cost,
            "sig": sig
        })

    # 3. Quality metrics
    c0_q = [r.get("composite_quality", r.get("composite_q", 0)) for r in c0_recs]
    raw_q_p = []
    q_keys = [cid for cid, _, _, _ in CONFIGS if cid != "C_0"]
    for cid in q_keys:
        t_q = [r.get("composite_quality", r.get("composite_q", 0)) for r in data[cid]["recs"]]
        _, p_val = compute_wilcoxon(c0_q, t_q)
        raw_q_p.append(p_val)
    adj_q_p = holm_bonferroni(raw_q_p)
    q_sig = {cid: (raw_q_p[i], adj_q_p[i], get_sig_flag(adj_q_p[i])) for i, cid in enumerate(q_keys)}

    quality_rows = []
    for cid, _, name, tier in CONFIGS:
        recs = data[cid]["recs"]
        cp_vals = [r.get("context_precision", 0) for r in recs]
        cr_vals = [r.get("context_recall", 0) for r in recs]
        faith_vals = [r.get("faithfulness", 0) for r in recs]
        ar_vals = [r.get("answer_relevancy", 0) for r in recs]
        f1_vals = [r.get("token_f1", 0) for r in recs]
        em_vals = [r.get("exact_match", 0) for r in recs]
        comp_q_vals = [r.get("composite_quality", r.get("composite_q", 0)) for r in recs]
        
        # Check refusal / rejection accuracy for enterprise
        ood_recs = [r for r in recs if "ei sisällä riittävästi" in r.get("groundTruthAnswer", "").lower() or "not contain sufficient" in r.get("groundTruthAnswer", "").lower()]
        if ood_recs:
            refused = [r for r in ood_recs if "ei sisällä riittävästi" in r.get("generatedAnswer", "").lower() or "not contain sufficient" in r.get("generatedAnswer", "").lower() or "ei löydy" in r.get("generatedAnswer", "").lower() or "valitettavasti" in r.get("generatedAnswer", "").lower()]
            rejection_acc = (len(refused) / len(ood_recs)) * 100.0
            # refusal token f1
            refusal_f1 = float(np.mean([r.get("token_f1", 0) for r in ood_recs])) * 100.0
        else:
            rejection_acc = 0.0
            refusal_f1 = 0.0

        quality_rows.append({
            "cid": cid,
            "name": name,
            "tier": tier,
            "cp": float(np.mean(cp_vals)),
            "cr": float(np.mean(cr_vals)),
            "faith": float(np.mean(faith_vals)),
            "ar": float(np.mean(ar_vals)),
            "f1": float(np.mean(f1_vals)) * 100.0,
            "em": float(np.mean(em_vals)) * 100.0,
            "rejection_acc": rejection_acc,
            "refusal_f1": refusal_f1,
            "comp_q": float(np.mean(comp_q_vals)),
            "sig": "" if cid == "C_0" else q_sig[cid][2]
        })

    # 4. Pareto Frontier analysis
    # Objectives: Maximize Q, Minimize Cost_1k, Minimize T_total P50
    pareto_candidates = []
    for i, cid in enumerate([c[0] for c in CONFIGS]):
        q_val = quality_rows[i]["comp_q"]
        cost_val = cost_rows[i]["cost_1k"]
        lat_val = latency_rows[i]["tot_p50"] / 1000.0  # seconds
        pareto_candidates.append({
            "cid": cid,
            "name": CONFIGS[i][2],
            "tier": CONFIGS[i][3],
            "q": q_val,
            "cost_1k": cost_val,
            "tot_p50_s": lat_val,
            "qpd": q_val / (cost_val / 1000.0) if cost_val > 0 else 0.0,
            "qps": q_val / lat_val if lat_val > 0 else 0.0
        })

    # Determine Pareto optimality
    for i, p1 in enumerate(pareto_candidates):
        is_dominated = False
        for j, p2 in enumerate(pareto_candidates):
            if i == j:
                continue
            # p2 dominates p1 if p2 is >= in all objectives (strictly better in at least one)
            # Objectives: Q higher is better, cost lower is better, lat lower is better
            better_or_equal = (p2["q"] >= p1["q"]) and (p2["cost_1k"] <= p1["cost_1k"]) and (p2["tot_p50_s"] <= p1["tot_p50_s"])
            strictly_better = (p2["q"] > p1["q"]) or (p2["cost_1k"] < p1["cost_1k"]) or (p2["tot_p50_s"] < p1["tot_p50_s"])
            if better_or_equal and strictly_better:
                is_dominated = True
                break
        p1["pareto_optimal"] = not is_dominated

    return {
        "latency": latency_rows,
        "cost": cost_rows,
        "quality": quality_rows,
        "pareto": pareto_candidates
    }

if __name__ == "__main__":
    musique_res = process_corpus("MuSiQue", "rag-code/rag-evaluator/runs/sample10_all16")
    nordea_res = process_corpus("Enterprise", "rag-code/rag-evaluator/runs/nordea_all16")
    
    out = {
        "musique": musique_res,
        "enterprise": nordea_res
    }
    Path("rag-code/rag-evaluator/scripts/extracted_results.json").write_text(json.dumps(out, indent=2))
    print("Successfully saved extracted_results.json")
