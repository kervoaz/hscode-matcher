# Phase 1 — Domain model and Spring Boot scaffold

## Phase goal (restated)

Establish the **shared domain language** (`HsEntry`, `Language`, `SearchResult`, `HierarchyContext`) and a **minimal Spring Boot 3.x / Java 17** application that **compiles to a standalone runnable JAR** with a **health probe** (Actuator), **without** Lucene, ONNX, CSV ingestion, or search behavior. Output is the foundation for Phase 2+ per `.planning/research/ARCHITECTURE.md` and `.planning/ROADMAP.md`.

**Success alignment (from roadmap):**

1. `./mvnw package` produces an executable JAR.
2. Application starts; health endpoint responds (UP) without nomenclature data.
3. Domain types compile; **JUnit 5** tests cover record/enum basics.

**Build tool:** **Maven** with **`mvnw` / `mvnw.cmd`** wrapper (Spring Boot default). Do not introduce Gradle in this phase.

**Stack locks:** Spring Boot **3.x**, Java **17** (toolchain or `maven.compiler.release=17`).

---

## Base package and layout

Suggested root package: `com.geodis.hs.matcher` (adjust only if org standards require a different groupId).

| Package | Purpose (Phase 1) |
|---------|-------------------|
| `com.geodis.hs.matcher` | `@SpringBootApplication` entrypoint |
| `com.geodis.hs.matcher.domain` | `HsEntry`, `Language`, `SearchResult`, `HierarchyContext` |
| `com.geodis.hs.matcher.ingestion` | Empty placeholder (`package-info.java` only) |
| `com.geodis.hs.matcher.search` | Empty placeholder (`package-info.java` only) |
| `com.geodis.hs.matcher.api` | Empty placeholder (`package-info.java` only) |

Optional: `README.md` under each empty module package explaining “populated in Phase N” — not required if `package-info.java` documents the same.

---

## Domain types to implement (exact contracts)

Align with `research/ARCHITECTURE.md` **Core Domain Model**. Use Java **records** and **enum** as shown; normalize HS code storage to a **single convention** (recommend **digits-only** `String`, e.g. `"010121"`) and document in Javadoc on `HsEntry.code` that display formatting (`0101.21.00`) is a presentation concern.

```java
// HsEntry.java — single nomenclature row
record HsEntry(
    String code,          // normalized 6-digit (or 2/4) HS segment, digits only recommended
    int level,            // 2 = chapter, 4 = heading, 6 = subheading
    String description,
    Language language,
    String parentCode     // null for chapters
) {}

// Language.java
enum Language { FR, EN, DE }

// SearchResult.java — API/search outcome (no engines in Phase 1)
record SearchResult(
    HsEntry entry,
    double hybridScore,
    double bm25Score,
    double cosineScore,
    HierarchyContext hierarchy
) {}

// HierarchyContext.java
record HierarchyContext(
    HsEntry chapter,
    HsEntry heading,
    java.util.List<HsEntry> siblings
) {}
```

**Minor naming:** Keep these four public type names as specified (`HsEntry`, `Language`, `SearchResult`, `HierarchyContext`). Inner lists use immutable factories where convenient (`List.copyOf` in compact constructors or factory methods) if you add validation later; Phase 1 may use plain records as above.

---

## Dependencies — Phase 1 only

**Include:**

- `spring-boot-starter` (minimal) or `spring-boot-starter-web` if you prefer readiness for Phase 5 — **prefer minimal** `spring-boot-starter` + **`spring-boot-starter-actuator`** for `/actuator/health` only.
- `spring-boot-starter-test` (scope test) — JUnit 5, AssertJ, etc.

**Do not add** Lucene or ONNX **artifacts** in Phase 1. In `pom.xml`, add an **HTML comment block** (or a commented `<dependency>` stub) documenting **planned** coordinates for Phase 3, for example:

- Apache Lucene 9.x (core + analyzers) — versions to pin when implementing Phase 3a.
- `com.microsoft.onnxruntime:onnxruntime` — version to pin when implementing Phase 3b.

Optional: a `<!-- Phase 3: ... -->` section listing property placeholders `${lucene.version}`, `${onnxruntime.version}` **without** activating dependencies.

---

## Numbered tasks

### Task 1 — Maven wrapper + Spring Boot skeleton

**Description:** Create a **multi-module-ready single module** Maven project at repo root **or** under a dedicated folder (recommend **`hscode-matcher-api/`** at repository root so existing `Nomenclature*.XLSX` stay untouched). Generate **`mvnw`** / **`mvnw.cmd`** and `.mvn/wrapper/*` via `mvn -N wrapper:wrapper` (or copy from Spring Initializr). Add **`pom.xml`**: `spring-boot-starter-parent` 3.x, `java.version` 17, `spring-boot-maven-plugin` for repackaging executable JAR. Add **`spring-boot-starter-actuator`**; expose health (default `application.properties`: `management.endpoints.web.exposure.include=health` if needed). Add **`Application.java`** with `@SpringBootApplication` and standard `main`. Create empty packages **`ingestion`**, **`search`**, **`api`** with **`package-info.java`** (one-line JavaDoc: layer purpose + phase). Add dependency **comment placeholders** for Lucene/ONNX as above.

**Files / packages to create (primary):**

- `hscode-matcher-api/pom.xml` (or root `pom.xml` if you collapse layout — pick one layout and document it in this task’s commit message)
- `hscode-matcher-api/mvnw`, `hscode-matcher-api/mvnw.cmd`, `hscode-matcher-api/.mvn/wrapper/*`
- `hscode-matcher-api/src/main/java/com/geodis/hs/matcher/Application.java`
- `hscode-matcher-api/src/main/resources/application.properties`
- `hscode-matcher-api/src/main/java/com/geodis/hs/matcher/ingestion/package-info.java`
- `hscode-matcher-api/src/main/java/com/geodis/hs/matcher/search/package-info.java`
- `hscode-matcher-api/src/main/java/com/geodis/hs/matcher/api/package-info.java`

**Acceptance criteria:**

- From the project directory: **`./mvnw -q -DskipTests package`** (Windows: **`mvnw.cmd`**) completes with **exit code 0**.
- **`java -jar target/*.jar`** (or the repackaged artifact name) starts without error; **`GET /actuator/health`** returns **200** with status **UP** (or equivalent JSON).
- No Lucene/ONNX dependencies on the classpath (only comments in POM).

**Dependencies:** None (first task).

---

### Task 2 — Domain records and enum

**Description:** Implement **`Language`**, **`HsEntry`**, **`HierarchyContext`**, and **`SearchResult`** in **`com.geodis.hs.matcher.domain`**. Use `public` top-level types (records/enum). Add brief Javadoc on `HsEntry` for `code` normalization and `level` meanings. Do **not** add Spring annotations to domain types. Keep **`List<HsEntry> siblings`** as `java.util.List`; null-safety policy: document whether `siblings` may be empty vs null (recommend **non-null empty list** for “no siblings” to simplify Phase 4 callers).

**Files to create:**

- `.../domain/Language.java`
- `.../domain/HsEntry.java`
- `.../domain/HierarchyContext.java`
- `.../domain/SearchResult.java`
- `.../domain/package-info.java` (optional but recommended: module purpose)

**Acceptance criteria:**

- **`./mvnw -q compile`** succeeds.
- Types match the field names and semantics in the “Domain types” section above (adjust only normalization docs, not public names).

**Dependencies:** **Task 1** (needs Java module + package root).

---

### Task 3 — JUnit 5 domain tests

**Description:** Add **`src/test/java/.../domain/`** tests that **instantiate** each record/enum and **assert** basic behavior: `Language.valueOf` / enum constants; `HsEntry` accessors and `equals`/`hashCode` for two equal entries; `HierarchyContext` with a non-empty `siblings` list; `SearchResult` holding an `HsEntry` and `HierarchyContext` with expected score fields. Use **JUnit 5** (`@Test`). No Spring test context required unless you prefer `@SpringBootTest` for smoke — **plain unit tests are sufficient** for Phase 1.

**Files to create:**

- `hscode-matcher-api/src/test/java/com/geodis/hs/matcher/domain/HsEntryTest.java` (or one `DomainModelTest.java` — prefer focused class names)
- Additional test classes as needed (`LanguageTest`, `SearchResultTest`, …) so failures are easy to locate.

**Acceptance criteria:**

- **`./mvnw -q test`** completes with **all tests passing**.
- At least **one test method** touches each of **`HsEntry`**, **`Language`**, **`SearchResult`**, **`HierarchyContext`**.

**Dependencies:** **Task 2**.

---

## Task dependency graph

```
Task 1 (Maven + Spring Boot + packages)
    └── Task 2 (domain types)
            └── Task 3 (JUnit 5 domain tests)
```

Execute **sequentially** (1 → 2 → 3).

---

## Verification commands (run from chosen project root)

```bash
./mvnw -q test
./mvnw -q -DskipTests package
# After package: run JAR and curl health (adjust artifact name)
java -jar target/hscode-matcher-api-*.jar &
curl -sf http://localhost:8080/actuator/health
```

Use `mvnw.cmd` on Windows where applicable.

---

## Explicitly out of scope for Phase 1

- Lucene indexes, analyzers, `FuzzyQuery`, BM25 configuration.
- ONNX Runtime, embedding vectors, model artifacts.
- CSV/XLSX ingestion, `NomenclatureRegistry`, hierarchy validation pipeline.
- REST search or reload endpoints (Phase 5).

---

## References

- `.planning/ROADMAP.md` — Phase 1 goal and success criteria
- `.planning/PROJECT.md` — product context and stack constraints
- `.planning/research/ARCHITECTURE.md` — domain model and build order
