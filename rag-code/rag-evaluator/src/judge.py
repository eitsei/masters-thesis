"""Evaluation judge initialization and management for RAGAS evaluation."""

from __future__ import annotations

import os
import time
import warnings
from typing import Any, Tuple

# Clean terminal output: filter third-party deprecation & future warnings
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", category=UserWarning)
warnings.filterwarnings("ignore", category=DeprecationWarning)

from dotenv import load_dotenv

load_dotenv()

# Prevent RAGAS and LangChain telemetry from blocking offline / HPC runs
os.environ.setdefault("RAGAS_DO_NOT_TRACK", "true")
os.environ.setdefault("LANGCHAIN_TRACING_V2", "false")
os.environ.setdefault("LANGSMITH_TRACING", "false")

from ragas.embeddings import LangchainEmbeddingsWrapper
from ragas.llms import LangchainLLMWrapper
from src.judge_factory import JudgeFactory


def get_judge_components(
    provider: str | None = None,
    model: str | None = None,
    embedding_model: str | None = None,
) -> Tuple[Any, Any]:
    """Initializes and returns (ragas_llm, ragas_embeddings) based on provider configuration.

    Delegates to JudgeFactory to provide local vLLM (Qwen + BGE)
    or Vertex AI (Gemini).
    """
    chosen_provider = (
        provider or os.getenv("JUDGE_PROVIDER", "vllm")
    ).strip().lower()

    if chosen_provider in ("none", "off", "disabled"):
        return None, None

    raw_llm, raw_embeddings = JudgeFactory.create_judge(
        provider=chosen_provider,
        model=model,
        embedding_model=embedding_model,
    )

    llm_wrapper = LangchainLLMWrapper(raw_llm, bypass_n=True) if raw_llm is not None else None
    if llm_wrapper is not None:
        llm_wrapper.bypass_n = True
    emb_wrapper = LangchainEmbeddingsWrapper(raw_embeddings) if raw_embeddings is not None else None
    return llm_wrapper, emb_wrapper


def test_judge_connection(
    provider: str | None = None,
    model: str | None = None,
    embedding_model: str | None = None,
) -> dict[str, Any]:
    """Tests connectivity, responsiveness, and latency of both judge LLM and embeddings."""
    t0 = time.perf_counter()
    ragas_llm, ragas_emb = get_judge_components(provider, model, embedding_model)
    init_ms = (time.perf_counter() - t0) * 1000

    if ragas_llm is None:
        return {
            "status": "disabled",
            "provider": provider,
            "message": "Judge is disabled ('none').",
            "latency_ms": init_ms,
        }

    # Ping LLM
    t_llm = time.perf_counter()
    try:
        from langchain_core.messages import HumanMessage
        msg = HumanMessage(content="Respond with 'OK' if you are operational.")
        raw_llm = getattr(ragas_llm, "langchain_llm", ragas_llm)
        resp = raw_llm.invoke([msg])
        response_text = getattr(resp, "content", str(resp))
        llm_ms = (time.perf_counter() - t_llm) * 1000

        # Ping Embeddings
        embed_ms = 0.0
        emb_status = "skipped"
        if ragas_emb is not None:
            raw_emb = getattr(ragas_emb, "langchain_embeddings", ragas_emb)
            if raw_emb is not None:
                t_emb = time.perf_counter()
                vec = raw_emb.embed_query("Connectivity ping.")
                embed_ms = (time.perf_counter() - t_emb) * 1000
                emb_status = f"OK (dim={len(vec)})"

        model_name = model or getattr(raw_llm, "model_name", getattr(raw_llm, "model", "unknown"))

        return {
            "status": "success",
            "provider": provider or os.getenv("JUDGE_PROVIDER", "vllm"),
            "model": model_name,
            "response": response_text.strip(),
            "init_latency_ms": init_ms,
            "llm_latency_ms": llm_ms,
            "embedding_status": emb_status,
            "embedding_latency_ms": embed_ms,
        }
    except Exception as e:
        llm_ms = (time.perf_counter() - t_llm) * 1000
        return {
            "status": "error",
            "provider": provider or os.getenv("JUDGE_PROVIDER", "vllm"),
            "error": str(e),
            "init_latency_ms": init_ms,
            "llm_latency_ms": llm_ms,
        }
