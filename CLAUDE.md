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
- **Reload (Phase 5 slice):** `POST /api/v1/admin/reload` — rebuilds Lucene indexes from current `nomenclature.csv.*` paths (swap atomically; in-flight searches finish on the old snapshot). Optional **`nomenclature.admin.reload-token`**; if set, send header **`X-Reload-Token: <value>`**. JSON: `{ "status": "RELOADED", "anyLanguageReady": true, "error": null }` or `ERROR` with message.

## Planning (GSD)

- `.planning/PROJECT.md` — product requirements
- `.planning/ROADMAP.md` — phased delivery
- `.planning/research/` — architecture, features, pitfalls
- `.planning/phases/phase-N/PLAN.md` — executable plans per phase

## Notes

- When updating nomenclature files, replace the relevant `.XLSX` file with the new export from CIRCABC. Runtime ingest uses CSV derived from these files (see roadmap Phase 2).
