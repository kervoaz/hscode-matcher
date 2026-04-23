package com.geodis.hs.matcher.bulk.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** JDBC settings for bulk chapter persistence ({@code bulk.persistence.*}). */
@ConfigurationProperties(prefix = "bulk.persistence")
public class BulkPersistenceProperties {

    private boolean enabled = false;
    private String url = "";
    private String username = "";
    private String password = "";
    /** Flush buffered items to {@code bulk_run_item} after this many rows. */
    private int batchSize = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
