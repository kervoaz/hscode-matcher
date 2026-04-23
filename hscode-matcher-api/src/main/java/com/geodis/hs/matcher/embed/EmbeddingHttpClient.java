package com.geodis.hs.matcher.embed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Calls Ollama {@code /api/embeddings} or OpenAI-compatible {@code /v1/embeddings}. */
@Component
public class EmbeddingHttpClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingHttpClient.class);

    private final BulkEmbeddingProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Object queryCacheLock = new Object();
    private final LinkedHashMap<String, float[]> queryCache;

    public EmbeddingHttpClient(
            BulkEmbeddingProperties properties,
            ObjectMapper objectMapper,
            @Qualifier("embeddingRestClient") RestClient embeddingRestClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = embeddingRestClient;
        int max = Math.max(0, properties.getQueryCacheMaxSize());
        if (max > 0) {
            this.queryCache =
                    new LinkedHashMap<>(16, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                            return size() > max;
                        }
                    };
        } else {
            this.queryCache = null;
        }
    }

    /** Clears the query-text LRU (e.g. after nomenclature reload or embedding model change). */
    public void clearQueryCache() {
        if (queryCache != null) {
            synchronized (queryCacheLock) {
                queryCache.clear();
            }
        }
    }

    /** L2-normalized embedding; empty array if disabled or HTTP failure. */
    public float[] embedOrEmpty(String text) {
        if (!properties.isEnabled()) {
            return new float[0];
        }
        if (text == null || text.isBlank()) {
            return new float[0];
        }
        String t = text.length() > 8000 ? text.substring(0, 8000) : text;
        String cacheKey = queryCacheKey(t);
        if (queryCache != null) {
            synchronized (queryCacheLock) {
                float[] cached = queryCache.get(cacheKey);
                if (cached != null) {
                    if (log.isTraceEnabled()) {
                        log.trace("embedding cache hit textLen={}", t.length());
                    }
                    return Arrays.copyOf(cached, cached.length);
                }
            }
        }
        try {
            long t0 = System.nanoTime();
            String body = buildRequestJson(t);
            String path = pathForStyle(properties.getStyle());
            String raw =
                    restClient
                            .post()
                            .uri(path)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(String.class);
            long httpMs = (System.nanoTime() - t0) / 1_000_000L;
            if (log.isDebugEnabled()) {
                log.debug("embedding HTTP path={} textLen={} httpMs={}", path, t.length(), httpMs);
            } else if (httpMs > 1500L) {
                log.warn("embedding HTTP slow path={} textLen={} httpMs={}", path, t.length(), httpMs);
            }
            float[] v = l2Normalize(parseEmbedding(raw));
            if (queryCache != null && v.length > 0) {
                synchronized (queryCacheLock) {
                    queryCache.put(cacheKey, Arrays.copyOf(v, v.length));
                }
            }
            return v;
        } catch (RestClientException e) {
            if (log.isDebugEnabled()) {
                log.debug("embedding HTTP error: {}", e.getMessage());
            }
            return new float[0];
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("embedding error: {}", e.getMessage());
            }
            return new float[0];
        }
    }

    private String queryCacheKey(String t) {
        String style = properties.getStyle() == null ? "" : properties.getStyle().strip().toLowerCase();
        String model = properties.getModel() == null ? "" : properties.getModel();
        return model + "\0" + style + "\0" + t;
    }

    private String pathForStyle(String style) {
        String s = style == null ? "" : style.strip().toLowerCase();
        if (s.equals("openai")) {
            return "/v1/embeddings";
        }
        return "/api/embeddings";
    }

    private String buildRequestJson(String text) throws Exception {
        String s = properties.getStyle() == null ? "" : properties.getStyle().strip().toLowerCase();
        if (s.equals("openai")) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", properties.getModel());
            root.put("input", text);
            return objectMapper.writeValueAsString(root);
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.getModel());
        root.put("prompt", text);
        return objectMapper.writeValueAsString(root);
    }

    private float[] parseEmbedding(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            return new float[0];
        }
        JsonNode root = objectMapper.readTree(raw);
        String s = properties.getStyle() == null ? "" : properties.getStyle().strip().toLowerCase();
        if (s.equals("openai")) {
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                return new float[0];
            }
            return readFloatArray(data.get(0).path("embedding"));
        }
        return readFloatArray(root.path("embedding"));
    }

    private static float[] readFloatArray(JsonNode arr) {
        if (!arr.isArray() || arr.isEmpty()) {
            return new float[0];
        }
        List<Float> tmp = new ArrayList<>(arr.size());
        for (JsonNode n : arr) {
            if (n.isNumber()) {
                tmp.add(n.floatValue());
            }
        }
        float[] out = new float[tmp.size()];
        for (int i = 0; i < tmp.size(); i++) {
            out[i] = tmp.get(i);
        }
        return out;
    }

    static float[] l2Normalize(float[] v) {
        if (v.length == 0) {
            return v;
        }
        double sum = 0;
        for (float x : v) {
            sum += (double) x * x;
        }
        double norm = Math.sqrt(sum);
        if (norm < 1e-9) {
            return v;
        }
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = (float) (v[i] / norm);
        }
        return out;
    }

    public static RestClient createRestClient(BulkEmbeddingProperties properties) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1, properties.getConnectTimeoutSeconds()) * 1000);
        factory.setReadTimeout(Math.max(1, properties.getReadTimeoutSeconds()) * 1000);
        RestClient.Builder b =
                RestClient.builder()
                        .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                        .requestFactory(factory);
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            b.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey().strip());
        }
        return b.build();
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
