package com.saksham.distributedcache.cluster;

import org.springframework.stereotype.Service;

/** Provides the same key-to-owner decision on every cache node. */
@Service
public class CacheRoutingService {
    private final CacheClusterProperties properties;
    private final ConsistentHashRing ring;

    public CacheRoutingService(CacheClusterProperties properties) {
        this.properties = properties;
        this.ring = new ConsistentHashRing(properties.getNodes(), properties.getVirtualNodes());
        if (properties.getNodes().stream().noneMatch(node -> node.id().equals(properties.getNodeId()))) {
            throw new IllegalStateException("cache.cluster.node-id must be present in cache.cluster.nodes");
        }
    }

    public CacheNode ownerOf(String key) { return ring.ownerOf(key); }
    public boolean isLocalOwner(String key) { return ownerOf(key).id().equals(properties.getNodeId()); }
}
