# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Purpose

This repository stores HS (Harmonized System) customs nomenclature data files in three languages:

- `NomenclatureFR.XLSX` — French customs nomenclature
- `NomenclatureEN.XLSX` — English customs nomenclature
- `NomenclatureDE.XLSX` — German customs nomenclature

These files are used by GEODIS for HS code lookups and classification.

## Data Source

Files are exported from the European Commission CIRCABC platform (circabc.europa.eu).

## Application code

- `hscode-matcher-api/` — Spring Boot 3.x / Java 17 service (HS code matcher API). Build with `./mvnw test` or `./mvnw package` from that directory.
- **Nomenclature CSV export (offline):** Maven needs a **`pom.xml`**. Either `cd hscode-matcher-api` first, **or** stay at repo root and pass `-f hscode-matcher-api/pom.xml`. The plugin is configured for **`-Dexec.in=...` and `-Dexec.out=...`** (not `exec.args` unless you quote a single combined property).  
  - From **`hscode-matcher-api/`:**  
    `mvn -q exec:java -Dexec.in=../NomenclatureFR.XLSX -Dexec.out=../nomenclature-fr.csv`  
  - From **repo root (`HSCODE/`):**  
    `mvn -q -f hscode-matcher-api/pom.xml exec:java -Dexec.in=NomenclatureFR.XLSX -Dexec.out=nomenclature-fr.csv`  
  (paths are relative to the shell’s current directory). Repeat for EN/DE. Do **not** use unquoted `-Dexec.args=... ...` in PowerShell (space splits the line).  
  Set `nomenclature.csv.{en,fr,de}` in `application.properties`, or use Spring profile **`dev`** (`application-dev.properties` loads `../nomenclature-*.csv`). **PowerShell:** run `mvn spring-boot:run -Pdev` from `hscode-matcher-api/` (do not use unquoted `-Dspring-boot.run.profiles=dev` — it breaks like `exec.args`). Quoted form: `mvn spring-boot:run "-Dspring-boot.run.profiles=dev"`. Runtime reads **CSV only** — no XLSX on startup.
- **Search (Phase 3a lexical + hierarchy):** `GET /api/v1/search?q=...&lang=FR|EN|DE&limit=10` — Lucene BM25 (`SimpleQueryParser`) **OR** per-token `FuzzyQuery` (boosted). Response includes **`fuzzyEnabled`**, **`fuzzyTerms`**, and **`hierarchy`** per hit. **503** if that language has no CSV loaded.
- **Bulk HS chapter (level 2) from descriptions:** `POST /api/v1/bulk/chapter-classify` — **JSON:** `Content-Type: application/json` with body `{ "lang": "FR", "items": [ { "id": "…", "description": "…" } ], "searchLimit": 15 }` (optional tuning: same optional fields as search: `fuzzy`, `bm25`, …). Max **5000** items. **Parallel chunks:** optional **`X-Bulk-Run-Id`** header or **`runId`** query (UUID); same value across workers. Query **`chunkIndex`** / **`chunkTotal`** (0-based index, default `0`/`1`); **`rowIndexOffset`** adds a base to each line’s persisted `row_index` (for global ordering). **`POST /api/v1/bulk/chapter-classify/runs`** returns `{ "runId": "<uuid>" }` for convenience (no DB write until classify). **Postgres persistence (optional):** when **`bulk.persistence.enabled=true`**, set **`bulk.persistence.url`** (JDBC, e.g. Supabase), **`bulk.persistence.username`**, **`bulk.persistence.password`**; Flyway applies [`db/migration`](hscode-matcher-api/src/main/resources/db/migration) on startup; each classified row is upserted into **`bulk_run_item`** (`UNIQUE(run_id, row_index)`); when **`chunkTotal`** chunks have reported **DONE**, **`bulk_run.status`** becomes **`DONE`**. Response: `runId`, `tuning`, per-line **`chapterLucene`**, **`confidenceLucene`** (0–1, share of top-3 score mass), **`top3Lucene`**, **`ambiguousLucene`**, **`latencyMsLucene`**, plus optional **LLM refinement** when **`bulk.llm.enabled=true`**: **`chapterLlm`**, **`confidenceLlm`**, **`ambiguousLlm`**, **`errorLlm`**, **`rationaleLlm`**, **`latencyMsLlm`**, **`agreeChapter`** (vs Lucene top chapter). The model receives the description, language, and **retrieval candidates** (up to five: Lucene-ranked chapters, optionally merged with semantic neighbors via RRF when **`bulk.embedding.enabled=true`**; see below). OpenAI-compatible `POST {bulk.llm.base-url}{bulk.llm.chat-path}`. Use **`bulk.llm.only-when-ambiguous=true`** to call the LLM only on ambiguous Lucene rows. **`outputFile`** is the absolute path of the written JSON when **`bulk.chapter-classify.output-dir`** is set. **CSV (MyGEODIS-style):** same URL with **`multipart/form-data`**, part name **`file`**; separator **`;`**; requires a **Product Description** column (optional **Quotation ID**); same **`runId`** / chunk query params and **`X-Bulk-Run-Id`** as JSON. Returns **`text/csv`** with original columns plus `chapter_lucene`, …, `error_llm`, `rationale_llm`, `latency_ms_llm`, `run_id`, etc.; response header **`X-Bulk-Output-File`** repeats the absolute path when the same **`bulk.chapter-classify.output-dir`** property is set (files `chapter-classify-<runId>.csv` / `.json`). Profile **`dev`** sets `bulk.chapter-classify.output-dir=../rest-client/out` (from `hscode-matcher-api/` cwd). **503** if the requested language index is not loaded.
- **Bulk chapter embeddings (optional, cascade):** when **`bulk.embedding.enabled=true`**, the API builds one **text portrait per HS chapter** from the loaded nomenclature (chapter title plus sample subheading lines), calls an embeddings endpoint, and stores **L2-normalized vectors per language**. Each classify request embeds the product description, ranks chapters by **cosine similarity**, and **fuses** that list with Lucene’s ranked chapters using **reciprocal rank fusion** (`bulk.embedding.rrf-k`, `bulk.embedding.lexical-rank-pool`, `bulk.embedding.semantic-pool`). The fused shortlist (**`bulk.embedding.refinement-candidate-count`**, default 5) is what the **LLM** sees as candidates; **`chapterLucene`**, **`confidenceLucene`**, and **`top3Lucene`** remain **purely lexical**. **`bulk.embedding.style`**: `ollama` uses `POST {bulk.embedding.base-url}/api/embeddings` (e.g. `nomic-embed-text`); `openai` uses `POST …/v1/embeddings`. Optional **`bulk.embedding.api-key`** (Bearer). **`bulk.embedding.eager-init=true`** warms the index at startup after CSV load; otherwise the **first** bulk/classify use per language triggers one HTTP call per chapter (can take a while). **`POST /api/v1/admin/reload`** clears the embedding index so vectors match the reloaded CSV. See keys in [`hscode-matcher-api/src/main/resources/application.properties`](hscode-matcher-api/src/main/resources/application.properties) (`bulk.embedding.*`).
- **Code lookup (Phase 5):** `GET /api/v1/codes/{code}?lang=FR|EN|DE` — registry lookup for a **2-, 4-, 6-, 8-, or 10-digit** key; non-digits in `{code}` are stripped (e.g. `0101.21` → `010121`, `2204.29.86.10` → `2204298610`). **400** invalid key or lang; **404** unknown code; **503** if that language is not loaded.
- **Reload (Phase 5 slice):** `POST /api/v1/admin/reload` — rebuilds Lucene indexes from current `nomenclature.csv.*` paths (swap atomically; in-flight searches finish on the old snapshot). Optional **`nomenclature.admin.reload-token`**; if set, send header **`X-Reload-Token: <value>`**. JSON: `{ "status": "RELOADED", "anyLanguageReady": true, "error": null }` or `ERROR` with message.

## Bulk chapter CSV — usage MyGEODIS (opérationnel)

Exports type MyGEODIS : **UTF-8**, séparateur **`;`**, en-tête avec au minimum une colonne **Product Description** (souvent aussi **Quotation ID**). Même endpoint que le bulk JSON : **`POST /api/v1/bulk/chapter-classify`** en **`multipart/form-data`**, nom de part **`file`**. Query **`lang=FR|EN|DE`** doit correspondre à une nomenclature chargée (**503** sinon).

- **Exemple dans ce dépôt :** [`Extraction des données MyGEODIS V1-1.csv`](Extraction%20des%20donn%C3%A9es%20MyGEODIS%20V1-1.csv) (~4389 lignes de données + en-tête), sous la limite **5000** lignes par requête.
- **Sortie :** corps de réponse = CSV (colonnes d’origine + `chapter_lucene`, `confidence_lucene`, champs LLM si activés, `run_id`, etc.). Si **`bulk.chapter-classify.output-dir`** est défini (profil **`dev`** : `../rest-client/out` depuis le cwd **`hscode-matcher-api/`**), le serveur écrit aussi `chapter-classify-<runId>.csv` (et JSON pour le flux JSON) ; la réponse HTTP inclut **`X-Bulk-Output-File`** avec le chemin absolu.
- **Bash (depuis le répertoire `hscode-matcher-api/`, API déjà démarrée, ex. `mvn spring-boot:run -Pdev`) :**

  `curl -sS -X POST "http://localhost:8080/api/v1/bulk/chapter-classify?lang=FR" -F "file=@../Extraction des données MyGEODIS V1-1.csv;type=text/csv" -o "../rest-client/out/mygeodis-classified.csv"`

- **PowerShell :** les chemins avec espaces et `-F` sont pénibles ; utiliser **Git Bash** pour la commande ci-dessus, ou copier le CSV vers un chemin sans espaces, ou invoquer `curl.exe` avec quoting adapté (éviter `-F` non quoté qui casse sur les espaces).
- **Gros fichiers, latence :** une passe **Lucene seule** (sans LLM ni embeddings) termine en ordre de **minutes** sur ~4k lignes. Pour cela, démarrer avec par exemple  
  `-Dspring-boot.run.arguments=--bulk.llm.enabled=false --bulk.embedding.enabled=false`  
  (les propriétés du profil `dev` peuvent ainsi être surchargées). Avec **`bulk.llm.enabled=true`** (et éventuellement **`bulk.embedding.enabled=true`**), compter un temps **beaucoup plus long** ; **`bulk.llm.only-when-ambiguous=true`** réduit les appels LLM.
- **Plus de 5000 lignes :** découper le CSV et enchaîner des requêtes avec le même **`runId`** (header **`X-Bulk-Run-Id`** ou query **`runId`**), **`chunkIndex`** / **`chunkTotal`**, et **`rowIndexOffset`** pour la persistance optionnelle en base.
- **E2E REST Client :** scénarios ordonnés (health, search, JSON, CSV fixture, `runId`) dans [`rest-client/bulk-chapter-classify.http`](rest-client/bulk-chapter-classify.http). **`{{baseUrl}}`** y est défini par défaut via **`@baseUrl`** (aucun environnement requis). Sous VS Code / Cursor avec **humao.rest-client**, les environnements **`local`** / **`local-18080`** sont aussi dans [`.vscode/settings.json`](.vscode/settings.json) (`rest-client.environmentVariables`) ; **JetBrains / Visual Studio** peuvent utiliser [`rest-client/http-client.env.json`](rest-client/http-client.env.json). Fixture CSV : [`rest-client/fixtures/bulk-chapter-sample.csv`](rest-client/fixtures/bulk-chapter-sample.csv).

## Planning (GSD)

- `.planning/PROJECT.md` — product requirements
- `.planning/ROADMAP.md` — phased delivery
- `.planning/research/` — architecture, features, pitfalls
- `.planning/phases/phase-N/PLAN.md` — executable plans per phase

## Notes

- When updating nomenclature files, replace the relevant `.XLSX` file with the new export from CIRCABC. Runtime ingest uses CSV derived from these files (see roadmap Phase 2).
