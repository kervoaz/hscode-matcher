package com.geodis.hs.matcher.bulk.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.geodis.hs.matcher.api.dto.bulk.BulkChapterItemResult;
import com.geodis.hs.matcher.search.LexicalSearchParams;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BulkChapterPersistenceServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry r) {
        r.add("bulk.persistence.enabled", () -> "true");
        r.add("bulk.persistence.url", postgres::getJdbcUrl);
        r.add("bulk.persistence.username", postgres::getUsername);
        r.add("bulk.persistence.password", postgres::getPassword);
        r.add("bulk.persistence.batch-size", () -> "10");
    }

    @Autowired
    private BulkChapterPersistenceService persistence;

    @Autowired
    private JdbcTemplate bulkJdbcTemplate;

    @Test
    void insertsRunItemsAndFinalizesSingleChunk() {
        UUID runId = UUID.randomUUID();
        LexicalSearchParams tuning = LexicalSearchParams.DEFAULT;
        persistence.ensureRun(runId, "test-pipeline", "FR", tuning, 1);
        BulkChapterItemResult row =
                new BulkChapterItemResult(
                        "q1",
                        "robe",
                        "62",
                        "Habillement",
                        0.8,
                        List.of(),
                        false,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0L,
                        null,
                        5L);
        try (BulkChapterPersistenceService.Session session = persistence.openSession(runId)) {
            session.append(0, "q1", row);
            session.completeChunk(0, 1);
        }

        Integer itemRows =
                bulkJdbcTemplate.queryForObject(
                        "SELECT COUNT(*)::int FROM bulk_run_item WHERE run_id = ?", Integer.class, runId);
        assertThat(itemRows).isEqualTo(1);

        String status =
                bulkJdbcTemplate.queryForObject(
                        "SELECT status FROM bulk_run WHERE id = ?", String.class, runId);
        assertThat(status).isEqualTo("DONE");
    }
}
