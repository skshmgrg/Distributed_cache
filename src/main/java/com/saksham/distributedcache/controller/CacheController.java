package com.saksham.distributedcache.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saksham.distributedcache.cluster.CacheNode;
import com.saksham.distributedcache.cluster.CacheRoutingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/** Public API. A request is handled locally only when this node owns its key. */
@RestController
@RequestMapping("/cache")
public class CacheController {
    private final CacheRoutingService routingService;
    private final InternalCacheController localCache;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CacheController(CacheRoutingService routingService, InternalCacheController localCache,
                           ObjectMapper objectMapper) {
        this.routingService = routingService;
        this.localCache = localCache;
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{key}")
    public ResponseEntity<?> put(@PathVariable String key, @Valid @RequestBody SetRequest request) {
        return routingService.isLocalOwner(key) ? localCache.put(key, request) : forward(key, request, HttpMethod.POST);
    }

    @GetMapping("/{key}")
    public ResponseEntity<?> get(@PathVariable String key) {
        return routingService.isLocalOwner(key) ? localCache.get(key) : forward(key, null, HttpMethod.GET);
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<?> delete(@PathVariable String key) {
        return routingService.isLocalOwner(key) ? localCache.delete(key) : forward(key, null, HttpMethod.DELETE);
    }

    /** Lets a client discover the owner before issuing its cache operation. */
    @GetMapping("/{key}/owner")
    public Map<String, String> owner(@PathVariable String key) {
        CacheNode owner = routingService.ownerOf(key);
        return Map.of("key", key, "nodeId", owner.id(), "url", owner.publicUrl());
    }

    private ResponseEntity<?> forward(String key, Object body, HttpMethod method) {
        URI target = UriComponentsBuilder.fromUriString(routingService.ownerOf(key).internalUrl())
                .pathSegment("internal", "cache", key).build().encode().toUri();
        RestClient.RequestHeadersSpec<?> request = switch (method) {
            case GET -> restClient.get().uri(target);
            case DELETE -> restClient.delete().uri(target);
            case POST -> restClient.post().uri(target).body(body);
        };
        return request.exchange((clientRequest, clientResponse) -> {
            JsonNode responseBody = clientResponse.getBody() == null ? null : objectMapper.readTree(clientResponse.getBody());
            return ResponseEntity.status(clientResponse.getStatusCode()).body(responseBody);
        });
    }

    private enum HttpMethod { GET, POST, DELETE }

    public record SetRequest(@NotNull JsonNode value, @Positive Long ttlSeconds) { }
    public record CacheGetResponse(String key, JsonNode value, Instant expiresAt, Long remainingTtlSeconds) { }
}
