package com.saksham.distributedcache.cluster;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterMembershipServiceTest {
    @Test
    void marksPeerDeadAfterThresholdAndAliveAfterSuccessfulHeartbeat() {
        CacheClusterProperties properties = properties();
        ClusterMembershipService membership = new ClusterMembershipService(properties, RestClient.create());

        membership.recordHeartbeatFailure("node2");
        assertThat(membership.isAlive("node2")).isTrue();

        membership.recordHeartbeatFailure("node2");
        assertThat(membership.isAlive("node2")).isTrue();

        membership.recordHeartbeatFailure("node2");
        assertThat(membership.isAlive("node2")).isFalse();
        assertThat(membership.status()).anySatisfy(status -> {
            assertThat(status.nodeId()).isEqualTo("node2");
            assertThat(status.consecutiveMisses()).isEqualTo(3);
        });

        membership.recordHeartbeatSuccess("node2");
        assertThat(membership.isAlive("node2")).isTrue();
        assertThat(membership.status()).anySatisfy(status -> {
            assertThat(status.nodeId()).isEqualTo("node2");
            assertThat(status.consecutiveMisses()).isZero();
        });
    }

    static CacheClusterProperties properties() {
        CacheClusterProperties properties = new CacheClusterProperties();
        properties.setNodeId("node1");
        properties.setVirtualNodes(16);
        properties.setReplicationFactor(3);
        properties.setHeartbeatMissThreshold(3);
        properties.setNodes(List.of(
                new CacheNode("node1", "http://node1:8080", "http://localhost:8081"),
                new CacheNode("node2", "http://node2:8080", "http://localhost:8082"),
                new CacheNode("node3", "http://node3:8080", "http://localhost:8083")));
        return properties;
    }
}
