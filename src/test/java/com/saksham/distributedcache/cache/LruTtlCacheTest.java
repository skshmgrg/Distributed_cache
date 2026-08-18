package com.saksham.distributedcache.cache;

import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LruTtlCacheTest {

    @Test
    void evictsTheLeastRecentlyUsedEntry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LruTtlCache cache = new LruTtlCache(2, clock);
        Instant expiresAt = clock.instant().plus(Duration.ofHours(1));
        cache.put("first", TextNode.valueOf("1"), expiresAt);
        cache.put("second", TextNode.valueOf("2"), expiresAt);
        cache.get("first");
        cache.put("third", TextNode.valueOf("3"), expiresAt);

        assertThat(cache.get("first")).isPresent();
        assertThat(cache.get("second")).isEmpty();
        assertThat(cache.get("third")).isPresent();
    }

    @Test
    void expiresValuesAndRemovesThemDuringASweep() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LruTtlCache cache = new LruTtlCache(2, clock);
        cache.put("temporary", TextNode.valueOf("value"), clock.instant().plusSeconds(5));
        clock.advance(Duration.ofSeconds(5));
        cache.removeExpiredEntries();

        assertThat(cache.get("temporary")).isEmpty();
    }

    @Test
    void neverExpiringValuesStayValidAndSweepsDoNotThrow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LruTtlCache cache = new LruTtlCache(2, clock);

        cache.put("forever", TextNode.valueOf("value"), null);

        assertThat(cache.get("forever")).isPresent();
        assertThatCode(cache::removeExpiredEntries).doesNotThrowAnyException();
        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.get("forever")).isPresent();
    }

    @Test
    void removeExpiredEntriesDropsExpiredEntriesWhenInvokedDirectly() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LruTtlCache cache = new LruTtlCache(2, clock);
        cache.put("expired", TextNode.valueOf("value"), clock.instant().plusSeconds(2));

        clock.advance(Duration.ofSeconds(3));
        cache.removeExpiredEntries();

        assertThat(cache.size()).isZero();
        assertThat(cache.get("expired")).isEmpty();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
