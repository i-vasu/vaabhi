package com.vaabhi.shared.cache;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/** In-process Caffeine caching (no Redis); caches are declared in {@code spring.cache.cache-names}. */
@Configuration(proxyBeanMethods = false)
@EnableCaching
class CacheConfig {}
