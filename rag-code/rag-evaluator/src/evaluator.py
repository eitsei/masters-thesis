"""RAGAS Evaluation runner and quality aggregator.

Converts JSONL query collection checkpoints into RAGAS dataset format,
computes core RAG evaluation metrics (Faithfulness, Answer Relevance,
Context Precision, Context Recall), calculates composite quality scores,
and aggregates operational metrics (latency, token usage, cache rate).
"""

from __future__ import annotations

import json
import logging
import warnings
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Sequence

import numpy as np
import pandas as pd
from langchain_core.embeddings import Embeddings
from langchain_core.language_models.chat_models import BaseChatModel
from ragas import EvaluationDataset, SingleTurnSample, evaluate
from ragas.metrics.base import Metric

# Suppress deprecation warnings on import of standard metrics
with warnings.catch_warnings():
    warnings.simplefilter("ignore", DeprecationWarning)
    from ragas.metrics import (
        answer_relevancy,
        context_precision,
        context_recall,
        faithfulness,
    )

from .judge_factory import JudgeFactory
from .metrics import compute_composite_quality

logger = logging.getLogger(__name__)

DEFAULT_METRICS: list[Metric] = [
    faithfulness,
    answer_relevancy,
    context_precision,
    context_recall,
]


def jsonl_to_ragas_dataset(records: list[dict[str, Any]]) -> EvaluationDataset:
    """Convert raw JSONL run records into a RAGAS EvaluationDataset.

    Handles single-turn samples with user_input, response, reference,
    and retrieved_contexts.
    """
    samples: list[SingleTurnSample] = []
    for rec in records:
        user_input = (
            rec.get("question")
            or rec.get("user_input")
            or rec.get("query")
            or ""
        )
        response = (
            rec.get("generatedAnswer")
            or rec.get("response")
            or rec.get("answer")
            or ""
        )
        reference = (
            rec.get("groundTruthAnswer")
            or rec.get("reference")
            or rec.get("ground_truth")
            or ""
        )

        # Extract context strings from retrievedChunks or retrieved_contexts
        raw_chunks = (
            rec.get("retrievedChunks")
            or rec.get("retrieved_contexts")
            or rec.get("contexts")
            or []
        )
        retrieved_contexts: list[str] = []
        for chunk in raw_chunks:
            if isinstance(chunk, dict):
                text = chunk.get("text") or chunk.get("snippet") or ""
            else:
                text = str(chunk)
            if text:
                retrieved_contexts.append(text)

        sample = SingleTurnSample(
            user_input=str(user_input),
            response=str(response),
            reference=str(reference) if reference else None,
            retrieved_contexts=retrieved_contexts,
        )
        samples.append(sample)

    return EvaluationDataset(samples=samples)


class RagEvaluator:
    """Orchestrates RAGAS metric evaluation and multi-dimensional analysis."""

    def __init__(
        self,
        llm: BaseChatModel | None = None,
        embeddings: Embeddings | None = None,
        metrics: Sequence[Metric] | None = None,
        judge_provider: str = "ollama",
    ) -> None:
        if llm is None or embeddings is None:
            factory_llm, factory_emb = JudgeFactory.create_judge(provider=judge_provider)
            self.llm = llm or factory_llm
            self.embeddings = embeddings or factory_emb
        else:
            self.llm = llm
            self.embeddings = embeddings

        self.metrics = list(metrics) if metrics is not None else list(DEFAULT_METRICS)

    def evaluate_records(
        self,
        records: list[dict[str, Any]],
        run_name: str = "unnamed_run",
        output_dir: str | Path = "runs",
        save_output: bool = True,
    ) -> dict[str, Any]:
        """Evaluate a list of benchmark query records with RAGAS.

        Args:
            records: List of record dictionaries from a collected benchmark run.
            run_name: Name of the benchmark run.
            output_dir: Directory where evaluation summary JSON will be stored.
            save_output: Whether to persist evaluation results to a JSON file.

        Returns:
            Dictionary containing composite quality, per-metric aggregates,
            operational metrics, and per-sample results.
        """
        if not records:
            raise ValueError("Cannot evaluate empty records list.")

        dataset = jsonl_to_ragas_dataset(records)

        # Run RAGAS evaluation
        ragas_result = evaluate(
            dataset=dataset,
            metrics=self.metrics,
            llm=self.llm,
            embeddings=self.embeddings,
            raise_exceptions=False,
            show_progress=False,
        )

        df = ragas_result.to_pandas()
        return self._compute_summary(
            records=records,
            df=df,
            run_name=run_name,
            output_dir=output_dir,
            save_output=save_output,
        )

    def evaluate_file(
        self,
        jsonl_path: str | Path,
        output_dir: str | Path | None = None,
        save_output: bool = True,
    ) -> dict[str, Any]:
        """Evaluate a benchmark run checkpoint file directly."""
        path = Path(jsonl_path)
        if not path.exists():
            raise FileNotFoundError(f"Checkpoint file does not exist: {path}")

        records: list[dict[str, Any]] = []
        with open(path, "r", encoding="utf-8") as f:
            for line in f:
                line_str = line.strip()
                if line_str:
                    try:
                        records.append(json.loads(line_str))
                    except json.JSONDecodeError:
                        continue

        if not records:
            raise ValueError(f"No valid JSON records found in {path}")

        run_name = path.stem
        target_output_dir = Path(output_dir) if output_dir else path.parent
        return self.evaluate_records(
            records=records,
            run_name=run_name,
            output_dir=target_output_dir,
            save_output=save_output,
        )

    def _compute_summary(
        self,
        records: list[dict[str, Any]],
        df: pd.DataFrame,
        run_name: str,
        output_dir: str | Path,
        save_output: bool,
    ) -> dict[str, Any]:
        # Identify metric score columns present in the DataFrame
        non_metric_inputs = {
            "user_input",
            "response",
            "reference",
            "retrieved_contexts",
            "reference_contexts",
            "rubrics",
            "persona_name",
            "query_style",
            "query_length",
        }
        metric_cols: list[str] = [
            c for c in df.columns
            if c not in non_metric_inputs and pd.api.types.is_numeric_dtype(df[c])
        ]

        # Per-sample metric values and composite score
        per_sample: list[dict[str, Any]] = []
        sample_composites: list[float] = []

        for idx, row in df.iterrows():
            rec = records[idx] if idx < len(records) else {}
            sample_scores: dict[str, float] = {}
            for col in metric_cols:
                val = row[col]
                if pd.notna(val) and isinstance(val, (int, float)):
                    sample_scores[col] = float(val)

            # Composite quality: regularized harmonic mean (Thesis Eq. 238) if core RAGAS metrics available
            has_core = any(k in sample_scores for k in ("context_precision", "context_recall", "faithfulness", "answer_relevance", "answer_relevancy"))
            if has_core:
                cp = sample_scores.get("context_precision", 0.0)
                cr = sample_scores.get("context_recall", 0.0)
                faith = sample_scores.get("faithfulness", 0.0)
                ar = sample_scores.get("answer_relevance", sample_scores.get("answer_relevancy", 0.0))
                composite = compute_composite_quality(cp, cr, faith, ar)
            elif sample_scores:
                composite = float(np.mean(list(sample_scores.values())))
            else:
                composite = 0.0

            sample_composites.append(composite)

            per_sample.append({
                "sampleIndex": int(idx),
                "questionId": rec.get("questionId") or rec.get("id"),
                "question": rec.get("question"),
                "generatedAnswer": rec.get("generatedAnswer"),
                "groundTruthAnswer": rec.get("groundTruthAnswer"),
                "scores": sample_scores,
                "compositeQuality": round(composite, 4),
            })

        # Quality aggregates
        quality_aggregates: dict[str, dict[str, float]] = {}
        quality_means: dict[str, float] = {}

        for col in metric_cols:
            series = pd.to_numeric(df[col], errors="coerce").dropna()
            if not series.empty:
                mean_val = float(series.mean())
                median_val = float(series.median())
                std_val = float(series.std()) if len(series) > 1 else 0.0
                min_val = float(series.min())
                max_val = float(series.max())
            else:
                mean_val = median_val = std_val = min_val = max_val = 0.0

            quality_means[col] = round(mean_val, 4)
            quality_aggregates[col] = {
                "mean": round(mean_val, 4),
                "median": round(median_val, 4),
                "std": round(std_val, 4),
                "min": round(min_val, 4),
                "max": round(max_val, 4),
            }

        composite_quality_mean = (
            float(np.mean(sample_composites)) if sample_composites else 0.0
        )
        quality_means["composite_quality"] = round(composite_quality_mean, 4)

        # Operational metrics aggregation (RQ1 & RQ2)
        operational_metrics = self._aggregate_operational_metrics(records)

        summary: dict[str, Any] = {
            "runName": run_name,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "totalSamples": len(records),
            "qualityScores": quality_means,
            "qualityAggregates": quality_aggregates,
            "operationalMetrics": operational_metrics,
            "perSample": per_sample,
        }

        if save_output:
            out_path = Path(output_dir) / f"{run_name}_evaluation.json"
            out_path.parent.mkdir(parents=True, exist_ok=True)
            with open(out_path, "w", encoding="utf-8") as f:
                json.dump(summary, f, indent=2, ensure_ascii=False)
            logger.info(f"Saved evaluation report to {out_path}")

        return summary

    def _aggregate_operational_metrics(
        self, records: list[dict[str, Any]]
    ) -> dict[str, Any]:
        """Aggregate latency (RQ1) and token usage / cost metrics (RQ2)."""
        latencies = [
            r["totalLatencyMs"]
            for r in records
            if "totalLatencyMs" in r and isinstance(r["totalLatencyMs"], (int, float))
        ]
        retrieval_times = [
            r["timings"]["retrievalMs"]
            for r in records
            if "timings" in r and isinstance(r["timings"], dict) and "retrievalMs" in r["timings"]
        ]
        gen_times = [
            r["timings"]["generationMs"]
            for r in records
            if "timings" in r and isinstance(r["timings"], dict) and "generationMs" in r["timings"]
        ]

        total_prompt_tokens = sum(
            r.get("tokenUsage", {}).get("promptTokens", 0) for r in records
        )
        total_completion_tokens = sum(
            r.get("tokenUsage", {}).get("completionTokens", 0) for r in records
        )
        total_cached_tokens = sum(
            r.get("tokenUsage", {}).get("cachedTokens", 0) for r in records
        )
        total_tokens = sum(
            r.get("tokenUsage", {}).get("totalTokens", 0) for r in records
        )

        evidence_found_count = sum(
            1 for r in records if r.get("supportingEvidenceFound") is True
        )

        prompt_sum = total_prompt_tokens + total_cached_tokens
        cache_hit_rate = (
            (total_cached_tokens / prompt_sum) if prompt_sum > 0 else 0.0
        )

        mean_latency = float(np.mean(latencies)) if latencies else 0.0
        p95_latency = float(np.percentile(latencies, 95)) if latencies else 0.0
        mean_retrieval = float(np.mean(retrieval_times)) if retrieval_times else 0.0
        mean_generation = float(np.mean(gen_times)) if gen_times else 0.0

        return {
            "latency": {
                "meanTotalLatencyMs": round(mean_latency, 2),
                "p95TotalLatencyMs": round(p95_latency, 2),
                "meanRetrievalMs": round(mean_retrieval, 2),
                "meanGenerationMs": round(mean_generation, 2),
            },
            "tokenUsage": {
                "totalPromptTokens": total_prompt_tokens,
                "totalCompletionTokens": total_completion_tokens,
                "totalCachedTokens": total_cached_tokens,
                "totalTokens": total_tokens,
                "cacheHitRate": round(cache_hit_rate, 4),
            },
            "supportingEvidence": {
                "evidenceFoundCount": evidence_found_count,
                "evidenceFoundRate": round(
                    evidence_found_count / len(records) if records else 0.0, 4
                ),
            },
        }


def evaluate_run(
    jsonl_path: str | Path,
    output_dir: str | Path | None = None,
    judge_provider: str = "ollama",
    llm: BaseChatModel | None = None,
    embeddings: Embeddings | None = None,
    metrics: Sequence[Metric] | None = None,
    save_output: bool = True,
) -> dict[str, Any]:
    """Convenience function to evaluate a collected JSONL run file."""
    evaluator = RagEvaluator(
        llm=llm,
        embeddings=embeddings,
        metrics=metrics,
        judge_provider=judge_provider,
    )
    return evaluator.evaluate_file(
        jsonl_path=jsonl_path,
        output_dir=output_dir,
        save_output=save_output,
    )
