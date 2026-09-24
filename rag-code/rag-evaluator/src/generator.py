"""RAGAS Synthetic Test Set Generator for Enterprise Nordea Private Banking.

Generates a stratified 100-question benchmark dataset conforming to Thesis §4.4:
- 40% Single-Hop Factual Queries (SingleHopSpecificQuerySynthesizer)
- 40% Multi-Hop Relational Queries (MultiHopSpecificQuerySynthesizer)
- 20% Out-of-Domain Unanswerable Queries with Standardized Refusal Ground Truth:
  Primary: 'Annettu konteksti ei sisällä riittävästi tietoa kysymykseen vastaamiseksi.'
  Alias:   'The provided context does not contain sufficient information to answer this query.'
"""

from __future__ import annotations

import json
import logging
import re
from pathlib import Path
from typing import Any, Optional, Sequence

from langchain_core.documents import Document
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import HumanMessage
from rich.console import Console
from rich.table import Table

from ragas.run_config import RunConfig
from ragas.testset import TestsetGenerator
from ragas.testset.persona import Persona
from ragas.testset.synthesizers import (
    MultiHopAbstractQuerySynthesizer,
    MultiHopSpecificQuerySynthesizer,
    SingleHopSpecificQuerySynthesizer,
)
try:
    from src.judge_factory import JudgeFactory
except ImportError:
    from .judge_factory import JudgeFactory

logger = logging.getLogger(__name__)


def _patch_ragas_headline_splitter() -> None:
    """Defensively patches Ragas HeadlineSplitter.split to safely handle nodes without headlines.

    Fixes a known bug in Ragas 0.4.3 where default_transforms only extracts headlines for
    documents > 500 tokens, but HeadlineSplitter runs on all documents and raises
    ValueError("'headlines' property not found in this node") on documents <= 500 tokens.
    """
    try:
        import ragas.testset.transforms.splitters.headline as h_module

        if getattr(h_module.HeadlineSplitter, "_patched", False):
            return

        orig_split = h_module.HeadlineSplitter.split

        async def _safe_split(self, node):
            headlines = node.get_property("headlines")
            if headlines is None:
                return [node], []
            return await orig_split(self, node)

        h_module.HeadlineSplitter.split = _safe_split
        h_module.HeadlineSplitter._patched = True
        logger.debug("Successfully applied defensive patch to Ragas HeadlineSplitter")
    except Exception as e:
        logger.warning(f"Failed to patch Ragas HeadlineSplitter: {e}")


def _patch_ragas_pydantic_prompt() -> None:
    """Patches BaseModel.model_validate_json to safely unwrap markdown-fenced JSON from LLMs.

    Fixes a known issue in Ragas 0.4.3 with Gemini and other modern LLMs where output JSON
    wrapped in ```json ... ``` code blocks triggers pydantic_core.ValidationError when passed
    directly to model_validate_json without stripping fences.
    """
    try:
        from pydantic import BaseModel
        from ragas.prompt.pydantic_prompt import extract_json

        if getattr(BaseModel, "_ragas_json_patched", False):
            return

        orig_validate_json = BaseModel.model_validate_json.__func__

        def clean_json_str(text: str) -> str:
            text = text.strip()
            if "```json" in text:
                start = text.find("```json") + 7
                end = text.rfind("```")
                if end > start:
                    text = text[start:end].strip()
            elif "```" in text:
                start = text.find("```") + 3
                end = text.rfind("```")
                if end > start:
                    text = text[start:end].strip()
            return extract_json(text)

        @classmethod
        def _safe_model_validate_json(cls, json_data, *args, **kwargs):
            if isinstance(json_data, (str, bytes)):
                s = json_data if isinstance(json_data, str) else json_data.decode("utf-8")
                s = clean_json_str(s)
                json_data = s
            return orig_validate_json(cls, json_data, *args, **kwargs)

        BaseModel.model_validate_json = _safe_model_validate_json
        BaseModel._ragas_json_patched = True
        logger.debug("Successfully applied JSON markdown-unwrapping patch to BaseModel.model_validate_json")
    except Exception as e:
        logger.warning(f"Failed to patch BaseModel.model_validate_json: {e}")

STANDARD_REFUSAL_FI = "Annettu konteksti ei sisällä riittävästi tietoa kysymykseen vastaamiseksi."
STANDARD_REFUSAL_EN = "The provided context does not contain sufficient information to answer this query."

NORDEA_LLM_CONTEXT = (
    "Toimialana on Nordea Private Banking (varallisuudenhoito, sijoituspalvelut, lakipalvelut ja eksklusiiviset maksukortit). "
    "Kaikki kysymykset ja mallivastaukset (ground truth) on laadittava sujuvalla, virheettömällä ja ammattimaisella suomen kielellä. "
    "Kysymysten tulee heijastaa aitoja suomalaisten varakkaiden yksityisasiakkaiden ja yrittäjien esittämiä asiointikysymyksiä. "
    "Mallivastauksen on perustuttava täsmällisesti ja ainoastaan annettuun lähdeaineistoon ilman ulkopuolista päättelyä."
)


def get_nordea_personas() -> list[Persona]:
    """Returns domain-specific Finnish customer personas for Nordea Private Banking."""
    return [
        Persona(
            name="Yrittäjä ja Holdingyhtiö-omistaja",
            role_description=(
                "Suomalainen pk- ja kasvuyritysten omistajayrittäjä, joka hallinnoi varallisuutta holdingyhtiön kautta. "
                "Kiinnostunut yrityksen varojen sijoittamisesta, osinkoverotuksesta, pääomasijoituksista ja sukupolvenvaihdoksesta."
            ),
        ),
        Persona(
            name="Perheen Varallisuussuunnittelija",
            role_description=(
                "Suomalainen yksityishenkilö ja varainhoidon asiakas, joka haluaa turvata perheensä taloudellisen tulevaisuuden sukupolvien yli. "
                "Etsii ratkaisuja perheoikeuteen, lahjoituksiin, testamentteihin, avioehtosopimuksiin ja edunvalvontavaltakirjoihin."
            ),
        ),
        Persona(
            name="Premium-kortinhaltija ja Matkustaja",
            role_description=(
                "Paljon matkustava Private Banking -asiakas, joka hyödyntää Nordea Platinum- ja Nordea Black -korttien "
                "matkavakuutusta, Priority Pass -lentokenttäloungeja, Concierge-palvelua ja ostoturvaa."
            ),
        ),
        Persona(
            name="Valtakirjasijoittaja",
            role_description=(
                "Aktiivinen sijoitusasiakas, joka käyttää täyden valtakirjan varainhoitoa (discretionary portfolio), "
                "arvopaperiluottoa (Lombard-luotto) ja vaihtoehtoisia sijoitusrahastoja."
            ),
        ),
    ]


def load_and_deduplicate_nordea_docs(
    md_dir: Path,
    min_chars: int = 150,
    max_docs: Optional[int] = None,
) -> list[Document]:
    """Scans and deduplicates markdown documents from pages/ and pdfs/.

    Extracts YAML frontmatter (url, title, type) into Document metadata and uses
    clean markdown body as page_content.
    """
    if not md_dir.exists():
        raise FileNotFoundError(f"Nordea markdown directory not found: {md_dir}")

    # Map url -> (length, Document)
    docs_by_url: dict[str, tuple[int, Document]] = {}

    for file_path in sorted(md_dir.rglob("*.md")):
        raw_text = file_path.read_text(encoding="utf-8", errors="ignore").strip()
        if not raw_text:
            continue

        # Extract YAML frontmatter
        url = str(file_path)
        title = file_path.stem
        doc_type = "html" if "pages" in file_path.parts else "pdf"

        body = raw_text
        if raw_text.startswith("---"):
            parts = raw_text.split("---", 2)
            if len(parts) >= 3:
                header = parts[1]
                body = parts[2].strip()

                url_m = re.search(r'url:\s*["\']?([^"\'\n]+)["\']?', header)
                if url_m:
                    url = url_m.group(1).strip()

                title_m = re.search(r'title:\s*["\']?([^"\'\n]+)["\']?', header)
                if title_m:
                    title = title_m.group(1).strip()

                type_m = re.search(r'type:\s*["\']?([^"\'\n]+)["\']?', header)
                if type_m:
                    doc_type = type_m.group(1).strip()

        if len(body) < min_chars:
            continue

        # Deduplicate by url: keep the version with more text if multiple snapshots exist
        doc_obj = Document(
            page_content=body,
            metadata={
                "source": url,
                "title": title,
                "type": doc_type,
                "filename": file_path.name,
            },
        )

        if url not in docs_by_url or len(body) > docs_by_url[url][0]:
            docs_by_url[url] = (len(body), doc_obj)

    canonical_docs = [item[1] for item in docs_by_url.values()]
    if max_docs is not None and max_docs > 0:
        canonical_docs = canonical_docs[:max_docs]

    return canonical_docs


def generate_out_of_domain_samples(
    llm: BaseChatModel,
    count: int,
    console: Optional[Console] = None,
) -> list[dict[str, Any]]:
    """Generates out-of-domain unanswerable banking queries in Finnish.

    Assigns the standardized refusal ground truth string per Thesis §4.4.
    """
    if count <= 0:
        return []

    if console:
        console.print(f"[bold cyan]Generating {count} Out-of-Domain refusal queries with Gemini 3.8 Flash...[/bold cyan]")

    prompt = f"""
Sinun tehtäväsi on luoda täsmälleen {count} kappaletta realistisia suomenkielisiä asiakaspalvelukysymyksiä pankkitoiminnasta.
NÄIDEN KYSYMYSTEN ON OLTAVA TÄYSIN ULKOPUOLISIA (Out-of-Domain) Nordea Private Banking -varainhoitotoiminnalle.

OHJEET:
1. Kysymysten tulee liittyä tavalliseen vähittäispankkiin tai muuhun finanssialan toimintaan, joita EI käsitellä varakkaiden Private Banking -aineistossa:
   - Opintolainat, Kelan opintotuen valtiontakaus ja opintolainahyvitys
   - Pienet kulutusluotot, osamaksusopimukset ja pikavipit
   - Kryptovaluuttojen ostaminen, siirrot ulkomaisiin kryptopörsseihin
   - Asuntolainan valtiontakaus ensiasunnon ostajalle (ASP-tili ja ASP-lainan ehdot)
   - Fyysisten käteiskonttoreiden aukioloajat ja kolikoiden talletusautomaatit
2. Kysymysten TULEE kuulostaa aidoilta pankkiasiakkaan esittämiltä kysymyksiltä.
3. Palauta vastaus AINOASTAAN puhtaana JSON-listana muodossa:
[
  {{"question": "Ensimmäinen kysymys..."}},
  {{"question": "Toinen kysymys..."}}
]
"""
    resp = llm.invoke([HumanMessage(content=prompt)])
    content = resp.content if hasattr(resp, "content") else str(resp)

    # Extract JSON array from LLM response
    cleaned = content.strip()
    if cleaned.startswith("```"):
        cleaned = re.sub(r"^```[a-zA-Z0-9_-]*\n", "", cleaned)
        cleaned = re.sub(r"\n```$", "", cleaned).strip()

    try:
        raw_items = json.loads(cleaned)
    except Exception as e:
        logger.warning(f"Failed to parse JSON directly ({e}), extracting with regex...")
        match = re.search(r"\[\s*\{.*\}\s*\]", cleaned, re.DOTALL)
        if match:
            raw_items = json.loads(match.group(0))
        else:
            raise ValueError(f"Could not parse valid JSON list from LLM output: {cleaned[:200]}")

    results: list[dict[str, Any]] = []
    for item in raw_items[:count]:
        q = item.get("question", "").strip()
        if not q:
            continue
        results.append(
            {
                "question": q,
                "ground_truth": STANDARD_REFUSAL_FI,
                "aliases": [STANDARD_REFUSAL_EN],
                "hopCount": 0,
                "category": "out-of-domain",
                "synthesizer": "OutOfDomainNegativeSynthesizer",
                "reference_contexts": [],
                "source_urls": [],
            }
        )

    return results


def generate_nordea_dataset(
    md_dir: Path,
    output_file: Path,
    total_size: int = 100,
    judge_model: str = "gemini-3.8-flash",
    limit_docs: Optional[int] = None,
    console: Optional[Console] = None,
) -> list[dict[str, Any]]:
    """Generates the full stratified Nordea benchmark dataset (Thesis §4.4).

    Partitioning:
    - 40% single-hop factual queries
    - 40% multi-hop relational queries
    - 20% out-of-domain unanswerable queries
    """
    console = console or Console()

    num_ood = max(1, int(round(total_size * 0.20)))
    num_in_domain = total_size - num_ood
    num_single = max(1, int(round(num_in_domain * 0.50)))
    num_multi = num_in_domain - num_single

    console.print(
        f"\n[bold magenta]=== Nordea Synthetic Test Set Generation ===[/bold magenta]\n"
        f"• Target Total Size:   [bold cyan]{total_size}[/bold cyan]\n"
        f"• Single-hop (40%):    [cyan]{num_single}[/cyan]\n"
        f"• Multi-hop (40%):     [cyan]{num_multi}[/cyan]\n"
        f"• Out-of-domain (20%): [cyan]{num_ood}[/cyan]\n"
        f"• Generator Model:     [cyan]{judge_model}[/cyan] (Vertex AI `global`)\n"
        f"• Source Directory:    [cyan]{md_dir}[/cyan]\n"
        f"• Output Path:         [cyan]{output_file}[/cyan]\n"
    )

    # 1. Load and deduplicate documents
    with console.status("[bold green]Loading and deduplicating Nordea markdown documents...[/bold green]"):
        canonical_docs = load_and_deduplicate_nordea_docs(
            md_dir=md_dir,
            min_chars=150,
            max_docs=limit_docs,
        )

    console.print(
        f"[bold green]✓[/bold green] Loaded [bold]{len(canonical_docs)}[/bold] canonical unique documents "
        f"from [cyan]{md_dir}[/cyan]."
    )

    # 2. Initialize Gemini 3.8 Flash & Embeddings via JudgeFactory
    with console.status(f"[bold magenta]Initializing LLM ({judge_model}) and Vertex Embeddings...[/bold magenta]"):
        llm, emb = JudgeFactory.create_judge(provider="gemini", model=judge_model)
    console.print(f"[bold green]✓[/bold green] LLM & Embeddings initialized.")

    # 3. Generate Out-of-Domain queries (20%)
    ood_samples = generate_out_of_domain_samples(llm=llm, count=num_ood, console=console)
    console.print(f"[bold green]✓[/bold green] Generated [bold]{len(ood_samples)}[/bold] Out-of-Domain refusal queries.")

    # 4. Generate In-Domain queries (80%) via Ragas TestsetGenerator
    console.print(
        f"\n[bold cyan]Configuring RAGAS 0.4.3 TestsetGenerator for {num_in_domain} in-domain queries...[/bold cyan]"
    )
    _patch_ragas_headline_splitter()
    _patch_ragas_pydantic_prompt()

    personas = get_nordea_personas()
    generator = TestsetGenerator.from_langchain(
        llm=llm,
        embedding_model=emb,
        llm_context=NORDEA_LLM_CONTEXT,
    )
    generator.persona_list = personas

    # Setup distribution: 50% single-hop, 50% multi-hop (relational across documents)
    s_single = SingleHopSpecificQuerySynthesizer(llm=generator.llm)
    s_multi = MultiHopAbstractQuerySynthesizer(llm=generator.llm)
    distribution = [(s_single, 0.5), (s_multi, 0.5)]

    # Paced execution configuration to avoid 429 quota spikes on Vertex AI
    run_config = RunConfig(max_workers=4, max_wait=120, max_retries=15)

    console.print(
        f"[bold green]Starting RAGAS KnowledgeGraph transforms and query synthesis ({num_in_domain} queries)...[/bold green]"
    )
    testset = generator.generate_with_langchain_docs(
        documents=canonical_docs,
        testset_size=num_in_domain,
        query_distribution=distribution,
        run_config=run_config,
        raise_exceptions=True,
    )

    # 5. Format and convert Ragas Testset to uniform schema
    in_domain_samples: list[dict[str, Any]] = []
    testset_records = testset.to_list()

    for rec in testset_records:
        user_input = rec.get("user_input") or ""
        reference = rec.get("reference") or ""
        ref_contexts = rec.get("reference_contexts") or []
        synthesizer_name = rec.get("synthesizer_name") or "single_hop_specific_query_synthesizer"

        is_multi = "multi_hop" in synthesizer_name.lower()
        cat = "multi-hop" if is_multi else "single-hop"
        hops = 2 if is_multi else 1

        # Resolve source URLs from reference contexts by matching against canonical documents
        urls: list[str] = []
        for ctx in ref_contexts:
            ctx_clean = re.sub(r"^<[0-9]+-hop>\s*", "", str(ctx)).strip()
            lines = [l.strip("# ").strip() for l in ctx_clean.split("\n") if l.strip("# ").strip()]
            snippet = lines[0] if lines else ctx_clean[:60]
            matched_url = None
            if snippet:
                for doc in canonical_docs:
                    if snippet in doc.page_content:
                        matched_url = doc.metadata.get("source")
                        break
            if not matched_url:
                url_match = re.search(r'url:\s*["\']?([^"\'\n]+)["\']?', str(ctx))
                if url_match:
                    matched_url = url_match.group(1)
            if matched_url:
                urls.append(matched_url)

        in_domain_samples.append(
            {
                "question": user_input,
                "ground_truth": reference,
                "aliases": [],
                "hopCount": hops,
                "category": cat,
                "synthesizer": synthesizer_name,
                "reference_contexts": ref_contexts,
                "source_urls": list(dict.fromkeys(urls)),
            }
        )

    console.print(f"[bold green]✓[/bold green] RAGAS generated [bold]{len(in_domain_samples)}[/bold] in-domain queries.")

    # 6. Merge, interleave, and assign final IDs
    all_samples: list[dict[str, Any]] = []
    total_count = len(in_domain_samples) + len(ood_samples)

    # Interleave OOD every ~5 queries to give an even distribution during evaluation
    in_idx = 0
    ood_idx = 0
    step = max(1, total_count // (len(ood_samples) + 1))

    for i in range(1, total_count + 1):
        if ood_idx < len(ood_samples) and (i % step == 0 or in_idx >= len(in_domain_samples)):
            item = ood_samples[ood_idx]
            ood_idx += 1
        elif in_idx < len(in_domain_samples):
            item = in_domain_samples[in_idx]
            in_idx += 1
        else:
            item = ood_samples[ood_idx]
            ood_idx += 1

        item["id"] = f"nordea_q{i:03d}"
        all_samples.append(item)

    # 7. Write to JSONL
    output_file.parent.mkdir(parents=True, exist_ok=True)
    with output_file.open("w", encoding="utf-8") as f:
        for rec in all_samples:
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")

    console.print(f"\n[bold green]✓ Successfully saved {len(all_samples)} questions to:[/bold green] [cyan]{output_file}[/cyan]")

    # 8. Print Summary Table
    table = Table(title="Nordea Synthetic Benchmark Dataset Summary", show_header=True)
    table.add_column("Category", style="cyan")
    table.add_column("Hop Count", justify="right", style="magenta")
    table.add_column("Count", justify="right", style="green")
    table.add_column("Percentage", justify="right", style="yellow")
    table.add_column("Expected Refusal Ground Truth", style="white")

    c_single = sum(1 for x in all_samples if x["category"] == "single-hop")
    c_multi = sum(1 for x in all_samples if x["category"] == "multi-hop")
    c_ood = sum(1 for x in all_samples if x["category"] == "out-of-domain")
    n = len(all_samples)

    table.add_row("Single-Hop Factual", "1", str(c_single), f"{c_single/n*100:.1f}%", "Context-derived factual answer")
    table.add_row("Multi-Hop Relational", "2", str(c_multi), f"{c_multi/n*100:.1f}%", "Cross-document relational answer")
    table.add_row("Out-of-Domain Refusal", "0", str(c_ood), f"{c_ood/n*100:.1f}%", f"'{STANDARD_REFUSAL_FI}'")
    table.add_row("[bold]Total[/bold]", "--", f"[bold]{n}[/bold]", "100.0%", "--")

    console.print(table)
    return all_samples
