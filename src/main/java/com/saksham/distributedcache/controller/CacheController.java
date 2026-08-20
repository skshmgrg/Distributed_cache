package com.saksham.distributedcache.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saksham.distributedcache.cluster.CacheNode;
import com.saksham.distributedcache.cluster.CacheRoutingService;
import com.saksham.distributedcache.cluster.ClusterMembershipService;
import com.saksham.distributedcache.cluster.ReplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
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
    private final ReplicationService replicationService;
    private final ClusterMembershipService membershipService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CacheController(CacheRoutingService routingService, InternalCacheController localCache,
                           ReplicationService replicationService,
                           ClusterMembershipService membershipService, ObjectMapper objectMapper,
                           RestClient.Builder restClientBuilder) {
        this.routingService = routingService;
        this.localCache = localCache;
        this.replicationService = replicationService;
        this.membershipService = membershipService;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{key}")
    public ResponseEntity<?> put(@PathVariable String key, @Valid @RequestBody SetRequest request) {
        if (!routingService.isLocalLiveTarget(key)) {
            return forward(key, request, HttpMethod.POST);
        }
        ResponseEntity<?> response = localCache.put(key, request);
        if (response.getStatusCode().is2xxSuccessful()) {
            replicationService.replicateAsync(key, request);
        }
        return response;
    }

    @GetMapping("/{key}")
    public ResponseEntity<?> get(@PathVariable String key) {
        return routingService.isLocalLiveTarget(key) ? localCache.get(key) : forward(key, null, HttpMethod.GET);
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<?> delete(@PathVariable String key) {
        return routingService.isLocalLiveTarget(key) ? localCache.delete(key) : forward(key, null, HttpMethod.DELETE);
    }

    /** Lets a client discover the owner before issuing its cache operation. */
    @GetMapping("/{key}/owner")
    public Map<String, String> owner(@PathVariable String key) {
        CacheNode owner = routingService.ownerOf(key);
        return Map.of("key", key, "nodeId", owner.id(), "url", owner.publicUrl());
    }

    @GetMapping("/{key}/replicas")
    public java.util.List<Map<String, String>> replicas(@PathVariable String key) {
        return routingService.preferenceListFor(key).stream()
                .map(node -> Map.of("nodeId", node.id(), "url", node.publicUrl()))
                .toList();
    }

    @GetMapping("/cluster/status")
    public java.util.List<ClusterMembershipService.MemberStatus> clusterStatus() {
        return membershipService.status();
    }

    private ResponseEntity<?> forward(String key, Object body, HttpMethod method) {
        CacheNode targetNode = routingService.liveTargetFor(key).orElse(null);
        if (targetNode == null) {
            return unavailable(key);
        }
        URI target = UriComponentsBuilder.fromUriString(targetNode.internalUrl())
                // Route a forwarded write through the owner's public handler so
                // that only its primary-owner branch starts replication.
                .pathSegment("cache", key).build().encode().toUri();
        RestClient.RequestHeadersSpec<?> request = switch (method) {
            case GET -> restClient.get().uri(target);
            case DELETE -> restClient.delete().uri(target);
            case POST -> restClient.post().uri(target).body(body);
        };
        try {
            return request.exchange((clientRequest, clientResponse) -> {
                JsonNode responseBody = clientResponse.getBody() == null ? null : objectMapper.readTree(clientResponse.getBody());
                return ResponseEntity.status(clientResponse.getStatusCode()).body(responseBody);
            });
        } catch (RuntimeException exception) {
            return unavailable(key);
        }
    }

    private ResponseEntity<Map<String, String>> unavailable(String key) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("key", key, "status", "no live cache node available"));
    }

    private enum HttpMethod { GET, POST, DELETE }

    public record SetRequest(@NotNull JsonNode value, @Positive Long ttlSeconds) { }
    public record CacheGetResponse(String key, JsonNode value, Instant expiresAt, Long remainingTtlSeconds) { }
}
