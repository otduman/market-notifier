package com.dumanch1.marketnotifier.service;

import com.dumanch1.marketnotifier.config.AlertProperties;
import com.dumanch1.marketnotifier.config.RedisKeys;
import com.dumanch1.marketnotifier.model.PriceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Service
public class AlertService {

    private final PriceStorageService priceStorageService;
    private final RedisTemplate<String, String> redisTemplate;
    private final AlertProperties alertProperties;

    // This is the core business logic of the entire application.
    //
    // Algorithm:
    // 1. Get all prices from the last windowSeconds (e.g., 60 seconds)
    // 2. If we don't have enough data yet (less than 2 points), skip
    // 3. Take the oldest price in the window as our baseline
    // 4. Compare current price to baseline
    // 5. If |change| >= threshold%, fire an alert
    //
    // percentageChange = ((current - baseline) / baseline) * 100
    //
    // We use BigDecimal throughout for financial precision.
    // RoundingMode.HALF_UP is the standard "school math" rounding (0.5 rounds up).
    public void checkAndAlert(PriceEvent currentEvent) {
        Set<String> pricesInWindow = priceStorageService.getPricesInLastNSeconds(
                currentEvent.getSymbol(), alertProperties.windowSeconds());

        // Need at least 2 data points to calculate a change
        if (pricesInWindow == null || pricesInWindow.size() < 2) {
            log.debug("Not enough data points for alert check ({} points)",
                    pricesInWindow == null ? 0 : pricesInWindow.size());
            return;
        }

        // The set is ordered oldest-first (lowest score/timestamp first).
        // So the first element is our oldest price in the window.
        String oldestMember = pricesInWindow.iterator().next();
        BigDecimal oldestPrice = new BigDecimal(oldestMember.split(":")[0]);
        BigDecimal currentPrice = currentEvent.getPrice();

        // Guard against division by zero (shouldn't happen with real prices, but be
        // safe)
        if (oldestPrice.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        // Calculate percentage change with 4 decimal places of precision
        BigDecimal change = currentPrice.subtract(oldestPrice);
        BigDecimal percentChange = change
                .divide(oldestPrice, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_UP);

        BigDecimal absChange = percentChange.abs();

        if (absChange.compareTo(alertProperties.thresholdPercent()) >= 0) {
            fireAlert(currentEvent.getSymbol(), currentPrice, oldestPrice, percentChange);
        }
    }

    private void fireAlert(String symbol, BigDecimal currentPrice,
            BigDecimal oldestPrice, BigDecimal percentChange) {

        // Determine direction: positive = pump, negative = dump
        String direction = percentChange.compareTo(BigDecimal.ZERO) > 0 ? "UP" : "DOWN";
        String emoji = direction.equals("UP") ? "🚀" : "🔻";

        String alertMessage = String.format(
                "%s ALERT [%s] %s moved %s%% in the last %ds | from: $%s → now: $%s | at: %s",
                emoji,
                symbol,
                direction,
                percentChange.toPlainString(),
                alertProperties.windowSeconds(),
                oldestPrice.toPlainString(),
                currentPrice.toPlainString(),
                Instant.now());

        // Log the alert — this is our primary delivery mechanism for the MVP
        log.warn("=== SMART ALERT === {}", alertMessage);

        // Also persist to Redis List so the REST endpoint can return alert history
        // LPUSH: push to the LEFT (front) of the list — newest first
        redisTemplate.opsForList().leftPush(RedisKeys.ALERTS_KEY, alertMessage);

        // LTRIM: keep only the most recent maxHistory entries.
        // Without this, the list would grow forever.
        // Indices: 0 = first (newest), maxHistory-1 = last (oldest we keep)
        redisTemplate.opsForList().trim(RedisKeys.ALERTS_KEY, 0, alertProperties.maxHistory() - 1);
    }
}