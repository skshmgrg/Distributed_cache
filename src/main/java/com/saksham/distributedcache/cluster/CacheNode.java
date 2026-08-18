package com.saksham.distributedcache.cluster;

/** A cache node's stable identity, internal address, and client-facing address. */
public record CacheNode(String id, String internalUrl, String publicUrl) {
    public CacheNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("node id must not be blank");
        }
        if (internalUrl == null || internalUrl.isBlank()) {
            throw new IllegalArgumentException("node internalUrl must not be blank");
        }
        if (publicUrl == null || publicUrl.isBlank()) {
            throw new IllegalArgumentException("node publicUrl must not be blank");
        }
    }
}
