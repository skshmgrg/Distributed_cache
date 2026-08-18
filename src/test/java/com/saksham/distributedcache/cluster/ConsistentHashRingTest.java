package com.saksham.distributedcache.cluster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConsistentHashRingTest {

    private final List<CacheNode> nodes = List.of(
            new CacheNode("node1", "http://node1:8080", "http://localhost:8081"),
            new CacheNode("node2", "http://node2:8080", "http://localhost:8082"),
            new CacheNode("node3", "http://node3:8080", "http://localhost:8083"),
            new CacheNode("node4", "http://node4:8080", "http://localhost:8084"),
            new CacheNode("node5", "http://node5:8080", "http://localhost:8085"));

    @Test
    void mapsAKeyToTheSameOwnerOnEveryNode() {
        ConsistentHashRing firstNodeView = new ConsistentHashRing(nodes, 128);
        ConsistentHashRing anotherNodeView = new ConsistentHashRing(nodes, 128);

        assertThat(firstNodeView.ownerOf("user:42")).isEqualTo(anotherNodeView.ownerOf("user:42"));
    }

    @Test
    void distributesKeysAcrossAllFiveNodes() {
        ConsistentHashRing ring = new ConsistentHashRing(nodes, 128);

        assertThat(java.util.stream.IntStream.range(0, 10_000)
                .mapToObj(index -> ring.ownerOf("key-" + index).id())
                .collect(java.util.stream.Collectors.toSet()))
                .containsExactlyInAnyOrder("node1", "node2", "node3", "node4", "node5");
    }
}
