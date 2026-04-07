package com.zou.hs.matcher.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    NomenclatureCsvProperties.class,
    NomenclatureAdminProperties.class,
    HsMatcherEmbeddingProperties.class
})
public class NomenclatureSearchConfiguration {}
