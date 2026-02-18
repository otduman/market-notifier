package com.dumanch1.marketnotifier.controller;

import com.dumanch1.marketnotifier.config.BinanceProperties;
import com.dumanch1.marketnotifier.config.RedisKeys;
import com.dumanch1.marketnotifier.service.PriceStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// @RestController = @Controller + @ResponseBody
// Every method automatically serializes its return value to JSON.
// No need to write @ResponseBody on each method.
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class MarketController {

    private static final String DEFAULT_SYMBOL = "BTCUSDT";

    private final PriceStorageService priceStorageService;
    private final RedisTemplate<String, String> redisTemplate;
    private final BinanceProperties binanceProperties;

    // GET /api/price/latest?symbol=BTCUSDT
    // Returns the most recently received Bitcoin price.
    //
    // @RequestParam with defaultValue means the parameter is optional.
    // If not provided, it defaults to "BTCUSDT".
    // Symbol is normalized to uppercase so callers can use any casing.
    @GetMapping("/price/latest")
    public ResponseEntity<Map<String, Object>> getLatestPrice(
            @RequestParam(defaultValue = DEFAULT_SYMBOL) String symbol) {

        // Normalize to uppercase — Redis keys are stored as prices:BTCUSDT
        String normalizedSymbol = symbol.toUpperCase();
        BigDecimal price = priceStorageService.getLatestPrice(normalizedSymbol);

        if (price == null) {
            // 404: we don't have any price data yet (system just started?)
            return ResponseEntity.notFound().build();
        }

        // Return a JSON object: { "symbol": "BTCUSDT", "price": 65432.10,
        // "retrievedAt": "..." }
        return ResponseEntity.ok(Map.of(
                "symbol", normalizedSymbol,
                "price", price,
                "retrievedAt", Instant.now().toString()));
    }

    // GET /api/alerts
    // Returns the most recent Smart Alerts from Redis.
    //
    // @RequestParam "limit" controls how many alerts to return (default 10, max
    // 100).
    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> getAlerts(
            @RequestParam(defaultValue = "10") int limit) {

        // Clamp limit to a reasonable range to prevent abuse
        int safeLimit = Math.min(Math.max(limit, 1), 100);

        // LRANGE: get elements from the list from index 0 to safeLimit-1
        // Remember: we use LPUSH so index 0 is the NEWEST alert
        List<String> alerts = redisTemplate.opsForList()
                .range(RedisKeys.ALERTS_KEY, 0, safeLimit - 1);

        return ResponseEntity.ok(Map.of(
                "count", alerts == null ? 0 : alerts.size(),
                "alerts", alerts == null ? List.of() : alerts,
                "retrievedAt", Instant.now().toString()));
    }

    // GET /api/health/custom
    // A simple custom health endpoint (in addition to /actuator/health).
    // Returns a quick summary of system state.
    @GetMapping("/health/custom")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "market-notifier",
                "timestamp", Instant.now().toString()));
    }

    // GET /api/symbols
    // Returns all symbols being tracked and their latest prices.
    // Symbols are stored and queried in uppercase (normalized at ingestion).
    @GetMapping("/symbols")
    public ResponseEntity<Map<String, Object>> getTrackedSymbols() {
        Map<String, BigDecimal> prices = new HashMap<>();

        for (String symbol : binanceProperties.symbols()) {
            // Normalize to uppercase for Redis key lookup
            String symbolUpper = symbol.toUpperCase();
            BigDecimal price = priceStorageService.getLatestPrice(symbolUpper);
            prices.put(symbolUpper, price);
        }

        return ResponseEntity.ok(Map.of(
                "count", binanceProperties.symbols().size(),
                "symbols", prices,
                "retrievedAt", Instant.now().toString()));
    }
}