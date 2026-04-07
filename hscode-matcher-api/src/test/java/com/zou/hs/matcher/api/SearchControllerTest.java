package com.zou.hs.matcher.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zou.hs.matcher.config.NomenclatureSearchRuntime;
import com.zou.hs.matcher.domain.Language;
import com.zou.hs.matcher.ingestion.NomenclatureRegistry;
import com.zou.hs.matcher.search.OutcomeMeta;
import com.zou.hs.matcher.search.UnifiedSearchOutcome;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SearchController.class)
class SearchControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private NomenclatureSearchRuntime searchRuntime;

    @Test
    void search_hybridFalse_passesLexicalOnlyOption() throws Exception {
        NomenclatureRegistry reg = new NomenclatureRegistry(Language.EN, Map.of());
        when(searchRuntime.isReady(Language.EN)).thenReturn(true);
        when(searchRuntime.configuredRrfK()).thenReturn(60);
        when(searchRuntime.candidatePoolForLimit(anyInt())).thenReturn(80);
        when(searchRuntime.candidatePoolForLimit(anyInt(), anyInt())).thenReturn(80);
        when(searchRuntime.search(eq(Language.EN), eq("coffee"), eq(10), any()))
                .thenReturn(new UnifiedSearchOutcome(List.of(), 0, false, 80, OutcomeMeta.empty()));
        when(searchRuntime.registry(Language.EN)).thenReturn(Optional.of(reg));

        mockMvc.perform(
                        get("/api/v1/search")
                                .param("q", "coffee")
                                .param("lang", "EN")
                                .param("limit", "10")
                                .param("hybrid", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hybridSuppressedByRequest").value(true))
                .andExpect(jsonPath("$.hybridEnabled").value(false))
                .andExpect(jsonPath("$.effectiveRrfK").value(60))
                .andExpect(jsonPath("$.debug").exists());

        verify(searchRuntime)
                .search(
                        eq(Language.EN),
                        eq("coffee"),
                        eq(10),
                        argThat(o -> !o.allowHybrid() && o.rrfKOverride() == 0 && o.poolMultiplierOverride() == 0));
    }

    @Test
    void search_rrfKOverride_echoedInResponse() throws Exception {
        NomenclatureRegistry reg = new NomenclatureRegistry(Language.FR, Map.of());
        when(searchRuntime.isReady(Language.FR)).thenReturn(true);
        when(searchRuntime.configuredRrfK()).thenReturn(60);
        when(searchRuntime.candidatePoolForLimit(anyInt())).thenReturn(80);
        when(searchRuntime.candidatePoolForLimit(anyInt(), anyInt())).thenReturn(80);
        when(searchRuntime.search(eq(Language.FR), eq("thé"), eq(10), any()))
                .thenReturn(new UnifiedSearchOutcome(List.of(), 0, false, 80, OutcomeMeta.empty()));
        when(searchRuntime.registry(Language.FR)).thenReturn(Optional.of(reg));

        mockMvc.perform(
                        get("/api/v1/search")
                                .param("q", "thé")
                                .param("lang", "FR")
                                .param("rrfK", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveRrfK").value(42))
                .andExpect(jsonPath("$.hybridSuppressedByRequest").value(false));

        verify(searchRuntime)
                .search(
                        eq(Language.FR),
                        eq("thé"),
                        eq(10),
                        argThat(o -> o.allowHybrid() && o.rrfKOverride() == 42));
    }
}
