# Feature Landscape: HS Code Matcher API

**Domain:** Customs classification — free-text to HS code search API
**Researched:** 2026-04-03
**Confidence note:** Web search and WebFetch were unavailable. All findings are based on training-data
knowledge of the HS code domain (WCO nomenclature, EU TARIC, UK Trade Tariff API, Avalara,
Zonos, Panjiva, and comparable commercial/open-source tools). Confidence is annotated per section.

---

## Table Stakes

Features users expect as baseline. Missing any of these causes abandonment or escalations.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Free-text query endpoint (`POST /search?q=...`) | Primary use case — users type goods descriptions in shipping forms | Low | Single endpoint with `q` param and optional `lang` param |
| Return top-N ranked results | Users expect a ranked list, not a flat dump | Low | Default N=5–10; configurable via `limit` param |
| HS code in result (6-digit) | The actual output users need | Low | Format: `XXXXXX` padded to 6 digits, not integer |
| Human-readable description alongside code | Code alone is meaningless to non-experts | Low | The nomenclature description from the XLSX source |
| Relevance/confidence score per result | Users need to know how certain the match is | Low | Float 0.0–1.0 or percentage; must be clearly documented as approximate |
| Fuzzy tolerance for typos | Shipping form inputs are error-prone; "aluminuim", "computor" are real inputs | Medium | Lucene FuzzyQuery / edit-distance 1-2 on tokenized terms |
| Language parameter on query | FR/EN/DE target market — same service, three nomenclatures | Low | `lang=fr|en|de`; default auto-detect or FR per PROJECT.md |
| HTTP JSON response format | Standard REST contract | Low | `application/json`; document the schema once and keep it stable |
| Meaningful HTTP status codes | 400 for bad input, 404 for no results, 500 for internal errors | Low | No results should return 200 + empty array, not 404 |
| Empty-result handling | Query returns nothing — must not 500 | Low | Return `{ "results": [], "query": "...", "matched": 0 }` |
| Basic hierarchy in response (chapter + heading) | Users need context to validate a result; "is this the right chapter?" | Medium | Include `chapter` (2-digit) and `heading` (4-digit) parent in each result |
| Health / liveness endpoint | Required for any production deployment | Low | `GET /health` → `{ "status": "UP" }` |

**Confidence:** HIGH — these are universal patterns in every search API and specifically observed
in UK Trade Tariff API, EU TARIC search, and commercial HS classification tools.

---

## Differentiators

Features that separate good implementations from minimal ones. Users notice and value these.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Semantic synonym matching | "vehicle" matches "automobile", "car", "truck" — not just exact tokens | High | Multilingual embeddings (MiniLM); already in PROJECT.md as a requirement |
| Type/subtype bridging | "dog" → animal → live animals chapter; handles under-specification gracefully | High | HS hierarchy traversal + embedding proximity; hardest matching mode |
| Hybrid scoring explanation | Tell the user *why* a result matched (lexical vs semantic vs hierarchy) | Medium | Add a `match_type` enum: `LEXICAL`, `SEMANTIC`, `TAXONOMIC`, `HYBRID` |
| Chapter/heading browse endpoint | `GET /chapters`, `GET /chapters/{chapter}/headings` — supports drill-down UX | Low | Static hierarchy; useful for form builders who want a picker alongside free text |
| Code lookup endpoint | `GET /codes/{hscode}` — fetch description + hierarchy for a known code | Low | Covers validation use case: "is this code valid?" |
| Auto-language detection | Detect FR/EN/DE from query text; avoids requiring callers to specify lang | Medium | fastText `lid.176.ftz` or `langdetect`; model is tiny (125KB compressed) |
| Multilingual cross-language search | Query in FR, match German nomenclature entries too | High | Requires embedding-space alignment across languages; MiniLM multilingual handles this |
| Result count + matched_count in response | `{ "total_candidates": 127, "returned": 5 }` — diagnostic transparency | Low | Cheap to add; helps callers tune their UI |
| Structured input option | Accept `{ "product_name": "...", "material": "...", "use_case": "..." }` | Medium | Concatenates fields server-side; same search pipeline; helps callers with structured forms |
| Reload endpoint without restart | `POST /admin/reload` — reloads nomenclature data from CSV in-place | Medium | Already in PROJECT.md; differentiator vs. all-or-nothing redeploys |
| Pagination on results | `offset` + `limit` for callers who want to show more candidates | Low | Rarely needed for classification (top 5 is enough), but cheap to implement |
| Request ID / trace header | `X-Request-Id` echoed back; aids debugging in shipping platform integrations | Low | Generate UUID if not provided by caller |
| Query normalization details | Return the normalized/stemmed query actually used — e.g. `"computors" → "computer"` | Low | Diagnostic; helps users understand why they got a given result |

**Confidence:** HIGH for structural features (browse, lookup, pagination, health).
MEDIUM for semantic/hybrid features — patterns observed in Avalara HS, UK Trade Tariff, and
academic NLP-for-customs literature; exact implementation varies.

---

## Anti-Features

Things to deliberately NOT build for v1. These consume disproportionate effort relative to value,
or actively harm the core use case.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| TARIC / CN 8–10 digit extension | EU Combined Nomenclature adds ~15,000 additional codes; data source is separate; legal classification risk is high | Stay at HS 6-digit per PROJECT.md; add a note in API docs that national extensions are out of scope |
| Country-specific duty/tariff data | Rates change constantly; regulatory liability; requires real-time data feeds | Out of scope; refer callers to TARIC Online, WTO Tariff Download Facility |
| User authentication / API keys for v1 | Adds infrastructure (OAuth, key store) before the core matching is validated | Use network-level access control (VPN, IP whitelist) if needed; add auth in a later milestone |
| Audit log / search history persistence | Stateless API is a PROJECT.md constraint; persistence adds DB dependency | Log to stdout/structured logs; ship to external log aggregator if needed |
| Admin/management UI | PROJECT.md explicit out-of-scope | API only; the reload endpoint covers the operator need |
| Spell-correction suggestions ("Did you mean?") | Requires separate suggestion pipeline; fuzzy matching already absorbs most typos | Let fuzzy matching do the work; don't add a second correction loop |
| Classification "certainty guarantees" | HS classification is a legal determination; software cannot guarantee correctness | Add disclaimer in API docs; expose confidence score so callers can set their own threshold |
| Streaming / WebSocket responses | Results fit in a single JSON response (< 2KB typical); streaming is premature complexity | Standard synchronous HTTP JSON |
| Batch classification endpoint (v1) | Bulk import use case is different from form-entry use case; complicates timeout/error model | Add in v2 if shipping platform needs it; v1 is single-query |
| Caching layer (Redis/Memcached) | At this query volume and index size, in-JVM caching (Caffeine) is sufficient | Use `@Cacheable` with Caffeine if cache is needed; no external infrastructure |
| OpenAI/external LLM calls at query time | PROJECT.md explicit constraint; adds latency, cost, and external dependency | Local ONNX model only |
| Returning chapter notes / legal text | WCO chapter notes are copyright-protected; legal text is verbose and rarely useful to end users | Return code + description only; link to official source in docs |

**Confidence:** HIGH — these anti-features were derived directly from PROJECT.md constraints and
from observed failure modes in similar domain projects (scope creep into trade compliance).

---

## Feature Dependencies

```
Language auto-detect
  └── depends on: langdetect/fastText model bundled at startup

Semantic matching (synonym/taxonomic)
  └── depends on: ONNX embedding model loaded at startup
  └── depends on: Nomenclature CSV ingested into vector index
  └── enables: Cross-language search (multilingual embeddings cover FR/EN/DE jointly)

Fuzzy matching (lexical)
  └── depends on: Lucene index built from nomenclature CSV
  └── independent of: embedding model

Hybrid scoring
  └── depends on: Both Lucene index AND vector index
  └── produces: match_type annotation (LEXICAL | SEMANTIC | HYBRID)

Hierarchy context in results (chapter + heading)
  └── depends on: HS code structure parsed at ingest time (code itself encodes hierarchy)
  └── no additional data required

Chapter/heading browse endpoints
  └── depends on: Nomenclature CSV ingested
  └── independent of: embedding model (metadata only)

Code lookup endpoint (`GET /codes/{hscode}`)
  └── depends on: Lucene or in-memory map of codes → descriptions
  └── independent of: embedding model

Reload endpoint (`POST /admin/reload`)
  └── depends on: Both indexes (Lucene + vector) support hot-swap
  └── must be atomic: serve old index until new one is fully built

Structured input option
  └── depends on: Free-text search endpoint working correctly (input is concatenated before search)
```

---

## MVP Recommendation

For the first shippable version that validates the core value proposition:

**Must have (MVP):**
1. `POST /search` — free-text query, `lang` param, top-5 results with code + description + score + chapter/heading context
2. Fuzzy (lexical) matching via Lucene — handles typos
3. Semantic matching via ONNX MiniLM — handles synonyms
4. `match_type` annotation on each result — transparency
5. `GET /health` — liveness
6. `POST /admin/reload` — operator need, low risk to delay but cheap to include early

**Defer to v2:**
- `GET /chapters` browse endpoint — useful but not core to form-entry use case
- `GET /codes/{hscode}` lookup — validation use case; callers can validate against their own data for now
- Structured input (`product_name`, `material`, etc.) — callers can concatenate fields client-side for v1
- Auto-language detection — force callers to pass `lang` explicitly for v1; reduces a failure mode
- Pagination — top-5 is sufficient for form-entry; add when a caller asks for more
- Batch endpoint — different use case entirely

**Rationale for ordering:**
The search quality (fuzzy + semantic) is the entire value proposition. Everything else is
scaffolding. Ship the core search pipeline first, prove it works on real shipping-form inputs
before adding browse, lookup, or structured input.

---

## Multilingual Search: How It Works in Practice

**Question from scope:** How do similar tools handle FR/EN/DE search?

**Pattern observed in the domain (MEDIUM confidence — training data):**

1. **Language-per-index:** Simple implementations maintain separate Lucene indexes per language.
   Query is routed to the correct index via `lang` param. No cross-language search. Fast, simple,
   but cannot match a French query against an English-trained model.

2. **Multilingual embedding space (recommended for this project):** Tools like Avalara's AI
   classifier and EU TARIC use a shared multilingual embedding space. The MiniLM
   `paraphrase-multilingual-MiniLM-L12-v2` model maps FR/EN/DE text into the same vector space,
   so semantic matching works across languages. A French query for "voiture" will match the English
   nomenclature entry for "motor car" because both map to nearby vectors.

3. **Language detection before routing:** A lightweight detector (fastText lid.176.ftz) classifies
   the query language in <1ms. If the user passes `lang=auto` or omits the param, the detector
   routes to the correct nomenclature. If detection confidence is low, default to the `lang` param
   or FR (per PROJECT.md).

**Implementation implication for this project:**
- Keep three separate Lucene indexes (one per language) for lexical/fuzzy matching. This ensures
  terminology-accurate fuzzy matching (French compound words, German compounds like "Kraftfahrzeug").
- Use one shared multilingual vector index for semantic matching — all three nomenclatures embedded
  together, with a `lang` metadata filter so results return descriptions in the requested language.
- The `lang` param controls which Lucene index is queried AND which description language is returned,
  but the semantic component benefits from the shared multilingual space.

---

## HS Hierarchy Exposure: Patterns

**Question from scope:** How do similar tools expose chapters/headings/subheadings?

**Pattern 1 — Embedded in search result (universal):**
Every result returns its full hierarchical path:
```json
{
  "code": "870321",
  "description": "Motor cars, spark-ignition engine, cylinder capacity <= 1000cc",
  "heading": { "code": "8703", "description": "Motor cars and other motor vehicles" },
  "chapter": { "code": "87", "description": "Vehicles other than railway or tramway" },
  "score": 0.92,
  "match_type": "SEMANTIC"
}
```
This is table stakes. Every production classification tool does this.

**Pattern 2 — Browse endpoints (differentiator):**
- `GET /chapters` → list of 21 sections + 97 chapters with 2-digit codes and descriptions
- `GET /chapters/{ch}` → headings under that chapter (4-digit codes)
- `GET /headings/{heading}` → subheadings under that heading (6-digit codes)

UK Trade Tariff API (`https://www.trade-tariff.service.gov.uk/api/v2/`) exposes this pattern.
Useful for form-builder UIs that want a collapsible picker alongside free-text search.

**Pattern 3 — Sibling/child expansion in search results (differentiator):**
When a match is at heading level (4-digit, ambiguous), return the subheadings as children:
```json
{
  "code": "8703",
  "description": "Motor cars...",
  "children": [
    { "code": "870310", ... },
    { "code": "870321", ... }
  ],
  "note": "Multiple subheadings match — please specify engine type and cylinder capacity"
}
```
This pattern guides users when their input is too generic. It is a differentiator; most
minimal implementations just return the heading-level match and leave the user stuck.

**Hierarchy encoding:** HS codes encode the hierarchy directly in their structure:
- `87` = chapter
- `8703` = heading (chapter 87, heading 03)
- `870321` = subheading (heading 8703, subheading 21)

This means hierarchy can be derived from the code itself at zero cost — no join required.

---

## Sources

**Note:** Web search and WebFetch were unavailable. All findings are from training-data knowledge
(cutoff August 2025) of the following systems and sources:

- WCO (World Customs Organization) HS Nomenclature structure and data model
- UK Trade Tariff API v2 (https://www.trade-tariff.service.gov.uk/api/v2/) — public REST API
- EU TARIC database and CIRCABC HS nomenclature exports (the data source in this project)
- Avalara HS Classification product (commercial tool, observed feature set)
- Zonos Classify API (commercial tool, observed feature set)
- `paraphrase-multilingual-MiniLM-L12-v2` model card (Hugging Face / sentence-transformers)
- Apache Lucene FuzzyQuery documentation
- General REST API design literature (Richardson Maturity Model, API design patterns)

**Confidence summary:**

| Area | Confidence | Reason |
|------|------------|--------|
| Table stakes features | HIGH | Universal across all search APIs and HS tools observed |
| Differentiators | MEDIUM-HIGH | Observed in multiple tools; exact patterns vary by implementation |
| Anti-features | HIGH | Derived directly from PROJECT.md constraints + domain scope creep patterns |
| Multilingual handling | MEDIUM | MiniLM multilingual approach is documented; cross-language HS search is less documented in public sources |
| Hierarchy encoding | HIGH | WCO HS code structure is a published standard; derivation from code string is a known technique |
