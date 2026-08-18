package com.saksham.distributedcache.cache;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Spring adapter that configures the local cache and schedules TTL cleanup. */
@Service
public class CacheService {

    private final LruTtlCache cache;
    private final Duration defaultTtl;
    private final Clock clock;

    public CacheService(
            @Value("${cache.max-entries:1000}") int maxEntries,
            @Value("${cache.default-ttl-seconds:3600}") long defaultTtlSeconds,
            Clock clock) {
        this(maxEntries, Duration.ofSeconds(defaultTtlSeconds), clock);
    }

    CacheService(int maxEntries, Duration defaultTtl, Clock clock) {
        if (defaultTtl == null || defaultTtl.isZero() || defaultTtl.isNegative()) {
            throw new IllegalArgumentException("defaultTtl must be positive");
        }
        this.cache = new LruTtlCache(maxEntries, clock);
        this.defaultTtl = defaultTtl;
        this.clock = clock;
    }

    public void set(String key, JsonNode value, Instant expiresAt) {
        cache.put(key, value, expiresAt);
    }

    public void put(String key, JsonNode value, Duration ttl) {
        Instant expiresAt = ttl == null ? clock.instant().plus(defaultTtl) : clock.instant().plus(ttl);
        set(key, value, expiresAt);
    }

    public Optional<LruTtlCache.CachedValue> get(String key) {
        return cache.get(key);
    }

    public boolean delete(String key) {
        return cache.delete(key);
    }

    @Scheduled(fixedDelayString = "${cache.ttl-sweep-interval-ms:5000}")
    public void removeExpiredEntries() {
        cache.removeExpiredEntries();
    }
}
