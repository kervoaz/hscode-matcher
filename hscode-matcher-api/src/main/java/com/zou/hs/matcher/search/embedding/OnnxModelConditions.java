package com.zou.hs.matcher.search.embedding;

/** JUnit {@code @EnabledIf} helpers: integration tests skip when the ONNX file is not on the test classpath. */
public final class OnnxModelConditions {

    private OnnxModelConditions() {}

    public static boolean testModelOnClasspath() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return cl.getResource("onnx/model_qint8_avx512.onnx") != null;
    }
}
