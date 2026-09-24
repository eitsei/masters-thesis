"""Unit tests for Nordea Personal Banking MuSiQue Dataset Builder."""

import json
from pathlib import Path
from unittest.mock import MagicMock

import pytest
from langchain_core.messages import AIMessage

from src.dataset_builder import (
    CorpusIndexer,
    CorpusParagraph,
    NordeaMusiqueDatasetBuilder,
    load_questions_from_csv,
    STANDARD_REFUSAL_FI,
    STANDARD_REFUSAL_EN,
)


@pytest.fixture
def temp_csv(tmp_path: Path) -> Path:
    csv_file = tmp_path / "questions.csv"
    csv_file.write_text(
        "question\n"
        "Miten tilaan rinnakkaiskortin lapselle?\n"
        "Miten avaan lapselle tilin?\n"
        "Miten tilaan rinnakkaiskortin lapselle?\n"  # duplicate
        "Kuinka voin sulkea alaikäisen tilin?\n",
        encoding="utf-8",
    )
    return csv_file


@pytest.fixture
def temp_corpus(tmp_path: Path) -> Path:
    corpus_dir = tmp_path / "md" / "pages"
    corpus_dir.mkdir(parents=True)

    page1 = corpus_dir / "kortit.md"
    page1.write_text(
        "---\n"
        'url: "https://www.nordea.fi/henkiloasiakkaat/kortit"\n'
        'title: "Maksukortit ja rinnakkaiskortti"\n'
        "---\n\n"
        "# Maksukortit\n\n"
        "## Rinnakkaiskortti lapselle\n\n"
        "Rinnakkaiskortin tilaaminen lapselle onnistuu helposti huoltajan verkkopankissa "
        "valitsemalla korttiasetukset ja lisäämällä alaikäisen tiedot. Kortti toimitetaan "
        "postitse kotiin kahden viikon kuluessa tilauksesta ja se aktivoidaan mobiilissa.\n\n"
        "## Kortin turvarajat\n\n"
        "Kortin turvarajaa voi muuttaa reaaliaikaisesti Nordea Mobile -sovelluksessa tai "
        "verkkopankissa kohdassa Kortit ja Turvarajat. Muutos astuu voimaan heti ilman viivettä.\n",
        encoding="utf-8",
    )

    page2 = corpus_dir / "tilit.md"
    page2.write_text(
        "---\n"
        'url: "https://www.nordea.fi/henkiloasiakkaat/tilit"\n'
        'title: "Tilit ja säästäminen"\n'
        "---\n\n"
        "# Pankkitilit\n\n"
        "## Lapsen tilin avaaminen\n\n"
        "Alaikäiselle voi avata tilin kumpikin huoltaja yhdessä sähköisesti verkossa "
        "tai asioimalla konttorissa. Tiliin voidaan liittää säästölipas ja verkkopankkitunnukset.\n",
        encoding="utf-8",
    )
    return tmp_path / "md"


def test_load_questions_from_csv(temp_csv: Path) -> None:
    questions = load_questions_from_csv(temp_csv)
    assert len(questions) == 3
    assert questions[0] == "Miten tilaan rinnakkaiskortin lapselle?"
    assert questions[1] == "Miten avaan lapselle tilin?"
    assert questions[2] == "Kuinka voin sulkea alaikäisen tilin?"


def test_corpus_indexer(temp_corpus: Path) -> None:
    indexer = CorpusIndexer(temp_corpus)
    assert len(indexer.paragraphs) >= 3

    candidates = indexer.search_candidates("Miten tilaan rinnakkaiskortin lapselle?", top_k=2)
    assert len(candidates) > 0
    top = candidates[0]
    assert "rinnakkaiskortti" in top.title.lower() or "rinnakkaiskortti" in top.paragraph_text.lower()


def test_build_musique_sample_schema(temp_corpus: Path) -> None:
    indexer = CorpusIndexer(temp_corpus)
    mock_llm = MagicMock()
    builder = NordeaMusiqueDatasetBuilder(corpus_indexer=indexer, llm=mock_llm)

    p1 = CorpusParagraph(
        doc_id="doc_001",
        title="Rinnakkaiskortti",
        paragraph_text="Rinnakkaiskortin voi tilata verkkopankista.",
    )
    p2 = CorpusParagraph(
        doc_id="doc_002",
        title="Turvarajat",
        paragraph_text="Turvarajoja voi muuttaa mobiilissa.",
    )

    sample = builder._build_musique_sample(
        sample_id="2hop__nordea_0001",
        question="Miten tilaan rinnakkaiskortin ja miten sen turvarajaa muutetaan?",
        answer="Rinnakkaiskortin voi tilata verkkopankista ja sen turvarajaa voi muuttaa mobiilipankissa.",
        aliases=["Verkkopankista tilaamalla ja mobiilissa rajaa muuttamalla."],
        supporting_paras=[p1, p2],
        candidate_paras=[p1, p2],
        decomposition=[
            {"question": "Mistä rinnakkaiskortin voi tilata?", "answer": "Verkkopankista.", "support_index": 0},
            {"question": "Miten turvarajaa voi muuttaa?", "answer": "Mobiilissa.", "support_index": 1},
        ],
        answerable=True,
        target_pool_size=5,
    )

    # Validate exact MuSiQue keys
    expected_keys = {"id", "question", "answer", "answer_aliases", "paragraphs", "question_decomposition", "answerable"}
    assert set(sample.keys()) == expected_keys
    assert sample["id"] == "2hop__nordea_0001"
    assert sample["answerable"] is True
    assert len(sample["paragraphs"]) == 5

    # Check paragraph items
    for p in sample["paragraphs"]:
        assert "idx" in p
        assert "title" in p
        assert "paragraph_text" in p
        assert "is_supporting" in p
        assert isinstance(p["is_supporting"], bool)

    # Exactly 2 supporting paragraphs
    supporting = [p for p in sample["paragraphs"] if p["is_supporting"]]
    assert len(supporting) == 2

    # Decomposition
    decomp = sample["question_decomposition"]
    assert len(decomp) == 2
    assert decomp[0]["paragraph_support_idx"] is not None
    # Remapped index must point to a supporting paragraph
    idx0 = decomp[0]["paragraph_support_idx"]
    assert sample["paragraphs"][idx0]["is_supporting"] is True


def test_out_of_domain_sample(temp_corpus: Path) -> None:
    indexer = CorpusIndexer(temp_corpus)
    mock_llm = MagicMock()
    builder = NordeaMusiqueDatasetBuilder(corpus_indexer=indexer, llm=mock_llm)

    sample = builder._build_musique_sample(
        sample_id="ood__nordea_0001",
        question="Miten S-Pankin bonus toimii?",
        answer=STANDARD_REFUSAL_FI,
        aliases=[STANDARD_REFUSAL_EN],
        supporting_paras=[],
        candidate_paras=[],
        decomposition=[],
        answerable=False,
        target_pool_size=5,
    )

    assert sample["answerable"] is False
    assert sample["answer"] == STANDARD_REFUSAL_FI
    assert len(sample["question_decomposition"]) == 0
    # No supporting paragraphs in OOD
    assert all(not p["is_supporting"] for p in sample["paragraphs"])
