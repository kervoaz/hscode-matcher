package com.geodis.hs.matcher.bulk.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.DependsOn;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(prefix = "bulk.persistence", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(BulkPersistenceProperties.class)
public class BulkPersistenceJdbcConfiguration {

    @Bean
    public DataSource bulkDataSource(BulkPersistenceProperties properties) {
        if (!StringUtils.hasText(properties.getUrl())) {
            throw new IllegalStateException(
                    "bulk.persistence.enabled=true requires a non-empty bulk.persistence.url (JDBC URL)");
        }
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(properties.getUrl().trim());
        cfg.setUsername(properties.getUsername() == null ? "" : properties.getUsername());
        cfg.setPassword(properties.getPassword() == null ? "" : properties.getPassword());
        cfg.setPoolName("bulk-persistence-pool");
        cfg.setMaximumPoolSize(8);
        cfg.setMinimumIdle(0);
        cfg.setAutoCommit(true);
        return new HikariDataSource(cfg);
    }

    @Bean
    @DependsOn("bulkFlyway")
    public JdbcTemplate bulkJdbcTemplate(DataSource bulkDataSource) {
        return new JdbcTemplate(bulkDataSource);
    }

    @Bean
    public Flyway bulkFlyway(DataSource bulkDataSource) {
        Flyway flyway =
                Flyway.configure()
                        .dataSource(bulkDataSource)
                        .locations("classpath:db/migration")
                        .load();
        flyway.migrate();
        return flyway;
    }
}
