package com.dumanch1.marketnotifier.config;

// Centralized Redis key constants.
//
// Both AlertService and MarketController need the "alerts:history" key.
// Previously each class had its own private static final String — if one
// was changed and the other wasn't, they'd silently diverge and the
// REST endpoint would return stale/empty data.
//
// By extracting them here, all Redis key references are in one place.
public final class RedisKeys {

    private RedisKeys() {
        // Utility class — prevent instantiation
    }

    // Sorted set keys for per-symbol price history: "prices:BTCUSDT"
    public static final String PRICE_KEY_PREFIX = "prices:";

    // List key for recent smart alert messages
    public static final String ALERTS_KEY = "alerts:history";
}
