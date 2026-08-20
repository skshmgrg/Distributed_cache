package com.saksham.distributedcache.cluster;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Provides the same key-to-owner decision on every cache node. */
@Service
public class CacheRoutingService {
    private final CacheClusterProperties properties;
    private final ClusterMembershipService membershipService;
    private final ConsistentHashRing ring;

    public CacheRoutingService(CacheClusterProperties properties, ClusterMembershipService membershipService) {
        this.properties = properties;
        this.membershipService = membershipService;
        this.ring = new ConsistentHashRing(properties.getNodes(), properties.getVirtualNodes());
        if (properties.getNodes().stream().noneMatch(node -> node.id().equals(properties.getNodeId()))) {
            throw new IllegalStateException("cache.cluster.node-id must be present in cache.cluster.nodes");
        }
    }

    public CacheNode ownerOf(String key) { return ring.ownerOf(key); }
    public List<CacheNode> preferenceListFor(String key) {
        return ring.preferenceList(key, properties.getReplicationFactor());
    }
    public List<CacheNode> livePreferenceListFor(String key) {
        return preferenceListFor(key).stream().filter(node -> membershipService.isAlive(node.id())).toList();
    }
    public Optional<CacheNode> liveTargetFor(String key) {
        return livePreferenceListFor(key).stream().findFirst();
    }
    public boolean isLocalLiveTarget(String key) {
        return liveTargetFor(key).map(node -> node.id().equals(properties.getNodeId())).orElse(false);
    }
}
