# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Purpose

This repository stores HS (Harmonized System) customs nomenclature data files in three languages:

- `NomenclatureFR.XLSX` — French customs nomenclature
- `NomenclatureEN.XLSX` — English customs nomenclature
- `NomenclatureDE.XLSX` — German customs nomenclature

These files feed the HS code matcher API (lookups and classification).

## Data Source

Files are exported from the European Commission CIRCABC platform (circabc.europa.eu).

## Application code

- `hscode-matcher-api/` — Spring Boot 3.x / Java 17 service (HS code matcher API). Build with `./mvnw test` or `./mvnw package` from that directory.
- **Azure :** profil Spring **`azure`** (`application-azure.properties`, `SPRING_PROFILES_ACTIVE=azure`), **`Dockerfile`** et **`DEPLOY-AZURE.md`** dans `hscode-matcher-api/` ; workflow GitHub **`.github/workflows/azure-webapp-hscode-matcher-api.yml`** (secrets `AZURE_WEBAPP_NAME`, `AZURE_WEBAPP_PUBLISH_PROFILE`). CSV / ONNX / cache : variables d’environnement et montages décrits dans `DEPLOY-AZURE.md`.
- **Nomenclature CSV export (offline):** Maven needs a **`pom.xml`**. Either `cd hscode-matcher-api` first, **or** stay at repo root and pass `-f hscode-matcher-api/pom.xml`. The plugin is configured for **`-Dexec.in=...` and `-Dexec.out=...`** (not `exec.args` unless you quote a single combined property).  
  - From **`hscode-matcher-api/`:**  
    `mvn -q exec:java -Dexec.in=../NomenclatureFR.XLSX -Dexec.out=../nomenclature-fr.csv`  
  - From **repo root (`HSCODE/`):**  
    `mvn -q -f hscode-matcher-api/pom.xml exec:java -Dexec.in=NomenclatureFR.XLSX -Dexec.out=nomenclature-fr.csv`  
  (paths are relative to the shell’s current directory). Repeat for EN/DE. Do **not** use unquoted `-Dexec.args=... ...` in PowerShell (space splits the line).  
  Set `nomenclature.csv.{en,fr,de}` in `application.properties`, or use Spring profile **`dev`** (`application-dev.properties` loads `../nomenclature-*.csv`). **PowerShell:** run `mvn spring-boot:run -Pdev` from `hscode-matcher-api/` (do not use unquoted `-Dspring-boot.run.profiles=dev` — it breaks like `exec.args`). Quoted form: `mvn spring-boot:run "-Dspring-boot.run.profiles=dev"`. Runtime reads **CSV only** — no XLSX on startup.
- **Web UI (recherche / lab):** Static files under `hscode-matcher-api/src/main/resources/static/` — open **`http://localhost:<port>/`**. Panneau **Options de test** : **limite** (1–50), **fuzzy** on/off et **minFuzzyTokenLength**, **explain** (JSON rangs + RRF), **minHybridChars** serveur (vide = défaut propriété), **embedTimeoutMs**, debounce, **rrfK**, **poolMultiplier**, garde hybrid client, vue **A/B** (`hybrid=true` vs `hybrid=false` en parallèle). Préférences dans `localStorage`.
- **Search (lexical + optional hybrid):** `GET /api/v1/search?q=...&lang=FR|EN|DE&limit=...` — Query params: **`hybrid=false`** lexical seul ; **`fuzzy=false`** désactive la branche fuzzy Lucene ; **`minFuzzyTokenLength`** (2–32) ; **`explain=true`** → JSON **`explain`** (lexical / semantic / fusion RRF, plafonné) ; **`minHybridChars`** (0–256, absent → **`hs.matcher.hybrid.min-query-chars`**) : pas d’hybrid tant que `trim(q)` est plus court ; **`embedTimeoutMs`** (0–120000, absent → **`hs.matcher.embed.timeout-ms`**) : timeout embedding par requête (fallback lexical, compteur **`hs.matcher.embed`**). **`rrfK`**, **`poolMultiplier`** comme avant. Réponse : **`hybridSuppressedByRequest`**, **`effectiveRrfK`**, **`debug`** (fallback embed, timeout, seuil serveur, fuzzy off, etc.), **`explain`** si demandé. Pool max 200. **503** si langue non chargée.
- **Code lookup (Phase 5):** `GET /api/v1/codes/{code}?lang=FR|EN|DE` — registry lookup for a **2-, 4-, or 6-digit** key; non-digits in `{code}` are stripped (e.g. `0101.21` → `010121`). **400** invalid key or lang; **404** unknown code; **503** if that language is not loaded.
- **Reload (Phase 5 slice):** `POST /api/v1/admin/reload` — rebuilds Lucene indexes from current `nomenclature.csv.*` paths (swap atomically; in-flight searches finish on the old snapshot). Optional **`nomenclature.admin.reload-token`**; if set, send header **`X-Reload-Token: <value>`**. JSON: `{ "status": "RELOADED", "anyLanguageReady": true, "error": null }` or `ERROR` with message.
- **Phase 3b ONNX (optional at runtime):** Default **`hs.matcher.onnx.enabled=false`**. To load the model: set **`hs.matcher.onnx.enabled=true`** and **`hs.matcher.onnx.model-path`** to a readable path (e.g. `file:C:/path/model_qint8_avx512.onnx` or `classpath:onnx/model_qint8_avx512.onnx`). **`tokenizer.json`** is bundled under `src/main/resources/onnx/`. The **quantized ONNX** file is **not** committed (`.gitignore`); download from [Hugging Face — onnx/model_qint8_avx512.onnx](https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/tree/main/onnx) into test or main `resources/onnx/` for local runs. Integration tests **`EmbeddingEngineTest`** / **`EmbeddingSimilarityIT`** run only when that file exists on the **test** classpath. With ONNX on, **`NomenclatureIndexBundle`** builds embedding matrices at startup/reload; **`GET /api/v1/search`** uses **RRF** hybrid when both lexical and semantic indexes exist for the language.
- **Health:** `GET /actuator/health` includes **`nomenclature`** — **OUT_OF_SERVICE** if no CSV is loaded for any language (readiness-style); **UP** with details **`lexicalLanguages`**, **`hybridLanguages`**, **`onnxBeanPresent`** when data is loaded (`management.endpoint.health.show-details=when_authorized` or `always` to see details in JSON).
- **Search JSON:** **`candidatePool`**, **`effectiveRrfK`**, **`hybridSuppressedByRequest`** ; **`debug`** : `hybridSkippedShortQuery`, `embeddingFallback`, `embeddingTimedOut`, `fuzzyDisabledByRequest`, `effectiveMinFuzzyTokenLength`, `serverMinHybridQueryChars` ; **`explain`** si `explain=true`.
- **Tracing:** Servlet filter sets response header **`X-Request-Id`** (echoes inbound header or generates a UUID).
- **Metrics:** `management.endpoints.web.exposure.include` includes **`metrics`** — timers **`hs.matcher.search`**, **`hs.matcher.reload`**, counter **`hs.matcher.embed`** (`outcome=timeout|error`). Inspect via `GET /actuator/metrics/hs.matcher.embed` etc.
- **Embedding disk cache:** Set **`hs.matcher.embedding.cache-dir`** to a writable directory (e.g. `./var/embedding-cache`) to reuse float matrices across restarts when the **same CSV** is unchanged (fingerprint: absolute path, size, lastModified, row count, **`hs.matcher.embedding.cache-salt`**). **Lucene indexes are still rebuilt** from CSV each startup; only ONNX encoding is skipped on cache hit. Bump **`cache-salt`** when changing the ONNX model or embedding text strategy.

## Planning (GSD)

- `.planning/PROJECT.md` — product requirements
- `.planning/PITCH_TECHNIQUE.md` — pitch technique (valeur, stack, hybrid Lucene + ONNX)
- `.planning/ROADMAP.md` — phased delivery
- `.planning/research/` — architecture, features, pitfalls
- `.planning/phases/phase-N/PLAN.md` — executable plans per phase

## Notes

- When updating nomenclature files, replace the relevant `.XLSX` file with the new export from CIRCABC. Runtime ingest uses CSV derived from these files (see roadmap Phase 2).
