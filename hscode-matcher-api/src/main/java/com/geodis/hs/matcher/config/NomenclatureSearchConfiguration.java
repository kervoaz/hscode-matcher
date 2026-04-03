package com.geodis.hs.matcher.config;

import com.geodis.hs.matcher.search.lucene.LuceneLexicalSearchService;
import java.io.IOException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NomenclatureCsvProperties.class)
public class NomenclatureSearchConfiguration {

    @Bean
    public NomenclatureSearchInfrastructure nomenclatureSearchInfrastructure(NomenclatureCsvProperties props)
            throws IOException {
        return NomenclatureSearchInfrastructure.load(props);
    }

    @Bean
    public LuceneLexicalSearchService luceneLexicalSearchService(NomenclatureSearchInfrastructure infrastructure) {
        return infrastructure.lexicalSearchService();
    }
}
