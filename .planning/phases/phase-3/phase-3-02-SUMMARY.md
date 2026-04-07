# Phase 3b — ONNX embeddings (summary)

**Status:** Implemented (2026-04-03). Hybrid RRF merge (Phase 4) and bundle wiring at startup/reload are follow-ups.

## Delivered

- **Dependencies:** `onnxruntime` 1.20.0, `ai.djl.huggingface:tokenizers` 0.36.0; Spring Boot `requiresUnpack` for tokenizers JAR (native libs).
- **`tokenizer.json`:** Bundled under `classpath:/onnx/tokenizer.json` (paraphrase-multilingual-MiniLM-L12-v2).
- **`ScoredHit`:** `search.model` — HS code + cosine score (dot product on L2-normalized vectors).
- **`EmbeddingEngine`:** DJL encode → ONNX run with inputs discovered from `session.getInputNames()` → mean pool + L2 normalize → `float[384]`.
- **`EmbeddingConfig`:** `@ConditionalOnProperty(hs.matcher.onnx.enabled=true)`; `OrtEnvironment` bean `destroyMethod=""`; model from `hs.matcher.onnx.model-path` (`ResourceLoader`: `classpath:` / `file:`).
- **`EmbeddingStore` / `EmbeddingSearcher`:** Per-language matrix + codes; brute-force top-k by dot product.
- **`NomenclatureEmbeddingText`:** M1 indexing text (parent + HS6 description; no raw codes).
- **Tests:** Unit tests for store/searcher; `EmbeddingEngineTest` + `EmbeddingSimilarityIT` gated with `@EnabledIf` when `onnx/model_qint8_avx512.onnx` is on the **test** classpath (file gitignored; download locally for full runs).

## Configuration

- Default: `hs.matcher.onnx.enabled=false` so the app starts without a model.
- To run embedding tests or enable later wiring: place `model_qint8_avx512.onnx` under `src/test/resources/onnx/` (or main resources), set `enabled=true` and `model-path` accordingly.

## Follow-up (done in Phase 4 slice, 2026-04-03)

- **`NomenclatureIndexBundle.load(..., Optional<EmbeddingEngine>)`** builds **`LanguageEmbeddingIndex`** per loaded language when ONNX is enabled (same atomic snapshot as Lucene).
- **`NomenclatureSearchRuntime.search`** uses **RRF** over lexical + semantic ranks; **`SearchResponse.hybridEnabled`**.

## Still open

- Labeled eval harness / per-channel score explanation in JSON (roadmap Phase 4 polish).
