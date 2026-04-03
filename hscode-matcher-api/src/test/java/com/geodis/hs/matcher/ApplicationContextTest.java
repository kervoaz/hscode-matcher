package com.geodis.hs.matcher;

import static org.assertj.core.api.Assertions.assertThat;

import com.geodis.hs.matcher.config.NomenclatureSearchRuntime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ApplicationContextTest {

    @Autowired
    private NomenclatureSearchRuntime nomenclatureSearchRuntime;

    @Test
    void contextLoadsAndSearchRuntimePresent() {
        assertThat(nomenclatureSearchRuntime).isNotNull();
    }
}
