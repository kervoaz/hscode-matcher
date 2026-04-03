package com.geodis.hs.matcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nomenclature.admin")
public class NomenclatureAdminProperties {

    /**
     * If non-blank, {@code POST /api/v1/admin/reload} requires header {@code X-Reload-Token} with
     * this exact value.
     */
    private String reloadToken = "";

    public String getReloadToken() {
        return reloadToken;
    }

    public void setReloadToken(String reloadToken) {
        this.reloadToken = reloadToken;
    }
}
