package com.saksham.distributedcache.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Day 1 goal: prove a container can start and respond.
 *
 * This same /ping endpoint later becomes the target of inter-node
 * heartbeats in the failure-detection phase — each node will poll
 * its peers' /ping and mark them dead after N missed responses.
 */
@RestController
public class HealthController {

    @Value("${NODE_ID:unknown-node}")
    private String nodeId;

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of(
                "nodeId", nodeId,
                "status", "alive"
        );
    }
}
