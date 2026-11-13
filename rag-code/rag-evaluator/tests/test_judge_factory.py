"""Unit tests for the LLM judge and embeddings factory."""

from unittest.mock import MagicMock, patch
import pytest
from langchain_openai import ChatOpenAI, OpenAIEmbeddings

from src.judge_factory import (
    JudgeFactory,
    get_judge,
    test_judge_connectivity as verify_judge_connectivity,
)


class TestJudgeFactoryCreation:
    def test_default_provider_is_vllm(self, monkeypatch):
        monkeypatch.delenv("JUDGE_PROVIDER", raising=False)
        monkeypatch.delenv("VLLM_BASE_URL", raising=False)
        monkeypatch.delenv("VLLM_EMBEDDING_BASE_URL", raising=False)
        monkeypatch.delenv("VLLM_JUDGE_MODEL", raising=False)
        monkeypatch.delenv("VLLM_EMBEDDING_MODEL", raising=False)

        llm, emb = JudgeFactory.create_judge()
        assert isinstance(llm, ChatOpenAI)
        assert llm.model_name == "qwen3.8-27b"
        assert "http://127.0.0.1:8000/v1" in str(llm.openai_api_base)
        assert isinstance(emb, OpenAIEmbeddings)
        assert emb.model == "bge-m3"
        assert "http://127.0.0.1:8001/v1" in str(emb.openai_api_base)

    def test_vllm_provider_configuration_defaults(self, monkeypatch):
        monkeypatch.delenv("VLLM_BASE_URL", raising=False)
        monkeypatch.delenv("VLLM_EMBEDDING_BASE_URL", raising=False)
        monkeypatch.delenv("VLLM_JUDGE_MODEL", raising=False)
        monkeypatch.delenv("VLLM_EMBEDDING_MODEL", raising=False)
        monkeypatch.delenv("JUDGE_MODEL", raising=False)
        monkeypatch.delenv("JUDGE_EMBEDDING_MODEL", raising=False)
        monkeypatch.setenv("JUDGE_ENABLE_THINKING", "0")

        llm, emb = JudgeFactory.create_judge(provider="vllm")

        assert isinstance(llm, ChatOpenAI)
        assert llm.model_name == "qwen3.8-27b"
        assert llm.temperature == 0.0
        assert "http://127.0.0.1:8000/v1" in str(llm.openai_api_base)
        assert isinstance(emb, OpenAIEmbeddings)
        assert emb.model == "bge-m3"
        assert "http://127.0.0.1:8001/v1" in str(emb.openai_api_base)
        assert llm.extra_body == {"chat_template_kwargs": {"enable_thinking": False}}

    def test_vllm_provider_ignores_cloud_judge_model_env(self, monkeypatch):
        # Simulate .env having Gemini settings
        monkeypatch.setenv("JUDGE_MODEL", "gemini-3.5-flash-lite")
        monkeypatch.setenv("JUDGE_EMBEDDING_MODEL", "text-embedding-004")
        monkeypatch.delenv("VLLM_JUDGE_MODEL", raising=False)
        monkeypatch.delenv("VLLM_EMBEDDING_MODEL", raising=False)

        llm, emb = JudgeFactory.create_judge(provider="vllm")
        assert llm.model_name == "qwen3.8-27b"
        assert emb.model == "bge-m3"

    def test_vllm_provider_thinking_enabled_flag(self, monkeypatch):
        monkeypatch.setenv("JUDGE_ENABLE_THINKING", "1")
        llm, _ = JudgeFactory.create_judge(provider="vllm")
        assert llm.extra_body == {"chat_template_kwargs": {"enable_thinking": True}}

    def test_vllm_provider_aliases_and_custom_env(self, monkeypatch):
        monkeypatch.setenv("VLLM_BASE_URL", "http://10.0.0.1:8000/v1")
        monkeypatch.setenv("VLLM_EMBEDDING_BASE_URL", "http://10.0.0.1:8001/v1")
        monkeypatch.setenv("VLLM_JUDGE_MODEL", "custom-judge")
        monkeypatch.setenv("VLLM_EMBEDDING_MODEL", "custom-emb")

        for alias in ("vllm", "local", "local-openai", "openai-compatible"):
            llm, emb = JudgeFactory.create_judge(provider=alias)
            assert isinstance(llm, ChatOpenAI)
            assert llm.model_name == "custom-judge"
            assert "http://10.0.0.1:8000/v1" in str(llm.openai_api_base)
            assert emb.model == "custom-emb"
            assert "http://10.0.0.1:8001/v1" in str(emb.openai_api_base)

    def test_vertex_ai_gemini_provider_configuration(self, monkeypatch):
        monkeypatch.delenv("GOOGLE_AI_API_KEY", raising=False)
        monkeypatch.delenv("GEMINI_API_KEY", raising=False)
        monkeypatch.setenv("GOOGLE_CLOUD_PROJECT_ID", "test-project")
        monkeypatch.delenv("GOOGLE_CLOUD_LOCATION", raising=False)
        monkeypatch.delenv("JUDGE_MODEL", raising=False)
        monkeypatch.delenv("JUDGE_EMBEDDING_MODEL", raising=False)

        with patch("langchain_google_vertexai.ChatVertexAI") as mock_chat, \
             patch("langchain_google_vertexai.VertexAIEmbeddings") as mock_emb:
            llm, emb = JudgeFactory.create_judge(provider="gemini")
            mock_chat.assert_called_once()
            call_kwargs = mock_chat.call_args.kwargs
            assert call_kwargs["model_name"] == "gemini-3.5-flash-lite"
            assert call_kwargs["location"] == "global"
            assert call_kwargs["project"] == "test-project"

            mock_emb.assert_called_once()
            emb_kwargs = mock_emb.call_args.kwargs
            assert emb_kwargs["location"] == "us-central1"
            assert emb_kwargs["model_name"] == "text-embedding-004"

    def test_vertex_ai_gemini_aliases(self, monkeypatch):
        monkeypatch.setenv("GOOGLE_CLOUD_PROJECT_ID", "test-project")

        with patch("langchain_google_vertexai.ChatVertexAI") as mock_chat, \
             patch("langchain_google_vertexai.VertexAIEmbeddings") as mock_emb:
            for alias in ("gemini", "google", "vertex", "vertexai", "vertex-gemini"):
                JudgeFactory.create_judge(provider=alias)
            assert mock_chat.call_count == 5
            assert mock_emb.call_count == 5

    def test_unsupported_provider_raises_error(self):
        with pytest.raises(ValueError, match="Unsupported judge provider"):
            JudgeFactory.create_judge(provider="unsupported-provider")

        with pytest.raises(ValueError, match="Unsupported judge provider"):
            JudgeFactory.create_judge(provider="claude")

        with pytest.raises(ValueError, match="Unsupported judge provider"):
            JudgeFactory.create_judge(provider="ollama")

    def test_get_judge_convenience_function(self):
        llm, emb = get_judge(provider="vllm")
        assert isinstance(llm, ChatOpenAI)
        assert isinstance(emb, OpenAIEmbeddings)


class TestJudgeConnectivity:
    def test_connectivity_success_vllm(self):
        mock_llm = MagicMock()
        mock_resp = MagicMock()
        mock_resp.content = "pong"
        mock_llm.invoke.return_value = mock_resp

        mock_emb = MagicMock()
        mock_emb.embed_query.return_value = [0.1, 0.2, 0.3]

        with patch("src.judge_factory.JudgeFactory.create_judge", return_value=(mock_llm, mock_emb)):
            ok = verify_judge_connectivity(provider="vllm")
            assert ok is True
            mock_llm.invoke.assert_called_once()
            mock_emb.embed_query.assert_called_once()

    def test_connectivity_llm_failure(self):
        mock_llm = MagicMock()
        mock_llm.invoke.side_effect = RuntimeError("LLM connection timed out")
        mock_emb = MagicMock()

        with patch("src.judge_factory.JudgeFactory.create_judge", return_value=(mock_llm, mock_emb)):
            ok = verify_judge_connectivity(provider="vllm")
            assert ok is False

    def test_connectivity_embeddings_empty_vector(self):
        mock_llm = MagicMock()
        mock_resp = MagicMock()
        mock_resp.content = "pong"
        mock_llm.invoke.return_value = mock_resp

        mock_emb = MagicMock()
        mock_emb.embed_query.return_value = []

        with patch("src.judge_factory.JudgeFactory.create_judge", return_value=(mock_llm, mock_emb)):
            ok = verify_judge_connectivity(provider="vllm")
            assert ok is False
