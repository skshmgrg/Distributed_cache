package com.saksham.distributedcache.cluster;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains this node's local, best-effort view of peer health. Gossip and
 * phi-accrual failure detection are deliberate future improvements.
 */
@Service
public class ClusterMembershipService {
    private final CacheClusterProperties properties;
    private final RestClient restClient;
    private final Map<String, MemberState> members = new ConcurrentHashMap<>();

    @Autowired
    public ClusterMembershipService(CacheClusterProperties properties, RestClient.Builder restClientBuilder) {
        this(properties, buildClient(restClientBuilder));
    }

    // Package-visible for heartbeat tests without network calls.
    ClusterMembershipService(CacheClusterProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
        properties.getNodes().forEach(node -> members.put(node.id(), new MemberState(true, 0)));
    }

    @Scheduled(fixedDelayString = "${cache.cluster.heartbeat-interval-ms:2000}")
    public void checkPeerHeartbeats() {
        for (CacheNode node : properties.getNodes()) {
            if (node.id().equals(properties.getNodeId())) {
                continue;
            }
            try {
                restClient.get().uri(UriComponentsBuilder.fromUriString(node.internalUrl())
                        .path("/ping").build().toUri()).retrieve().toBodilessEntity();
                recordHeartbeatSuccess(node.id());
            } catch (RuntimeException exception) {
                recordHeartbeatFailure(node.id());
            }
        }
    }

    public boolean isAlive(String nodeId) {
        return members.getOrDefault(nodeId, new MemberState(false, 0)).alive();
    }

    public List<MemberStatus> status() {
        return members.entrySet().stream()
                .map(entry -> new MemberStatus(entry.getKey(), entry.getValue().alive(), entry.getValue().misses()))
                .sorted(Comparator.comparing(MemberStatus::nodeId))
                .toList();
    }

    void recordHeartbeatSuccess(String nodeId) {
        members.computeIfPresent(nodeId, (ignored, current) -> new MemberState(true, 0));
    }

    void recordHeartbeatFailure(String nodeId) {
        if (nodeId.equals(properties.getNodeId())) {
            return;
        }
        members.computeIfPresent(nodeId, (ignored, current) -> {
            int misses = current.misses() + 1;
            return new MemberState(misses < properties.getHeartbeatMissThreshold(), misses);
        });
    }

    private static RestClient buildClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1_000);
        requestFactory.setReadTimeout(1_000);
        return builder.requestFactory(requestFactory).build();
    }

    private record MemberState(boolean alive, int misses) { }
    public record MemberStatus(String nodeId, boolean alive, int consecutiveMisses) { }
}
