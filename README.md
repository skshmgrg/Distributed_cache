# distributed-cache

A distributed, sharded, replicated in-memory cache built from scratch (no Redis under the hood), to understand how systems like Redis Cluster distribute data.

## Status: Phase 3 sharded cache

Five cache containers form a consistent-hash ring. Each node has 128 virtual tokens, and every key maps deterministically to exactly one primary owner. A request may be sent to any exposed port: if that node is not the owner, it forwards the request directly to the owner. There is no fan-out or coordinator. Each owner keeps its own bounded LRU cache with per-entry TTL expiry.

## Run it

```bash
docker compose up --build
```

The five cache nodes are exposed at ports 8081 through 8085. Confirm a node is alive:

```bash
curl http://localhost:8081/ping
```

## Cache API

Store any JSON value. `ttlSeconds` is optional; omitting it stores a value with no expiry. This may be sent to any node; only the key's owner stores it:

```bash
curl -X POST http://localhost:8081/cache/user:42 \
  -H "Content-Type: application/json" \
  -d '{"value":{"name":"Saksham"},"ttlSeconds":300}'
```

Read or delete it:

```bash
curl http://localhost:8081/cache/user:42
curl -X DELETE http://localhost:8081/cache/user:42
```

To have a client choose the target before the cache operation, ask any node for the key owner, then use the returned node's corresponding host port (node1 is 8081 through node5 is 8085):

```bash
curl http://localhost:8081/cache/user:42/owner
```

A successful `GET` now returns the cached JSON value plus the expiry metadata:

```json
{
  "key": "user:42",
  "value": {
    "name": "Saksham"
  },
  "expiresAt": "2026-08-18T12:00:00Z",
  "remainingTtlSeconds": 300
}
```

`GET` and `DELETE` return `404` when a key does not exist, including after TTL expiry. Expired entries are swept every 5 seconds by the scheduled cleaner and are also rejected immediately on read. By default a node keeps at most 1,000 entries; when full, it evicts the least recently used entry. Configure a node with `CACHE_MAX_ENTRIES`, `CACHE_DEFAULT_TTL_SECONDS`, and `CACHE_TTL_SWEEP_INTERVAL_MS`.

## Roadmap

1. **Docker pipeline working** — done
2. **Single-node cache** — GET/SET/DELETE, LRU eviction, TTL expiry — done
3. **Sharding across 5 nodes** — consistent hashing ring with virtual nodes — done
4. **Replication** — each key copied to its primary plus two replica nodes
5. **Failure detection + failover** — heartbeats and fallback to a live replica
6. **Benchmarking** — throughput, latency, key movement, and recovery time
7. **Docs + diagrams** — architecture diagrams and walkthrough
