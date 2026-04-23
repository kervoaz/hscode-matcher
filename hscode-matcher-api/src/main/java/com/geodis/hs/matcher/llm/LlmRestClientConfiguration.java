package com.geodis.hs.matcher.llm;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(BulkLlmProperties.class)
public class LlmRestClientConfiguration {

    @Bean
    public RestClient llmRestClient(BulkLlmProperties properties) {
        return LlmChapterRefinementService.createRestClient(properties);
    }
}
