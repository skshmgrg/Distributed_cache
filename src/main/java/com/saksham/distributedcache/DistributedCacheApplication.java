package com.saksham.distributedcache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for a single cache node.
 *
 * Each node in the cluster runs this same application — what differs
 * between nodes is only the NODE_ID and PORT environment variables
 * (see docker-compose.yml). There is no separate "coordinator" binary;
 * every node is symmetric.
 */
@SpringBootApplication
public class DistributedCacheApplication {

    public static void main(String[] args) {
        SpringApplication.run(DistributedCacheApplication.class, args);
    }
}
