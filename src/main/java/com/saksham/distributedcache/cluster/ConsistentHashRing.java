package com.saksham.distributedcache.cluster;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * A deterministic consistent-hashing ring. Each physical node owns several
 * tokens, which gives a more even distribution than one token per node.
 */
public final class ConsistentHashRing {
    private final NavigableMap<Long, CacheNode> tokens = new TreeMap<>();

    public ConsistentHashRing(Collection<CacheNode> nodes, int virtualNodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("at least one cache node is required");
        }
        if (virtualNodes < 1) {
            throw new IllegalArgumentException("virtualNodes must be at least 1");
        }
        for (CacheNode node : nodes) {
            for (int replica = 0; replica < virtualNodes; replica++) {
                tokens.put(hash(node.id() + "#" + replica), node);
            }
        }
    }

    /** Returns the first token clockwise from the key, wrapping at ring end. */
    public CacheNode ownerOf(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        var clockwise = tokens.ceilingEntry(hash(key));
        return (clockwise != null ? clockwise : tokens.firstEntry()).getValue();
    }

    /**
     * Returns the primary followed by distinct physical nodes clockwise on the
     * ring. Virtual-node duplicates are intentionally skipped.
     */
    public List<CacheNode> preferenceList(String key, int replicationFactor) {
        if (replicationFactor < 1) {
            throw new IllegalArgumentException("replicationFactor must be at least 1");
        }

        int distinctNodeCount = (int) tokens.values().stream().map(CacheNode::id).distinct().count();
        int wanted = Math.min(replicationFactor, distinctNodeCount);
        List<CacheNode> result = new ArrayList<>(wanted);
        Set<String> includedIds = new HashSet<>();
        var current = tokens.ceilingEntry(hash(key));
        if (current == null) {
            current = tokens.firstEntry();
        }

        // At most one pass of the token map is needed to encounter every node.
        for (int visited = 0; visited < tokens.size() && result.size() < wanted; visited++) {
            if (includedIds.add(current.getValue().id())) {
                result.add(current.getValue());
            }
            current = tokens.higherEntry(current.getKey());
            if (current == null) {
                current = tokens.firstEntry();
            }
        }
        return List.copyOf(result);
    }

    static long hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            long result = 0;
            for (int index = 0; index < Long.BYTES; index++) {
                result = (result << Byte.SIZE) | (digest[index] & 0xffL);
            }
            return result & Long.MAX_VALUE;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
