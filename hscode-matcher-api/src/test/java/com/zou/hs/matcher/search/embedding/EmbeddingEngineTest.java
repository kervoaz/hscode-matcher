package com.zou.hs.matcher.search.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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
class EmbeddingEngineTest {

    @Autowired private EmbeddingEngine embeddingEngine;

    @Test
    void embed_returnsLength384() throws Exception {
        float[] v = embeddingEngine.embed("test text for dimension");
        assertThat(v).hasSize(384);
    }

    @Test
    void embed_vectorIsL2Unit() throws Exception {
        float[] v = embeddingEngine.embed("normalization check phrase");
        double sumSq = 0;
        for (float x : v) {
            sumSq += (double) x * x;
        }
        assertThat(sumSq).isCloseTo(1.0, within(1e-4));
    }

    @Test
    void embed_typicalText_doesNotThrow() throws Exception {
        assertThat(embeddingEngine.embed("motor vehicles and parts")).isNotEmpty();
    }
}
