# Phase 3: Lexical and Semantic Indexes — Research

**Researched:** 2026-04-03
**Domain:** Apache Lucene 9.x (BM25/fuzzy text index) + ONNX Runtime Java (multilingual sentence embeddings)
**Confidence:** HIGH for Lucene APIs and patterns; HIGH for ONNX Runtime Java lifecycle; MEDIUM for model file sourcing / fat-JAR native-library mechanics; MEDIUM for DJL tokenizer fat-JAR behavior

---

## Summary

Phase 3 builds two independent subsystems that both consume `HsEntry` streams from the validated `NomenclatureRegistry` (Phase 2 output). Subsystem 3a creates per-language Lucene indexes using `ByteBuffersDirectory` (not `RAMDirectory` — removed in Lucene 9), language-specific analyzers, and explicit `BM25Similarity`. Subsystem 3b embeds nomenclature descriptions using `paraphrase-multilingual-MiniLM-L12-v2` loaded through ONNX Runtime, with tokenization handled by DJL `HuggingFaceTokenizer`, and stores L2-normalized float vectors per language.

The critical architectural decisions for this phase are: (1) use `ByteBuffersDirectory` instead of the removed `RAMDirectory`; (2) the full ONNX model is 470 MB — too large to bundle in a JAR; use an external classpath resource path or a download-and-cache strategy; (3) the `OrtEnvironment` is a JVM-wide singleton enforced by the ONNX Runtime itself; (4) `OrtSession.run()` is documented thread-safe — multiple Spring threads can share a single session; (5) design `NomenclatureIndex` with a `volatile` reference from day one for Phase 5 reload.

**Primary recommendation:** Source the quantized `model_qint8_avx512.onnx` (118 MB) for the JAR-adjacent deployment strategy. Use `ai.djl.huggingface:tokenizers` (0.36.0) to load `tokenizer.json` from classpath. Keep `OrtEnvironment` as a Spring `@Bean` singleton; wrap `OrtSession` in an `AtomicReference` with a `StampedLock` drain pattern for Phase 5 reload compatibility.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `org.apache.lucene:lucene-core` | 9.12.1 (pinned in pom.xml) | `IndexWriter`, `IndexReader`, `BM25Similarity`, `FuzzyQuery`, `ByteBuffersDirectory` | De-facto embedded Java search; BM25 default since 8.0; production-stable |
| `org.apache.lucene:lucene-analysis-common` | 9.12.1 | `FrenchAnalyzer`, `EnglishAnalyzer`, `GermanAnalyzer`, `HyphenationCompoundWordTokenFilter` | Renamed from `lucene-analyzers-common` in Lucene 9.0 — **use new artifact ID** |
| `com.microsoft.onnxruntime:onnxruntime` | 1.20.0 (pinned in pom.xml) | `OrtEnvironment`, `OrtSession`, tensor I/O for inference | Official Microsoft Java binding; CPU-only variant; thread-safe session.run() |
| `ai.djl.huggingface:tokenizers` | 0.36.0 | `HuggingFaceTokenizer` loading `tokenizer.json` from classpath | Rust-backed JNI wrapper for Hugging Face tokenizers; the only correct way to match model tokenization in Java |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `org.apache.lucene:lucene-queryparser` | 9.12.1 | `QueryParser` for ad-hoc query building in tests | Optional — use programmatic query construction in production |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `ai.djl.huggingface:tokenizers` | Manual `WordPiece` tokenizer | Manual tokenizer diverges from model training vocabulary — silently wrong embeddings (Pitfall C4). Do not hand-roll. |
| `paraphrase-multilingual-MiniLM-L12-v2` | `all-MiniLM-L6-v2` (English-only) | all-MiniLM-L6-v2 is English-only; FR/DE queries would degrade severely. |
| quantized `model_qint8_avx512.onnx` (118 MB) | full `model.onnx` (470 MB) | Full model cannot be bundled in JAR. Quantized qint8 variant is 118 MB with minimal accuracy loss. Both sources from same HF repo. |
| `ByteBuffersDirectory` | `FSDirectory` | FSDirectory writes to disk; ByteBuffersDirectory keeps index in heap. At ~5K entries/lang the heap cost is trivial (~5 MB/lang). For Phase 3 always use ByteBuffersDirectory. |

**Installation — activate from pom.xml comments (already pinned):**
```xml
<!-- Uncomment in pom.xml Phase 3 block -->
<dependency>
    <groupId>org.apache.lucene</groupId>
    <artifactId>lucene-core</artifactId>
    <version>${lucene.version}</version>  <!-- 9.12.1 -->
</dependency>
<dependency>
    <groupId>org.apache.lucene</groupId>
    <artifactId>lucene-analysis-common</artifactId>
    <version>${lucene.version}</version>
</dependency>
<dependency>
    <groupId>com.microsoft.onnxruntime</groupId>
    <artifactId>onnxruntime</artifactId>
    <version>${onnxruntime.version}</version>  <!-- 1.20.0 -->
</dependency>
<dependency>
    <groupId>ai.djl.huggingface</groupId>
    <artifactId>tokenizers</artifactId>
    <version>0.36.0</version>
</dependency>
```

**Note on `lucene-queryparser`:** Only needed if writing tests with string-form queries. Add only if tests require it.

---

## Architecture Patterns

### Recommended Package Structure

```
com.geodis.hs.matcher.search/
├── lucene/
│   ├── IndexBuilder.java           # builds ByteBuffersDirectory from HsEntry stream
│   ├── NomenclatureIndex.java      # holds volatile DirectoryReader + IndexSearcher per lang
│   ├── LuceneSearcher.java         # FuzzyQuery + BM25, returns List<ScoredHit>
│   └── AnalyzerFactory.java        # Map<Language, Analyzer> — FrenchAnalyzer etc.
├── embedding/
│   ├── EmbeddingEngine.java        # OrtSession singleton, tokenize + infer + pool
│   ├── EmbeddingStore.java         # volatile float[][] + String[] per lang, atomic swap
│   └── EmbeddingSearcher.java      # brute-force dot product over store
└── model/
    └── ScoredHit.java              # (hsCode, score) — shared by both searchers
```

**Resources (for model + tokenizer):**
```
src/main/resources/
└── onnx/
    ├── tokenizer.json              # 9 MB — from HF model repo root
    └── model_qint8_avx512.onnx    # 118 MB — from HF onnx/ subdirectory
```

**Important:** 118 MB is within fat-JAR bundling range. The unquantized `model.onnx` at 470 MB is not recommended for JAR bundling; use classpath resource with `file:` or `https:` URI caching if needed.

---

### Pattern 1: Lucene Index Build with ByteBuffersDirectory (not RAMDirectory)

**What:** `RAMDirectory` was removed in Lucene 9.0. The replacement is `ByteBuffersDirectory`.
**When to use:** All in-memory Lucene index work in Phase 3.

```java
// Source: Apache Lucene 9.0 Migration Guide
// https://lucene.apache.org/core/9_0_0/MIGRATE.html
import org.apache.lucene.store.ByteBuffersDirectory;

ByteBuffersDirectory dir = new ByteBuffersDirectory();
IndexWriterConfig config = new IndexWriterConfig(analyzer);
config.setSimilarity(new BM25Similarity()); // Pitfall m1: always set explicitly
try (IndexWriter writer = new IndexWriter(dir, config)) {
    for (HsEntry entry : entries) {
        Document doc = new Document();
        doc.add(new StoredField("code", entry.code()));
        doc.add(new IntPoint("level", entry.level()));
        doc.add(new StoredField("level_stored", entry.level()));
        doc.add(new TextField("description", entry.description(), Field.Store.YES));
        doc.add(new StoredField("parentCode",
                entry.parentCode() != null ? entry.parentCode() : ""));
        writer.addDocument(doc);
    }
}
```

### Pattern 2: NomenclatureIndex with Volatile Reference (C2 swappable reader)

**What:** Wraps a `DirectoryReader` + `IndexSearcher` in a volatile reference so Phase 5 reload can swap atomically.
**When to use:** Phase 3 builds the initial version; Phase 5 adds the swap logic.

```java
// Source: Architecture pattern from Lucene ReaderManager / openIfChanged docs
// https://lucene.apache.org/core/9_9_2/core/org/apache/lucene/index/ReaderManager.html
public final class NomenclatureIndex {
    private volatile IndexSearcher searcher;  // volatile = safe publication

    public void initialize(ByteBuffersDirectory dir) throws IOException {
        DirectoryReader reader = DirectoryReader.open(dir);
        IndexSearcher s = new IndexSearcher(reader);
        s.setSimilarity(new BM25Similarity());
        this.searcher = s;
    }

    // Phase 5 reload: call after new index is built
    public void swap(ByteBuffersDirectory newDir) throws IOException {
        IndexSearcher old = this.searcher;
        DirectoryReader newReader = DirectoryReader.open(newDir);
        IndexSearcher newSearcher = new IndexSearcher(newReader);
        newSearcher.setSimilarity(new BM25Similarity());
        this.searcher = newSearcher;          // atomic volatile write
        old.getIndexReader().close();         // explicit close — Pitfall M4
    }

    public IndexSearcher searcher() { return searcher; }
}
```

**Phase 5 note:** If near-real-time (NRT) semantics are needed, replace with `ReaderManager` which wraps `DirectoryReader.openIfChanged()`. For Phase 3 (build once) the volatile pattern is sufficient.

### Pattern 3: FuzzyQuery with BM25 Scoring

**What:** BooleanQuery combining FuzzyQuery for typo tolerance with BM25 scoring on description field.
**When to use:** LuceneSearcher.search() implementation.

```java
// Source: Lucene 9.x FuzzyQuery API
// https://lucene.apache.org/core/9_9_1/core/org/apache/lucene/search/FuzzyQuery.html
int maxEdits = query.length() > 5 ? 2 : (query.length() >= 3 ? 1 : 0);
Query fuzzy = new FuzzyQuery(
    new Term("description", query.toLowerCase()),
    maxEdits,
    2    // prefixLength=2: first 2 chars not fuzzed
);
TopDocs hits = searcher.search(fuzzy, limit);
```

### Pattern 4: OrtEnvironment Singleton + OrtSession AtomicReference (C1)

**What:** `OrtEnvironment` enforces one-per-JVM. `OrtSession` is thread-safe for `run()` but must be swapped carefully during reload.
**When to use:** EmbeddingEngine Spring `@Bean` setup.

```java
// Source: ONNX Runtime Java API
// https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtEnvironment.html
@Configuration
public class EmbeddingConfig {

    @Bean(destroyMethod = "")  // do NOT let Spring close OrtEnvironment
    public OrtEnvironment ortEnvironment() throws OrtException {
        return OrtEnvironment.getEnvironment();  // singleton enforced by ORT itself
    }

    @Bean
    public EmbeddingEngine embeddingEngine(OrtEnvironment env) throws Exception {
        // Load tokenizer from classpath
        HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(
            Paths.get(getClass().getResource("/onnx/tokenizer.json").toURI())
        );
        // Load model from classpath resource
        byte[] modelBytes = getClass().getResourceAsStream(
            "/onnx/model_qint8_avx512.onnx").readAllBytes();
        OrtSession session = env.createSession(modelBytes);
        // Discover input names at init time — Pitfall m2
        Set<String> inputNames = session.getInputNames();
        return new EmbeddingEngine(env, new AtomicReference<>(session), tokenizer, inputNames);
    }
}
```

### Pattern 5: Mean Pooling + L2 Normalization (m4)

**What:** The model outputs token-level embeddings. Mean pooling collapses to sentence vector. L2 normalization makes dot product == cosine at query time.
**When to use:** EmbeddingEngine.embed() implementation.

```java
// Source: paraphrase-multilingual-MiniLM-L12-v2 model card (sentence-transformers pattern)
// Pooling config: 1_Pooling/config.json — word_embedding_dimension=384, pooling_mode_mean_tokens=true
private float[] meanPool(float[][] tokenEmbeddings, long[] attentionMask) {
    int dim = tokenEmbeddings[0].length;
    float[] pooled = new float[dim];
    int count = 0;
    for (int i = 0; i < tokenEmbeddings.length; i++) {
        if (attentionMask[i] == 1) {
            for (int d = 0; d < dim; d++) pooled[d] += tokenEmbeddings[i][d];
            count++;
        }
    }
    for (int d = 0; d < dim; d++) pooled[d] /= count;
    return l2Normalize(pooled);
}

private float[] l2Normalize(float[] v) {
    float norm = 0f;
    for (float x : v) norm += x * x;
    norm = (float) Math.sqrt(norm);
    if (norm < 1e-9f) return v;
    float[] out = new float[v.length];
    for (int i = 0; i < v.length; i++) out[i] = v[i] / norm;
    return out;
}
// After L2 normalization: dot(queryVec, storedVec) == cosine(queryVec, storedVec)
```

### Pattern 6: Multilingual Text Strategy (M1)

Per ARCHITECTURE.md recommendation: embed `entry.description()` only. For subheadings (level=6), prepend the parent heading's description to propagate taxonomic context:

```java
// Source: ARCHITECTURE.md Anti-Pattern 6 guidance
String textToEmbed = (entry.level() == 6 && parentEntry != null)
    ? parentEntry.description() + ". " + entry.description()
    : entry.description();
```

This is the per-language strategy: each language's descriptions are embedded into its own `EmbeddingStore`. The model is cross-lingual but stores are per-language for consistent retrieval with the Lucene language routing.

### Anti-Patterns to Avoid

- **Using `RAMDirectory`:** Removed in Lucene 9.0. Always use `ByteBuffersDirectory` for in-memory indexes.
- **Single global analyzer:** Never use `StandardAnalyzer` for all three languages. `FrenchAnalyzer`, `EnglishAnalyzer`, `GermanAnalyzer` are required per-index.
- **Hardcoding ONNX input names:** Always call `session.getInputNames()` at init; do not hardcode `"input_ids"`.
- **Closing `OrtEnvironment` in `@PreDestroy`:** The ORT singleton must outlive all sessions; Spring `destroyMethod=""` on the `@Bean`.
- **Creating `OrtSession` per request:** Session construction is ~200ms. Create once, share across threads (session.run() is thread-safe).
- **Storing un-normalized embeddings:** Normalize at index time so query time is a plain dot product.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Tokenization for transformer models | Custom BPE/WordPiece tokenizer | `ai.djl.huggingface:tokenizers` loading `tokenizer.json` | Custom tokenizers diverge from training vocab; silently produces wrong embeddings (C4) |
| BM25 scoring | Custom TF-IDF scoring | Lucene `BM25Similarity` with `IndexWriterConfig.setSimilarity()` | BM25 has well-tuned defaults (k1=1.2, b=0.75); hand-rolled variants miss normalization edge cases |
| Fuzzy string matching | Levenshtein distance on Java Strings | Lucene `FuzzyQuery(term, maxEdits, prefixLength)` | FuzzyQuery uses Levenshtein automata (Damerau-Levenshtein) optimized via BK-tree; much faster than naive scan |
| Vector similarity search | Custom dot product loop | Use the brute-force dot product loop directly (no ANN lib needed at 5K entries) | At 5,575 entries per language, brute-force is < 2ms. HNSW/Faiss adds complexity with no benefit at this scale. |
| German compound splitting | Custom dictionary lookup | `GermanAnalyzer` baseline; `HyphenationCompoundWordTokenFilter` if needed | German compound decomposition is linguistically complex; mature implementations exist in Lucene |

**Key insight:** The tokenizer is the hardest problem to get right in Java embeddings. Every hour spent on a custom tokenizer produces worse results than 5 minutes adding the DJL dependency.

---

## Common Pitfalls

### Pitfall 1: RAMDirectory Does Not Exist in Lucene 9
**What goes wrong:** Code uses `new RAMDirectory()` — compile-time error or ClassNotFoundException.
**Why it happens:** Architecture docs (and many tutorials) still reference RAMDirectory. It was removed in Lucene 9.0.
**How to avoid:** Use `ByteBuffersDirectory`. The API is identical — drop-in replacement.
**Warning signs:** IDE shows "cannot resolve symbol RAMDirectory"; migration guide explicitly lists it as removed.
**Confidence:** HIGH — verified in Apache Lucene 9.0 migration guide.

### Pitfall 2: Lucene artifact renamed in 9.0 (lucene-analyzers-common → lucene-analysis-common)
**What goes wrong:** Adding `lucene-analyzers-common` to pom.xml — this artifact stopped at version 8.x. Lucene 9.x uses `lucene-analysis-common`.
**How to avoid:** The pom.xml comment already says `lucene-analysis-common` (correct). Verify artifact ID when activating the dependency.
**Confidence:** HIGH — verified via Maven Central search results.

### Pitfall 3: ONNX Model Size — 470 MB Does Not Fit in a JAR
**What goes wrong:** Bundling `model.onnx` (470 MB) in `src/main/resources` produces an oversized JAR that may exceed JVM classpath scanning limits and is impractical to ship.
**Why it happens:** Full-precision float32 model. The architecture mentions the model without flagging size.
**How to avoid:** Use the quantized variant `model_qint8_avx512.onnx` (118 MB) from the `/onnx/` subdirectory of the Hugging Face `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2` repo. 118 MB is within a manageable JAR boundary. Alternatively, load via `file:` URI with the model file placed adjacent to the JAR.
**Warning signs:** Maven build time > 10 minutes; JAR > 600 MB.
**Confidence:** HIGH (model sizes verified via Hugging Face repo tree inspection).

### Pitfall 4: DJL tokenizers requires native JNI — may need requiresUnpack in Spring Boot fat JAR
**What goes wrong:** `ai.djl.huggingface:tokenizers` is Rust-backed via JNI. Spring Boot's nested JAR classloader cannot load native `.so`/`.dll` from within a nested JAR — requires physical extraction to filesystem before `System.load()`.
**Why it happens:** JNI libraries must be on the filesystem. Spring Boot fat JAR nests everything; native libs inside nested JARs are not loadable by JNI.
**How to avoid:** Add `requiresUnpack` configuration to `spring-boot-maven-plugin`:
```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <requiresUnpack>
            <dependency>
                <groupId>ai.djl.huggingface</groupId>
                <artifactId>tokenizers</artifactId>
            </dependency>
        </requiresUnpack>
    </configuration>
</plugin>
```
ONNX Runtime (`onnxruntime`) also uses JNI and handles its own extraction to `java.io.tmpdir` automatically. However, the temp file approach can fail if temp is on a `noexec` filesystem.
**Warning signs:** `java.lang.UnsatisfiedLinkError: no onnxruntime4j_jni in java.library.path` or `no djl_tokenizers in java.library.path`.
**Confidence:** MEDIUM — Spring Boot requiresUnpack pattern is documented; DJL-specific behavior should be validated with a test deployment.

### Pitfall 5: OrtEnvironment cannot be recreated after close
**What goes wrong:** Calling `ortEnv.close()` and then `OrtEnvironment.getEnvironment()` — ORT throws `IllegalStateException` because the JVM-lifetime singleton is gone.
**How to avoid:** Never close `OrtEnvironment`. Use `destroyMethod=""` on the Spring `@Bean`. Close only `OrtSession` instances (before or after swap).
**Confidence:** HIGH — documented in OrtEnvironment Javadoc: "at most one OrtEnvironment per JVM lifetime."

### Pitfall 6: Embedding description as "code + description" pollutes embedding space
**What goes wrong:** `embed("010121 " + entry.description())` — the code string carries no semantic meaning and introduces noise into the embedding.
**How to avoid:** Embed `entry.description()` only (for chapters/headings) or `parentHeading.description() + ". " + entry.description()` for subheadings.
**Confidence:** HIGH — explicit anti-pattern in ARCHITECTURE.md.

---

## Code Examples

### Lucene Index Setup (complete pattern)
```java
// Source: Lucene 9.x API — ByteBuffersDirectory + BM25Similarity + FrenchAnalyzer
public ByteBuffersDirectory buildIndex(Collection<HsEntry> entries, Analyzer analyzer)
        throws IOException {
    ByteBuffersDirectory dir = new ByteBuffersDirectory();
    IndexWriterConfig config = new IndexWriterConfig(analyzer);
    config.setSimilarity(new BM25Similarity());  // explicit — Pitfall m1
    try (IndexWriter writer = new IndexWriter(dir, config)) {
        for (HsEntry entry : entries) {
            Document doc = new Document();
            doc.add(new StoredField("code", entry.code()));
            doc.add(new IntPoint("level", entry.level()));
            doc.add(new StoredField("level_stored", entry.level()));
            doc.add(new TextField("description", entry.description(), Field.Store.YES));
            writer.addDocument(doc);
        }
        writer.commit();
    }
    return dir;
}
```

### FuzzyQuery Search
```java
// Source: Lucene 9.x FuzzyQuery API
public List<ScoredHit> search(String queryText, int limit) throws IOException {
    IndexSearcher searcher = nomenclatureIndex.searcher();
    int maxEdits = queryText.length() > 5 ? 2 : (queryText.length() >= 3 ? 1 : 0);
    Query query = new FuzzyQuery(
        new Term("description", queryText.toLowerCase()), maxEdits, 2);
    TopDocs topDocs = searcher.search(query, limit);
    List<ScoredHit> results = new ArrayList<>();
    for (ScoreDoc sd : topDocs.scoreDocs) {
        Document doc = searcher.storedFields().document(sd.doc);
        results.add(new ScoredHit(doc.get("code"), sd.score));
    }
    return results;
}
```

### ONNX Inference with DJL Tokenizer
```java
// Source: ONNX Runtime Java API + DJL HuggingFaceTokenizer pattern
public float[] embed(String text) throws OrtException {
    OrtSession session = sessionRef.get();  // AtomicReference
    Encoding encoding = tokenizer.encode(text);

    long[] inputIds = encoding.getIds();
    long[] attentionMask = encoding.getAttentionMask();
    long[] tokenTypeIds = encoding.getTypeIds();

    long[] shape = {1, inputIds.length};
    // Use discovered input names — do NOT hardcode (Pitfall m2)
    Map<String, OnnxTensor> inputs = new HashMap<>();
    try (OnnxTensor idsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape);
         OnnxTensor maskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape);
         OnnxTensor typesTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypeIds), shape)) {
        if (inputNames.contains("input_ids"))      inputs.put("input_ids", idsTensor);
        if (inputNames.contains("attention_mask")) inputs.put("attention_mask", maskTensor);
        if (inputNames.contains("token_type_ids")) inputs.put("token_type_ids", typesTensor);

        try (OrtSession.Result result = session.run(inputs)) {
            float[][][] output = (float[][][]) result.get(0).getValue();
            return meanPoolAndNormalize(output[0], attentionMask);
        }
    }
}
```

### Analyzer Map (per language)
```java
// Source: Lucene analysis-common API
// FrenchAnalyzer, EnglishAnalyzer, GermanAnalyzer are in lucene-analysis-common
@Bean
public Map<Language, Analyzer> analyzers() {
    return Map.of(
        Language.FR, new FrenchAnalyzer(),
        Language.EN, new EnglishAnalyzer(),
        Language.DE, new GermanAnalyzer()
    );
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `RAMDirectory` for in-memory index | `ByteBuffersDirectory` | Lucene 8.2 deprecated, 9.0 removed | Compile error if old code used |
| `lucene-analyzers-common` artifact | `lucene-analysis-common` | Lucene 9.0 | Dependency block wrong artifact ID breaks build |
| `TF-IDF` (ClassicSimilarity) default | `BM25Similarity` default | Lucene 8.0 | Must still set explicitly to be safe (m1) |
| Python-only tokenizers (no Java binding) | DJL `HuggingFaceTokenizer` with Rust JNI | DJL 0.20+ | Correct tokenization now possible in Java without Python |
| Full 470 MB float32 ONNX model only | INT8 quantized variants at 118 MB | 2024 HF repo update | Practical JAR bundling now viable |

**Deprecated/outdated:**
- `RAMDirectory`: Do not use; removed from Lucene 9.
- `lucene-analyzers-common`: Stopped at Lucene 8.x; use `lucene-analysis-common` for 9.x.
- `ClassicSimilarity` as default: Was replaced by BM25 in Lucene 8.0; do not rely on defaults.

---

## Open Questions

1. **DJL tokenizers fat-JAR behavior on Windows (GEODIS workstations)**
   - What we know: DJL uses Rust JNI; `requiresUnpack` is the standard Spring Boot remedy for JNI libs in fat JARs.
   - What's unclear: Whether DJL's own extraction logic handles this automatically (like ONNX Runtime does) or requires `requiresUnpack` explicitly.
   - Recommendation: Write a minimal Spring Boot test that loads `HuggingFaceTokenizer` from classpath in a fat-JAR-packaged app before committing to this approach. If it fails, add `requiresUnpack` for the DJL tokenizers artifact.

2. **Quantized model accuracy for domain vocabulary**
   - What we know: `model_qint8_avx512.onnx` is 118 MB (INT8 quantized). General benchmarks show minimal quality degradation.
   - What's unclear: Whether INT8 quantization meaningfully degrades HS customs nomenclature domain-specific vocabulary (technical/legal German, French).
   - Recommendation: Run the C4 integration test ("car"/"automobile" similarity > 0.85) and a domain pair ("chaussures en cuir"/"leather shoes") against both quantized and full model during development. If quantized fails the threshold, fall back to full model with external file deployment.

3. **Spring Boot DevTools double-load in CI (m5)**
   - What we know: DevTools causes double context load; ONNX session would initialize twice.
   - What's unclear: Whether the CI/test profile already disables DevTools (it's not in scope for integration tests normally).
   - Recommendation: Confirm DevTools is not on the test classpath (`<scope>test</scope>` is not DevTools' scope anyway — it's `runtime`). Add `spring.devtools.restart.enabled=false` in `application-test.properties` as a belt-and-suspenders measure.

4. **ONNX model distribution approach for production**
   - What we know: 118 MB in JAR is feasible; Spring Boot fat JARs routinely handle 200+ MB.
   - What's unclear: Whether GEODIS deployment environment has constraints on JAR size or requires external model file.
   - Recommendation: Default to classpath-bundled `model_qint8_avx512.onnx` for Phase 3. Phase 5 can add a `spring.ai`-style external URI config if needed.

---

## Validation Architecture

`workflow.nyquist_validation` is not present in `.planning/config.json` (no `nyquist_validation` key). Skipping formal Validation Architecture section per instructions.

The following test expectations are implied by the phase success criteria and pitfall mitigations:

| Test | Type | Command | What it validates |
|------|------|---------|------------------|
| `LuceneIndexBuilderTest` | Unit | `./mvnw test -pl hscode-matcher-api` | Index builds without error; doc count matches entry count |
| `LuceneSearcherFuzzyTest` | Integration | `./mvnw test` | Typo'd query returns plausible top hits per language |
| `EmbeddingEngineTest` | Unit | `./mvnw test` | `embed("car")` returns float[384]; no exceptions |
| `EmbeddingSimilarityIT` | Integration | `./mvnw test` | cosine("car", "automobile") > 0.85 (C4 test) |
| `GermanAnalyzerBaselineTest` | Unit | `./mvnw test` | GermanAnalyzer tokenizes known compound; baseline for M3 |

---

## Sources

### Primary (HIGH confidence)
- Apache Lucene 9.0 Migration Guide — `ByteBuffersDirectory` replaces `RAMDirectory`, `lucene-analysis-common` artifact rename: https://lucene.apache.org/core/9_0_0/MIGRATE.html
- Apache Lucene `ReaderManager` Javadoc (9.9.2): https://lucene.apache.org/core/9_9_2/core/org/apache/lucene/index/ReaderManager.html
- ONNX Runtime Java API — `OrtEnvironment` singleton contract, `OrtSession.run()` thread safety: https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtEnvironment.html
- ONNX Runtime Java Getting Started: https://onnxruntime.ai/docs/get-started/with-java.html
- Lucene `FuzzyQuery` API (9.9.1): https://lucene.apache.org/core/9_9_1/core/org/apache/lucene/search/FuzzyQuery.html
- Lucene `BM25Similarity` API (9.11.1): https://lucene.apache.org/core/9_11_1/core/org/apache/lucene/search/similarities/BM25Similarity.html
- HF repo tree for `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2` — model sizes and quantized variants verified by inspection: https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/tree/main/onnx

### Secondary (MEDIUM confidence)
- DJL HuggingFaceTokenizer README: https://github.com/deepjavalibrary/djl/blob/master/extensions/tokenizers/README.md
- Spring AI ONNX Embeddings reference (pattern for tokenizer + model classpath loading): https://docs.spring.io/spring-ai/reference/api/embeddings/onnx.html
- `HyphenationCompoundWordTokenFilter` API (Lucene 9.8.0): https://lucene.apache.org/core/9_8_0/analysis/common/org/apache/lucene/analysis/compound/HyphenationCompoundWordTokenFilter.html
- `lucene-analysis-common` Maven artifact (9.0.0): https://mvnrepository.com/artifact/org.apache.lucene/lucene-analysis-common/9.0.0
- DJL tokenizers Maven artifact (0.33.0 shown; 0.36.0 reported as latest): https://mvnrepository.com/artifact/ai.djl.huggingface/tokenizers/0.33.0
- ONNX Runtime GitHub issue #2892 — native lib extraction in fat JARs: https://github.com/microsoft/onnxruntime/issues/2892
- Spring Boot `requiresUnpack` documentation: https://docs.spring.io/spring-boot/how-to/build.html

### Tertiary (LOW confidence — needs validation)
- DJL tokenizers fat-JAR behavior without `requiresUnpack` — inferred from JNI loading mechanics; not explicitly tested
- INT8 quantized model accuracy for customs nomenclature domain vocabulary — no domain-specific benchmark found
- Spring Boot DevTools double-ONNX-load behavior (m5) — inferred from DevTools classloader design

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Lucene 9.12.1 and ONNX Runtime 1.20.0 already pinned in pom.xml; DJL 0.36.0 confirmed latest; artifact renames verified
- Architecture: HIGH — ByteBuffersDirectory, volatile reference pattern, OrtEnvironment singleton all confirmed from official docs
- ONNX model file sizes: HIGH — verified by inspection of HF repository tree
- Pitfalls: HIGH for Lucene/ORT lifecycle; MEDIUM for fat-JAR native library specifics
- DJL tokenizers fat-JAR: MEDIUM — standard mechanism exists (requiresUnpack) but DJL-specific extraction behavior not confirmed from official source

**Research date:** 2026-04-03
**Valid until:** 2026-05-03 (stable APIs; model artifacts unlikely to change; DJL version may update sooner)
