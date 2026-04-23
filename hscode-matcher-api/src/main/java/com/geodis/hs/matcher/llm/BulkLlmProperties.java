package com.geodis.hs.matcher.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** OpenAI-compatible chat completions for HS chapter refinement ({@code bulk.llm.*}). */
@ConfigurationProperties(prefix = "bulk.llm")
public class BulkLlmProperties {

    private boolean enabled = false;
    /** Origin only, e.g. {@code https://api.openai.com} or {@code http://localhost:11434}. */
    private String baseUrl = "https://api.openai.com";
    /** Bearer token (OpenAI, Azure, etc.). Ollama often works with an empty key. */
    private String apiKey = "";
    private String model = "gpt-4o-mini";
    /** Path on {@link #baseUrl}, e.g. {@code /v1/chat/completions}. */
    private String chatPath = "/v1/chat/completions";
    private int connectTimeoutSeconds = 15;
    private int readTimeoutSeconds = 120;
    /**
     * If true, call the LLM only when Lucene marks the row as ambiguous (saves latency/cost when
     * retrieval is already decisive).
     */
    private boolean onlyWhenAmbiguous = false;
    private int maxOutputTokens = 350;
    private double temperature = 0.15;
    /** Max characters of product description sent to the model. */
    private int maxDescriptionChars = 1200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    public String getChatPath() {
        return chatPath;
    }

    public void setChatPath(String chatPath) {
        this.chatPath = chatPath;
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

    public boolean isOnlyWhenAmbiguous() {
        return onlyWhenAmbiguous;
    }

    public void setOnlyWhenAmbiguous(boolean onlyWhenAmbiguous) {
        this.onlyWhenAmbiguous = onlyWhenAmbiguous;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxDescriptionChars() {
        return maxDescriptionChars;
    }

    public void setMaxDescriptionChars(int maxDescriptionChars) {
        this.maxDescriptionChars = maxDescriptionChars;
    }
}
