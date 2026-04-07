package com.zou.hs.matcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hs.matcher.embedding")
public class HsMatcherEmbeddingProperties {

    /**
     * Directory for embedding matrix cache files. Empty = always rebuild from ONNX at startup (no
     * disk cache).
     */
    private String cacheDir = "";

    /**
     * Bumped when the ONNX model or embedding strategy changes so old cache files are ignored.
     */
    private String cacheSalt = "1";

    public String getCacheDir() {
        return cacheDir;
    }

    public void setCacheDir(String cacheDir) {
        this.cacheDir = cacheDir;
    }

    public String getCacheSalt() {
        return cacheSalt;
    }

    public void setCacheSalt(String cacheSalt) {
        this.cacheSalt = cacheSalt;
    }
}
