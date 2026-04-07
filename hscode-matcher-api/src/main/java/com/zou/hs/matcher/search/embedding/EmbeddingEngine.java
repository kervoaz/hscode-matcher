package com.zou.hs.matcher.search.embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Multilingual sentence embeddings via ONNX (e.g. paraphrase-multilingual-MiniLM-L12-v2): mean pool
 * over last hidden states with attention mask, then L2-normalize so dot product equals cosine
 * similarity.
 */
public final class EmbeddingEngine {

    private final OrtEnvironment env;
    private final AtomicReference<OrtSession> sessionRef;
    private final HuggingFaceTokenizer tokenizer;
    private final Set<String> inputNames;

    public EmbeddingEngine(
            OrtEnvironment env,
            AtomicReference<OrtSession> sessionRef,
            HuggingFaceTokenizer tokenizer,
            Set<String> inputNames) {
        this.env = env;
        this.sessionRef = sessionRef;
        this.tokenizer = tokenizer;
        this.inputNames = inputNames;
    }

    public Set<String> inputNames() {
        return inputNames;
    }

    public float[] embed(String text) throws OrtException {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        Encoding encoding = tokenizer.encode(text.trim());
        long[] inputIds = encoding.getIds();
        long[] attentionMask = encoding.getAttentionMask();
        long[] tokenTypeIds = typeIdsAligned(encoding, inputIds.length);
        if (attentionMask.length != inputIds.length) {
            throw new IllegalStateException(
                    "attention_mask length " + attentionMask.length + " != input_ids " + inputIds.length);
        }

        OrtSession session = sessionRef.get();
        List<OnnxTensor> tensors = new ArrayList<>();
        try {
            Map<String, OnnxTensor> feeds = new LinkedHashMap<>();
            for (String name : inputNames) {
                if ("input_ids".equals(name)) {
                    OnnxTensor t = OnnxTensor.createTensor(env, unsqueeze(inputIds));
                    tensors.add(t);
                    feeds.put(name, t);
                } else if ("attention_mask".equals(name)) {
                    OnnxTensor t = OnnxTensor.createTensor(env, unsqueeze(attentionMask));
                    tensors.add(t);
                    feeds.put(name, t);
                } else if ("token_type_ids".equals(name)) {
                    OnnxTensor t = OnnxTensor.createTensor(env, unsqueeze(tokenTypeIds));
                    tensors.add(t);
                    feeds.put(name, t);
                } else {
                    throw new IllegalStateException("Unknown ONNX input name: " + name);
                }
            }
            try (OrtSession.Result result = session.run(feeds)) {
                float[][][] tokenEmbeddings = readLastHiddenState(session, result);
                return meanPoolAndL2Normalize(tokenEmbeddings[0], attentionMask);
            }
        } finally {
            for (OnnxTensor t : tensors) {
                t.close();
            }
        }
    }

    private static long[] typeIdsAligned(Encoding encoding, int seqLen) {
        long[] typeIds = encoding.getTypeIds();
        if (typeIds != null && typeIds.length == seqLen) {
            return typeIds;
        }
        return new long[seqLen];
    }

    private static long[][] unsqueeze(long[] row) {
        long[][] m = new long[1][row.length];
        System.arraycopy(row, 0, m[0], 0, row.length);
        return m;
    }

    private float[][][] readLastHiddenState(OrtSession session, OrtSession.Result result)
            throws OrtException {
        for (String outName : session.getOutputNames()) {
            OnnxValue val = result.get(outName).orElse(null);
            if (!(val instanceof OnnxTensor tensor)) {
                continue;
            }
            Object raw = tensor.getValue();
            if (raw instanceof float[][][] f) {
                return f;
            }
            throw new IllegalStateException(
                    "Expected float[][][] for output " + outName + ", got: " + raw.getClass());
        }
        throw new IllegalStateException("No float[][][] tensor in model outputs: " + session.getOutputNames());
    }

    private static float[] meanPoolAndL2Normalize(float[][] tokenEmbeddings, long[] attentionMask) {
        int dim = tokenEmbeddings[0].length;
        float[] pooled = new float[dim];
        int count = 0;
        for (int i = 0; i < tokenEmbeddings.length && i < attentionMask.length; i++) {
            if (attentionMask[i] == 1L) {
                for (int d = 0; d < dim; d++) {
                    pooled[d] += tokenEmbeddings[i][d];
                }
                count++;
            }
        }
        if (count > 0) {
            for (int d = 0; d < dim; d++) {
                pooled[d] /= count;
            }
        }
        return l2Normalize(pooled);
    }

    private static float[] l2Normalize(float[] v) {
        float norm = 0f;
        for (float x : v) {
            norm += x * x;
        }
        norm = (float) Math.sqrt(norm);
        if (norm < 1e-9f) {
            return v;
        }
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = v[i] / norm;
        }
        return out;
    }
}
