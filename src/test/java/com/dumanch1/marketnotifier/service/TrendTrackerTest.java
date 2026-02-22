package com.dumanch1.marketnotifier.service;

import com.dumanch1.marketnotifier.model.ActiveTrend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TrendTracker}.
 */
class TrendTrackerTest {

    private TrendTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new TrendTracker();
    }

    @Test
    @DisplayName("Should start a new trend and mark it as new")
    void startsNewTrend() {
        boolean isNew = tracker.startOrUpdate("BTCUSDT", "UP",
                new BigDecimal("1.5000"), new BigDecimal("96000"),
                new BigDecimal("97440"));

        assertTrue(isNew);
        assertTrue(tracker.hasActiveTrend("BTCUSDT"));
    }

    @Test
    @DisplayName("Should update peak when same direction and higher extreme")
    void updatesPeakOnNewExtreme() {
        tracker.startOrUpdate("BTCUSDT", "UP",
                new BigDecimal("1.5000"), new BigDecimal("96000"),
                new BigDecimal("97440"));

        // Higher change → should update, but return false (not a NEW trend)
        boolean isNew = tracker.startOrUpdate("BTCUSDT", "UP",
                new BigDecimal("2.1000"), new BigDecimal("96000"),
                new BigDecimal("98016"));

        assertFalse(isNew);
        assertTrue(tracker.hasActiveTrend("BTCUSDT"));
    }

    @Test
    @DisplayName("Should NOT update peak when change is lower than existing peak")
    void doesNotUpdatePeakWhenNotExtreme() {
        tracker.startOrUpdate("BTCUSDT", "UP",
                new BigDecimal("2.1000"), new BigDecimal("96000"),
                new BigDecimal("98016"));

        // Lower change → should not update peak
        boolean isNew = tracker.startOrUpdate("BTCUSDT", "UP",
                new BigDecimal("1.8000"), new BigDecimal("96000"),
                new BigDecimal("97728"));

        assertFalse(isNew);
    }

    @Test
    @DisplayName("Should replace trend on direction reversal")
    void replacesOnReversal() {
        tracker.startOrUpdate("BTCUSDT", "UP",
                new BigDecimal("1.5000"), new BigDecimal("96000"),
                new BigDecimal("97440"));

        boolean isNew = tracker.startOrUpdate("BTCUSDT", "DOWN",
                new BigDecimal("-2.0000"), new BigDecimal("97440"),
                new BigDecimal("95491"));

        assertTrue(isNew); // reversal counts as a new trend
        assertTrue(tracker.hasActiveTrend("BTCUSDT"));
    }

    @Test
    @DisplayName("Should resolve trends that have expired")
    void resolvesExpiredTrends() {
        tracker.startOrUpdate("BTCUSDT", "UP",
                new BigDecimal("1.5000"), new BigDecimal("96000"),
                new BigDecimal("97440"));

        // With Duration.ZERO, everything is instantly expired
        List<ActiveTrend> resolved = tracker.resolveExpired(Duration.ZERO);

        assertEquals(1, resolved.size());
        assertEquals("BTCUSDT", resolved.get(0).getSymbol());
        assertEquals("UP", resolved.get(0).getDirection());

        // Should be removed from active tracking
        assertFalse(tracker.hasActiveTrend("BTCUSDT"));
    }

    @Test
    @DisplayName("Should NOT resolve trends within cooldown window")
    void doesNotResolveActiveTrends() {
        tracker.startOrUpdate("BTCUSDT", "UP",
                new BigDecimal("1.5000"), new BigDecimal("96000"),
                new BigDecimal("97440"));

        // Very long timeout → nothing should expire
        List<ActiveTrend> resolved = tracker.resolveExpired(Duration.ofHours(1));

        assertTrue(resolved.isEmpty());
        assertTrue(tracker.hasActiveTrend("BTCUSDT"));
    }

    @Test
    @DisplayName("Should track multiple symbols independently")
    void tracksMultipleSymbols() {
        tracker.startOrUpdate("BTCUSDT", "UP",
                new BigDecimal("1.5000"), new BigDecimal("96000"),
                new BigDecimal("97440"));
        tracker.startOrUpdate("ETHUSDT", "DOWN",
                new BigDecimal("-2.0000"), new BigDecimal("3200"),
                new BigDecimal("3136"));

        assertTrue(tracker.hasActiveTrend("BTCUSDT"));
        assertTrue(tracker.hasActiveTrend("ETHUSDT"));

        // Resolve all (instant timeout)
        List<ActiveTrend> resolved = tracker.resolveExpired(Duration.ZERO);
        assertEquals(2, resolved.size());
    }
}
