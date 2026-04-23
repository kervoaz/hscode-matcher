package com.geodis.hs.matcher.embed;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Optional embedding HTTP client for chapter-level semantic retrieval (Ollama or OpenAI-style). */
@ConfigurationProperties(prefix = "bulk.embedding")
public class BulkEmbeddingProperties {

    private boolean enabled = false;

    /** When true, builds per-language chapter vectors at startup (blocking) if CSV is ready. */
    private boolean eagerInit = false;

    private String baseUrl = "http://127.0.0.1:11434";
    private String apiKey = "";
    private String model = "nomic-embed-text";

    /** {@code ollama} → POST /api/embeddings ; {@code openai} → POST /v1/embeddings */
    private String style = "ollama";

    private int connectTimeoutSeconds = 15;
    private int readTimeoutSeconds = 120;

    /** Reciprocal-rank fusion constant (higher ⇒ smoother rank blending). */
    private int rrfK = 30;

    /** Max chapters returned from semantic similarity before fusion. */
    private int semanticPool = 10;

    /** Chapters passed to the LLM after Lucene+semantic fusion (capped). */
    private int refinementCandidateCount = 5;

    /** Lexical chapters (Lucene) participating in RRF, best-first. */
    private int lexicalRankPool = 10;

    /**
     * LRU size for query-text embeddings (same description in bulk ⇒ one HTTP call). {@code 0} =
     * disabled.
     */
    private int queryCacheMaxSize = 50_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEagerInit() {
        return eagerInit;
    }

    public void setEagerInit(boolean eagerInit) {
        this.eagerInit = eagerInit;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    public int getRrfK() {
        return rrfK;
    }

    public void setRrfK(int rrfK) {
        this.rrfK = rrfK;
    }

    public int getSemanticPool() {
        return semanticPool;
    }

    public void setSemanticPool(int semanticPool) {
        this.semanticPool = semanticPool;
    }

    public int getRefinementCandidateCount() {
        return refinementCandidateCount;
    }

    public void setRefinementCandidateCount(int refinementCandidateCount) {
        this.refinementCandidateCount = refinementCandidateCount;
    }

    public int getLexicalRankPool() {
        return lexicalRankPool;
    }

    public void setLexicalRankPool(int lexicalRankPool) {
        this.lexicalRankPool = lexicalRankPool;
    }

    public int getQueryCacheMaxSize() {
        return queryCacheMaxSize;
    }

    public void setQueryCacheMaxSize(int queryCacheMaxSize) {
        this.queryCacheMaxSize = queryCacheMaxSize;
    }
}
