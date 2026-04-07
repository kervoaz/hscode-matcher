# HS Code Matcher API

## What This Is

A Java Spring Boot REST API that maps free-text user input to HS (Harmonized System) customs classification codes. It is customer-facing — end users describe their goods in plain language (in French, English, or German) and receive the correct HS code, even when the input is imprecise or informal.

## Core Value

A user can type any plain-language description of goods and reliably get the right HS code — regardless of typos, informal vocabulary, or level of specificity.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] User can submit a plain-text query in FR, EN, or DE and receive matching HS codes
- [ ] Fuzzy matching tolerates typos and spelling errors
- [ ] Semantic matching handles synonyms and conceptually related terms (vehicle → car, bus, truck)
- [ ] Type/subtype matching resolves specificity gaps (dog → animal, apple → fruit)
- [ ] Results are hierarchical: best match shown with its parent chapter and sibling/child subheadings
- [ ] Each result includes a relevance/confidence score
- [ ] HS nomenclature data can be reloaded at runtime without restarting the service
- [ ] API supports all three nomenclature languages: FR, EN, DE
- [ ] Query language can be specified per request (default: auto-detect or FR)

### Out of Scope

- Admin or management UI — API only
- User accounts or search history — stateless API
- TARIC / national 8-10 digit extensions — HS 6-digit level only for v1
- Real-time external API calls for semantic matching — must work standalone

## Context

- HS nomenclature files are already in the repo as `.XLSX` files (FR, EN, DE), sourced from the EU CIRCABC platform. They will be converted to CSV for ingestion.
- HS codes are hierarchical: 2-digit chapters → 4-digit headings → 6-digit subheadings. The hierarchy is encoded in the code itself.
- The matching challenge has three distinct layers:
  1. **Lexical** (typos) — fuzzy string matching, Apache Lucene
  2. **Semantic** (synonyms, related concepts) — local multilingual embeddings (e.g. `paraphrase-multilingual-MiniLM-L12-v2` via ONNX), no external API dependency
  3. **Taxonomic** (type/subtype) — resolved via HS hierarchy traversal and embedding proximity
- Standalone deployment: no Elasticsearch or external vector DB. Index lives in-memory or on local disk, rebuilt from CSV on startup or via reload endpoint.

## Constraints

- **Stack**: Java 17+, Spring Boot 3.x — chosen by team
- **Deployment**: Standalone JAR, no external infrastructure dependencies
- **Offline**: Semantic model must run locally (ONNX or Ollama) — no OpenAI/external API calls at query time
- **Data format**: Source files are XLSX (FR/EN/DE), converted to CSV at ingest time

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Hybrid search (Lucene + embeddings) | Fuzzy alone misses synonyms; embeddings alone miss typos — hybrid covers all three matching modes | — Pending |
| Local ONNX embedding model | Standalone requirement rules out external APIs; multilingual MiniLM is fast, small, and covers FR/EN/DE | — Pending |
| In-memory index with reload endpoint | Data changes occasionally; restart is unacceptable; full rebuild on reload is fast at this scale | — Pending |

---
*Last updated: 2026-04-03 after initialization*
