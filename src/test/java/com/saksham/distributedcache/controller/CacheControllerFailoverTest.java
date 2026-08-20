package com.saksham.distributedcache.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saksham.distributedcache.cache.CacheService;
import com.saksham.distributedcache.controller.CacheController;
import com.saksham.distributedcache.controller.InternalCacheController;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CacheControllerFailoverTest {
    @Test
    void skipsDeadPrimaryAndServesTheNextLiveReplica() {
        CacheClusterProperties properties = properties();
        ClusterMembershipService membership = new ClusterMembershipService(properties, RestClient.create());
        CacheRoutingService routing = new CacheRoutingService(properties, membership);
        String key = keyWhoseSecondPreferenceIsLocalNode(routing, "node1");
        String deadPrimaryId = routing.ownerOf(key).id();
        membership.recordHeartbeatFailure(deadPrimaryId);

        CacheService cacheService = new CacheService(10, 3600, Clock.systemUTC());
        CacheController controller = new CacheController(routing,
                new InternalCacheController(cacheService, Clock.systemUTC()),
                new ReplicationService(routing, properties, RestClient.create()),
                membership, new ObjectMapper(), RestClient.builder());

        assertThat(controller.get(key).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(routing.liveTargetFor(key)).hasValueSatisfying(node -> assertThat(node.id()).isEqualTo("node1"));
    }

    private static String keyWhoseSecondPreferenceIsLocalNode(CacheRoutingService routing, String localNodeId) {
        return java.util.stream.IntStream.range(0, 100_000).mapToObj(index -> "key-" + index)
                .filter(key -> !routing.ownerOf(key).id().equals(localNodeId))
                .filter(key -> routing.preferenceListFor(key).get(1).id().equals(localNodeId))
                .findFirst().orElseThrow();
    }

    private static CacheClusterProperties properties() {
        CacheClusterProperties properties = new CacheClusterProperties();
        properties.setNodeId("node1");
        properties.setVirtualNodes(16);
        properties.setReplicationFactor(3);
        properties.setHeartbeatMissThreshold(1);
        properties.setNodes(List.of(
                new CacheNode("node1", "http://node1:8080", "http://localhost:8081"),
                new CacheNode("node2", "http://node2:8080", "http://localhost:8082"),
                new CacheNode("node3", "http://node3:8080", "http://localhost:8083")));
        return properties;
    }
}
