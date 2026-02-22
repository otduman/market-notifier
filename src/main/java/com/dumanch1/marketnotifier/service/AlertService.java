package com.dumanch1.marketnotifier.service;

import com.dumanch1.marketnotifier.config.AlertProperties;
import com.dumanch1.marketnotifier.config.RedisKeys;
import com.dumanch1.marketnotifier.model.ActiveTrend;
import com.dumanch1.marketnotifier.model.PriceEvent;
import com.dumanch1.marketnotifier.notification.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class AlertService {

    private final PriceStorageService priceStorageService;
    @SuppressWarnings("rawtypes")
    private final RedisTemplate<String, String> redisTemplate;
    private final AlertProperties alertProperties;
    private final TrendTracker trendTracker;

    // Optional: only present when app.telegram.enabled=true (or any other
    // NotificationService implementation is active). When empty, alerts are
    // still logged and persisted to Redis, just not pushed externally.
    private final Optional<NotificationService> notificationService;

    public AlertService(PriceStorageService priceStorageService,
            RedisTemplate<String, String> redisTemplate,
            AlertProperties alertProperties,
            TrendTracker trendTracker,
            Optional<NotificationService> notificationService) {
        this.priceStorageService = priceStorageService;
        this.redisTemplate = redisTemplate;
        this.alertProperties = alertProperties;
        this.trendTracker = trendTracker;
        this.notificationService = notificationService;
    }

    // This is the core business logic of the entire application.
    //
    // Algorithm:
    // 1. Get all prices from the last windowSeconds (e.g., 60 seconds)
    // 2. If we don't have enough data yet (less than 2 points), skip
    // 3. Take the oldest price in the window as our baseline
    // 4. Compare current price to baseline
    // 5. If |change| >= threshold%, start or update a trend
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
            String direction = percentChange.compareTo(BigDecimal.ZERO) > 0 ? "UP" : "DOWN";

            // Start a new trend or update the peak of an existing one.
            // No notification is sent here — that happens when the trend settles.
            boolean isNew = trendTracker.startOrUpdate(
                    currentEvent.getSymbol(), direction, percentChange,
                    oldestPrice, currentPrice);

            if (isNew) {
                log.warn("=== TREND DETECTED === {} {} {}% | tracking...",
                        currentEvent.getSymbol(), direction,
                        percentChange.toPlainString());
            }
        }
    }

    /**
     * Runs every 10 seconds to check for trends that have settled
     * (no new extreme in cooldownSeconds). When a trend settles,
     * we format a summary, persist it to Redis, and send a notification.
     */
    @Scheduled(fixedRate = 10_000)
    public void resolveSettledTrends() {
        Duration cooldown = Duration.ofSeconds(alertProperties.cooldownSeconds());
        List<ActiveTrend> settled = trendTracker.resolveExpired(cooldown);

        for (ActiveTrend trend : settled) {
            String alertMessage = formatTrendSummary(trend);

            log.warn("=== SMART ALERT === {}", alertMessage);

            // Persist to Redis for the REST API history
            redisTemplate.opsForList().leftPush(RedisKeys.ALERTS_KEY, alertMessage);
            redisTemplate.opsForList().trim(RedisKeys.ALERTS_KEY,
                    0, alertProperties.maxHistory() - 1);

            // Send notification (Telegram, etc.)
            notificationService.ifPresent(service -> service.send(alertMessage));
        }
    }

    /**
     * Formats a settled trend into a human-readable summary message.
     * Designed to look clean in Telegram with spacing and line breaks.
     */
    private String formatTrendSummary(ActiveTrend trend) {
        String emoji = "UP".equals(trend.getDirection()) ? "🚀" : "🔻";

        Duration duration = Duration.between(trend.getStartTime(), Instant.now());
        long minutes = duration.toMinutes();
        long seconds = duration.toSecondsPart();
        String durationStr = minutes > 0
                ? String.format("%dm %ds", minutes, seconds)
                : String.format("%ds", seconds);

        // Strip trailing zeros from prices: 0.28130000 → 0.2813
        String startPrice = trend.getStartPrice().stripTrailingZeros().toPlainString();
        String currentPrice = trend.getCurrentPrice().stripTrailingZeros().toPlainString();
        String peakChange = trend.getPeakPercentChange().stripTrailingZeros().toPlainString();

        // Human-readable time: "20:47 UTC" instead of ISO-8601
        java.time.ZonedDateTime utcNow = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);
        String timeStr = String.format("%02d:%02d UTC", utcNow.getHour(), utcNow.getMinute());

        // Multi-line format with spacing for Telegram readability:
        //
        // 🚀 ADAUSDT UP
        // 📊 Change: +0.68% (peak: 0.68%)
        // 💰 $0.2813 → $0.2839
        // ⏱ Duration: 2m 47s
        // 🕐 20:47 UTC
        String sign = "UP".equals(trend.getDirection()) ? "+" : "";

        return String.format(
                "%s %s  %s\n📊 Change: %s%s%%  (peak: %s%%)\n💰 $%s → $%s\n⏱ Duration: %s\n🕐 %s",
                emoji,
                trend.getSymbol(),
                trend.getDirection(),
                sign,
                peakChange,
                peakChange,
                startPrice,
                currentPrice,
                durationStr,
                timeStr);
    }
}