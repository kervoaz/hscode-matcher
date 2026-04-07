package com.zou.hs.matcher.search.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.zou.hs.matcher.domain.Language;
import com.zou.hs.matcher.search.model.ScoredHit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "hs.matcher.onnx.enabled=true",
            "hs.matcher.onnx.model-path=classpath:onnx/model_qint8_avx512.onnx"
        })
@EnabledIf("com.zou.hs.matcher.search.embedding.OnnxModelConditions#testModelOnClasspath")
class EmbeddingSimilarityIT {

    @Autowired private EmbeddingEngine engine;

    @Autowired private EmbeddingStore store;

    @Autowired private EmbeddingSearcher searcher;

    @Test
    void carAndAutomobile_similarityAboveThreshold() throws Exception {
        float[][] matrix = new float[3][];
        matrix[0] = engine.embed("car");
        matrix[1] = engine.embed("bicycle");
        matrix[2] = engine.embed("ship");
        String[] codes = {"MOCK-CAR", "MOCK-BIKE", "MOCK-SHIP"};
        store.initialize(Language.EN, matrix, codes);

        float[] queryVec = engine.embed("automobile");
        List<ScoredHit> results = searcher.search(queryVec, Language.EN, 3);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).hsCode()).isEqualTo("MOCK-CAR");
        assertThat(results.get(0).score()).isGreaterThan(0.85f);
    }

    @Test
    void emptyStore_returnsEmptyList() throws Exception {
        float[] queryVec = engine.embed("Kraftfahrzeug");
        List<ScoredHit> results = searcher.search(queryVec, Language.DE, 5);
        assertThat(results).isEmpty();
    }
}
