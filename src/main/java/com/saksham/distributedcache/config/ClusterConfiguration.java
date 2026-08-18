package com.saksham.distributedcache.config;

import com.saksham.distributedcache.cluster.CacheClusterProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CacheClusterProperties.class)
public class ClusterConfiguration {
}
