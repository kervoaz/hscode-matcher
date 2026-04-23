package com.geodis.hs.matcher.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.geodis.hs.matcher.classify.ChapterAggregation;
import com.geodis.hs.matcher.classify.ChapterCandidate;
import com.geodis.hs.matcher.classify.LuceneChapterClassifier;
import com.geodis.hs.matcher.config.NomenclatureSearchRuntime;
import com.geodis.hs.matcher.domain.Language;
import com.geodis.hs.matcher.llm.BulkLlmProperties;
import com.geodis.hs.matcher.llm.LlmChapterRefinementService;
import com.geodis.hs.matcher.search.LexicalSearchParams;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = BulkChapterClassifyController.class)
class BulkChapterClassifyControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private NomenclatureSearchRuntime searchRuntime;

    @MockBean private LuceneChapterClassifier luceneChapterClassifier;

    @MockBean private LlmChapterRefinementService llmChapterRefinementService;

    @MockBean private BulkChapterOutputService bulkChapterOutputService;

    @MockBean private BulkLlmProperties bulkLlmProperties;

    @BeforeEach
    void stubDiskOutput() throws Exception {
        lenient()
                .when(bulkChapterOutputService.writeJsonIfConfigured(anyString(), any()))
                .thenReturn(Optional.empty());
        lenient()
                .when(bulkChapterOutputService.writeCsvIfConfigured(anyString(), any()))
                .thenReturn(Optional.empty());
        lenient()
                .when(llmChapterRefinementService.refine(anyString(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(bulkLlmProperties.isEnabled()).thenReturn(false);
        lenient().when(bulkLlmProperties.isOnlyWhenAmbiguous()).thenReturn(false);
    }

    @Test
    void jsonBulk_returnsLuceneFields() throws Exception {
        when(searchRuntime.isReady(Language.FR)).thenReturn(true);
        when(luceneChapterClassifier.classify(eq(Language.FR), any(), any(LexicalSearchParams.class), anyInt()))
                .thenReturn(
                        new ChapterAggregation(
                                "84",
                                "Vêtements",
                                0.7,
                                List.of(new ChapterCandidate("84", "Vêtements", 5.0)),
                                false,
                                false,
                                null,
                                List.of(new ChapterCandidate("84", "Vêtements", 5.0)),
                                List.of(new ChapterCandidate("84", "Vêtements", 5.0))));

        String body =
                """
                {"lang":"FR","items":[{"id":"q1","description":"robe"}]}
                """;
        mockMvc.perform(
                        post("/api/v1/bulk/chapter-classify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lang").value("FR"))
                .andExpect(jsonPath("$.items[0].chapterLucene").value("84"))
                .andExpect(jsonPath("$.items[0].confidenceLucene").value(0.7))
                .andExpect(jsonPath("$.summary.itemCount").value(1));
    }

    @Test
    void createBulkRunId_returnsUuid() throws Exception {
        mockMvc.perform(post("/api/v1/bulk/chapter-classify/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").exists());
    }

    @Test
    void jsonBulk_serviceUnavailableWhenLanguageNotReady() throws Exception {
        when(searchRuntime.isReady(Language.FR)).thenReturn(false);
        String body = "{\"lang\":\"FR\",\"items\":[{\"id\":\"1\",\"description\":\"x\"}]}";
        mockMvc.perform(
                        post("/api/v1/bulk/chapter-classify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void csvBulk_appendsColumns() throws Exception {
        when(searchRuntime.isReady(Language.FR)).thenReturn(true);
        when(luceneChapterClassifier.classify(eq(Language.FR), any(), any(LexicalSearchParams.class), anyInt()))
                .thenReturn(
                        new ChapterAggregation(
                                "62",
                                "Habillement",
                                0.5,
                                List.of(new ChapterCandidate("62", "Habillement", 2.0)),
                                true,
                                false,
                                null,
                                List.of(new ChapterCandidate("62", "Habillement", 2.0)),
                                List.of(new ChapterCandidate("62", "Habillement", 2.0))));

        String csv = "Quotation ID;Product Description\nabc-1;pantalon\n";
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "in.csv",
                        "text/csv",
                        csv.getBytes(StandardCharsets.UTF_8));

        byte[] out =
                mockMvc.perform(
                                multipart("/api/v1/bulk/chapter-classify")
                                        .file(file)
                                        .param("lang", "FR"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();

        String text = new String(out, StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(text).contains("chapter_lucene");
        org.assertj.core.api.Assertions.assertThat(text).contains("62");
        org.assertj.core.api.Assertions.assertThat(text).contains("pantalon");
    }
}
