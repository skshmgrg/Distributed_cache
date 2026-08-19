package com.saksham.distributedcache.cluster;

import com.fasterxml.jackson.databind.node.TextNode;
import com.saksham.distributedcache.controller.CacheController;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.http.HttpMethod.POST;

class ReplicationServiceTest {
    @Test
    void continuesReplicatingWhenOneReplicaFails() {
        CacheClusterProperties properties = properties();
        CacheRoutingService routing = new CacheRoutingService(properties);
        String key = keyOwnedBy(routing, "node1");
        List<CacheNode> replicas = routing.preferenceListFor(key).stream()
                .filter(node -> !node.id().equals("node1")).toList();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(replicas.get(0).internalUrl() + "/internal/cache/" + key + "/replica"))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo(replicas.get(1).internalUrl() + "/internal/cache/" + key + "/replica"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        ReplicationService service = new ReplicationService(routing, properties, builder.build());
        assertThatCode(() -> service.replicateAsync(key, new CacheController.SetRequest(TextNode.valueOf("value"), 60L)))
                .doesNotThrowAnyException();
        server.verify();
    }

    private static CacheClusterProperties properties() {
        CacheClusterProperties properties = new CacheClusterProperties();
        properties.setNodeId("node1");
        properties.setVirtualNodes(16);
        properties.setReplicationFactor(3);
        properties.setNodes(List.of(
                new CacheNode("node1", "http://node1:8080", "http://localhost:8081"),
                new CacheNode("node2", "http://node2:8080", "http://localhost:8082"),
                new CacheNode("node3", "http://node3:8080", "http://localhost:8083")));
        return properties;
    }

    private static String keyOwnedBy(CacheRoutingService routing, String nodeId) {
        return java.util.stream.IntStream.range(0, 100_000).mapToObj(index -> "key-" + index)
                .filter(key -> routing.ownerOf(key).id().equals(nodeId)).findFirst().orElseThrow();
    }
}
