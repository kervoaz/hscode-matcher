package com.zou.hs.matcher.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zou.hs.matcher.config.NomenclatureSearchRuntime;
import com.zou.hs.matcher.domain.HsEntry;
import com.zou.hs.matcher.domain.Language;
import com.zou.hs.matcher.ingestion.NomenclatureRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CodeLookupController.class)
class CodeLookupControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private NomenclatureSearchRuntime searchRuntime;

    @Test
    void lookup_ok_includesHierarchy() throws Exception {
        Language en = Language.EN;
        Map<String, HsEntry> map = new LinkedHashMap<>();
        map.put("87", new HsEntry("87", 2, "Vehicles", en, null));
        map.put("8703", new HsEntry("8703", 4, "Motor cars", en, "87"));
        map.put("870321", new HsEntry("870321", 6, "Of cylinder capacity <= 1000cc", en, "8703"));
        NomenclatureRegistry reg = new NomenclatureRegistry(en, map);

        when(searchRuntime.isReady(en)).thenReturn(true);
        when(searchRuntime.registry(en)).thenReturn(Optional.of(reg));

        mockMvc.perform(get("/api/v1/codes/870321").param("lang", "EN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("870321"))
                .andExpect(jsonPath("$.level").value(6))
                .andExpect(jsonPath("$.description").value("Of cylinder capacity <= 1000cc"))
                .andExpect(jsonPath("$.lang").value("EN"))
                .andExpect(jsonPath("$.hierarchy.chapter.code").value("87"))
                .andExpect(jsonPath("$.hierarchy.heading.code").value("8703"));
    }

    @Test
    void lookup_normalizesDotsInPath() throws Exception {
        Language en = Language.EN;
        Map<String, HsEntry> map =
                Map.of("010121", new HsEntry("010121", 6, "Horses", en, "0101"), "0101", new HsEntry(
                        "0101", 4, "Live horses", en, "01"), "01", new HsEntry("01", 2, "Animals", en, null));
        NomenclatureRegistry reg = new NomenclatureRegistry(en, map);
        when(searchRuntime.isReady(en)).thenReturn(true);
        when(searchRuntime.registry(en)).thenReturn(Optional.of(reg));

        mockMvc.perform(get("/api/v1/codes/0101.21").param("lang", "EN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("010121"));
    }

    @Test
    void lookup_badCodeFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/codes/12345").param("lang", "EN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lookup_unknownCode_returns404() throws Exception {
        Language en = Language.EN;
        NomenclatureRegistry reg = new NomenclatureRegistry(en, Map.of());
        when(searchRuntime.isReady(en)).thenReturn(true);
        when(searchRuntime.registry(en)).thenReturn(Optional.of(reg));

        mockMvc.perform(get("/api/v1/codes/999999").param("lang", "EN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void lookup_notReady_returns503() throws Exception {
        when(searchRuntime.isReady(any())).thenReturn(false);

        mockMvc.perform(get("/api/v1/codes/010121").param("lang", "EN"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void lookup_invalidLang_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/codes/01").param("lang", "XX"))
                .andExpect(status().isBadRequest());
    }
}
