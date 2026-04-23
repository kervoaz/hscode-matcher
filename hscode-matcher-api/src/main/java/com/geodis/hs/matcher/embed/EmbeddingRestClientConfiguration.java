package com.geodis.hs.matcher.embed;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(BulkEmbeddingProperties.class)
public class EmbeddingRestClientConfiguration {

    @Bean
    RestClient embeddingRestClient(BulkEmbeddingProperties properties) {
        return EmbeddingHttpClient.createRestClient(properties);
    }
}
