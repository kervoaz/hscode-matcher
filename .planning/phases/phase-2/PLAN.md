# Phase 2 — CSV ingestion, registry, hierarchy validation

## Outcome (done)

- **XLSX layout (EU CIRCABC):** columns Goods code, …, Language, **Hier. Pos.**, Indent, Description. Goods code is a 10-digit TARIC/CN base plus suffix (e.g. `0101210000 80`). Hierarchy positions 2 / 4 / 6 / 8 / 10.
- **HS v1 model:** Registry keys are **2 / 4 / 6 digit** strings derived from the leading digits of the 10-digit code. Rows at **8 or 10** contribute **description text only** to the **6-digit** bucket (CN subdivisions folded for HS6-centric search).
- **Implementation:** `XlsxNomenclatureExporter` + `ExportNomenclatureCsv` CLI; `CsvNomenclatureReader`; `NomenclatureRegistryBuilder`; `NomenclatureIntegrityValidator` with EU full-export count ranges.
- **Tests:** unit tests + `NomenclatureEuFilesIT` against repo `Nomenclature{EN,FR,DE}.XLSX`.

## Verification

```bash
cd hscode-matcher-api && ./mvnw test
```

## References

- `.planning/research/PITFALLS.md` — C3, M5, M2, m3
- `.planning/ROADMAP.md` — Phase 2 goals
