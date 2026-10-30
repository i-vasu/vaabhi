package com.vaabhi.shared.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class PostgresUrlEnvironmentPostProcessorTest {

    private final PostgresUrlEnvironmentPostProcessor processor = new PostgresUrlEnvironmentPostProcessor();

    @Test
    void translatesNeonUrlsIntoDatasourceAndFlywayProperties() {
        var environment = environmentWith(Map.of(
                "DATABASE_URL", "postgresql://neondb_owner:p%40ss@ep-x-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require",
                "DATABASE_URL_UNPOOLED", "postgresql://neondb_owner:p%40ss@ep-x.ap-southeast-1.aws.neon.tech/neondb?sslmode=require"));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://ep-x-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channelBinding=require");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("neondb_owner");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("p@ss");
        assertThat(environment.getProperty("spring.flyway.url"))
                .isEqualTo("jdbc:postgresql://ep-x.ap-southeast-1.aws.neon.tech/neondb?sslmode=require");
        assertThat(environment.getProperty("spring.flyway.user")).isEqualTo("neondb_owner");
    }

    @Test
    void leavesJdbcUrlsAlone() {
        var environment = environmentWith(Map.of("DATABASE_URL", "jdbc:postgresql://localhost:5432/vaabhi"));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getPropertySources().contains("postgresUrl")).isFalse();
    }

    private static StandardEnvironment environmentWith(Map<String, Object> values) {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", values));
        return environment;
    }
}
