#!/usr/bin/env python3
"""Context Inspector CLI.

A standalone utility to inspect and compare retrieved context chunks, questions,
generated answers, and quality metrics across benchmark run files (JSONL format).

Works with both raw and evaluated benchmark runs (MuSiQue, Enterprise, etc.).
Standard library only: requires no external dependencies.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

DEFAULT_RUN_FILE = Path(
    "rag-code/rag-evaluator/runs/evaluated/musique/run_C0_baseline.jsonl"
)
FALLBACK_RUN_FILE = Path(
    "rag-code/rag-evaluator/runs/raw/musique/run_C0_baseline.jsonl"
)


def resolve_run_path(file_path: Optional[str]) -> Path:
    """Resolves the run file path, falling back to sensible defaults."""
    if file_path:
        p = Path(file_path)
        if p.exists():
            return p
        raise FileNotFoundError(f"Specified run file not found: {file_path}")

    if DEFAULT_RUN_FILE.exists():
        return DEFAULT_RUN_FILE
    if FALLBACK_RUN_FILE.exists():
        return FALLBACK_RUN_FILE

    # Search in common run directories
    candidates = list(Path("rag-code/rag-evaluator/runs").glob("**/*.jsonl"))
    if candidates:
        return candidates[0]

    raise FileNotFoundError(
        "Could not find a default benchmark run file. Please specify one with --run."
    )


def load_run_records(run_path: Path) -> List[Dict[str, Any]]:
    """Loads all JSONL records from a run file."""
    records: List[Dict[str, Any]] = []
    with open(run_path, "r", encoding="utf-8") as f:
        for line_num, line in enumerate(f, start=1):
            line_str = line.strip()
            if not line_str:
                continue
            try:
                rec = json.loads(line_str)
                rec["_line_number"] = line_num
                rec["_source_file"] = str(run_path)
                records.append(rec)
            except json.JSONDecodeError as err:
                print(
                    f"Warning: Failed to parse line {line_num} in {run_path}: {err}",
                    file=sys.stderr,
                )
    return records


def format_header(title: str, width: int = 80, char: str = "=") -> str:
    """Formats a visual section header."""
    return f"{char * width}\n {title}\n{char * width}"


def print_record(
    record: Dict[str, Any],
    index: int,
    full_text: bool = False,
    max_chunk_chars: int = 500,
) -> None:
    """Pretty-prints a single benchmark query record."""
    qid = record.get("questionId") or record.get("id") or f"idx_{index}"
    question = record.get("question") or "N/A"
    gen_ans = record.get("generatedAnswer") or record.get("response") or "N/A"
    gt_ans = record.get("groundTruthAnswer") or record.get("answer") or "N/A"
    gt_aliases = record.get("groundTruthAliases") or record.get("answerAliases") or []
    is_refusal = record.get("is_refusal", False)
    answerable = record.get("answerable")
    hops = record.get("hopCount")
    model_used = record.get("model_used") or record.get("modelUsed") or "N/A"
    table_used = record.get("tableNameUsed") or "N/A"
    chunks = record.get("retrievedChunks") or []

    # Optional metric fields (present in evaluated runs)
    em = record.get("exact_match")
    f1 = record.get("token_f1")
    comp_q = record.get("composite_quality")
    cp = record.get("context_precision")
    cr = record.get("context_recall")
    faith = record.get("faithfulness")
    ar = record.get("answer_relevancy")

    print(format_header(f"Query #{index} | ID: {qid} | Hops: {hops} | Answerable: {answerable}"))
    print(f"File:         {record.get('_source_file', 'N/A')} (line {record.get('_line_number', 'N/A')})")
    print(f"Question:     {question}")
    print(f"Ground Truth: {gt_ans}")
    if gt_aliases:
        print(f"Aliases:      {gt_aliases}")
    print(f"Generated:    {gen_ans}")
    print(f"Model Used:   {model_used} | Table: {table_used} | Refusal: {is_refusal}")

    # Metrics section
    if any(m is not None for m in [em, f1, comp_q, cp, cr, faith, ar]):
        metrics_str = []
        if em is not None:
            metrics_str.append(f"EM: {em:.1f}")
        if f1 is not None:
            metrics_str.append(f"F1: {f1:.3f}")
        if comp_q is not None:
            metrics_str.append(f"Comp Q: {comp_q:.4f}")
        if cp is not None:
            metrics_str.append(f"CP: {cp:.3f}")
        if cr is not None:
            metrics_str.append(f"CR: {cr:.3f}")
        if faith is not None:
            metrics_str.append(f"Faith: {faith:.3f}")
        if ar is not None:
            metrics_str.append(f"AR: {ar:.3f}")
        print(f"Metrics:      {' | '.join(metrics_str)}")

    # Retrieved Chunks
    print(f"\n--- Retrieved Context Chunks ({len(chunks)} chunks) ---")
    if not chunks:
        print("  [No chunks retrieved or empty context]")
    else:
        for c_idx, chunk in enumerate(chunks, start=1):
            if isinstance(chunk, dict):
                cid = chunk.get("id") or f"chunk_{c_idx}"
                text = chunk.get("text") or chunk.get("snippet") or ""
            else:
                cid = f"chunk_{c_idx}"
                text = str(chunk)

            # Extract title if present in first line
            lines = text.strip().split("\n")
            first_line = lines[0] if lines else ""

            print(f"\n  [Chunk {c_idx}/{len(chunks)}] (ID: {cid})")
            if first_line and len(lines) > 1:
                print(f"  Title / Header: {first_line}")

            if full_text or len(text) <= max_chunk_chars:
                display_text = text
            else:
                display_text = text[:max_chunk_chars] + f"\n  ... [truncated {len(text) - max_chunk_chars} characters; use --full to view entire chunk]"

            # Indent text for clean display
            indented = "\n".join(f"    {line}" for line in display_text.splitlines())
            print(indented)
    print()


def compare_records(
    rec_a: Dict[str, Any],
    rec_b: Dict[str, Any],
    label_a: str,
    label_b: str,
    full_text: bool = False,
) -> None:
    """Pretty-prints a side-by-side comparison of two runs for the same query."""
    qid = rec_a.get("questionId") or rec_a.get("id") or "N/A"
    question = rec_a.get("question") or "N/A"
    gt_ans = rec_a.get("groundTruthAnswer") or rec_a.get("answer") or "N/A"

    print(format_header(f"COMPARISON | ID: {qid}"))
    print(f"Question:     {question}")
    print(f"Ground Truth: {gt_ans}\n")

    print(f"--- Pipeline A: {label_a} ---")
    print(f"Model:     {rec_a.get('model_used') or rec_a.get('modelUsed') or 'N/A'}")
    print(f"Answer:    {rec_a.get('generatedAnswer') or 'N/A'}")
    print(f"EM / F1:   {rec_a.get('exact_match', 'N/A')} / {rec_a.get('token_f1', 'N/A')}")
    print(f"Comp Q:    {rec_a.get('composite_quality', 'N/A')} (CR: {rec_a.get('context_recall', 'N/A')}, CP: {rec_a.get('context_precision', 'N/A')})")
    print(f"Refusal:   {rec_a.get('is_refusal', False)}")

    chunks_a = rec_a.get("retrievedChunks") or []
    print(f"Chunks ({len(chunks_a)}):")
    for i, c in enumerate(chunks_a, start=1):
        txt = c.get("text") or c.get("snippet") if isinstance(c, dict) else str(c)
        preview = txt.replace("\n", " ")[:120]
        print(f"  {i}. {preview}...")

    print(f"\n--- Pipeline B: {label_b} ---")
    print(f"Model:     {rec_b.get('model_used') or rec_b.get('modelUsed') or 'N/A'}")
    print(f"Answer:    {rec_b.get('generatedAnswer') or 'N/A'}")
    print(f"EM / F1:   {rec_b.get('exact_match', 'N/A')} / {rec_b.get('token_f1', 'N/A')}")
    print(f"Comp Q:    {rec_b.get('composite_quality', 'N/A')} (CR: {rec_b.get('context_recall', 'N/A')}, CP: {rec_b.get('context_precision', 'N/A')})")
    print(f"Refusal:   {rec_b.get('is_refusal', False)}")

    chunks_b = rec_b.get("retrievedChunks") or []
    print(f"Chunks ({len(chunks_b)}):")
    for i, c in enumerate(chunks_b, start=1):
        txt = c.get("text") or c.get("snippet") if isinstance(c, dict) else str(c)
        preview = txt.replace("\n", " ")[:120]
        print(f"  {i}. {preview}...")
    print()


def print_summary(records: List[Dict[str, Any]], run_path: Path) -> None:
    """Prints an aggregated statistical summary of models, hops, refusals, and metrics."""
    total_queries = len(records)
    if total_queries == 0:
        print("No records to summarize.")
        return

    # Model and Hop distributions
    model_counts: Counter[str] = Counter()
    hop_counts: Counter[int] = Counter()
    hop_model_counts: Dict[int, Counter[str]] = defaultdict(Counter)

    answerable_count = 0
    unanswerable_count = 0
    total_refusals = 0
    false_refusals = 0
    correct_refusals = 0

    # Metric accumulators
    ems: List[float] = []
    f1s: List[float] = []
    comp_qs: List[float] = []
    cps: List[float] = []
    crs: List[float] = []
    faiths: List[float] = []
    ars: List[float] = []

    for rec in records:
        model = str(rec.get("model_used") or rec.get("modelUsed") or "Unknown")
        model_counts[model] += 1

        hops = rec.get("hopCount")
        if hops is not None:
            hop_counts[hops] += 1
            hop_model_counts[hops][model] += 1

        is_ans = rec.get("answerable", True)
        is_ref = bool(rec.get("is_refusal", False))

        if is_ref:
            total_refusals += 1

        if is_ans:
            answerable_count += 1
            if is_ref:
                false_refusals += 1
            # Metrics on answerable
            for field, target_list in [
                ("exact_match", ems),
                ("token_f1", f1s),
                ("composite_quality", comp_qs),
                ("context_precision", cps),
                ("context_recall", crs),
                ("faithfulness", faiths),
                ("answer_relevancy", ars),
            ]:
                val = rec.get(field)
                if val is not None and isinstance(val, (int, float)):
                    target_list.append(float(val))
        else:
            unanswerable_count += 1
            if is_ref:
                correct_refusals += 1

    print(format_header(f"RUN SUMMARY | {run_path.name}"))
    print(f"File Path:       {run_path}")
    print(f"Total Queries:   {total_queries}")
    print(f"Partition:       {answerable_count} Answerable, {unanswerable_count} Unanswerable\n")

    # Model Distribution Table
    print("--- Model Routing Distribution ---")
    print(f"{'Model Name':<28} {'Count':>6} {'Share':>8}")
    print("-" * 44)
    for model, count in model_counts.most_common():
        pct = (count / total_queries) * 100.0
        print(f"{model:<28} {count:>6} {pct:>7.1f}%")

    # Model by Hop Breakdown
    if hop_model_counts:
        print("\n--- Model Routing by Reasoning Tier (Hops) ---")
        for h in sorted(hop_model_counts.keys()):
            h_total = hop_counts[h]
            print(f"  [{h}-Hop Queries] (N={h_total}):")
            for model, count in hop_model_counts[h].most_common():
                pct = (count / h_total) * 100.0
                print(f"    - {model:<26} {count:>3}/{h_total} ({pct:>5.1f}%)")

    # Refusal Statistics
    frr_pct = (false_refusals / answerable_count * 100.0) if answerable_count else 0.0
    ref_acc_pct = (correct_refusals / unanswerable_count * 100.0) if unanswerable_count else 0.0
    print("\n--- Abstention & Refusal Statistics ---")
    print(f"Total Refusals:           {total_refusals} / {total_queries} ({(total_refusals / total_queries * 100.0):.1f}%)")
    print(f"False Refusal Rate (FRR): {false_refusals} / {answerable_count} ({frr_pct:.1f}%) [Answerable abstentions]")
    if unanswerable_count:
        print(f"Refusal Accuracy:         {correct_refusals} / {unanswerable_count} ({ref_acc_pct:.1f}%) [Correct OOD abstentions]")

    # Metrics Summary
    if ems or comp_qs:
        print("\n--- Average Quality Metrics (Answerable Partition) ---")
        if ems:
            print(f"Exact Match (EM):     {(sum(ems) / len(ems) * 100.0):.1f}%")
        if f1s:
            print(f"Token F1:             {(sum(f1s) / len(f1s) * 100.0):.1f}%")
        if comp_qs:
            print(f"Composite Quality Q:  {(sum(comp_qs) / len(comp_qs)):.4f}")
        if cps:
            print(f"Context Precision:    {(sum(cps) / len(cps)):.3f}")
        if crs:
            print(f"Context Recall:       {(sum(crs) / len(crs)):.3f}")
        if faiths:
            print(f"Faithfulness:         {(sum(faiths) / len(faiths)):.3f}")
        if ars:
            print(f"Answer Relevancy:     {(sum(ars) / len(ars)):.3f}")
    print()


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Inspect retrieved contexts, queries, answers, and metrics from benchmark runs.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Inspect first 5 queries in default baseline run:
  python3 context_inspector.py

  # Search for queries containing 'Dark Hazard':
  python3 context_inspector.py -q "Dark Hazard"

  # Search by specific question ID and show full context text:
  python3 context_inspector.py --id "2hop__739909_807845" --full

  # Inspect a specific run file (e.g. M2 Semantic Reranker):
  python3 context_inspector.py -r rag-code/rag-evaluator/runs/evaluated/musique/run_C2_M2.jsonl -q "president"

  # Compare baseline (C0) against semantic reranker (C2) on a query:
  python3 context_inspector.py -r rag-code/rag-evaluator/runs/evaluated/musique/run_C0_baseline.jsonl \\
      --compare rag-code/rag-evaluator/runs/evaluated/musique/run_C2_M2.jsonl -q "Fuser"

  # Filter queries by hop count (e.g. 4-hop queries):
  python3 context_inspector.py --hops 4 --limit 3

  # Inspect queries where the model refused to answer:
  python3 context_inspector.py --refusals-only --limit 5
        """,
    )

    parser.add_argument(
        "-r",
        "--run",
        dest="run_file",
        help="Path to the primary benchmark JSONL run file (raw or evaluated).",
        default=None,
    )
    parser.add_argument(
        "-q",
        "--query",
        "--search",
        dest="query_search",
        help="Search substring in question text (case-insensitive).",
        default=None,
    )
    parser.add_argument(
        "--id",
        dest="question_id",
        help="Filter by exact question ID (e.g. '2hop__739909_807845').",
        default=None,
    )
    parser.add_argument(
        "-i",
        "--index",
        dest="index",
        type=int,
        help="Filter by 0-based index in the run file.",
        default=None,
    )
    parser.add_argument(
        "-s",
        "--summary",
        "--stats",
        action="store_true",
        help="Print an aggregated statistical summary (model routing, hops, refusals, metrics).",
    )
    parser.add_argument(
        "-m",
        "--model",
        dest="model_filter",
        help="Filter queries by model name substring (e.g. 'pro', 'flash', 'lite').",
        default=None,
    )
    parser.add_argument(
        "--hops",
        dest="hops",
        type=int,
        choices=[2, 3, 4],
        help="Filter by reasoning depth / hop count (2, 3, or 4).",
        default=None,
    )
    parser.add_argument(
        "--refusals-only",
        action="store_true",
        help="Show only queries where the model returned a refusal.",
    )
    parser.add_argument(
        "--failures-only",
        action="store_true",
        help="Show only queries where Exact Match is 0 (answerable only).",
    )
    parser.add_argument(
        "-c",
        "--compare",
        dest="compare_file",
        help="Path to a second run file to compare side-by-side with the primary run.",
        default=None,
    )
    parser.add_argument(
        "--full",
        action="store_true",
        help="Display full chunk text without length truncation.",
    )
    parser.add_argument(
        "-n",
        "--limit",
        dest="limit",
        type=int,
        help="Maximum number of matched queries to display (default: 5, use 0 for all).",
        default=5,
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Output matched records as raw JSON.",
    )

    args = parser.parse_args()

    try:
        primary_path = resolve_run_path(args.run_file)
    except FileNotFoundError as err:
        print(f"Error: {err}", file=sys.stderr)
        return 1

    records = load_run_records(primary_path)
    if not records:
        print(f"Error: No valid records found in {primary_path}", file=sys.stderr)
        return 1

    if args.summary:
        print_summary(records, primary_path)
        if args.compare_file:
            comp_path = Path(args.compare_file)
            if comp_path.exists():
                print_summary(load_run_records(comp_path), comp_path)
        return 0

    # Load comparison run if requested
    compare_records_by_id: Dict[str, Dict[str, Any]] = {}
    if args.compare_file:
        comp_path = Path(args.compare_file)
        if not comp_path.exists():
            print(f"Error: Comparison file not found: {comp_path}", file=sys.stderr)
            return 1
        comp_recs = load_run_records(comp_path)
        for r in comp_recs:
            qid = r.get("questionId") or r.get("id")
            if qid:
                compare_records_by_id[qid] = r

    # Filtering
    filtered: List[Tuple[int, Dict[str, Any]]] = []
    for idx, rec in enumerate(records):
        if args.index is not None and idx != args.index:
            continue

        qid = str(rec.get("questionId") or rec.get("id") or "")
        if args.question_id and args.question_id.lower() not in qid.lower():
            continue

        q_text = str(rec.get("question") or "")
        if args.query_search and args.query_search.lower() not in q_text.lower():
            continue

        if args.hops is not None and rec.get("hopCount") != args.hops:
            continue

        if args.model_filter:
            rec_model = str(rec.get("model_used") or rec.get("modelUsed") or "").lower()
            if args.model_filter.lower() not in rec_model:
                continue

        if args.refusals_only and not rec.get("is_refusal"):
            continue

        if args.failures_only:
            # Check if answerable and EM == 0
            if not rec.get("answerable", True) or rec.get("exact_match", 1.0) != 0.0:
                continue

        filtered.append((idx, rec))

    if not filtered:
        print("No queries matched the specified filters.")
        return 0

    total_matches = len(filtered)
    limit = args.limit if args.limit > 0 else total_matches
    display_subset = filtered[:limit]

    if args.json:
        out_list = [r for _, r in display_subset]
        print(json.dumps(out_list, indent=2))
        return 0

    print(f"Loaded {len(records)} queries from: {primary_path}")
    print(f"Found {total_matches} matching queries (showing first {len(display_subset)}):\n")

    for idx, rec in display_subset:
        if args.compare_file:
            qid = rec.get("questionId") or rec.get("id") or ""
            comp_rec = compare_records_by_id.get(qid)
            if comp_rec:
                compare_records(
                    rec,
                    comp_rec,
                    label_a=primary_path.name,
                    label_b=Path(args.compare_file).name,
                    full_text=args.full,
                )
            else:
                print(f"Warning: Question ID '{qid}' not found in comparison file.")
                print_record(rec, idx, full_text=args.full)
        else:
            print_record(rec, idx, full_text=args.full)

    if total_matches > limit:
        print(f"\n... and {total_matches - limit} more matching queries. Use --limit 0 to view all.")

    return 0


if __name__ == "__main__":
    sys.exit(main())
