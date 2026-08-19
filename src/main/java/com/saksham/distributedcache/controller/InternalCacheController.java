package com.saksham.distributedcache.controller;

import com.saksham.distributedcache.cache.CacheService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Endpoints used only after the ring has selected this node as key owner. */
@RestController
@RequestMapping("/internal/cache")
public class InternalCacheController {
    private final CacheService cacheService;
    private final Clock clock;

    public InternalCacheController(CacheService cacheService, Clock clock) {
        this.cacheService = cacheService;
        this.clock = clock;
    }

    @PostMapping("/{key}")
    public ResponseEntity<Map<String, Object>> put(@PathVariable String key,
                                                    @Valid @RequestBody CacheController.SetRequest request) {
        Instant expiresAt = request.ttlSeconds() == null ? null : clock.instant().plusSeconds(request.ttlSeconds());
        cacheService.set(key, request.value(), expiresAt);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("key", key, "status", "stored"));
    }

    @PostMapping("/{key}/replica")
    public ResponseEntity<Map<String, Object>> putReplica(@PathVariable String key,
                                                           @Valid @RequestBody CacheController.SetRequest request) {
        // A dedicated path makes replicated writes unambiguous and prevents
        // recursive fan-out; it is clearer here than a routing header.
        return put(key, request);
    }

    @GetMapping("/{key}")
    public ResponseEntity<CacheController.CacheGetResponse> get(@PathVariable String key) {
        return cacheService.get(key)
                .map(value -> ResponseEntity.ok(new CacheController.CacheGetResponse(
                        key, value.value(), value.expiresAt(),
                        value.expiresAt() == null ? null : Math.max(0,
                                Duration.between(clock.instant(), value.expiresAt()).getSeconds()))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(@PathVariable String key) {
        return cacheService.delete(key) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
