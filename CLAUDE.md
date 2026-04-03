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
- **Nomenclature CSV export (offline):** from `hscode-matcher-api/`, run  
  `mvn -q exec:java -Dexec.args="../NomenclatureEN.XLSX target/nomenclature-en.csv"`  
  (adjust paths). Runtime ingest reads UTF-8 CSV only — do not load XLSX on application startup.

## Planning (GSD)

- `.planning/PROJECT.md` — product requirements
- `.planning/ROADMAP.md` — phased delivery
- `.planning/research/` — architecture, features, pitfalls
- `.planning/phases/phase-N/PLAN.md` — executable plans per phase

## Notes

- When updating nomenclature files, replace the relevant `.XLSX` file with the new export from CIRCABC. Runtime ingest uses CSV derived from these files (see roadmap Phase 2).
