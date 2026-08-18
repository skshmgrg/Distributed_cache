package com.saksham.distributedcache.cluster;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.NavigableMap;
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
