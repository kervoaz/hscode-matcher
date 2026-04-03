---
phase: phase-3
plan: "01"
type: execute
wave: 1
depends_on: []
files_modified:
  - hscode-matcher-api/pom.xml
  - hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/lucene/IndexBuilder.java
  - hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/lucene/NomenclatureIndex.java
  - hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/lucene/LuceneSearcher.java
  - hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/lucene/AnalyzerFactory.java
  - hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/model/ScoredHit.java
  - hscode-matcher-api/src/test/java/com/geodis/hs/matcher/search/lucene/IndexBuilderTest.java
  - hscode-matcher-api/src/test/java/com/geodis/hs/matcher/search/lucene/LuceneSearcherFuzzyTest.java
autonomous: true
requirements:
  - R2
  - R8

must_haves:
  truths:
    - "Per-language Lucene indexes build from a NomenclatureRegistry without errors"
    - "FuzzyQuery returns plausible top hits for intentionally typo'd queries in FR, EN, and DE"
    - "BM25Similarity is set explicitly on both writer and searcher — not left to defaults"
    - "NomenclatureIndex holds a volatile IndexSearcher reference ready for Phase 5 atomic swap"
    - "Per-language analyzers are used — FrenchAnalyzer, EnglishAnalyzer, GermanAnalyzer — not a single global analyzer"
  artifacts:
    - path: "hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/lucene/IndexBuilder.java"
      provides: "Builds ByteBuffersDirectory from Collection<HsEntry> with per-language analyzer + BM25Similarity"
      exports: ["buildIndex(Collection<HsEntry>, Analyzer): ByteBuffersDirectory"]
    - path: "hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/lucene/NomenclatureIndex.java"
      provides: "Wraps volatile IndexSearcher; initialize() and swap() methods for Phase 5 reload"
      exports: ["initialize(ByteBuffersDirectory)", "swap(ByteBuffersDirectory)", "searcher(): IndexSearcher"]
    - path: "hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/lucene/LuceneSearcher.java"
      provides: "FuzzyQuery search over NomenclatureIndex, returns List<ScoredHit>"
      exports: ["search(String queryText, int limit): List<ScoredHit>"]
    - path: "hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/lucene/AnalyzerFactory.java"
      provides: "Map<Language, Analyzer> with FrenchAnalyzer, EnglishAnalyzer, GermanAnalyzer"
    - path: "hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/model/ScoredHit.java"
      provides: "Shared result record (hsCode, score) consumed by both Lucene and embedding searchers"
      contains: "record ScoredHit"
  key_links:
    - from: "LuceneSearcher"
      to: "NomenclatureIndex.searcher()"
      via: "volatile field read"
      pattern: "nomenclatureIndex\\.searcher\\(\\)"
    - from: "IndexBuilder.buildIndex()"
      to: "ByteBuffersDirectory"
      via: "IndexWriter with BM25Similarity"
      pattern: "new ByteBuffersDirectory\\(\\)"
    - from: "NomenclatureIndex.initialize()"
      to: "DirectoryReader.open(dir)"
      via: "volatile searcher assignment"
      pattern: "DirectoryReader\\.open"
---

<objective>
Build the Lucene subsystem (Phase 3a): per-language in-memory indexes over all HS nomenclature entries using BM25 scoring and FuzzyQuery for typo-tolerant retrieval.

Purpose: Provides the lexical half of the hybrid search engine. Phase 4 (HybridMerger) wires the output of LuceneSearcher with the embedding searcher.

Output:
- ScoredHit record (shared with embedding subsystem)
- AnalyzerFactory Spring @Bean mapping Language -> per-language Lucene Analyzer
- IndexBuilder building ByteBuffersDirectory with explicit BM25Similarity
- NomenclatureIndex with volatile IndexSearcher (C2 reload-ready)
- LuceneSearcher executing FuzzyQuery + collecting ScoredHit results
- Unit tests: index doc count, fuzzy typo hits per language
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
    String code,        // normalized digits only, e.g. "010121" (6-digit)
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
public final class NomenclatureRegistry {
    public Language language();
    public Optional<HsEntry> get(String code);
    public Collection<HsEntry> entries();   // use this to feed IndexBuilder
    public int size();
    public long countAtLevel(int level);
}
```
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: ScoredHit, AnalyzerFactory, and IndexBuilder</name>
  <files>
    hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/model/ScoredHit.java,
    hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/lucene/AnalyzerFactory.java,
    hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/lucene/IndexBuilder.java,
    hscode-matcher-api/src/test/java/com/geodis/hs/matcher/search/lucene/IndexBuilderTest.java,
    hscode-matcher-api/pom.xml
  </files>
  <behavior>
    - ScoredHit is an immutable record: ScoredHit(String hsCode, float score)
    - IndexBuilder.buildIndex() with a list of 3 HsEntry objects returns a ByteBuffersDirectory whose opened DirectoryReader has exactly 3 documents
    - IndexBuilder.buildIndex() with an empty list returns a directory with 0 documents
    - AnalyzerFactory produces non-null Analyzer for Language.FR, Language.EN, Language.DE
  </behavior>
  <action>
    **pom.xml:** Activate the Phase 3 Lucene dependencies that are currently commented out. Uncomment (or add) these three dependencies inside `<dependencies>`:
    ```xml
    <dependency>
        <groupId>org.apache.lucene</groupId>
        <artifactId>lucene-core</artifactId>
        <version>${lucene.version}</version>
    </dependency>
    <dependency>
        <groupId>org.apache.lucene</groupId>
        <artifactId>lucene-analysis-common</artifactId>
        <version>${lucene.version}</version>
    </dependency>
    ```
    (Leave `onnxruntime` commented — that is Plan 02's concern.)

    Also add `requiresUnpack` to spring-boot-maven-plugin for the DJL tokenizers artifact that Plan 02 will add — structure it now so Plan 02 only needs to add one `<dependency>` entry:
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

    **ScoredHit** (package `com.geodis.hs.matcher.search.model`):
    ```java
    public record ScoredHit(String hsCode, float score) {}
    ```

    **AnalyzerFactory** (package `com.geodis.hs.matcher.search.lucene`):
    Spring `@Component`. Exposes a `@Bean` method `analyzers()` returning `Map<Language, Analyzer>`:
    ```java
    Map.of(
        Language.FR, new FrenchAnalyzer(),
        Language.EN, new EnglishAnalyzer(),
        Language.DE, new GermanAnalyzer()
    )
    ```
    Import from `org.apache.lucene.analysis.fr.FrenchAnalyzer`, `org.apache.lucene.analysis.en.EnglishAnalyzer`, `org.apache.lucene.analysis.de.GermanAnalyzer` (all in `lucene-analysis-common`). Do NOT use `StandardAnalyzer`.

    **IndexBuilder** (package `com.geodis.hs.matcher.search.lucene`):
    Spring `@Component`. Single public method:
    ```java
    public ByteBuffersDirectory buildIndex(Collection<HsEntry> entries, Analyzer analyzer) throws IOException
    ```
    Implementation:
    1. Create `ByteBuffersDirectory dir = new ByteBuffersDirectory()` — NOT `RAMDirectory` (removed in Lucene 9)
    2. `IndexWriterConfig config = new IndexWriterConfig(analyzer)` then `config.setSimilarity(new BM25Similarity())` — always explicit per pitfall m1
    3. Open `IndexWriter` in try-with-resources, iterate entries:
       - `new StoredField("code", entry.code())`
       - `new TextField("description", entry.description(), Field.Store.YES)`
       - `new StoredField("level_stored", entry.level())`
       - `new IntPoint("level", entry.level())`
       - `new StoredField("parentCode", entry.parentCode() != null ? entry.parentCode() : "")`
    4. `writer.commit()` before close
    5. Return `dir`

    **IndexBuilderTest** (package `com.geodis.hs.matcher.search.lucene`):
    Write tests FIRST (TDD):
    - `buildIndex_emptyInput_returnsZeroDocDirectory`: build with empty list, open DirectoryReader, assert `reader.numDocs() == 0`
    - `buildIndex_threeEntries_indexesAllDocs`: build with 3 HsEntry objects (all FR, mixed levels), assert `reader.numDocs() == 3`
    - `buildIndex_usesStoredCode`: build with one entry code "010121", open reader, retrieve stored doc, assert `doc.get("code").equals("010121")`

    Use `@SpringBootTest` slice or plain unit test with `new IndexBuilder()` and `new FrenchAnalyzer()` (no Spring context needed for this class).
  </action>
  <verify>
    <automated>cd /c/Users/zou/dev/GEODIS/HSCODE/hscode-matcher-api && rtk ./mvnw test -pl . -Dtest=IndexBuilderTest -q 2>&1 | tail -20</automated>
  </verify>
  <done>
    - pom.xml compiles with lucene-core and lucene-analysis-common 9.12.1 active
    - ScoredHit record exists and compiles
    - AnalyzerFactory @Bean returns non-null analyzers for all three languages
    - IndexBuilderTest passes: 0 docs for empty input, 3 docs for 3 entries, stored field round-trip correct
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: NomenclatureIndex and LuceneSearcher</name>
  <files>
    hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/lucene/NomenclatureIndex.java,
    hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/lucene/LuceneSearcher.java,
    hscode-matcher-api/src/test/java/com/geodis/hs/matcher/search/lucene/LuceneSearcherFuzzyTest.java
  </files>
  <behavior>
    - LuceneSearcher.search("woool", 5) on an EN index containing "wool" returns at least 1 result (maxEdits=2 for length>5, but "woool" is 5 chars so maxEdits=1; adjust test query to "woolll" which is 6 chars → maxEdits=2 to match "wool")
    - LuceneSearcher.search("automobiles", 5) on a French index containing "automobiles de tourisme" returns at least 1 hit
    - LuceneSearcher.search("kraftfahrzeuge", 5) on a German index containing "Kraftfahrzeuge" returns at least 1 hit (case-insensitive via GermanAnalyzer)
    - LuceneSearcher.search("xxxxnotexist", 5) returns empty list, no exception
    - NomenclatureIndex.searcher() returns the volatile IndexSearcher after initialize() is called
    - swap() replaces the searcher and closes the old reader without exception
  </behavior>
  <action>
    **NomenclatureIndex** (package `com.geodis.hs.matcher.search.lucene`):
    ```java
    public final class NomenclatureIndex {
        private volatile IndexSearcher searcher;

        public void initialize(ByteBuffersDirectory dir) throws IOException {
            DirectoryReader reader = DirectoryReader.open(dir);
            IndexSearcher s = new IndexSearcher(reader);
            s.setSimilarity(new BM25Similarity());  // explicit BM25 on searcher too
            this.searcher = s;
        }

        // Phase 5 atomic swap — close old reader after volatile write
        public void swap(ByteBuffersDirectory newDir) throws IOException {
            IndexSearcher old = this.searcher;
            DirectoryReader newReader = DirectoryReader.open(newDir);
            IndexSearcher newSearcher = new IndexSearcher(newReader);
            newSearcher.setSimilarity(new BM25Similarity());
            this.searcher = newSearcher;     // volatile write — safe publication
            if (old != null) {
                old.getIndexReader().close(); // explicit close to avoid heap leak (M4)
            }
        }

        public IndexSearcher searcher() { return searcher; }
    }
    ```
    Do NOT make this a Spring `@Component` — it is a plain value object. Callers instantiate it and call `initialize()` from a `@Bean` factory.

    **LuceneSearcher** (package `com.geodis.hs.matcher.search.lucene`):
    Spring `@Component`. Constructor injection: `NomenclatureIndex index`.
    Single public method:
    ```java
    public List<ScoredHit> search(String queryText, int limit) throws IOException {
        IndexSearcher searcher = index.searcher();
        if (searcher == null) return List.of();
        String term = queryText.toLowerCase(Locale.ROOT);
        int maxEdits = term.length() > 5 ? 2 : (term.length() >= 3 ? 1 : 0);
        Query query = new FuzzyQuery(new Term("description", term), maxEdits, 2);
        TopDocs topDocs = searcher.search(query, limit);
        List<ScoredHit> results = new ArrayList<>();
        for (ScoreDoc sd : topDocs.scoreDocs) {
            Document doc = searcher.storedFields().document(sd.doc);
            results.add(new ScoredHit(doc.get("code"), sd.score));
        }
        return results;
    }
    ```
    Use `searcher.storedFields().document(sd.doc)` — NOT the deprecated `searcher.doc(sd.doc)` removed in Lucene 9.

    **LuceneSearcherFuzzyTest** (package `com.geodis.hs.matcher.search.lucene`):
    Plain JUnit 5 test — no Spring context needed:
    1. Helper: `buildIndex(List<HsEntry> entries, Analyzer analyzer)` calling `IndexBuilder.buildIndex()` then `NomenclatureIndex.initialize()`
    2. Test `fuzzyHit_english_typo`: Build EN index with one entry `HsEntry("020130", 6, "wool textile fibres", Language.EN, "0201")`. Search "woolll" (6 chars → maxEdits=2). Assert results non-empty, first result hsCode == "020130".
    3. Test `fuzzyHit_french`: Build FR index with entry `HsEntry("870321", 6, "automobiles de tourisme", Language.FR, "8703")`. Search "automobils" (10 chars → maxEdits=2). Assert results non-empty.
    4. Test `fuzzyHit_german`: Build DE index with entry `HsEntry("870321", 6, "Kraftfahrzeuge zum Transport", Language.DE, "8703")`. Search "Kraftfahrzuge" (typo, 13 chars → maxEdits=2). Assert results non-empty.
    5. Test `noHit_unknownQuery`: Search "xxxxnotexist" on any index. Assert results empty list, no exception.

    Note: `LuceneSearcher` takes `NomenclatureIndex` via constructor — instantiate both directly in tests without Spring.
  </action>
  <verify>
    <automated>cd /c/Users/zou/dev/GEODIS/HSCODE/hscode-matcher-api && rtk ./mvnw test -pl . -Dtest="IndexBuilderTest,LuceneSearcherFuzzyTest" -q 2>&1 | tail -20</automated>
  </verify>
  <done>
    - NomenclatureIndex.initialize() and swap() compile and pass without exception
    - LuceneSearcherFuzzyTest passes all 5 cases: EN typo, FR typo, DE typo, empty result for unknown query
    - Full test suite `./mvnw test` remains green (no regressions in Phase 1/2 tests)
  </done>
</task>

</tasks>

<verification>
After both tasks complete:

```bash
cd hscode-matcher-api && rtk ./mvnw test -q 2>&1 | tail -20
```

All tests must pass including pre-existing Phase 1 and Phase 2 tests.

Check that `ByteBuffersDirectory` is used (not `RAMDirectory`):
```bash
grep -r "RAMDirectory" hscode-matcher-api/src/ && echo "FAIL: RAMDirectory found" || echo "OK: no RAMDirectory"
```

Check that `lucene-analysis-common` (not `lucene-analyzers-common`) is in pom.xml:
```bash
grep "lucene-analysis-common" hscode-matcher-api/pom.xml && echo "OK: correct artifact" || echo "FAIL: artifact not found"
```

Check that `StandardAnalyzer` is not used in production code:
```bash
grep -r "StandardAnalyzer" hscode-matcher-api/src/main/ && echo "FAIL: StandardAnalyzer in main" || echo "OK: no StandardAnalyzer in main"
```
</verification>

<success_criteria>
1. `./mvnw test` passes — all Phase 3a unit tests green, no Phase 1/2 regressions
2. Fuzzy test returns hits for typo'd EN/FR/DE queries confirming per-language analyzer + BM25 pipeline works
3. `NomenclatureIndex` has `volatile IndexSearcher searcher` — confirmed by code inspection and swap() test
4. `lucene-analysis-common` 9.12.1 and `lucene-core` 9.12.1 active in pom.xml
5. `ByteBuffersDirectory` used everywhere — no `RAMDirectory` in source
</success_criteria>

<output>
After completion, create `.planning/phases/phase-3/phase-3-01-SUMMARY.md` following the summary template.
</output>
