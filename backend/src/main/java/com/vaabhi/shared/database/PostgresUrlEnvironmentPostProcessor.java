package com.vaabhi.shared.database;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Accepts Neon-style {@code postgres://user:password@host/db?sslmode=require} URLs in
 * {@code DATABASE_URL} (pooled, used by the app) and {@code DATABASE_URL_UNPOOLED} (direct, used by
 * Flyway) and translates them into Spring datasource properties. JDBC URLs are left untouched.
 */
class PostgresUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> properties = new HashMap<>();
        var pooled = environment.getProperty("DATABASE_URL");
        var direct = environment.getProperty("DATABASE_URL_UNPOOLED");
        if (isPostgresUrl(pooled)) {
            var connection = JdbcConnection.parse(pooled);
            properties.put("spring.datasource.url", connection.url());
            properties.put("spring.datasource.username", connection.username());
            properties.put("spring.datasource.password", connection.password());
        }
        if (isPostgresUrl(direct)) {
            var connection = JdbcConnection.parse(direct);
            properties.put("spring.flyway.url", connection.url());
            properties.put("spring.flyway.user", connection.username());
            properties.put("spring.flyway.password", connection.password());
        }
        if (!properties.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("postgresUrl", properties));
        }
    }

    private static boolean isPostgresUrl(String url) {
        return url != null && (url.startsWith("postgres://") || url.startsWith("postgresql://"));
    }

    record JdbcConnection(String url, String username, String password) {

        static JdbcConnection parse(String postgresUrl) {
            var uri = URI.create(postgresUrl);
            var userInfo = uri.getRawUserInfo() == null ? "" : uri.getRawUserInfo();
            var separator = userInfo.indexOf(':');
            var username = decode(separator < 0 ? userInfo : userInfo.substring(0, separator));
            var password = separator < 0 ? "" : decode(userInfo.substring(separator + 1));
            var port = uri.getPort() < 0 ? "" : ":" + uri.getPort();
            var query = jdbcQuery(uri.getRawQuery());
            var url = "jdbc:postgresql://" + uri.getHost() + port + uri.getRawPath() + (query.isEmpty() ? "" : "?" + query);
            return new JdbcConnection(url, username, password);
        }

        /** libpq's {@code channel_binding} is called {@code channelBinding} in pgjdbc. */
        private static String jdbcQuery(String rawQuery) {
            if (rawQuery == null || rawQuery.isEmpty()) {
                return "";
            }
            Map<String, String> params = new LinkedHashMap<>();
            for (var pair : rawQuery.split("&")) {
                var eq = pair.indexOf('=');
                var key = eq < 0 ? pair : pair.substring(0, eq);
                var value = eq < 0 ? "" : pair.substring(eq + 1);
                params.put(key.equals("channel_binding") ? "channelBinding" : key, value);
            }
            var joined = new StringBuilder();
            params.forEach((key, value) -> joined.append(joined.isEmpty() ? "" : "&").append(key).append('=').append(value));
            return joined.toString();
        }

        private static String decode(String value) {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
    }
}
