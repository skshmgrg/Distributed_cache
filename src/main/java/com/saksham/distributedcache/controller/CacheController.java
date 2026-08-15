package com.saksham.distributedcache.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.saksham.distributedcache.cache.CacheService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** HTTP API for the local cache node. No routing or replication happens yet. */
@RestController
@RequestMapping("/cache")
public class CacheController {

    private final CacheService cacheService;

    public CacheController(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @PostMapping("/{key}")
    public ResponseEntity<Map<String, Object>> put(
            @PathVariable String key,
            @Valid @RequestBody CachePutRequest request) {
        cacheService.put(key, request.value(), request.ttlSeconds() == null
                ? null : Duration.ofSeconds(request.ttlSeconds()));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("key", key, "status", "stored"));
    }

    @GetMapping("/{key}")
    public ResponseEntity<CacheGetResponse> get(@PathVariable String key) {
        return cacheService.get(key)
                .map(value -> ResponseEntity.ok(new CacheGetResponse(key, value.value(), value.expiresAt())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(@PathVariable String key) {
        return cacheService.delete(key) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    public record CachePutRequest(@NotNull JsonNode value, @Positive Long ttlSeconds) {
    }

    public record CacheGetResponse(String key, JsonNode value, Instant expiresAt) {
    }
}
