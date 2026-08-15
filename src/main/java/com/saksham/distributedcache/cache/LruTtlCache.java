package com.saksham.distributedcache.cache;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * A bounded in-memory cache implemented with a HashMap and a doubly linked
 * list. The map provides O(1) key lookup; the list keeps the most recently
 * used item at the front and the eviction candidate at the back.
 */
public class LruTtlCache {

    private final int capacity;
    private final Clock clock;
    private final Map<String, Node> entries = new HashMap<>();
    private final Node head = new Node(null, null, null); // most-recent sentinel
    private final Node tail = new Node(null, null, null); // least-recent sentinel

    public LruTtlCache(int capacity, Clock clock) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        this.capacity = capacity;
        this.clock = clock;
        head.next = tail;
        tail.previous = head;
    }

    public synchronized void put(String key, JsonNode value, Instant expiresAt) {
        Node existing = entries.get(key);
        if (existing != null) {
            existing.value = value;
            existing.expiresAt = expiresAt;
            moveToFront(existing);
            return;
        }

        Node node = new Node(key, value, expiresAt);
        entries.put(key, node);
        addAfterHead(node);
        if (entries.size() > capacity) {
            Node leastRecentlyUsed = tail.previous;
            unlink(leastRecentlyUsed);
            entries.remove(leastRecentlyUsed.key);
        }
    }

    public synchronized Optional<CachedValue> get(String key) {
        Node node = entries.get(key);
        if (node == null) {
            return Optional.empty();
        }
        if (node.isExpired(clock.instant())) {
            remove(node);
            return Optional.empty();
        }
        moveToFront(node);
        return Optional.of(new CachedValue(node.value, node.expiresAt));
    }

    public synchronized boolean delete(String key) {
        Node node = entries.remove(key);
        if (node == null) {
            return false;
        }
        unlink(node);
        return true;
    }

    /** Removes expired values even if no client reads them. */
    public synchronized void removeExpiredEntries() {
        Instant now = clock.instant();
        Iterator<Node> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Node node = iterator.next();
            if (node.isExpired(now)) {
                unlink(node);
                iterator.remove();
            }
        }
    }

    private void moveToFront(Node node) {
        unlink(node);
        addAfterHead(node);
    }

    private void addAfterHead(Node node) {
        node.next = head.next;
        node.previous = head;
        head.next.previous = node;
        head.next = node;
    }

    private void remove(Node node) {
        unlink(node);
        entries.remove(node.key);
    }

    private void unlink(Node node) {
        node.previous.next = node.next;
        node.next.previous = node.previous;
    }

    private static final class Node {
        private final String key;
        private JsonNode value;
        private Instant expiresAt;
        private Node previous;
        private Node next;

        private Node(String key, JsonNode value, Instant expiresAt) {
            this.key = key;
            this.value = value;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired(Instant now) {
            return !expiresAt.isAfter(now);
        }
    }

    public record CachedValue(JsonNode value, Instant expiresAt) {
    }
}
