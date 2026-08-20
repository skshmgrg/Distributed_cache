package com.saksham.distributedcache.cluster;

import com.saksham.distributedcache.controller.CacheController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/** Performs best-effort copies from a primary owner to its healthy replicas. */
@Service
public class ReplicationService {
    private static final Logger log = LoggerFactory.getLogger(ReplicationService.class);

    private final CacheRoutingService routingService;
    private final CacheClusterProperties properties;
    private final RestClient restClient;

    @Autowired
    public ReplicationService(CacheRoutingService routingService, CacheClusterProperties properties,
                              RestClient.Builder restClientBuilder) {
        this(routingService, properties, buildClient(restClientBuilder));
    }

    // Package-visible for HTTP-isolated tests.
    ReplicationService(CacheRoutingService routingService, CacheClusterProperties properties, RestClient restClient) {
        this.routingService = routingService;
        this.properties = properties;
        this.restClient = restClient;
    }

    private static RestClient buildClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2_000);
        requestFactory.setReadTimeout(2_000);
        return builder.requestFactory(requestFactory).build();
    }

    @Async("replicationExecutor")
    public void replicateAsync(String key, CacheController.SetRequest request) {
        for (CacheNode node : routingService.livePreferenceListFor(key)) {
            if (node.id().equals(properties.getNodeId())) {
                continue;
            }
            URI target = UriComponentsBuilder.fromUriString(node.internalUrl())
                    .pathSegment("internal", "cache", key, "replica").build().encode().toUri();
            try {
                restClient.post().uri(target).body(request).retrieve().toBodilessEntity();
            } catch (RuntimeException exception) {
                // Hinted handoff / a retry queue and rejoin backfill are future work.
                log.warn("Could not replicate key '{}' to node '{}'", key, node.id(), exception);
            }
        }
    }
}
