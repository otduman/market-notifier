package com.dumanch1.marketnotifier.service;

import com.dumanch1.marketnotifier.model.ActiveTrend;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active price trends per symbol.
 *
 * Instead of firing a notification on every tick that crosses the alert
 * threshold, this component accumulates trend data and only resolves
 * (triggers notification) once the price stops making new extremes.
 *
 * Thread-safe: uses ConcurrentHashMap because the Kafka listener thread
 * writes trends while the @Scheduled thread reads/removes expired ones.
 */
@Slf4j
@Component
public class TrendTracker {

    private final Map<String, ActiveTrend> activeTrends = new ConcurrentHashMap<>();

    /**
     * Start tracking a new trend, or update the peak of an existing one.
     *
     * If the symbol already has an active trend in the SAME direction,
     * we update its peak if this is a new extreme.
     *
     * If the symbol has an active trend in the OPPOSITE direction,
     * the old trend is replaced (direction reversal).
     *
     * @return true if this is a NEW trend (first detection), false if updating
     */
    public boolean startOrUpdate(String symbol, String direction,
            BigDecimal percentChange, BigDecimal startPrice,
            BigDecimal currentPrice) {

        ActiveTrend existing = activeTrends.get(symbol);

        // No existing trend → start a new one
        if (existing == null) {
            activeTrends.put(symbol, new ActiveTrend(
                    symbol, direction, startPrice, currentPrice, percentChange));
            log.debug("New trend started: {} {} at {}%", symbol, direction,
                    percentChange.toPlainString());
            return true;
        }

        // Direction reversal → replace with new trend
        if (!existing.getDirection().equals(direction)) {
            activeTrends.put(symbol, new ActiveTrend(
                    symbol, direction, startPrice, currentPrice, percentChange));
            log.debug("Trend reversal: {} now {} at {}%", symbol, direction,
                    percentChange.toPlainString());
            return true;
        }

        // Same direction → update peak if new extreme
        boolean updated = existing.updateIfNewExtreme(percentChange, currentPrice);
        if (updated) {
            log.debug("Trend updated: {} {} peak now {}%", symbol, direction,
                    existing.getPeakPercentChange().toPlainString());
        }
        return false;
    }

    /**
     * Find and remove all trends where the price hasn't made a new extreme
     * within the given timeout duration.
     *
     * @param timeout how long since the last extreme before resolving
     * @return list of resolved (settled) trends
     */
    public List<ActiveTrend> resolveExpired(Duration timeout) {
        List<ActiveTrend> resolved = new ArrayList<>();
        Instant cutoff = Instant.now().minus(timeout);

        activeTrends.entrySet().removeIf(entry -> {
            ActiveTrend trend = entry.getValue();
            if (!trend.getLastExtremeTime().isAfter(cutoff)) {
                resolved.add(trend);
                log.debug("Trend resolved: {} {} peak {}%",
                        trend.getSymbol(), trend.getDirection(),
                        trend.getPeakPercentChange().toPlainString());
                return true; // remove from map
            }
            return false;
        });

        return resolved;
    }

    /**
     * Check if a symbol currently has an active trend being tracked.
     */
    public boolean hasActiveTrend(String symbol) {
        return activeTrends.containsKey(symbol);
    }
}
