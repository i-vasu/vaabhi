package com.vaabhi.shared.web;

import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson always writes every record component (null included), so every response property is required.
 * {@code @Nullable} components are additionally typed as nullable
 * ({@code springdoc.model-converters.java-nullable-property-customizer.enabled}). The generated TypeScript types
 * then read {@code badge: Badge | null} instead of {@code badge?: Badge}.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    OpenApiCustomizer everyPropertyRequired() {
        return openApi -> {
            if (openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
                openApi.getComponents().getSchemas().values().forEach(OpenApiConfig::markRequired);
            }
        };
    }

    static void markRequired(Schema<?> schema) {
        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            schema.setRequired(List.copyOf(schema.getProperties().keySet()));
        }
    }
}
