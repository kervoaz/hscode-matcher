package com.geodis.hs.matcher.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    NomenclatureCsvProperties.class,
    NomenclatureAdminProperties.class,
    LexicalSearchRankProperties.class
})
public class NomenclatureSearchConfiguration {}
