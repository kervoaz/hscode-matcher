package com.geodis.hs.matcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nomenclature.csv")
public class NomenclatureCsvProperties {

    /**
     * UTF-8 CSV: {@code classpath:nomenclature/nomenclature-en.csv} or an absolute filesystem path.
     */
    private String en = "";

    private String fr = "";
    private String de = "";

    public String getEn() {
        return en;
    }

    public void setEn(String en) {
        this.en = en;
    }

    public String getFr() {
        return fr;
    }

    public void setFr(String fr) {
        this.fr = fr;
    }

    public String getDe() {
        return de;
    }

    public void setDe(String de) {
        this.de = de;
    }
}
