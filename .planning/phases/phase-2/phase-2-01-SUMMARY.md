---
phase: phase-2
plan: "01"
subsystem: ingestion
tags: [apache-poi, commons-csv, xlsx, csv, nomenclature, hs-codes, registry, hierarchy]

# Dependency graph
requires:
  - phase: phase-1
    provides: HsEntry, Language domain records used as registry values
provides:
  - XlsxNomenclatureExporter (XLSX -> CSV conversion via Apache POI)
  - ExportNomenclatureCsv CLI (one-shot XLSX->CSV for build-time use)
  - CsvNomenclatureReader (UTF-8 CSV reader, commons-csv)
  - NomenclatureRegistryBuilder (builds in-memory HS registry from rows)
  - NomenclatureRegistry (immutable code->HsEntry map with level/parent resolution)
  - NomenclatureIntegrityValidator (fail-fast parent links + EU count range checks)
  - GoodsCodes (EU TARIC/CN goods code parser, HS key derivation)
affects: [phase-3, phase-4, phase-5]

# Tech tracking
tech-stack:
  added:
    - apache-poi 5.4.0 (XLSX parsing, build-time / CLI only)
    - commons-csv 1.12.0 (CSV read/write, runtime ingest)
  patterns:
    - CN 8/10-digit rows folded into HS6 bucket (description concat) for HS6-centric registry
    - XLSX never loaded on app startup — CSV-only runtime ingest (pitfall M2)
    - Fail-fast integrity validation before any indexing work
    - Registry keyed by 2/4/6-digit HS strings derived from 10-digit TARIC/CN goods codes

key-files:
  created:
    - hscode-matcher-api/src/main/java/com/geodis/hs/matcher/ingestion/GoodsCodes.java
    - hscode-matcher-api/src/main/java/com/geodis/hs/matcher/ingestion/NomenclatureRegistryBuilder.java
    - hscode-matcher-api/src/main/java/com/geodis/hs/matcher/ingestion/NomenclatureRegistry.java
    - hscode-matcher-api/src/main/java/com/geodis/hs/matcher/ingestion/NomenclatureIntegrityValidator.java
    - hscode-matcher-api/src/main/java/com/geodis/hs/matcher/ingestion/NomenclatureIngestException.java
    - hscode-matcher-api/src/main/java/com/geodis/hs/matcher/ingestion/RawNomenclatureRow.java
    - hscode-matcher-api/src/main/java/com/geodis/hs/matcher/ingestion/csv/CsvNomenclatureReader.java
    - hscode-matcher-api/src/main/java/com/geodis/hs/matcher/ingestion/csv/NomenclatureCsvFormat.java
    - hscode-matcher-api/src/main/java/com/geodis/hs/matcher/ingestion/xlsx/XlsxNomenclatureExporter.java
    - hscode-matcher-api/src/main/java/com/geodis/hs/matcher/ingestion/export/ExportNomenclatureCsv.java
    - hscode-matcher-api/src/test/java/com/geodis/hs/matcher/ingestion/GoodsCodesTest.java
    - hscode-matcher-api/src/test/java/com/geodis/hs/matcher/ingestion/NomenclatureRegistryBuilderTest.java
    - hscode-matcher-api/src/test/java/com/geodis/hs/matcher/ingestion/NomenclatureEuFilesIT.java
  modified:
    - hscode-matcher-api/pom.xml (added apache-poi, commons-csv, exec-maven-plugin)

key-decisions:
  - "CN 8/10-digit hierarchy rows are folded into the 6-digit HS bucket — API is HS6-centric, not TARIC/CN"
  - "XLSX parsing via Apache POI is CLI/build-time only; runtime ingest reads UTF-8 CSV exclusively (pitfall M2)"
  - "NomenclatureIntegrityValidator uses fail-fast with EU count ranges [90-110 chapters, 1100-1450 headings, 5000-9500 HS6] to block bad data before Phase 3 indexing"
  - "NomenclatureEuFilesIT uses assumeTrue so it skips gracefully if XLSX files are absent, but runs and validates when present"

patterns-established:
  - "GoodsCodes.hsKey(hierarchyPosition, tenDigit): canonical HS key derivation — hierarchy pos 8/10 map to pos 6 key"
  - "NomenclatureRegistryBuilder.build(): immutable Map.copyOf result — registry is read-only after construction"
  - "NomenclatureIntegrityValidator.validate(): always called after build() before any downstream consumer"

requirements-completed: []

# Metrics
duration: retrospective (already implemented)
completed: 2026-04-03
---

# Phase 2: CSV Ingestion, Registry, and Hierarchy Validation Summary

**XLSX-to-CSV pipeline with Apache POI + commons-csv producing an in-memory NomenclatureRegistry of 6,907 HS entries (98 chapters, 1,234 headings, 5,575 HS6 codes) per language, with fail-fast integrity validation as the data gate before Phase 3 indexing**

## Performance

- **Duration:** retrospective verification (~10 min)
- **Started:** 2026-04-03T13:44:14Z
- **Completed:** 2026-04-03T13:54:00Z
- **Tasks:** 2 (Phase 2 ingestion pipeline + verification)
- **Files modified:** 14 new files, 1 modified (pom.xml)

## Accomplishments

- Full XLSX-to-registry pipeline implemented: `XlsxNomenclatureExporter` reads EU CIRCABC XLSX files using Apache POI; `ExportNomenclatureCsv` CLI converts offline; `CsvNomenclatureReader` reads UTF-8 CSV at runtime
- `NomenclatureRegistryBuilder` correctly folds CN 8/10-digit rows into HS6 buckets, producing a 3-level tree (chapter/heading/subheading) keyed by 2/4/6-digit strings
- `NomenclatureIntegrityValidator` enforces parent-link completeness and EU full-export cardinality ranges; all three XLSX files (FR, EN, DE) pass validation with identical counts: 98 chapters, 1,234 headings, 5,575 HS6 entries
- `NomenclatureEuFilesIT` round-trips XLSX -> CSV -> registry and verifies spot queries (e.g. `870321` resolves to parent `8703`, which resolves to chapter `87`)

## Task Commits

1. **Phase 1 domain model** - `13326c2` (feat(phase-1): domain model and Spring Boot scaffold)
2. **Phase 2 ingestion pipeline** - `4d8ddc2` (feat(phase-2): CSV ingestion, registry, and hierarchy validation)

## Files Created/Modified

- `ingestion/GoodsCodes.java` — EU goods-code parser; HS key derivation (2/4/6 from 10-digit TARIC)
- `ingestion/NomenclatureRegistryBuilder.java` — builds immutable registry, folds CN 8/10 into HS6
- `ingestion/NomenclatureRegistry.java` — immutable map with level/parent resolution helpers
- `ingestion/NomenclatureIntegrityValidator.java` — fail-fast parent-link and count-range checks
- `ingestion/NomenclatureIngestException.java` — typed ingest failure exception
- `ingestion/RawNomenclatureRow.java` — immutable record for parsed rows
- `ingestion/csv/CsvNomenclatureReader.java` — UTF-8 CSV reader (commons-csv)
- `ingestion/csv/NomenclatureCsvFormat.java` — CSV column name constants
- `ingestion/xlsx/XlsxNomenclatureExporter.java` — Apache POI XLSX reader (CLI/build only)
- `ingestion/export/ExportNomenclatureCsv.java` — main() CLI entry point
- `pom.xml` — added apache-poi 5.4.0, commons-csv 1.12.0, exec-maven-plugin 3.5.0

## Decisions Made

- **CN 8/10 folded into HS6:** EU CIRCABC XLSX contains hierarchy positions 2/4/6/8/10. Positions 8 and 10 are CN/TARIC subdivisions; their descriptions are concatenated into the 6-digit HS bucket. This keeps the registry HS6-centric as required by the roadmap.
- **CSV-only runtime ingest:** Apache POI (`XSSFWorkbook`) is used only in CLI/test context — never on the application startup path — addressing pitfall M2.
- **Fail-fast validation before Phase 3:** `NomenclatureIntegrityValidator` with EU full-export ranges blocks bad data from reaching Lucene / embedding indexes.
- **`NomenclatureEuFilesIT` uses `assumeTrue`:** Integration test gracefully skips if XLSX files are absent (e.g. in CI without data), but validates fully when present.

## Deviations from Plan

None - plan executed exactly as written. All three success criteria from the PLAN.md are met:
1. All three languages (EN, FR, DE) ingest from CSV/XLSX into registry with no integrity failures.
2. Spot queries resolve correctly: `870321` -> parent `8703` -> parent `87`.
3. Discarded-row counts logged; final 6-digit count (5,575) is within expected ballpark for HS v1 scope.

## Issues Encountered

None. All 17 tests pass (15 unit tests via `./mvnw test` + 2 integration tests in `NomenclatureEuFilesIT`). Note: `*IT.java` classes are excluded from surefire's default pattern and must be run explicitly with `-Dtest=NomenclatureEuFilesIT` or via failsafe plugin.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Registry is the proven data gate required by ROADMAP.md before Phase 3 indexing
- `NomenclatureRegistry` exposes `entries()`, `get(code)`, `countAtLevel(level)` for Phase 3 index builders and Phase 4 hierarchy resolver
- Per-language Lucene indexes (3a) and ONNX embedding store (3b) can now be built from `HsEntry` streams from the validated registry
- No blockers

---
*Phase: phase-2*
*Completed: 2026-04-03*
