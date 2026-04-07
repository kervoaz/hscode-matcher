package com.zou.hs.matcher.config;

import com.zou.hs.matcher.search.embedding.EmbeddingEngine;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Readiness-style signal: OUT_OF_SERVICE when no nomenclature is loaded; UP otherwise with lexical
 * / hybrid language lists.
 */
@Component
public class NomenclatureHealthIndicator implements HealthIndicator {

    private final NomenclatureSearchRuntime searchRuntime;
    private final ObjectProvider<EmbeddingEngine> embeddingEngineProvider;

    public NomenclatureHealthIndicator(
            NomenclatureSearchRuntime searchRuntime, ObjectProvider<EmbeddingEngine> embeddingEngineProvider) {
        this.searchRuntime = searchRuntime;
        this.embeddingEngineProvider = embeddingEngineProvider;
    }

    @Override
    public Health health() {
        if (!searchRuntime.anyLanguageReady()) {
            return Health.outOfService()
                    .withDetail("reason", "No nomenclature CSV loaded for any language")
                    .build();
        }
        return Health.up()
                .withDetail("lexicalLanguages", searchRuntime.lexicalReadyLanguageTags())
                .withDetail("hybridLanguages", searchRuntime.hybridReadyLanguageTags())
                .withDetail("onnxBeanPresent", embeddingEngineProvider.getIfAvailable() != null)
                .build();
    }
}
