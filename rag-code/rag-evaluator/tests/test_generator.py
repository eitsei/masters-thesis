"""Unit tests for the RAGAS Synthetic Test Set Generator (src/generator.py)."""

from __future__ import annotations

import json
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest
from langchain_core.documents import Document
from langchain_core.messages import AIMessage

from src.generator import (
    NORDEA_LLM_CONTEXT,
    STANDARD_REFUSAL_EN,
    STANDARD_REFUSAL_FI,
    generate_nordea_dataset,
    generate_out_of_domain_samples,
    get_nordea_personas,
    load_and_deduplicate_nordea_docs,
)


def test_get_nordea_personas() -> None:
    """Verifies that the Finnish customer personas are defined with appropriate roles."""
    personas = get_nordea_personas()
    assert len(personas) == 4
    names = [p.name for p in personas]
    assert "Yrittäjä ja Holdingyhtiö-omistaja" in names
    assert "Perheen Varallisuussuunnittelija" in names
    assert "Premium-kortinhaltija ja Matkustaja" in names
    assert "Valtakirjasijoittaja" in names

    for p in personas:
        assert len(p.role_description) > 30
        assert "suomalainen" in p.role_description.lower() or "asiakas" in p.role_description.lower() or "sijoittaja" in p.role_description.lower()


def test_load_and_deduplicate_nordea_docs(tmp_path: Path) -> None:
    """Verifies frontmatter parsing, URL deduplication, and size filtering."""
    pages_dir = tmp_path / "pages"
    pages_dir.mkdir()

    # Doc 1: Version A (shorter)
    doc1_a = pages_dir / "001-doc.md"
    doc1_a.write_text(
        "---\nurl: \"https://example.com/page1\"\ntitle: \"Page 1 vA\"\ntype: \"html\"\n---\n"
        + "Tämä on ensimmäinen sivu varainhoidosta ja palveluista." * 5,
        encoding="utf-8",
    )

    # Doc 1: Version B (longer - should be retained)
    doc1_b = pages_dir / "002-doc.md"
    doc1_b.write_text(
        "---\nurl: \"https://example.com/page1\"\ntitle: \"Page 1 vB\"\ntype: \"html\"\n---\n"
        + "Tämä on ensimmäinen sivu varainhoidosta ja palveluista, jossa on pidempi kuvaus." * 10,
        encoding="utf-8",
    )

    # Doc 2: Different URL
    doc2 = pages_dir / "003-doc.md"
    doc2.write_text(
        "---\nurl: \"https://example.com/page2\"\ntitle: \"Page 2\"\ntype: \"html\"\n---\n"
        + "Tämä on toinen erillinen sivu Nordea Platinum -korteista ja eduista." * 8,
        encoding="utf-8",
    )

    # Doc 3: Too short (< 150 chars body) - should be skipped
    doc3 = pages_dir / "004-short.md"
    doc3.write_text(
        "---\nurl: \"https://example.com/page3\"\ntitle: \"Short\"\ntype: \"html\"\n---\nLyhyt teksti.",
        encoding="utf-8",
    )

    docs = load_and_deduplicate_nordea_docs(tmp_path, min_chars=150)
    assert len(docs) == 2

    urls = [d.metadata["source"] for d in docs]
    assert "https://example.com/page1" in urls
    assert "https://example.com/page2" in urls
    assert "https://example.com/page3" not in urls

    # Check that the longer version was chosen for page1
    page1_doc = next(d for d in docs if d.metadata["source"] == "https://example.com/page1")
    assert "pidempi kuvaus" in page1_doc.page_content
    assert page1_doc.metadata["title"] == "Page 1 vB"
    assert page1_doc.metadata["type"] == "html"


def test_load_and_deduplicate_max_docs(tmp_path: Path) -> None:
    """Verifies that max_docs limits the output."""
    for i in range(5):
        p = tmp_path / f"doc_{i}.md"
        p.write_text(
            f"---\nurl: \"https://example.com/{i}\"\ntitle: \"Doc {i}\"\n---\n"
            + f"Sisältöä dokumentille numero {i}..." * 15,
            encoding="utf-8",
        )

    docs = load_and_deduplicate_nordea_docs(tmp_path, min_chars=50, max_docs=3)
    assert len(docs) == 3


def test_generate_out_of_domain_samples() -> None:
    """Verifies generation of OOD refusal records with mock LLM."""
    mock_llm = MagicMock()
    mock_json_response = json.dumps([
        {"question": "Miten opintolainan valtiontakaus haetaan Kelasta?"},
        {"question": "Voinko siirtää euroja suoraan Binance-tilille verkkopankissa?"},
    ])
    mock_llm.invoke.return_value = AIMessage(content=f"```json\n{mock_json_response}\n```")

    samples = generate_out_of_domain_samples(llm=mock_llm, count=2)
    assert len(samples) == 2

    q1 = samples[0]
    assert q1["question"] == "Miten opintolainan valtiontakaus haetaan Kelasta?"
    assert q1["ground_truth"] == STANDARD_REFUSAL_FI
    assert STANDARD_REFUSAL_EN in q1["aliases"]
    assert q1["hopCount"] == 0
    assert q1["category"] == "out-of-domain"
    assert q1["synthesizer"] == "OutOfDomainNegativeSynthesizer"
    assert q1["reference_contexts"] == []
    assert q1["source_urls"] == []


def test_generate_nordea_dataset_end_to_end(tmp_path: Path) -> None:
    """Verifies complete orchestration with mocked JudgeFactory and Ragas TestsetGenerator."""
    # Create sample docs in tmp_path
    md_dir = tmp_path / "md"
    md_dir.mkdir()
    doc_path = md_dir / "sample.md"
    doc_path.write_text(
        "---\nurl: \"https://www.nordea.fi/pb\"\ntitle: \"Private Banking\"\n---\n"
        + "Tämä on Nordea Private Bankingin kattava palvelukuvaus ja sijoitusstrategia." * 10,
        encoding="utf-8",
    )

    out_file = tmp_path / "nordea_testset.jsonl"

    # Mock JudgeFactory
    mock_llm = MagicMock()
    mock_llm.invoke.return_value = AIMessage(
        content=json.dumps([{"question": "Mikä on opintolainan korkomarginaali?"}])
    )
    mock_emb = MagicMock()

    # Mock Ragas Testset
    mock_testset = MagicMock()
    mock_testset.to_list.return_value = [
        {
            "user_input": "Mitä palveluita Nordea Private Banking tarjoaa?",
            "reference": "Nordea Private Banking tarjoaa varainhoitoa ja sijoitusneuvontaa.",
            "reference_contexts": ['url: "https://www.nordea.fi/pb"\nNordea Private Banking tarjoaa...'],
            "synthesizer_name": "single_hop_specific_query_synthesizer",
        },
        {
            "user_input": "Miten holdingyhtiön osingot verotetaan sijoitustoiminnassa?",
            "reference": "Holdingyhtiön kautta osingot verotetaan yhteisöverokannan mukaisesti.",
            "reference_contexts": ['url: "https://www.nordea.fi/pb"\nHoldingyhtiö sijoittaa...'],
            "synthesizer_name": "multi_hop_specific_query_synthesizer",
        },
    ]

    mock_generator = MagicMock()
    mock_generator.llm = mock_llm
    mock_generator.generate_with_langchain_docs.return_value = mock_testset

    with patch("src.generator.JudgeFactory.create_judge", return_value=(mock_llm, mock_emb)):
        with patch("src.generator.TestsetGenerator.from_langchain", return_value=mock_generator):
            records = generate_nordea_dataset(
                md_dir=md_dir,
                output_file=out_file,
                total_size=3,  # 1 single, 1 multi, 1 OOD
                judge_model="gemini-3.8-flash",
            )

    assert len(records) == 3
    assert out_file.exists()

    # Verify that file has 3 JSON lines
    lines = out_file.read_text(encoding="utf-8").strip().splitlines()
    assert len(lines) == 3

    parsed = [json.loads(line) for line in lines]
    ids = [r["id"] for r in parsed]
    assert ids == ["nordea_q001", "nordea_q002", "nordea_q003"]

    categories = [r["category"] for r in parsed]
    assert "single-hop" in categories
    assert "multi-hop" in categories
    assert "out-of-domain" in categories

    ood_rec = next(r for r in parsed if r["category"] == "out-of-domain")
    assert ood_rec["ground_truth"] == STANDARD_REFUSAL_FI
    assert ood_rec["hopCount"] == 0

    sh_rec = next(r for r in parsed if r["category"] == "single-hop")
    assert sh_rec["hopCount"] == 1
    assert "https://www.nordea.fi/pb" in sh_rec["source_urls"]
