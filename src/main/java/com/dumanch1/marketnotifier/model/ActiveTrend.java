package com.dumanch1.marketnotifier.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Mutable state holder for one active price trend per symbol.
 *
 * Created when a symbol first crosses the alert threshold.
 * Updated as the price continues to move. Resolved (and a notification sent)
 * once the price stops making new extremes for a configurable cooldown period.
 */
public class ActiveTrend {

    private final String symbol;
    private final String direction; // "UP" or "DOWN"
    private final Instant startTime;
    private final BigDecimal startPrice; // the baseline price when the trend was detected
    private BigDecimal currentPrice;
    private BigDecimal peakPercentChange; // the most extreme % change observed
    private Instant lastExtremeTime; // when peakPercentChange was last updated

    public ActiveTrend(String symbol, String direction, BigDecimal startPrice,
            BigDecimal currentPrice, BigDecimal percentChange) {
        this.symbol = symbol;
        this.direction = direction;
        this.startTime = Instant.now();
        this.startPrice = startPrice;
        this.currentPrice = currentPrice;
        this.peakPercentChange = percentChange;
        this.lastExtremeTime = Instant.now();
    }

    /**
     * Update the peak if this tick represents a new extreme in the trend direction.
     *
     * For an UP trend, a new extreme means a HIGHER positive % change.
     * For a DOWN trend, a new extreme means a LOWER (more negative) % change.
     *
     * @return true if the peak was updated (new extreme reached)
     */
    public boolean updateIfNewExtreme(BigDecimal percentChange, BigDecimal price) {
        this.currentPrice = price;

        boolean isNewExtreme;
        if ("UP".equals(direction)) {
            isNewExtreme = percentChange.compareTo(peakPercentChange) > 0;
        } else {
            // DOWN: more negative is a new extreme
            isNewExtreme = percentChange.compareTo(peakPercentChange) < 0;
        }

        if (isNewExtreme) {
            this.peakPercentChange = percentChange;
            this.lastExtremeTime = Instant.now();
            return true;
        }
        return false;
    }

    // --- Getters ---

    public String getSymbol() {
        return symbol;
    }

    public String getDirection() {
        return direction;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public BigDecimal getStartPrice() {
        return startPrice;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public BigDecimal getPeakPercentChange() {
        return peakPercentChange;
    }

    public Instant getLastExtremeTime() {
        return lastExtremeTime;
    }
}
