---
phase: phase-3
plan: "02"
type: execute
wave: 1
depends_on: []
files_modified:
  - hscode-matcher-api/pom.xml
  - hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/embedding/EmbeddingEngine.java
  - hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/embedding/EmbeddingStore.java
  - hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/embedding/EmbeddingSearcher.java
  - hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/embedding/EmbeddingConfig.java
  - hscode-matcher-api/src/main/resources/onnx/tokenizer.json
  - hscode-matcher-api/src/test/java/com/geodis/hs/matcher/search/embedding/EmbeddingEngineTest.java
  - hscode-matcher-api/src/test/java/com/geodis/hs/matcher/search/embedding/EmbeddingSimilarityIT.java
autonomous: true
requirements:
  - R3
  - R8

must_haves:
  truths:
    - "EmbeddingEngine.embed() returns a float[384] L2-normalized vector for any non-empty text"
    - "OrtEnvironment is created once per JVM — Spring @Bean with destroyMethod=\"\""
    - "ONNX input names are discovered via session.getInputNames() — not hardcoded"
    - "EmbeddingStore holds a per-language float[][] matrix and String[] code array built from HsEntry streams"
    - "EmbeddingSearcher returns high cosine similarity (>0.85) for synonym pair 'car' / 'automobile'"
    - "tokenizer.json is loadable from classpath at /onnx/tokenizer.json"
    - "Model path is configurable via Spring property hs.matcher.onnx.model-path (not hardcoded)"
  artifacts:
    - path: "hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/embedding/EmbeddingEngine.java"
      provides: "Tokenize + ONNX infer + mean pool + L2 normalize; wraps OrtSession via AtomicReference"
      exports: ["embed(String text): float[]"]
    - path: "hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/embedding/EmbeddingStore.java"
      provides: "Per-language volatile float[][] matrix + String[] code array; initialize() and swap() for reload"
      exports: ["initialize(Language, float[][], String[])", "matrix(Language): float[][]", "codes(Language): String[]"]
    - path: "hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/embedding/EmbeddingSearcher.java"
      provides: "Brute-force dot product over EmbeddingStore, returns List<ScoredHit>"
      exports: ["search(float[] queryVec, Language lang, int limit): List<ScoredHit>"]
    - path: "hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/embedding/EmbeddingConfig.java"
      provides: "Spring @Configuration with OrtEnvironment @Bean and EmbeddingEngine @Bean"
    - path: "hscode-matcher-api/src/main/resources/onnx/tokenizer.json"
      provides: "HuggingFace tokenizer config for paraphrase-multilingual-MiniLM-L12-v2"
      contains: "tokenizer.json from HF model repo root"
  key_links:
    - from: "EmbeddingConfig"
      to: "OrtEnvironment.getEnvironment()"
      via: "@Bean(destroyMethod=\"\")"
      pattern: "destroyMethod.*="
    - from: "EmbeddingEngine.embed()"
      to: "session.getInputNames()"
      via: "inputNames Set<String> stored at construction time"
      pattern: "getInputNames\\(\\)"
    - from: "EmbeddingEngine.embed()"
      to: "meanPool + l2Normalize"
      via: "float[][][] ONNX output tensor"
      pattern: "l2Normalize|l2normalize"
    - from: "EmbeddingSearcher.search()"
      to: "EmbeddingStore.matrix(lang)"
      via: "brute-force dot product loop"
      pattern: "embeddingStore\\.matrix\\("
---

<objective>
Build the ONNX embedding subsystem (Phase 3b): multilingual sentence embeddings using `paraphrase-multilingual-MiniLM-L12-v2` (quantized INT8, ~118 MB), tokenized via DJL HuggingFaceTokenizer, stored as L2-normalized float vectors per language, with brute-force dot product search.

Purpose: Provides the semantic half of the hybrid search engine. Phase 4 (HybridMerger) fuses output from EmbeddingSearcher with LuceneSearcher results via RRF.

Output:
- EmbeddingConfig Spring @Configuration (OrtEnvironment singleton + EmbeddingEngine bean)
- EmbeddingEngine: tokenize + ONNX infer + mean pool + L2 normalize
- EmbeddingStore: volatile per-language float[][] matrix with initialize/swap for Phase 5 reload
- EmbeddingSearcher: dot product ranked search returning List<ScoredHit>
- tokenizer.json bundled as classpath resource
- Unit tests: embed() returns float[384]; synonym pair similarity > 0.85 (C4 validation)
</objective>

<execution_context>
@C:/Users/zou/.claude/get-shit-done/workflows/execute-plan.md
@C:/Users/zou/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/phase-2/phase-2-01-SUMMARY.md
@.planning/phases/phase-3/phase-3-RESEARCH.md

<interfaces>
<!-- Key contracts the executor needs. No codebase exploration required. -->

From src/main/java/com/geodis/hs/matcher/domain/HsEntry.java:
```java
public record HsEntry(
    String code,        // e.g. "010121"
    int level,          // 2=chapter, 4=heading, 6=subheading
    String description,
    Language language,
    String parentCode)  // null for chapters
```

From src/main/java/com/geodis/hs/matcher/domain/Language.java:
```java
public enum Language { FR, EN, DE }
```

From src/main/java/com/geodis/hs/matcher/ingestion/NomenclatureRegistry.java:
```java
public Collection<HsEntry> entries();   // use this to build embedding matrix
public Optional<HsEntry> get(String code);  // use for parent lookup (multilingual text strategy)
```

From src/main/java/com/geodis/hs/matcher/search/model/ScoredHit.java (created by Plan 01, same wave):
```java
public record ScoredHit(String hsCode, float score) {}
```
Note: Plan 01 and Plan 02 are both Wave 1 and touch different packages. ScoredHit is in
`com.geodis.hs.matcher.search.model` — reference it from there. If Plan 01 has not run yet,
create a minimal ScoredHit stub in the same location; it will be replaced by Plan 01's version.
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: pom.xml dependencies and EmbeddingEngine</name>
  <files>
    hscode-matcher-api/pom.xml,
    hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/embedding/EmbeddingEngine.java,
    hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/embedding/EmbeddingConfig.java,
    hscode-matcher-api/src/main/resources/onnx/tokenizer.json,
    hscode-matcher-api/src/test/java/com/geodis/hs/matcher/search/embedding/EmbeddingEngineTest.java
  </files>
  <behavior>
    - EmbeddingEngine.embed("hello world") returns a float[] of length 384 with no exception
    - Returned vector is L2-normalized: Math.abs(dotProduct(v, v) - 1.0f) < 1e-5f
    - OrtEnvironment @Bean uses destroyMethod="" — confirmed by Spring context test
    - EmbeddingEngine stores inputNames from session.getInputNames() at construction time — not "input_ids" hardcoded
  </behavior>
  <action>
    **pom.xml:** Add ONNX Runtime and DJL tokenizers to `<dependencies>`. These are currently commented out:
    ```xml
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

    Also ensure `spring-boot-maven-plugin` has `requiresUnpack` for the DJL tokenizers artifact (for fat-JAR native library extraction — Pitfall 4). If Plan 01 already added this block, do not duplicate it:
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

    Add a Spring property placeholder to `application.properties`:
    ```properties
    hs.matcher.onnx.model-path=classpath:onnx/model_qint8_avx512.onnx
    ```
    This keeps the model path configurable for production deployments that place the model externally.

    **tokenizer.json:** Download from HuggingFace model repo:
    `https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/tokenizer.json`
    Save to `hscode-matcher-api/src/main/resources/onnx/tokenizer.json`.
    This file is ~9 MB — appropriate for classpath bundling.

    **EmbeddingConfig** (package `com.geodis.hs.matcher.search.embedding`):
    ```java
    @Configuration
    public class EmbeddingConfig {

        @Value("${hs.matcher.onnx.model-path}")
        private String modelPath;

        @Bean(destroyMethod = "")  // CRITICAL: do NOT let Spring close OrtEnvironment (Pitfall 5)
        public OrtEnvironment ortEnvironment() throws OrtException {
            return OrtEnvironment.getEnvironment();
        }

        @Bean
        public EmbeddingEngine embeddingEngine(OrtEnvironment env) throws Exception {
            // Load tokenizer from classpath — tokenizer.json at /onnx/tokenizer.json
            URL tokenizerUrl = getClass().getResource("/onnx/tokenizer.json");
            HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(
                Paths.get(tokenizerUrl.toURI())
            );
            // Load model bytes — supports both classpath: and file: URIs via ResourceLoader
            // For Phase 3, load from classpath resource directly
            byte[] modelBytes;
            try (InputStream is = getClass().getResourceAsStream("/onnx/model_qint8_avx512.onnx")) {
                if (is == null) {
                    throw new IllegalStateException(
                        "ONNX model not found at classpath:/onnx/model_qint8_avx512.onnx. " +
                        "Download model_qint8_avx512.onnx from HuggingFace " +
                        "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/tree/main/onnx " +
                        "and place it at hscode-matcher-api/src/main/resources/onnx/");
                }
                modelBytes = is.readAllBytes();
            }
            OrtSession session = env.createSession(modelBytes);
            Set<String> inputNames = session.getInputNames();  // discover — do NOT hardcode (Pitfall m2)
            return new EmbeddingEngine(env, new AtomicReference<>(session), tokenizer, inputNames);
        }
    }
    ```

    **EmbeddingEngine** (package `com.geodis.hs.matcher.search.embedding`):
    NOT a Spring `@Component` — instantiated by EmbeddingConfig. Constructor:
    ```java
    public EmbeddingEngine(OrtEnvironment env, AtomicReference<OrtSession> sessionRef,
                           HuggingFaceTokenizer tokenizer, Set<String> inputNames)
    ```

    Public method:
    ```java
    public float[] embed(String text) throws OrtException
    ```

    Implementation:
    1. `OrtSession session = sessionRef.get()`
    2. `Encoding encoding = tokenizer.encode(text)`
    3. Extract `long[] inputIds = encoding.getIds()`, `long[] attentionMask = encoding.getAttentionMask()`, `long[] tokenTypeIds = encoding.getTypeIds()`
    4. `long[] shape = {1, inputIds.length}`
    5. Build input map using `inputNames` (discovered at init — not hardcoded):
       - If `inputNames.contains("input_ids")` → create tensor from inputIds
       - If `inputNames.contains("attention_mask")` → create tensor from attentionMask
       - If `inputNames.contains("token_type_ids")` → create tensor from tokenTypeIds
    6. Use try-with-resources for all `OnnxTensor` objects
    7. `session.run(inputs)` → `float[][][] output = (float[][][]) result.get(0).getValue()`
    8. Call `meanPoolAndL2Normalize(output[0], attentionMask)` → returns float[384]

    **Mean pooling + L2 normalization** (private methods):
    ```java
    private float[] meanPoolAndL2Normalize(float[][] tokenEmbeddings, long[] attentionMask) {
        int dim = tokenEmbeddings[0].length;  // 384 for MiniLM-L12
        float[] pooled = new float[dim];
        int count = 0;
        for (int i = 0; i < tokenEmbeddings.length; i++) {
            if (attentionMask[i] == 1) {
                for (int d = 0; d < dim; d++) pooled[d] += tokenEmbeddings[i][d];
                count++;
            }
        }
        if (count > 0) {
            for (int d = 0; d < dim; d++) pooled[d] /= count;
        }
        return l2Normalize(pooled);
    }

    private float[] l2Normalize(float[] v) {
        float norm = 0f;
        for (float x : v) norm += x * x;
        norm = (float) Math.sqrt(norm);
        if (norm < 1e-9f) return v;  // avoid div by zero on zero vector
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i] / norm;
        return out;
    }
    ```
    After L2 normalization: dot(queryVec, storedVec) == cosine(queryVec, storedVec). This is the m4 mitigation.

    **Text strategy for indexing** (M1 — multilingual): When calling `embed()` to build EmbeddingStore:
    - For level 6 entries: embed `parentEntry.description() + ". " + entry.description()` if parent exists
    - For level 2/4 entries: embed `entry.description()` only
    - Never embed the HS code string itself (Pitfall 6)

    **EmbeddingEngineTest** (package `com.geodis.hs.matcher.search.embedding`):
    This test requires the ONNX model. Use `@SpringBootTest` with `@Autowired EmbeddingEngine`.
    Tests:
    - `embed_returnsCorrectDimension`: `embed("test text")` returns float[] of length 384
    - `embed_vectorIsNormalized`: dot product of vector with itself equals 1.0f ± 1e-4
    - `embed_doesNotThrow`: no exception for typical text strings
    Annotate with `@Tag("requires-model")` — skip gracefully if model file is absent:
    ```java
    @BeforeAll
    static void checkModel() {
        assumeTrue(
            EmbeddingEngineTest.class.getResourceAsStream("/onnx/model_qint8_avx512.onnx") != null,
            "Skipping: ONNX model not bundled"
        );
    }
    ```
  </action>
  <verify>
    <automated>cd /c/Users/zou/dev/GEODIS/HSCODE/hscode-matcher-api && rtk ./mvnw test -pl . -Dtest=EmbeddingEngineTest -q 2>&1 | tail -20</automated>
  </verify>
  <done>
    - pom.xml compiles with onnxruntime 1.20.0 and djl tokenizers 0.36.0 active
    - tokenizer.json exists at src/main/resources/onnx/tokenizer.json
    - EmbeddingEngine compiles; inputNames populated from session.getInputNames()
    - EmbeddingEngineTest either passes (model present) or skips cleanly with assumeTrue (model absent)
    - OrtEnvironment @Bean has destroyMethod="" confirmed by code review
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: EmbeddingStore, EmbeddingSearcher, and similarity integration test</name>
  <files>
    hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/embedding/EmbeddingStore.java,
    hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/embedding/EmbeddingSearcher.java,
    hscode-matcher-api/src/test/java/com/geodis/hs/matcher/search/embedding/EmbeddingSimilarityIT.java
  </files>
  <behavior>
    - EmbeddingStore.initialize(Language.EN, matrix, codes) stores the matrix; matrix(Language.EN) returns it
    - EmbeddingStore.initialize() called twice for same language replaces the previous matrix (volatile swap)
    - EmbeddingSearcher.search(queryVec, Language.EN, 5) over a store with 3 entries returns at most 3 results
    - Results are sorted by score descending
    - cosine("car", "automobile") > 0.85 via EmbeddingSearcher (C4 integration test — requires ONNX model)
    - EmbeddingSearcher.search() with empty store returns empty list without exception
  </behavior>
  <action>
    **EmbeddingStore** (package `com.geodis.hs.matcher.search.embedding`):
    Spring `@Component`. Holds per-language volatile references:
    ```java
    @Component
    public class EmbeddingStore {
        // Inner record holding the language snapshot
        private record Snapshot(float[][] matrix, String[] codes) {}

        private final Map<Language, AtomicReference<Snapshot>> stores = new EnumMap<>(Language.class);

        public EmbeddingStore() {
            for (Language lang : Language.values()) {
                stores.put(lang, new AtomicReference<>(null));
            }
        }

        // Build and store embedding matrix for one language
        // Called at startup (and during reload for Phase 5)
        public void initialize(Language lang, float[][] matrix, String[] codes) {
            stores.get(lang).set(new Snapshot(matrix, codes));
        }

        // Returns null if not initialized for this language
        public float[][] matrix(Language lang) {
            Snapshot s = stores.get(lang).get();
            return s != null ? s.matrix() : null;
        }

        public String[] codes(Language lang) {
            Snapshot s = stores.get(lang).get();
            return s != null ? s.codes() : null;
        }

        public boolean isInitialized(Language lang) {
            return stores.get(lang).get() != null;
        }
    }
    ```

    **EmbeddingSearcher** (package `com.geodis.hs.matcher.search.embedding`):
    Spring `@Component`. Constructor injection: `EmbeddingStore store`.
    ```java
    public List<ScoredHit> search(float[] queryVec, Language lang, int limit) {
        float[][] matrix = store.matrix(lang);
        String[] codes = store.codes(lang);
        if (matrix == null || matrix.length == 0) return List.of();

        // Brute force dot product — at 5,575 entries < 2ms (no ANN lib needed at this scale)
        float[] scores = new float[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            float dot = 0f;
            for (int d = 0; d < queryVec.length; d++) {
                dot += queryVec[d] * matrix[i][d];
            }
            scores[i] = dot;  // dot product == cosine after L2 normalization
        }

        // Select top-k by partial sort — return limit results sorted by score desc
        Integer[] indices = IntStream.range(0, matrix.length).boxed().toArray(Integer[]::new);
        Arrays.sort(indices, (a, b) -> Float.compare(scores[b], scores[a]));
        int resultCount = Math.min(limit, matrix.length);
        List<ScoredHit> results = new ArrayList<>(resultCount);
        for (int i = 0; i < resultCount; i++) {
            results.add(new ScoredHit(codes[indices[i]], scores[indices[i]]));
        }
        return results;
    }
    ```

    **EmbeddingSimilarityIT** (package `com.geodis.hs.matcher.search.embedding`):
    `@SpringBootTest`. Tests the full pipeline: EmbeddingEngine -> EmbeddingStore -> EmbeddingSearcher.
    ```java
    @SpringBootTest
    class EmbeddingSimilarityIT {

        @Autowired EmbeddingEngine engine;
        @Autowired EmbeddingStore store;
        @Autowired EmbeddingSearcher searcher;

        @BeforeAll
        static void checkModel(@Autowired EmbeddingEngine engine) {
            // Skip gracefully if model is not available (CI without model file)
            assumeTrue(
                EmbeddingSimilarityIT.class.getResourceAsStream("/onnx/model_qint8_avx512.onnx") != null,
                "Skipping: ONNX model not bundled"
            );
        }

        @Test
        void carAndAutomobile_similarityAboveThreshold() throws Exception {
            // Build a tiny English store: 3 entries — "car", "bicycle", "ship"
            float[][] matrix = new float[3][];
            matrix[0] = engine.embed("car");          // codes[0] = "MOCK-CAR"
            matrix[1] = engine.embed("bicycle");      // codes[1] = "MOCK-BIKE"
            matrix[2] = engine.embed("ship");         // codes[2] = "MOCK-SHIP"
            String[] codes = {"MOCK-CAR", "MOCK-BIKE", "MOCK-SHIP"};

            store.initialize(Language.EN, matrix, codes);

            float[] queryVec = engine.embed("automobile");
            List<ScoredHit> results = searcher.search(queryVec, Language.EN, 3);

            assertFalse(results.isEmpty(), "Expected at least one result");
            // Top result should be "car" with high cosine similarity
            assertEquals("MOCK-CAR", results.get(0).hsCode(),
                "Expected 'automobile' to match 'car' over 'bicycle' or 'ship'");
            assertTrue(results.get(0).score() > 0.85f,
                () -> "Expected similarity > 0.85, got: " + results.get(0).score());
        }

        @Test
        void emptyStore_returnsEmptyList() throws Exception {
            // Do not initialize Language.DE store
            float[] queryVec = engine.embed("Kraftfahrzeug");
            List<ScoredHit> results = searcher.search(queryVec, Language.DE, 5);
            assertTrue(results.isEmpty(), "Expected empty results for uninitialized store");
        }
    }
    ```

    Also add a plain unit test (no Spring) for `EmbeddingStore`:
    - `initialize_and_retrieve`: store matrix, retrieve matrix, assert same reference
    - `uninitialized_returnsNull`: `matrix(Language.FR)` returns null before initialize
    - `initialize_twice_replacesMatrix`: second initialize replaces first matrix

    And a plain unit test for `EmbeddingSearcher` with mock data (float arrays, no ONNX needed):
    - `search_sortedByScoreDesc`: 3 entries with known scores, assert result order
    - `search_emptyStore_returnsEmpty`: null matrix → empty list
  </action>
  <verify>
    <automated>cd /c/Users/zou/dev/GEODIS/HSCODE/hscode-matcher-api && rtk ./mvnw test -pl . -Dtest="EmbeddingEngineTest,EmbeddingSimilarityIT" -q 2>&1 | tail -20</automated>
  </verify>
  <done>
    - EmbeddingStore compile and unit tests pass (no ONNX required)
    - EmbeddingSearcher compile and sort-order unit tests pass (no ONNX required)
    - EmbeddingSimilarityIT either passes "car"/"automobile" > 0.85 threshold (model present) or skips cleanly (model absent)
    - Full test suite `./mvnw test` remains green
  </done>
</task>

</tasks>

<verification>
After both tasks complete:

```bash
cd hscode-matcher-api && rtk ./mvnw test -q 2>&1 | tail -20
```

All tests pass. Pre-existing Phase 1 and Phase 2 tests must not regress.

Verify OrtEnvironment destroyMethod:
```bash
grep -n "destroyMethod" hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/embedding/EmbeddingConfig.java
```
Must show `destroyMethod = ""`.

Verify inputNames are discovered at init, not hardcoded:
```bash
grep -n "input_ids" hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/embedding/EmbeddingEngine.java
```
Must not show a hardcoded string assignment — only a conditional check like `inputNames.contains("input_ids")`.

Verify L2 normalization present:
```bash
grep -n "l2Normalize\|l2normalize" hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/embedding/EmbeddingEngine.java
```
</verification>

<success_criteria>
1. `./mvnw test` passes — all embedding subsystem tests green, no regressions
2. EmbeddingConfig has `@Bean(destroyMethod = "")` for OrtEnvironment — confirmed by code review
3. EmbeddingEngine discovers ONNX input names via `session.getInputNames()` — no hardcoded "input_ids"
4. embed() output is L2-normalized — dot product of vector with itself ≈ 1.0
5. EmbeddingSimilarityIT passes (or skips gracefully with assumeTrue if model absent): "car"/"automobile" cosine > 0.85
6. tokenizer.json exists at src/main/resources/onnx/tokenizer.json
7. onnxruntime 1.20.0 and djl tokenizers 0.36.0 active in pom.xml
</success_criteria>

<output>
After completion, create `.planning/phases/phase-3/phase-3-02-SUMMARY.md` following the summary template.
</output>
