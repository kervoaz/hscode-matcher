# HS Code Matcher API — Project Roadmap

## Executive summary

This roadmap delivers a **standalone Spring Boot 3.x / Java 17+ JAR** that maps free-text goods descriptions in **French, English, or German** to **HS 6-digit** codes using **hybrid search** (Apache Lucene fuzzy/BM25 + local ONNX multilingual embeddings), with **hierarchical context** and **per-result scores**, and **hot reload** of nomenclature data without restart. Work follows the component build order in `research/ARCHITECTURE.md`, but **gates heavy indexing on a proven CSV + hierarchy validation pipeline** so ingestion mistakes (EU XLSX quirks) do not force downstream rewrites (**PITFALLS C3, M5**). **MVP** is the end-to-end search API plus health and reload; browse/lookup/batch and optional auto language detection are **deferred** per `research/FEATURES.md` unless explicitly pulled forward.

---

## Constraints (non-negotiable)

| Constraint | Implication |
|------------|-------------|
| Java 17+, Spring Boot 3.x | Dependencies and APIs chosen for this baseline |
| Standalone JAR, **no external infra at query time** | No Elasticsearch, no remote vector DB, no OpenAI — Lucene + in-process ONNX |
| HS **6-digit only** for v1 | No TARIC/CN 8–10 digit extensions |
| Source data: `Nomenclature*.XLSX` in repo | **XLSX → CSV** for runtime ingest; avoid loading XLSX in the running service (**M2**) |

---

## Data prerequisite (before Lucene / embeddings)

**Order:** Convert EU CIRCABC **XLSX → UTF-8 CSV** (build-time or one-shot tooling, not hot path), then run **hierarchy validation** on parsed rows **before** building Lucene RAM directories or embedding matrices.

| Step | Purpose | Pitfalls addressed |
|------|---------|-------------------|
| XLSX → CSV with **UTF-8** | Stable, low-memory ingest; no POI DOM in runtime | **M2**, **m3** |
| Parse codes; **filter non-code rows** (notes, legal text) | Avoid phantom hits and inflated doc counts | **M5** |
| Reconstruct tree; **assert** every 6-digit has 4-digit parent, etc. | Enables hierarchy in responses and taxonomic behavior | **C3** |
| **Fail loudly** on count/parent anomalies | Blocks bad data from reaching indexes | **C3**, **M5** |

Do **not** start Phase 3 (indexes) until this validation passes on all three language files.

---

## Phases (dependency order)

Summary checklist:

- [x] **Phase 1 — Domain model and Spring Boot scaffold** — Shared types and runnable empty service
- [x] **Phase 2 — CSV ingestion, registry, and hierarchy validation** — Data gate before indexing
- [ ] **Phase 3 — Lexical and semantic indexes** — Lucene (3a) **done**; ONNX (3b) **done**; **bundle + reload** builds embeddings with Lucene; optional **disk cache** for embedding matrices (`hs.matcher.embedding.cache-dir`, 2026-04-04)
- [ ] **Phase 4 — Hybrid merger and hierarchy enrichment** — **RRF hybrid search** in `/api/v1/search` when ONNX enabled (2026-04-03); **hierarchy in JSON** done; eval harness / score explain still TBD
- [ ] **Phase 5 — REST API, language handling, reload, and index lifecycle** — `GET /search` done; **`GET /api/v1/codes/{code}`** done (2026-04-03); **`POST /api/v1/admin/reload`** done (2026-04-03); optional token; full orchestration polish TBD
- [ ] **Phase 6 — Quality tuning, observability, and hardening** — **Nomenclature health**, **`candidatePool`**, **`X-Request-Id`**, Micrometer **`hs.matcher.search`** / **`hs.matcher.reload`** timers + **`/actuator/metrics`** (2026-04-04); Prometheus export / eval harness / German compound follow-through TBD

---

## Phase details

### Phase 1: Domain model and Spring Boot scaffold

**Goal:** Establish the shared domain language and a minimal **Spring Boot 3.x** application that compiles to a **standalone JAR** (no search behavior yet).

**Depends on:** Nothing (greenfield app added to this data-first repo).

**Key tasks**

- Add Maven/Gradle project with Java 17+, Spring Boot 3.x, dependency placeholders for Lucene and ONNX Runtime (versions pinned when implementing Phase 3).
- Implement core records/enums per architecture: `HsEntry`, `Language`, `SearchResult`, `HierarchyContext` (and related DTOs for API later).
- Basic packaging layout: domain package separate from `ingestion`, `search`, `api`.

**Pitfall mitigations**

- **m5** (DevTools double ONNX load): Prefer disabling DevTools for integration-test profile or guarding heavy native init once Phase 3 adds ONNX; document for Phase 3.

**Success criteria** (observable)

1. `./mvnw package` or `./gradlew build` produces a runnable JAR.
2. Application starts with a health indicator (or placeholder actuator) without nomenclature data loaded.
3. Domain types compile and are covered by trivial unit tests (serialization/validation boundaries optional but recommended).

**Plans:** [Phase 1 plan](phases/phase-1/PLAN.md)

---

### Phase 2: CSV ingestion, registry, and hierarchy validation

**Goal:** Load **UTF-8 CSV** (derived from XLSX) into a **NomenclatureRegistry**, with **correct parent/child links** and **validated row sets** — the **gate** for all indexing work.

**Depends on:** Phase 1.

**Key tasks**

- Implement **XLSX → CSV** conversion using Apache POI **off the request path** (build plugin, CLI, or one-shot task); runtime reads **CSV only** (**M2**).
- Define CSV column mapping after **manual spot-check** of real files (chapter, heading, subheading rows) — treat **code structure as ground truth** for hierarchy where possible (**C3**).
- Filter rows: keep only codes matching `^\d{2,6}$` (2/4/6 digit HS segments); discard note/legal rows without valid codes (**M5**).
- Build in-memory registry: code → `HsEntry`, `parentCode`, `level`; run **integrity checks** (6-digit parent exists, expected approximate counts for HS scope) (**C3**, **M5**).
- Log counts of dropped rows and fail fast on invariant violations.

**Pitfall mitigations**

- **C3:** Document encoding of hierarchy from real XLSX; validate tree, not “row count = success.”
- **M5:** Explicit note-row filtering and expected 6-digit cardinality checks.
- **m3:** UTF-8 end-to-end on CSV write/read; verify non-ASCII round-trip on sample rows.
- **M2:** No `XSSFWorkbook` on application startup for full file load.

**Success criteria**

1. All three languages ingest from CSV into registry with **no integrity check failures**.
2. Spot queries: given a 6-digit code, **chapter and heading parents** resolve from registry.
3. Discarded-row counts and final 6-digit counts are logged and within expected ballpark for HS v1 scope.

**Plans:** [Phase 2 plan](phases/phase-2/PLAN.md)

---

### Phase 3: Lexical and semantic indexes

**Goal:** Two parallel subsystems — **per-language Lucene** indexes for fuzzy/BM25 and **ONNX-backed embedding store** for semantic similarity — both fed from `HsEntry` streams.

**Depends on:** Phase 2.

**Key tasks**

- **3a — Lucene:** `IndexBuilder`, `NomenclatureIndex`, `LuceneSearcher`; per-language `FrenchAnalyzer` / `EnglishAnalyzer` / `GermanAnalyzer` (no single global analyzer for all langs).
- **3b — Embeddings:** Bundle full model artifact including **tokenizer** files; `EmbeddingEngine`, `EmbeddingStore`, `EmbeddingSearcher`; **L2-normalize** stored vectors (**m4**).
- Cross-lingual quality: adopt an explicit strategy for multilingual descriptions (**M1**) — e.g. embed per-language text as defined in architecture, or concatenate heading + description for subheadings per anti-pattern guidance in ARCHITECTURE.
- Integration tests: fuzzy hit on typo’d query; embedding sanity check known synonym pair (**C4**).

**Pitfall mitigations**

- **C2:** Design for **swappable** `DirectoryReader` / `IndexSearcher` from day one (reader manager or open-if-changed pattern planned for Phase 5 reload).
- **C4:** Use tokenizer bundle from Hugging Face export; test “car” / “automobile” similarity threshold.
- **C1:** `OrtEnvironment` once per JVM; plan **session lifecycle** with atomic swap compatible with reload (full pattern in Phase 5).
- **m1:** Explicit **BM25Similarity** on writer/searcher config.
- **m2:** Discover ONNX input names via session metadata, do not hardcode blindly.
- **M3:** Track German compound recall risk; baseline with `GermanAnalyzer` — optional **dictionary compound filter** if acceptance tests fail (may spill into Phase 6).

**Success criteria**

1. For each language, Lucene returns plausible top hits for intentionally typo’d queries against that language’s index.
2. Embedding search returns high similarity for an agreed synonym/concept pair (**C4**).
3. Memory and startup remain compatible with standalone JAR expectations (no full XLSX DOM at runtime).

**Plans:** TBD

---

### Phase 4: Hybrid merger and hierarchy enrichment

**Goal:** Produce a **single ranked list** per query with **hybrid scores** and **hierarchical context** (chapter, heading, siblings/children as required by product behavior).

**Depends on:** Phase 3.

**Key tasks**

- Implement `HybridMerger` — default **RRF (k≈60)** first; optional linear blend behind config (**C5**, architecture).
- Implement `HierarchyResolver` enriching results from **NomenclatureRegistry** (parents, siblings under same heading where applicable).
- Expose **per-result** lexical vs semantic contributions consistent with API contract (e.g. `match_type` / component scores as per FEATURES MVP).
- Start a **small labeled eval set** (20–30 query → expected code pairs) before tuning fusion (**C5**).

**Pitfall mitigations**

- **C5:** Prefer RRF initially; avoid tuning raw BM25 + cosine without normalization or eval harness.
- **M1:** Revisit embedding text strategy if cross-language retrieval fails acceptance tests.
- **m4:** Confirm query-time path uses dot product on unit vectors.

**Success criteria**

1. Hybrid search returns ranked 6-digit codes with **chapter and heading context** on each row.
2. Each result includes a **documented relevance/score** (hybrid and/or components per API design).
3. Eval harness runs in CI or locally with stable fixtures; failures block merges to main.

**Plans:** TBD

---

### Phase 5: REST API, language handling, reload, and index lifecycle

**Goal:** **MVP-shippable** HTTP API: search, health, and **reload**; **language** per request; **atomic index swap** without blocking in-flight queries.

**Depends on:** Phase 4.

**Key tasks**

- Search endpoint: validate `q`, `limit`, `lang` (FR/EN/DE); default **FR** if product chooses; **optional** auto-detect only if explicitly scheduled (FEATURES defers auto-detect for simplest v1 — align with stakeholders; avoid double ONNX embed for “detect + search” per architecture anti-pattern).
- Orchestration: `SearchOrchestrator` wiring Lucene + embedding + merger + hierarchy.
- `POST` (or documented verb) **admin reload**: rebuild staging indexes/registry, then **volatile/atomic swap**; Lucene **new readers/searchers** on swap (**C2**); ONNX **session swap** with safe lifecycle (**C1**); explicit **close** of old readers/tensors where applicable (**M4**).
- Health endpoint for orchestration/K8s-style probes.

**Pitfall mitigations**

- **C1:** Single long-lived `OrtEnvironment`; `AtomicReference` (or RW lock + drain) for session swap; never close session under active inference.
- **C2:** New `IndexReader`/`IndexSearcher` after reload; no cached stale reader.
- **M4:** Close old Lucene readers and ONNX tensors after swap; monitor heap across repeated reload tests.
- Architecture anti-pattern: **no** embedding the query twice for language detection; use param/header/default.

**Success criteria**

1. Caller can **POST/GET search** (per chosen REST style) with `lang` and receive **JSON** with ranked HS **6-digit** results, descriptions, scores, and hierarchy fields.
2. **Reload** refreshes results without process restart; concurrent searches complete against old or new snapshot without corruption (**C1**, **C2**).
3. **Health** returns UP when indexes and model are ready.

**Plans:** TBD

---

### Phase 6: Quality tuning, observability, and hardening

**Goal:** Production-grade **calibration and operability**: structured logging of score components, actuator metrics, analyzer tuning (especially **German compounds**), and optional fusion strategy comparison.

**Depends on:** Phase 5.

**Key tasks**

- Tune RRF vs linear blend using eval set; adjust **α** only with labeled data (**C5**).
- Actuator: metrics (latency, reload duration, index size); optional heap exposure in health for ops (**M4**).
- Structured logs per result: BM25 rank/score, cosine rank/score, fusion outcome.
- Address **M3** if German recall gaps appear: compound dictionary filters / tests.
- Performance pass: singleton ONNX session, vectorized dot products, Lucene query patterns.

**Pitfall mitigations**

- **C5:** No weight tuning without eval harness.
- **M3:** Targeted tests for compound nouns if user acceptance fails.
- **m5:** Confirm DevTools / test profiles do not double-load ONNX in CI.

**Success criteria**

1. Operators can diagnose **why** a result ranked as it did from logs (within documented limits).
2. Reload stress test (e.g. **5×** reload) shows **bounded heap growth** (**M4**).
3. German (and FR/EN) acceptance queries meet agreed quality bar or have documented gaps.

**Plans:** TBD

---

## Requirement traceability (PROJECT.md — Active checklist)

| ID | Active requirement (paraphrased) | Phase(s) |
|----|----------------------------------|----------|
| **R1** | Plain-text query in FR/EN/DE → matching HS codes | 5 (API + orchestration), 3–4 (engines + merger) |
| **R2** | Fuzzy matching for typos/spelling | 3a (Lucene FuzzyQuery + analyzers) |
| **R3** | Semantic matching (synonyms, related concepts) | 3b (ONNX embeddings), 4 (fusion) |
| **R4** | Type/subtype via hierarchy / embeddings | 2 (valid tree), 3b (embedding text strategy), 4 (HierarchyResolver + hybrid) |
| **R5** | Hierarchical results (chapter, heading, siblings/children as needed) | 2, 4, 5 |
| **R6** | Relevance/confidence score per result | 4 (merger + scoring), 5 (JSON contract) |
| **R7** | Runtime nomenclature reload without restart | 5 (reload + atomic swap), mitigations **C1**, **C2**, **M4** |
| **R8** | All three nomenclatures FR, EN, DE | 2 (ingest all CSVs), 3 (per-lang Lucene; shared ONNX engine) |
| **R9** | Query language per request; default auto-detect or FR | 5 (param/header/default); **auto-detect optional/deferred** per FEATURES — confirm product default |

**Coverage:** All nine Active items map to at least one phase; none left unowned.

---

## Out of scope / deferred

### From PROJECT.md (explicit out of scope)

- Admin or management UI — API only
- User accounts or search history — stateless API
- TARIC / national **8–10 digit** extensions — **HS 6-digit only** for v1
- **Real-time external APIs** for semantics — local model only

### From FEATURES.md (defer to v2 unless promoted)

- `GET /chapters` (and related browse/drill-down endpoints)
- `GET /codes/{hscode}` lookup
- Structured multi-field request body (`product_name`, `material`, …) — clients may concatenate for v1
- **Auto language detection** — FEATURES recommends explicit `lang` for v1; reconcile with PROJECT “auto or FR” early in Phase 5
- Pagination, batch classification endpoint
- Redis/external cache layers
- Spell-correction “did you mean”, streaming/WebSocket responses
- Returning full legal chapter notes / copyrighted legal blocks (link to official sources in docs instead)

---

## Progress

| Phase | Plans complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Domain + scaffold | 1/1 | Complete | `hscode-matcher-api/` (2026-04-03) |
| 2. Ingestion + validation | 1/1 | Complete | XLSX→CSV, registry, integrity (2026-04-03) |
| 3. Lucene + embeddings | partial | In progress | Lucene + GET search (2026-04-03); ONNX TBD |
| 4. Hybrid + hierarchy | 0/TBD | Not started | — |
| 5. REST + reload (MVP) | 0/TBD | Not started | — |
| 6. Tuning + observability | 0/TBD | Not started | — |

---

## References

- `.planning/PROJECT.md` — core value, Active requirements, constraints
- `.planning/research/ARCHITECTURE.md` — components and build order (Phases 1–6 technical)
- `.planning/research/FEATURES.md` — MVP vs defer, API surface expectations
- `.planning/research/PITFALLS.md` — risk IDs **C1–C5**, **M1–M5**, **m1–m5**

*Roadmap generated for GSD planning — align execution with `/gsd:plan-phase`.*
