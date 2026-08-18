package com.saksham.distributedcache.cluster;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "cache.cluster")
public class CacheClusterProperties {
    private String nodeId;
    private int virtualNodes = 128;
    private List<CacheNode> nodes = new ArrayList<>();

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public int getVirtualNodes() { return virtualNodes; }
    public void setVirtualNodes(int virtualNodes) { this.virtualNodes = virtualNodes; }
    public List<CacheNode> getNodes() { return nodes; }
    public void setNodes(List<CacheNode> nodes) { this.nodes = nodes; }
}
