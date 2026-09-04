"""Unit tests for the LLM judge and embeddings factory."""

from unittest.mock import MagicMock, patch
import pytest
from langchain_anthropic import ChatAnthropic
from langchain_google_genai import ChatGoogleGenerativeAI, GoogleGenerativeAIEmbeddings
from langchain_openai import ChatOpenAI, OpenAIEmbeddings

from src.judge_factory import (
    JudgeFactory,
    discover_ollama_endpoint,
    get_judge,
    test_judge_connectivity as verify_judge_connectivity,
)


class TestOllamaDiscovery:
    def test_discover_wsl2_when_active(self):
        def mock_get(url, timeout=1.0):
            mock_resp = MagicMock()
            if "172.21.64.1" in url:
                mock_resp.status_code = 200
                return mock_resp
            raise ConnectionError("unreachable")

        with patch("requests.get", side_effect=mock_get):
            endpoint = discover_ollama_endpoint(candidate_urls=["http://172.21.64.1:11434", "http://localhost:11434"])
            assert endpoint == "http://172.21.64.1:11434"

    def test_discover_localhost_fallback(self):
        def mock_get(url, timeout=1.0):
            mock_resp = MagicMock()
            if "localhost" in url:
                mock_resp.status_code = 200
                return mock_resp
            raise ConnectionError("unreachable")

        with patch("requests.get", side_effect=mock_get):
            endpoint = discover_ollama_endpoint(candidate_urls=["http://172.21.64.1:11434", "http://localhost:11434"])
            assert endpoint == "http://localhost:11434"

    def test_discover_fallback_when_none_respond(self, monkeypatch):
        monkeypatch.delenv("OLLAMA_BASE_URL", raising=False)
        with patch("requests.get", side_effect=ConnectionError("offline")):
            endpoint = discover_ollama_endpoint()
            assert endpoint == "http://172.21.64.1:11434"


class TestJudgeFactoryCreation:
    def test_ollama_provider_configuration(self, monkeypatch):
        monkeypatch.setenv("OLLAMA_BASE_URL", "http://127.0.0.1:11434")
        with patch("requests.get") as mock_get:
            mock_get.return_value.status_code = 200
            llm, emb = JudgeFactory.create_judge(
                provider="ollama",
                model="gemma4:e2b",
                embedding_model="nomic-embed-text:latest",
            )

        assert isinstance(llm, ChatOpenAI)
        assert llm.model_name == "gemma4:e2b"
        assert llm.temperature == 0.0
        assert "http://127.0.0.1:11434/v1" in str(llm.openai_api_base)
        assert isinstance(emb, OpenAIEmbeddings)
        assert emb.model == "nomic-embed-text:latest"

    def test_gemini_provider_configuration(self, monkeypatch):
        monkeypatch.setenv("GOOGLE_AI_API_KEY", "fake-gemini-key")
        llm, emb = JudgeFactory.create_judge(
            provider="gemini",
            model="gemini-2.5-flash",
            embedding_model="models/text-embedding-004",
        )

        assert isinstance(llm, ChatGoogleGenerativeAI)
        assert "gemini-2.5-flash" in llm.model
        assert isinstance(emb, GoogleGenerativeAIEmbeddings)
        assert emb.model == "models/text-embedding-004"

    def test_claude_provider_configuration(self, monkeypatch):
        monkeypatch.setenv("ANTHROPIC_API_KEY", "fake-anthropic-key")
        monkeypatch.delenv("GOOGLE_AI_API_KEY", raising=False)
        monkeypatch.delenv("GEMINI_API_KEY", raising=False)

        with patch("src.judge_factory.discover_ollama_endpoint", return_value="http://172.21.64.1:11434"):
            llm, emb = JudgeFactory.create_judge(
                provider="claude",
                model="claude-3-5-sonnet-20241022",
                embedding_model="nomic-embed-text:latest",
            )

        assert isinstance(llm, ChatAnthropic)
        assert llm.model == "claude-3-5-sonnet-20241022"
        assert llm.temperature == 0.0
        # Embeddings fallback to Ollama embeddings
        assert isinstance(emb, OpenAIEmbeddings)
        assert emb.model == "nomic-embed-text:latest"

    def test_unsupported_provider_raises_error(self):
        with pytest.raises(ValueError, match="Unsupported judge provider"):
            JudgeFactory.create_judge(provider="unsupported-provider")

    def test_get_judge_convenience_function(self, monkeypatch):
        monkeypatch.setenv("OLLAMA_BASE_URL", "http://127.0.0.1:11434")
        with patch("requests.get") as mock_get:
            mock_get.return_value.status_code = 200
            llm, emb = get_judge(provider="ollama")
            assert isinstance(llm, ChatOpenAI)
            assert isinstance(emb, OpenAIEmbeddings)


class TestJudgeConnectivity:
    def test_connectivity_success(self):
        mock_llm = MagicMock()
        mock_resp = MagicMock()
        mock_resp.content = "pong"
        mock_llm.invoke.return_value = mock_resp

        mock_emb = MagicMock()
        mock_emb.embed_query.return_value = [0.1, 0.2, 0.3]

        with patch("src.judge_factory.JudgeFactory.create_judge", return_value=(mock_llm, mock_emb)):
            ok = verify_judge_connectivity(provider="ollama")
            assert ok is True
            mock_llm.invoke.assert_called_once()
            mock_emb.embed_query.assert_called_once()

    def test_connectivity_llm_failure(self):
        mock_llm = MagicMock()
        mock_llm.invoke.side_effect = RuntimeError("LLM connection timed out")
        mock_emb = MagicMock()

        with patch("src.judge_factory.JudgeFactory.create_judge", return_value=(mock_llm, mock_emb)):
            ok = verify_judge_connectivity(provider="ollama")
            assert ok is False

    def test_connectivity_embeddings_empty_vector(self):
        mock_llm = MagicMock()
        mock_resp = MagicMock()
        mock_resp.content = "pong"
        mock_llm.invoke.return_value = mock_resp

        mock_emb = MagicMock()
        mock_emb.embed_query.return_value = []

        with patch("src.judge_factory.JudgeFactory.create_judge", return_value=(mock_llm, mock_emb)):
            ok = verify_judge_connectivity(provider="ollama")
            assert ok is False
