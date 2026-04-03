# Domain Pitfalls: HS Code Hybrid Search API

**Domain:** Hybrid search (BM25/fuzzy + local ONNX embeddings) over hierarchical customs nomenclature, multilingual (FR/EN/DE), standalone Spring Boot JAR
**Researched:** 2026-04-03
**Confidence basis:** Training knowledge on Lucene, ONNX Runtime Java API, multilingual sentence transformers, Apache POI, RRF scoring — all well-documented, stable domains. Flagged where confidence is lower.

---

## Critical Pitfalls

These will cause rewrites, silent correctness failures, or production outages if not addressed early.

---

### Pitfall C1: ONNX Session is Not Thread-Safe at the Wrong Level

**What goes wrong:** You create one `OrtSession` and share it across concurrent Spring request threads. ONNX Runtime Java's `OrtSession.run()` is thread-safe for read-only inference, but the `OrtEnvironment` and `OrtSession` must be constructed exactly once and never closed while inference is happening. If you inject the session as a `@Bean` but let Spring destroy it during context refresh (reload endpoint), in-flight requests fail with native memory access errors or silent wrong results.

**Why it happens:** Spring's lifecycle doesn't know about ONNX resource handles. Developers assume `@Bean` `@Scope("singleton")` is safe — it is for Spring, but ONNX has its own ref-counted native handles underneath.

**Consequences:**
- Race condition between reload and in-flight inference: native crash (SIGSEGV on Linux, AccessViolation on Windows) rather than a Java exception
- Difficult to reproduce locally (needs concurrent load)

**Prevention:**
- Create `OrtEnvironment` once per JVM life, never recreate it. It is explicitly documented as a singleton.
- Wrap `OrtSession` in an `AtomicReference`. The reload path builds a new session, swaps the reference, then closes the old one only after confirming no in-flight calls hold it (use a `ReadWriteLock` or a `StampedLock` with a drain).
- Never close `OrtSession` in a Spring `@PreDestroy` without first quiescing all inference threads.

**Warning signs:**
- Tests pass but load tests crash intermittently
- JVM exit code -1 or 255 with no Java stack trace

**Phase:** Address in the ONNX integration phase, before wiring to the REST layer.

---

### Pitfall C2: Lucene Index Reader is Stale After Reload — Queries Return Old Data

**What goes wrong:** You build a Lucene index, open an `IndexReader` once, and cache it in a service bean. When the reload endpoint rewrites the index, existing `IndexReader` instances point to the old commit. New queries run against the old data with no error.

**Why it happens:** Lucene `IndexReader` is a snapshot. It does not auto-refresh. This is by design for consistency, but surprises developers who expect live reads.

**Consequences:** Reloaded data is invisible to all queries until the service restarts. The reload endpoint "works" (no exception) but has no effect.

**Prevention:**
- Use `DirectoryReader.openIfChanged(oldReader)` after each reload. It returns `null` if nothing changed, or a new reader if the index changed.
- Wrap reader access in a `ReaderManager` (Lucene's built-in near-real-time reader pool) — it handles the swap atomically.
- The `IndexSearcher` must be rebuilt from the new reader; old `IndexSearcher` instances keep the old reader open.

**Warning signs:**
- Reload returns HTTP 200 but search results don't change
- `IndexReader.numDocs()` returns the old count after reload

**Phase:** Address in the index lifecycle / reload endpoint phase.

---

### Pitfall C3: EU CIRCABC XLSX Has Non-Obvious Hierarchy Encoding — You Will Parse It Wrong

**What goes wrong:** The EU nomenclature XLSX does not use a parent-ID column to encode hierarchy. It encodes hierarchy through indentation levels in the description column (leading spaces or merged cells) and through the structure of the code itself (2-digit chapter, 4-digit heading, 6-digit subheading). Naive row-by-row parsing with Apache POI produces a flat list where chapter/heading/subheading relationships are lost.

**Why it happens:** The XLSX is designed for human printing, not machine ingestion. Formatting carries semantic meaning.

**Consequences:**
- Hierarchy traversal ("show parent chapter and siblings") is impossible
- Taxonomic matching (dog → animal) cannot be implemented
- You discover this after writing the ingestion pipeline, requiring a rewrite

**Prevention:**
- Before writing any code, open the XLSX in Excel and manually trace three rows: one chapter (2-digit), one heading (4-digit), one subheading (6-digit). Document exactly how hierarchy is encoded — is it purely from code length, or also from cell indentation?
- Write a validation step that reconstructs the tree and asserts that every 6-digit code has a parent 4-digit code in the dataset. Fail loudly on data integrity violations.
- Treat the code structure (string length / prefix matching) as ground truth for hierarchy, not cell formatting, since code structure is format-independent.

**Warning signs:**
- Parsed row count matches raw row count but no parent-child relationships exist
- Apache POI returns cell value `""` for cells that look populated in Excel (merged/formatted cells)

**Phase:** Address on day one of data ingestion, before building any index or search logic.

---

### Pitfall C4: Embedding Model Tokenizer Is Not Bundled — Inference Produces Wrong Vectors Silently

**What goes wrong:** You load the ONNX model weights (`model.onnx`) but not the matching tokenizer (`tokenizer.json`, `vocab.txt`, `special_tokens_map.json`). You implement tokenization manually or use a different tokenizer. The model runs without error but produces vectors for a different token sequence than the model was trained on. Cosine similarity scores are systematically degraded.

**Why it happens:** ONNX format carries the computation graph but not the tokenizer. Java has no equivalent of Python's `transformers.AutoTokenizer`. Developers use a generic BPE or WordPiece tokenizer that is "close enough" — it isn't.

**Consequences:** Semantic search has poor precision. The failure is silent — scores are in range [0,1], results look plausible but wrong concepts cluster together.

**Prevention:**
- Use the `tokenizers` library via its Java bindings (`ai.djl.huggingface:tokenizers`) or the Hugging Face tokenizers Rust library compiled to JNI. These load `tokenizer.json` directly from the model card.
- Download the full model artifact bundle from Hugging Face (`paraphrase-multilingual-MiniLM-L12-v2`), not just the ONNX weights. The bundle includes `tokenizer.json`.
- Write an integration test that encodes a known sentence pair (e.g., "car" and "automobile") and asserts cosine similarity > 0.85. This will catch tokenizer mismatches.

**Warning signs:**
- Semantic scores for obvious synonyms are < 0.5
- "vehicle" and "car" score lower than "vehicle" and "bicycle"

**Phase:** Address in the embedding / semantic search foundation phase. Must be validated before any scoring fusion work.

---

### Pitfall C5: Scoring Fusion Hides Calibration Bugs — You Don't Know Which Signal Is Wrong

**What goes wrong:** You implement Reciprocal Rank Fusion (RRF) or a linear combination `α * bm25_score + β * cosine_score`. The combined score produces reasonable-looking rankings in demos. But BM25 scores are unbounded and query-length dependent, while cosine similarity is bounded [−1, 1]. Without normalizing BM25 scores first, α and β are not stable across queries: a one-word query produces tiny BM25 scores, a five-word query produces large ones. α that works for one query breaks another.

**Why it happens:** BM25 score magnitude is an implementation detail of Lucene's scoring, not a calibrated probability. Developers treat the raw float as if it were comparable to cosine similarity.

**Consequences:** Ranking quality varies unpredictably by query length. Longer queries get BM25-dominated results; single-word queries get embedding-dominated results. Tuning α/β becomes impossible.

**Prevention:**
- Normalize BM25 scores before fusion. Two common approaches:
  1. Min-max normalize within the result set (simple, per-query)
  2. Convert to rank position and use RRF: `score = Σ 1/(k + rank_i)` where k=60 is the standard constant. RRF is rank-based and immune to score magnitude differences.
- RRF is the safer default for a first implementation. Linear combination is only worthwhile after you have labeled eval data to tune α/β against.
- Build an evaluation harness with 20–30 manually labeled (query, expected_hs_code) pairs before touching fusion weights.

**Warning signs:**
- Tuning α produces good results on 5 test queries but breaks 5 others
- Single-word queries and phrase queries return qualitatively different result orderings

**Phase:** Address in the scoring fusion phase. Do not attempt to tune fusion weights before having an eval set.

---

## Moderate Pitfalls

These cause degraded quality or significant rework if not addressed, but won't crash production.

---

### Pitfall M1: Cross-Language Embedding Collapse — Model Treats Languages as Different Concepts

**What goes wrong:** A French query "chaussures en cuir" (leather shoes) does not retrieve the same HS code as the English query "leather shoes" despite the multilingual model supposedly handling both. The model works but the HS description text in your index was only embedded in one language.

**Why it happens:** `paraphrase-multilingual-MiniLM-L12-v2` produces language-agnostic embeddings for general sentences, but the quality degrades significantly for domain-specific vocabulary (customs nomenclature uses legal/technical phrasing). If you index only the French descriptions, a German query may miss entries because the overlap in embedding space is insufficient.

**Prevention:**
- Index all three language descriptions for each HS code as a single concatenated text OR as separate fields. The concatenated approach means the embedding captures multilingual context.
- Alternatively, embed each language's description separately and store three vectors per HS code. At query time, match against all three and take the max score.
- Validate with cross-language queries: French query against English-only embedded index should still find results.

**Warning signs:**
- Language-specific vocabulary (legal phrasing in DE) scores lower than it should
- Cross-language synonym test ("Schuhe" query fails to retrieve "chaussures" top-ranked result)

**Phase:** Address in the embedding indexing design, before generating the index.

---

### Pitfall M2: Apache POI Memory Spike on XLSX Load — OOM on First Startup

**What goes wrong:** Apache POI's default `XSSFWorkbook` loads the entire XLSX into memory as a DOM. For a nomenclature file with 5,000–10,000 rows, this uses 200–500 MB of heap during parsing even though the data itself is small. On a constrained deployment environment, this causes OOM on first startup.

**Why it happens:** `XSSFWorkbook` was designed for Excel manipulation (read+write), not streaming ingestion. The DOM model is convenient but heavyweight.

**Prevention:**
- Use `SXSSFWorkbook` for write or `XSSFSheetXMLHandler` + `OPCPackage` for streaming read (SAX-based). This keeps memory constant at ~10–20 MB regardless of file size.
- Alternatively, convert XLSX to CSV as a pre-processing step (can be done once with a script), and use a simple CSV reader at runtime. CSV parsing is trivial and carries no memory risk.
- The PROJECT.md already mentions converting to CSV for ingestion — follow through on this. Do not load XLSX at runtime.

**Warning signs:**
- JVM heap dump shows `CTRow`, `CTCell`, `XSSFRow` objects consuming > 100 MB
- Startup time is > 30 seconds for data ingestion

**Phase:** Address in the data ingestion design. The CSV conversion path is already planned — stick to it.

---

### Pitfall M3: Lucene's FST (Fuzzy) Search Misses Compound German Words

**What goes wrong:** Lucene's `FuzzyQuery` and `NGramTokenFilter` work well for French and English but fail systematically for German compound nouns. "Lederschuhe" (leather shoes) will not match "Schuhe" (shoes) because the German compound is a single token. Levenshtein distance is too small relative to word length for compound words to match their components.

**Why it happens:** German nominal compounding is productive — any combination of nouns is valid and dictionary coverage is impossible. Standard analyzers treat the compound as one token.

**Prevention:**
- Apply `GermanAnalyzer` (Lucene built-in) which includes `GermanLightStemFilter`. This handles common inflections but NOT decomposition.
- For decomposition, use `HyphenationCompoundWordTokenFilter` or `DictionaryCompoundWordTokenFilter` (both in Lucene's `analysis-common` module). These require a German compound word dictionary but significantly improve recall.
- Treat German as a special case from the start; do not assume the French/English analyzer is portable.

**Warning signs:**
- German single-word queries for specific goods return no results despite the nomenclature containing the concept
- Adding spaces to a compound ("Leder Schuhe") returns results but the natural form ("Lederschuhe") does not

**Phase:** Address in the Lucene analysis chain design. Fixing analyzers after the index is built requires a full re-index.

---

### Pitfall M4: In-Memory Reload Leaves Old Embeddings in Memory — Heap Never Releases

**What goes wrong:** The reload endpoint rebuilds the embedding index (e.g., a float[][] array or a list of float[]) and replaces the reference. The old array becomes eligible for GC. But ONNX `OrtTensor` objects and Lucene index files on heap may not be released promptly. After several reloads, old-generation heap fills up, G1GC promotion failures occur, and latency spikes.

**Why it happens:** Java's GC is not deterministic. Large float arrays (5,000 vectors × 384 dimensions × 4 bytes = ~7 MB per language, ~21 MB total) are not huge individually, but ONNX tensors, Lucene `ByteBuffers`, and MMap file handles pile up if not explicitly closed.

**Prevention:**
- Explicitly call `OrtTensor.close()` and `IndexReader.close()` on old instances after the atomic swap, don't rely on GC.
- Use try-with-resources for all ONNX tensor allocations.
- Add a `/actuator/health` response that includes heap used and a test that reloads 5 times and checks heap delta.

**Warning signs:**
- Heap grows monotonically across reload operations
- GC logs show large old-generation after multiple reloads

**Phase:** Address in the reload endpoint phase.

---

### Pitfall M5: HS Code Hierarchy Reconstruction Fails for Notes Rows

**What goes wrong:** EU CIRCABC XLSX files include "Note" rows interspersed between code rows. These rows describe legal exceptions and do not correspond to HS codes. If your parser treats every row as a code row, notes rows become phantom entries in the index. Searches for phrases in notes text return note entries as top results with no valid HS code.

**Why it happens:** The file is structured for legal publication, not data ingestion. Note rows may have empty code columns or codes formatted differently (e.g., dashes).

**Prevention:**
- In the parsing step, explicitly filter rows where the code column does not match the pattern `^\d{2,6}$` (digits only, 2/4/6 length).
- Log and count discarded rows during ingest. Assert the count of valid codes is in the expected range (HS 2022 has ~5,382 6-digit subheadings).
- Treat any deviation from expected counts as a parsing error, not a warning.

**Warning signs:**
- Index contains more entries than expected (> ~5,400 for HS 6-digit level)
- Top results for specific queries are descriptions that read like legal text ("For the purposes of this chapter...")

**Phase:** Address in the data ingestion validation step.

---

## Minor Pitfalls

These cause friction or minor quality issues but are easy to fix once noticed.

---

### Pitfall m1: Lucene's Default Similarity Is Not BM25 in All Versions

Lucene changed its default similarity from TF-IDF to BM25 in version 8.0. If you are running Lucene 9.x (bundled with Spring Boot 3.x via Elasticsearch client transitive deps or pulled directly), BM25 is the default. But if any test or legacy code explicitly sets `ClassicSimilarity`, your BM25 scoring assumption is silently violated. Always explicitly set `BM25Similarity` on the `IndexWriterConfig` and `IndexSearcher`.

**Phase:** Address in the Lucene index setup.

---

### Pitfall m2: ONNX Model Input Names Are Model-Specific — Don't Hardcode "input_ids"

The `paraphrase-multilingual-MiniLM-L12-v2` ONNX export uses input names `input_ids`, `attention_mask`, and `token_type_ids`. Other ONNX exports omit `token_type_ids`. Hardcoding input names will break if the model is ever swapped. Use `OrtSession.getInputNames()` to retrieve the actual input names at session initialization and fail fast if expected names are missing.

**Phase:** Address in the ONNX session initialization code.

---

### Pitfall m3: CJK / Special Characters in XLSX Don't Survive CSV Conversion

When converting XLSX to CSV, Excel's default export uses system locale encoding. On Windows (common for GEODIS workstations), this defaults to Windows-1252 or CP1250 rather than UTF-8. German umlauts (ä, ö, ü) and French accented characters (é, è, ê) survive in these encodings, but any unexpected Unicode characters in descriptions will silently corrupt. Always specify UTF-8 when exporting/reading CSV, and validate round-trip encoding on the first row containing non-ASCII characters.

**Phase:** Address in the CSV export/ingestion step.

---

### Pitfall m4: Cosine Similarity on Un-Normalized Vectors Is Slower Than Necessary

ONNX `paraphrase-multilingual-MiniLM-L12-v2` outputs embeddings that are NOT L2-normalized by default. Computing cosine similarity requires dividing by two norms per pair. If you pre-normalize all stored embeddings (divide by L2 norm at index time), cosine similarity reduces to a dot product at query time — which is a vectorized operation and ~2x faster on large result sets. This matters if you scan all 5,000 embeddings per query without an ANN index.

**Phase:** Address in the embedding storage design.

---

### Pitfall m5: Spring Boot DevTools Causes Double Context Load — ONNX Session Loads Twice

Spring Boot DevTools uses a two-classloader restart mechanism. On startup with DevTools active, the application context loads twice. If ONNX session initialization is in a `@PostConstruct` bean, it will execute twice, consuming double the memory and time. Disable DevTools in the profile used for integration testing, or guard ONNX initialization with a static flag.

**Phase:** Address before first integration test run.

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| XLSX / CSV data ingestion | C3 (hierarchy encoding), M5 (notes rows), m3 (encoding) | Manual data audit before writing code |
| Lucene index setup | C2 (stale reader), m1 (BM25 not set explicitly), M3 (German compounds) | Set similarity explicitly; use ReaderManager |
| ONNX session init | C1 (thread safety), C4 (tokenizer missing), m2 (input name hardcoding), m5 (DevTools double load) | AtomicReference swap pattern; bundle tokenizer |
| Embedding design | M1 (cross-language collapse), m4 (un-normalized vectors) | Multi-language field strategy; normalize at index time |
| Scoring fusion | C5 (BM25 unbounded) | Use RRF for first implementation; build eval set first |
| Reload endpoint | C2 (stale reader), M4 (heap leak) | ReaderManager + explicit close on old resources |

---

## Sources

- Confidence: HIGH — Lucene `ReaderManager`, `DirectoryReader.openIfChanged()`, `BM25Similarity`, `GermanAnalyzer`, `DictionaryCompoundWordTokenFilter` are documented in Apache Lucene Javadoc (9.x series).
- Confidence: HIGH — ONNX Runtime Java API thread safety documented at https://onnxruntime.ai/docs/api/java/ (OrtEnvironment singleton contract, OrtSession.run() thread safety).
- Confidence: HIGH — `paraphrase-multilingual-MiniLM-L12-v2` model card on Hugging Face documents required tokenizer files and ONNX export bundle.
- Confidence: HIGH — RRF scoring formula (`1/(k+rank)`, k=60) is from the original Cormack et al. 2009 paper and widely reproduced.
- Confidence: MEDIUM — EU CIRCABC XLSX structure inference is based on known EU nomenclature publication format; exact column structure requires manual verification against the actual files (see C3 warning).
- Confidence: MEDIUM — Apache POI streaming vs. DOM memory characteristics are well-documented but actual heap numbers depend on JVM version and GC settings.
- Confidence: LOW — Spring Boot DevTools double-load behavior with ONNX (m5) is inferred from general DevTools classloader behavior; validate in practice.
