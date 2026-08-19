package com.saksham.distributedcache.cluster;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

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

    @Test
    void preferenceListIsClockwiseDeduplicatedAndWrapsAtTheEndOfTheRing() {
        List<CacheNode> smallCluster = nodes.subList(0, 3);
        int virtualNodes = 3;
        ConsistentHashRing ring = new ConsistentHashRing(smallCluster, virtualNodes);
        NavigableMap<Long, CacheNode> tokens = tokensFor(smallCluster, virtualNodes);
        String wrappingKey = java.util.stream.IntStream.range(0, 100_000)
                .mapToObj(index -> "wrap-" + index)
                .filter(key -> ConsistentHashRing.hash(key) > tokens.lastKey())
                .findFirst().orElseThrow();

        List<CacheNode> preferenceList = ring.preferenceList(wrappingKey, 99);

        assertThat(preferenceList).containsExactlyElementsOf(clockwiseDistinct(tokens, tokens.firstEntry().getKey()));
        assertThat(preferenceList).extracting(CacheNode::id).doesNotHaveDuplicates();
        assertThat(preferenceList).hasSize(3);
    }

    private static NavigableMap<Long, CacheNode> tokensFor(List<CacheNode> nodes, int virtualNodes) {
        NavigableMap<Long, CacheNode> result = new TreeMap<>();
        nodes.forEach(node -> java.util.stream.IntStream.range(0, virtualNodes)
                .forEach(replica -> result.put(ConsistentHashRing.hash(node.id() + "#" + replica), node)));
        return result;
    }

    private static List<CacheNode> clockwiseDistinct(NavigableMap<Long, CacheNode> tokens, long start) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.ArrayList<CacheNode> result = new java.util.ArrayList<>();
        for (var entry : tokens.tailMap(start, true).entrySet()) {
            if (seen.add(entry.getValue().id())) result.add(entry.getValue());
        }
        for (var entry : tokens.headMap(start, false).entrySet()) {
            if (seen.add(entry.getValue().id())) result.add(entry.getValue());
        }
        return result;
    }
}
