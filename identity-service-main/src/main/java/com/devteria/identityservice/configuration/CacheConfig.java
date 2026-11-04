package com.devteria.identityservice.configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cấu hình Caffeine Cache hiệu năng cao cho hệ thống AI Music Companion.
 * - Cache: topTrendingSongsCache (TTL = 5 phút, tối đa 100 phần tử).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String TOP_TRENDING_SONGS_CACHE = "topTrendingSongsCache";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(TOP_TRENDING_SONGS_CACHE);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(10)
                .maximumSize(100)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats());
        return cacheManager;
    }
}
