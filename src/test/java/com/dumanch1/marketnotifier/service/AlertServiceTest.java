package com.dumanch1.marketnotifier.service;

import com.dumanch1.marketnotifier.config.AlertProperties;
import com.dumanch1.marketnotifier.model.PriceEvent;
import com.dumanch1.marketnotifier.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AlertService}.
 * Verifies trend-tracking behavior: checkAndAlert() starts/updates trends,
 * resolveSettledTrends() sends notifications for settled trends.
 */
@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private PriceStorageService priceStorageService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private NotificationService notificationService;

    private TrendTracker trendTracker;
    private AlertService alertService;

    // Alert config: 1% threshold, 60s window, keep 100 alerts, 120s cooldown
    private static final AlertProperties TEST_PROPS = new AlertProperties(new BigDecimal("1.0"), 60, 100, 120);

    @BeforeEach
    void setUp() {
        trendTracker = new TrendTracker();
        alertService = new AlertService(priceStorageService, redisTemplate,
                TEST_PROPS, trendTracker, Optional.of(notificationService));
    }

    @Test
    @DisplayName("Should start trend tracking when price change exceeds threshold (no immediate notification)")
    void startsTrendOnThresholdBreach() {
        // Given: BTC was 100.00, now it's 102.00 (2% increase)
        Set<String> prices = orderedSet("100.00:1000000", "101.00:1030000", "102.00:1060000");
        when(priceStorageService.getPricesInLastNSeconds("BTCUSDT", 60)).thenReturn(prices);

        PriceEvent event = PriceEvent.builder()
                .symbol("BTCUSDT")
                .price(new BigDecimal("102.00"))
                .timestamp(Instant.now())
                .build();

        // When
        alertService.checkAndAlert(event);

        // Then: trend should be tracked, but NO notification sent yet
        verify(notificationService, never()).send(anyString());
        assert trendTracker.hasActiveTrend("BTCUSDT");
    }

    @Test
    @DisplayName("Should send notification only when trend settles via resolveSettledTrends()")
    void sendsNotificationOnSettledTrend() {
        // Given: start a trend
        Set<String> prices = orderedSet("100.00:1000000", "101.00:1030000", "102.00:1060000");
        when(priceStorageService.getPricesInLastNSeconds("BTCUSDT", 60)).thenReturn(prices);
        when(redisTemplate.opsForList()).thenReturn(listOperations);

        PriceEvent event = PriceEvent.builder()
                .symbol("BTCUSDT")
                .price(new BigDecimal("102.00"))
                .timestamp(Instant.now())
                .build();
        alertService.checkAndAlert(event);

        // Hack: use a test-specific AlertService with 0s cooldown to force resolution
        AlertProperties zeroCooldownProps = new AlertProperties(new BigDecimal("1.0"), 60, 100, 0);
        AlertService zeroCooldownService = new AlertService(priceStorageService,
                redisTemplate, zeroCooldownProps, trendTracker,
                Optional.of(notificationService));

        // When: resolve settled trends (0s cooldown = instant resolution)
        zeroCooldownService.resolveSettledTrends();

        // Then: notification was sent
        verify(notificationService).send(anyString());
        verify(listOperations).leftPush(eq("alerts:history"), anyString());
    }

    @Test
    @DisplayName("Should NOT fire alert when price change is below threshold")
    void doesNotFireAlertBelowThreshold() {
        // Given: BTC was 100.00, now it's 100.50 (0.5% — below 1% threshold)
        Set<String> prices = orderedSet("100.00:1000000", "100.25:1030000", "100.50:1060000");
        when(priceStorageService.getPricesInLastNSeconds("BTCUSDT", 60)).thenReturn(prices);

        PriceEvent event = PriceEvent.builder()
                .symbol("BTCUSDT")
                .price(new BigDecimal("100.50"))
                .timestamp(Instant.now())
                .build();

        alertService.checkAndAlert(event);

        verifyNoInteractions(redisTemplate);
        assert !trendTracker.hasActiveTrend("BTCUSDT");
    }

    @Test
    @DisplayName("Should NOT fire alert when less than 2 price points exist")
    void doesNotFireAlertWithInsufficientData() {
        Set<String> prices = orderedSet("100.00:1000000");
        when(priceStorageService.getPricesInLastNSeconds("BTCUSDT", 60)).thenReturn(prices);

        PriceEvent event = PriceEvent.builder()
                .symbol("BTCUSDT")
                .price(new BigDecimal("100.00"))
                .timestamp(Instant.now())
                .build();

        alertService.checkAndAlert(event);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Should NOT fire alert when window data is null")
    void doesNotFireAlertOnNullData() {
        when(priceStorageService.getPricesInLastNSeconds("BTCUSDT", 60)).thenReturn(null);

        PriceEvent event = PriceEvent.builder()
                .symbol("BTCUSDT")
                .price(new BigDecimal("100.00"))
                .timestamp(Instant.now())
                .build();

        alertService.checkAndAlert(event);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Should handle price drop (negative change) — starts DOWN trend")
    void startsTrendOnPriceDrop() {
        Set<String> prices = orderedSet("100.00:1000000", "98.00:1030000", "97.00:1060000");
        when(priceStorageService.getPricesInLastNSeconds("BTCUSDT", 60)).thenReturn(prices);

        PriceEvent event = PriceEvent.builder()
                .symbol("BTCUSDT")
                .price(new BigDecimal("97.00"))
                .timestamp(Instant.now())
                .build();

        alertService.checkAndAlert(event);

        // Trend started, but no immediate notification
        verify(notificationService, never()).send(anyString());
        assert trendTracker.hasActiveTrend("BTCUSDT");
    }

    @Test
    @DisplayName("Should handle zero oldest price without error")
    void handlesZeroOldestPrice() {
        Set<String> prices = orderedSet("0:1000000", "100.00:1060000");
        when(priceStorageService.getPricesInLastNSeconds("BTCUSDT", 60)).thenReturn(prices);

        PriceEvent event = PriceEvent.builder()
                .symbol("BTCUSDT")
                .price(new BigDecimal("100.00"))
                .timestamp(Instant.now())
                .build();

        alertService.checkAndAlert(event);

        verifyNoInteractions(redisTemplate);
    }

    /**
     * Creates a LinkedHashSet that preserves insertion order,
     * mimicking Redis ZRANGEBYSCORE's ascending-score output.
     */
    private Set<String> orderedSet(String... members) {
        Set<String> set = new LinkedHashSet<>();
        for (String m : members) {
            set.add(m);
        }
        return set;
    }
}
