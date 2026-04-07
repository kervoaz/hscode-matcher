package com.zou.hs.matcher.search.embedding;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@Configuration
@ConditionalOnProperty(prefix = "hs.matcher.onnx", name = "enabled", havingValue = "true")
public class EmbeddingConfig {

    @Bean(destroyMethod = "")
    public OrtEnvironment ortEnvironment() throws OrtException {
        return OrtEnvironment.getEnvironment();
    }

    @Bean
    public EmbeddingEngine embeddingEngine(
            OrtEnvironment env,
            ResourceLoader resourceLoader,
            @Value("${hs.matcher.onnx.model-path}") String modelPath)
            throws Exception {
        if (modelPath == null || modelPath.isBlank()) {
            throw new IllegalStateException("hs.matcher.onnx.model-path must be set when hs.matcher.onnx.enabled=true");
        }
        URL tokenizerUrl = EmbeddingConfig.class.getResource("/onnx/tokenizer.json");
        if (tokenizerUrl == null) {
            throw new IllegalStateException("Missing classpath resource /onnx/tokenizer.json");
        }
        HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(Paths.get(tokenizerUrl.toURI()));
        Resource modelResource = resourceLoader.getResource(modelPath.trim());
        if (!modelResource.exists() || !modelResource.isReadable()) {
            throw new IllegalStateException("ONNX model not readable: " + modelPath);
        }
        byte[] modelBytes;
        try (InputStream is = modelResource.getInputStream()) {
            modelBytes = is.readAllBytes();
        }
        try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
            OrtSession session = env.createSession(modelBytes, opts);
            Set<String> discovered = new LinkedHashSet<>(session.getInputNames());
            return new EmbeddingEngine(env, new AtomicReference<>(session), tokenizer, discovered);
        }
    }
}
