package com.geodis.hs.matcher;

import static org.assertj.core.api.Assertions.assertThat;

import com.geodis.hs.matcher.search.lucene.LuceneLexicalSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ApplicationContextTest {

    @Autowired
    private LuceneLexicalSearchService lexicalSearchService;

    @Test
    void contextLoadsAndLexicalBeanPresent() {
        assertThat(lexicalSearchService).isNotNull();
    }
}
