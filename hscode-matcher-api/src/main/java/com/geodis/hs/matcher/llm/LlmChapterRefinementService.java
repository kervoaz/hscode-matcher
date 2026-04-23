package com.geodis.hs.matcher.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.geodis.hs.matcher.classify.ChapterAggregation;
import com.geodis.hs.matcher.classify.ChapterCandidate;
import com.geodis.hs.matcher.domain.Language;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls an OpenAI-compatible {@code /v1/chat/completions} endpoint to pick the best HS chapter
 * (2-digit) given the product text and Lucene's top candidates.
 */
@Service
public class LlmChapterRefinementService {

    private static final Logger log = LoggerFactory.getLogger(LlmChapterRefinementService.class);
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[^{}]*(?:\\{[^{}]*}[^{}]*)*}");

    private final BulkLlmProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public LlmChapterRefinementService(
            BulkLlmProperties properties,
            ObjectMapper objectMapper,
            @Qualifier("llmRestClient") RestClient llmRestClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = llmRestClient;
    }

    /**
     * Returns empty if LLM is disabled or skipped ({@link BulkLlmProperties#isOnlyWhenAmbiguous()}).
     * Otherwise performs one chat completion and parses JSON from the assistant message.
     */
    public Optional<LlmChapterRefinement> refine(
            String description, Language language, ChapterAggregation lucene) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        if (description == null || description.isBlank()) {
            return Optional.empty();
        }
        if (properties.isOnlyWhenAmbiguous() && !lucene.ambiguous() && lucene.bestChapterCode() != null) {
            return Optional.empty();
        }

        String desc = truncate(description.strip(), properties.getMaxDescriptionChars());
        long t0 = System.nanoTime();
        try {
            String body = buildRequestJson(desc, language, lucene);
            String raw =
                    restClient
                            .post()
                            .uri(properties.getChatPath())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(String.class);
            if (log.isDebugEnabled()) {
                long ms = (System.nanoTime() - t0) / 1_000_000L;
                log.debug(
                        "LLM chapter refine HTTP ok ms={} lang={} descChars={} ambiguousLucene={} bestChapter={}",
                        ms,
                        language,
                        desc.length(),
                        lucene.ambiguous(),
                        lucene.bestChapterCode());
            }
            return Optional.of(parseAssistantJson(raw));
        } catch (RestClientException e) {
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            log.warn("LLM chapter refinement HTTP error after {} ms: {}", ms, e.getMessage());
            return Optional.of(LlmChapterRefinement.error("HTTP: " + e.getMessage()));
        } catch (Exception e) {
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            log.warn("LLM chapter refinement failed after {} ms", ms, e);
            return Optional.of(LlmChapterRefinement.error(e.getMessage()));
        }
    }

    private String buildRequestJson(String description, Language language, ChapterAggregation lucene)
            throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.getModel());
        root.put("temperature", properties.getTemperature());
        root.put("max_tokens", properties.getMaxOutputTokens());
        ArrayNode messages = root.putArray("messages");
        ObjectNode sys = messages.addObject();
        sys.put("role", "system");
        sys.put("content", systemPrompt(language));
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userPrompt(description, language, lucene));
        return objectMapper.writeValueAsString(root);
    }

    private static String systemPrompt(Language lang) {
        boolean fr = lang == Language.FR;
        if (fr) {
            return "Tu es un expert en classification douanière Harmonized System (HS). "
                    + "Tu reçois une description de marchandise et une liste de chapitres HS (2 chiffres) "
                    + "proposés par un moteur de recherche. Choisis le chapitre le plus pertinent. "
                    + "Réponds UNIQUEMENT avec un objet JSON valide, sans markdown, avec les clés: "
                    + "chapter (string 2 chiffres), confidence (nombre entre 0 et 1), "
                    + "ambiguous (boolean), rationale (string courte en français).";
        }
        return "You are an expert in Harmonized System (HS) customs classification. "
                + "You receive a goods description and a list of candidate HS chapters (2-digit codes) "
                + "from a search engine. Pick the most appropriate chapter. "
                + "Reply with ONLY a valid JSON object, no markdown, keys: "
                + "chapter (2-digit string), confidence (0..1 number), "
                + "ambiguous (boolean), rationale (short string in English).";
    }

    private static String userPrompt(String description, Language language, ChapterAggregation lucene) {
        StringBuilder sb = new StringBuilder();
        sb.append("Product description:\n").append(description).append("\n\n");
        sb.append("Language context: ").append(language.name()).append("\n\n");
        sb.append("Retrieval candidates (2-digit chapter, official title, internal score):\n");
        List<ChapterCandidate> top = lucene.refinementCandidates();
        if (top.isEmpty()) {
            top = lucene.top3();
        }
        if (top.isEmpty()) {
            sb.append("(none — pick the best chapter you can infer from the text alone.)\n");
        } else {
            for (ChapterCandidate c : top) {
                sb.append("- ")
                        .append(c.code())
                        .append(" — ")
                        .append(c.description() == null ? "" : c.description())
                        .append(" (score ")
                        .append(c.score())
                        .append(")\n");
            }
        }
        if (lucene.bestChapterCode() != null) {
            sb.append("\nLucene top chapter: ")
                    .append(lucene.bestChapterCode())
                    .append(" — ")
                    .append(lucene.bestChapterDescription() == null ? "" : lucene.bestChapterDescription())
                    .append("\n");
        }
        sb.append("\nReturn JSON only.");
        return sb.toString();
    }

    private LlmChapterRefinement parseAssistantJson(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return LlmChapterRefinement.error("empty response");
        }
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return LlmChapterRefinement.error("no choices in response");
            }
            String content = choices.get(0).path("message").path("content").asText("");
            String json = extractJsonObject(content);
            JsonNode obj = objectMapper.readTree(json);
            String chapter = normalizeChapter(obj.path("chapter").asText(null));
            double conf = clamp01(obj.path("confidence").asDouble(Double.NaN));
            if (Double.isNaN(conf)) {
                conf = 0.5;
            }
            boolean amb = obj.path("ambiguous").asBoolean(false);
            String rationale = obj.path("rationale").asText(null);
            if (chapter == null) {
                return LlmChapterRefinement.error("missing or invalid chapter in model JSON");
            }
            return new LlmChapterRefinement(chapter, conf, amb, rationale, null);
        } catch (Exception e) {
            return LlmChapterRefinement.error("parse: " + e.getMessage());
        }
    }

    private static String extractJsonObject(String content) {
        String t = content.strip();
        if (t.startsWith("```")) {
            int start = t.indexOf('{');
            int end = t.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return t.substring(start, end + 1);
            }
        }
        Matcher m = JSON_OBJECT.matcher(t);
        if (m.find()) {
            return m.group();
        }
        return t;
    }

    private static String normalizeChapter(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() >= 2) {
            return digits.substring(0, 2);
        }
        if (digits.length() == 1) {
            return "0" + digits;
        }
        return null;
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v) || v < 0) {
            return 0.0;
        }
        return Math.min(1.0, v);
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...";
    }

    /** Builds a {@link RestClient} for the configured LLM base URL and timeouts. */
    public static RestClient createRestClient(BulkLlmProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1, properties.getConnectTimeoutSeconds()) * 1000);
        factory.setReadTimeout(Math.max(1, properties.getReadTimeoutSeconds()) * 1000);
        RestClient.Builder b =
                RestClient.builder()
                        .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                        .requestFactory(factory);
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            b.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey().strip());
        }
        return b.build();
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
