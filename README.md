# Masters Thesis & LLM_Wiki

Tämä repositorio sisältää diplomityöprosessin tutkimuksen, koodit ja itse diplomityön dokumentaation.

## 📂 Projektirakenne

*   **`LLM_wiki/`**: LLM-tutkimukseen liittyvät lähdeartikkelit (`sources/`), markdown-muotoinen wiki (`wiki/`), BibTeX-viitteet (`bib/`) sekä Java/Spring AI -testauskoodi (`code/`).
*   **`thesis/`**: Overleaf-projektin Git Subtree -alikansio, joka sisältää diplomityön LaTeX-tiedostot.
*   **`GEMINI.md`**: Työnkulun säännöt, ohjeet ja valitut tutkimuskysymykset kirjallisuuskatsauksen tekoon.

---

## 🔄 Overleaf Git Subtree -työnkulku

Diplomityön kirjoitus tapahtuu `thesis/`-kansiossa, joka on linkitetty Overleafiin käyttäen `git subtree` -mekanismia. Alla on ohjeet muutosten hallintaan ja synkronointiin.

### 1. Määritä Overleaf-etärepositorio (Tehdään vain kerran)

Jotta komentojen kirjoittaminen on helpompaa ja lyhyempää, lisää Overleaf etäosoitteeksi nimellä `overleaf` suorittamalla seuraava komento projektin juuressa:

```bash
git remote add overleaf https://git@git.overleaf.com/69e3841827002aeac06eb2e8
```

> [!NOTE]
> Kun teet haku- (pull) tai puskutoimintoja (push) Overleafiin, Git kysyy salasanaa. Käytä Overleafin luomaa henkilökohtaista Git-tokenia (esim. `olp_...`).

---

### 2. Muutosten tallentaminen paikallisesti (Commit)

Kaikki muutokset, joita teet `thesis/`-kansion tiedostoihin, tallennetaan osana tämän repositorion normaalia git-historiaa:

```bash
# 1. Lisää muuttuneet tiedostot
git add thesis/

# 2. Tee commit päärepositorioon
git commit -m "kirjoita: johdannon luonnostelua ja viitteiden lisäys"
```

---

### 3. Paikallisten muutosten puskeminen Overleaf-pilveen (Push)

Kun olet tallentanut muutokset paikallisesti ja haluat synkronoida ne Overleafin verkkoympäristöön, käytä seuraavaa komentoa:

```bash
git subtree push --prefix=thesis overleaf master
```

---

### 4. Uusien muutosten hakeminen Overleaf-pilvestä (Pull)

Jos kirjoitat työtä suoraan Overleafin nettiselaimessa, sinun on haettava uudet muutokset paikalliseen työskentely-ympäristöösi. Suorita silloin:

```bash
git subtree pull --prefix=thesis overleaf master --squash
```

> [!IMPORTANT]
> Tee aina paikallinen commit (kohta 2) ennen kuin suoritat `pull`-haun Overleafista, jotta git voi yhdistää (merge) muutokset puhtaasti.