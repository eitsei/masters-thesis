"""Factory for constructing LLM judges and embedding models for RAGAS evaluation.

Supports Ollama (with automatic WSL2 host / localhost discovery), Google Gemini,
and Anthropic Claude.
"""

from __future__ import annotations

import logging
import os
from typing import Any

import requests
from langchain_anthropic import ChatAnthropic
from langchain_core.embeddings import Embeddings
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_google_genai import ChatGoogleGenerativeAI, GoogleGenerativeAIEmbeddings
from langchain_openai import ChatOpenAI, OpenAIEmbeddings

logger = logging.getLogger(__name__)

DEFAULT_OLLAMA_CANDIDATES = [
    "http://172.21.64.1:11434",  # WSL2 host IP
    "http://localhost:11434",    # Standard localhost
    "http://127.0.0.1:11434",    # Loopback IP
]


def discover_ollama_endpoint(
    candidate_urls: list[str] | None = None,
    timeout: float = 1.0,
) -> str:
    """Discover an active Ollama endpoint.

    Checks candidates sequentially via GET /api/tags. If an endpoint responds
    with HTTP 200, it is returned immediately. If none respond, falls back to
    `OLLAMA_BASE_URL` env or the first candidate.
    """
    env_url = os.getenv("OLLAMA_BASE_URL")
    candidates: list[str] = []
    if env_url and env_url.strip():
        candidates.append(env_url.strip().rstrip("/"))

    if candidate_urls:
        candidates.extend([c.rstrip("/") for c in candidate_urls if c not in candidates])
    else:
        for c in DEFAULT_OLLAMA_CANDIDATES:
            if c not in candidates:
                candidates.append(c)

    for url in candidates:
        try:
            resp = requests.get(f"{url}/api/tags", timeout=timeout)
            if resp.status_code == 200:
                logger.info(f"Discovered active Ollama endpoint at: {url}")
                return url
        except Exception:
            continue

    fallback = candidates[0] if candidates else "http://172.21.64.1:11434"
    logger.debug(f"No active Ollama endpoint responded. Falling back to default: {fallback}")
    return fallback


class JudgeFactory:
    """Factory for RAG evaluation judge models and embeddings."""

    @staticmethod
    def create_judge(
        provider: str | None = None,
        model: str | None = None,
        embedding_model: str | None = None,
        base_url: str | None = None,
        temperature: float = 0.0,
        **kwargs: Any,
    ) -> tuple[BaseChatModel, Embeddings]:
        """Create an LLM judge and associated embedding model for RAGAS.

        Args:
            provider: 'ollama', 'gemini', or 'claude'. Defaults to `JUDGE_PROVIDER` env or 'ollama'.
            model: Judge model name. Defaults to `JUDGE_MODEL` env or provider default.
            embedding_model: Embedding model name. Defaults to `JUDGE_EMBEDDING_MODEL` or provider default.
            base_url: Base URL for Ollama (optional, auto-discovered if omitted).
            temperature: LLM temperature (default 0.0 for deterministic evaluation).
            **kwargs: Extra parameters passed to the model constructors.

        Returns:
            Tuple of (llm_judge, embedding_model).
        """
        selected_provider = (
            provider or os.getenv("JUDGE_PROVIDER") or "ollama"
        ).strip().lower()

        if selected_provider == "ollama":
            active_base_url = (base_url or discover_ollama_endpoint()).rstrip("/")
            judge_model = model or os.getenv("JUDGE_MODEL") or "gemma4:e2b"
            emb_model = embedding_model or os.getenv("JUDGE_EMBEDDING_MODEL") or "nomic-embed-text:latest"

            llm = ChatOpenAI(
                base_url=f"{active_base_url}/v1",
                api_key="ollama",
                model=judge_model,
                temperature=temperature,
                **kwargs,
            )
            embeddings = OpenAIEmbeddings(
                base_url=f"{active_base_url}/v1",
                api_key="ollama",
                model=emb_model,
                check_embedding_ctx_length=False,
            )
            return llm, embeddings

        elif selected_provider in ("gemini", "google"):
            judge_model = model or os.getenv("JUDGE_MODEL") or "gemini-2.5-flash"
            emb_model = (
                embedding_model
                or os.getenv("JUDGE_EMBEDDING_MODEL")
                or "models/text-embedding-004"
            )
            api_key = os.getenv("GOOGLE_AI_API_KEY") or os.getenv("GEMINI_API_KEY")

            llm_kwargs = dict(kwargs)
            llm_kwargs["model"] = judge_model
            llm_kwargs["temperature"] = temperature
            if api_key:
                llm_kwargs["google_api_key"] = api_key

            emb_kwargs: dict[str, Any] = {"model": emb_model}
            if api_key:
                emb_kwargs["google_api_key"] = api_key

            llm = ChatGoogleGenerativeAI(**llm_kwargs)
            embeddings = GoogleGenerativeAIEmbeddings(**emb_kwargs)
            return llm, embeddings

        elif selected_provider in ("claude", "anthropic"):
            judge_model = (
                model or os.getenv("JUDGE_MODEL") or "claude-3-5-sonnet-20241022"
            )
            api_key = os.getenv("ANTHROPIC_API_KEY")

            llm_kwargs = dict(kwargs)
            llm_kwargs["model"] = judge_model
            llm_kwargs["temperature"] = temperature
            if api_key:
                llm_kwargs["api_key"] = api_key

            llm = ChatAnthropic(**llm_kwargs)

            # Claude does not provide an embeddings API. Fall back to Gemini if available,
            # otherwise Ollama embeddings via discovery.
            if os.getenv("GOOGLE_AI_API_KEY") or os.getenv("GEMINI_API_KEY"):
                gemini_key = os.getenv("GOOGLE_AI_API_KEY") or os.getenv("GEMINI_API_KEY")
                embeddings = GoogleGenerativeAIEmbeddings(
                    model=embedding_model or "models/text-embedding-004",
                    google_api_key=gemini_key,
                )
            else:
                active_base_url = (base_url or discover_ollama_endpoint()).rstrip("/")
                emb_model = (
                    embedding_model
                    or os.getenv("JUDGE_EMBEDDING_MODEL")
                    or "nomic-embed-text:latest"
                )
                embeddings = OpenAIEmbeddings(
                    base_url=f"{active_base_url}/v1",
                    api_key="ollama",
                    model=emb_model,
                )
            return llm, embeddings

        else:
            raise ValueError(
                f"Unsupported judge provider: '{selected_provider}'. "
                "Supported providers are 'ollama', 'gemini', and 'claude'."
            )


def get_judge(
    provider: str | None = None,
    model: str | None = None,
    embedding_model: str | None = None,
    base_url: str | None = None,
    temperature: float = 0.0,
    **kwargs: Any,
) -> tuple[BaseChatModel, Embeddings]:
    """Convenience helper to create judge LLM and embeddings."""
    return JudgeFactory.create_judge(
        provider=provider,
        model=model,
        embedding_model=embedding_model,
        base_url=base_url,
        temperature=temperature,
        **kwargs,
    )


def test_judge_connectivity(
    provider: str | None = None,
    model: str | None = None,
    embedding_model: str | None = None,
    base_url: str | None = None,
) -> bool:
    """Test connectivity to the configured judge LLM and embeddings endpoint.

    Performs a lightweight invocation and embedding to ensure the judge
    infrastructure is responsive before starting long evaluation runs.

    Returns:
        True if both LLM and embedding tests succeed, False otherwise.
    """
    try:
        llm, embeddings = JudgeFactory.create_judge(
            provider=provider,
            model=model,
            embedding_model=embedding_model,
            base_url=base_url,
        )

        # 1. Test LLM invocation
        res = llm.invoke("Say hello in one word.")
        if not res or not getattr(res, "content", None):
            logger.error("Judge LLM invocation returned empty response.")
            return False

        # 2. Test Embedding invocation
        if embeddings is not None:
            vec = embeddings.embed_query("Connectivity check.")
            if not vec or len(vec) == 0:
                logger.error("Judge embeddings returned empty vector.")
                return False

        logger.info(f"Successfully verified judge connectivity for provider='{provider or 'default'}'.")
        return True

    except Exception as exc:
        logger.error(f"Judge connectivity test failed: {exc}", exc_info=True)
        return False


# Tell pytest not to collect this production helper function as a test
test_judge_connectivity.__test__ = False
