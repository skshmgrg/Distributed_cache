package com.saksham.distributedcache.cluster;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "cache.cluster")
public class CacheClusterProperties {
    private String nodeId;
    private int virtualNodes = 128;
    private int replicationFactor = 3;
    private long heartbeatIntervalMs = 2_000;
    private int heartbeatMissThreshold = 3;
    private List<CacheNode> nodes = new ArrayList<>();

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public int getVirtualNodes() { return virtualNodes; }
    public void setVirtualNodes(int virtualNodes) { this.virtualNodes = virtualNodes; }
    public int getReplicationFactor() { return replicationFactor; }
    public void setReplicationFactor(int replicationFactor) { this.replicationFactor = replicationFactor; }
    public long getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) { this.heartbeatIntervalMs = heartbeatIntervalMs; }
    public int getHeartbeatMissThreshold() { return heartbeatMissThreshold; }
    public void setHeartbeatMissThreshold(int heartbeatMissThreshold) { this.heartbeatMissThreshold = heartbeatMissThreshold; }
    public List<CacheNode> getNodes() { return nodes; }
    public void setNodes(List<CacheNode> nodes) { this.nodes = nodes; }
}
