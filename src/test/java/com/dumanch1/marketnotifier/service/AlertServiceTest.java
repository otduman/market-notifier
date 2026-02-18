package com.dumanch1.marketnotifier.service;

import com.dumanch1.marketnotifier.config.AlertProperties;
import com.dumanch1.marketnotifier.model.PriceEvent;
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
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AlertService}.
 * Uses Mockito to isolate the alert logic from Redis and PriceStorageService.
 */
@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private PriceStorageService priceStorageService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    private AlertService alertService;

    // Alert config: 1% threshold, 60-second window, keep 100 alerts
    private static final AlertProperties TEST_PROPS = new AlertProperties(new BigDecimal("1.0"), 60, 100);

    @BeforeEach
    void setUp() {
        alertService = new AlertService(priceStorageService, redisTemplate, TEST_PROPS);
    }

    @Test
    @DisplayName("Should fire alert when price change exceeds threshold")
    void firesAlertWhenPriceExceedsThreshold() {
        // Given: BTC was 100.00 sixty seconds ago, now it's 102.00 (2% increase)
        Set<String> prices = orderedSet("100.00:1000000", "101.00:1030000", "102.00:1060000");
        when(priceStorageService.getPricesInLastNSeconds("BTCUSDT", 60)).thenReturn(prices);
        when(redisTemplate.opsForList()).thenReturn(listOperations);

        PriceEvent event = PriceEvent.builder()
                .symbol("BTCUSDT")
                .price(new BigDecimal("102.00"))
                .timestamp(Instant.now())
                .build();

        // When
        alertService.checkAndAlert(event);

        // Then: alert was saved to Redis (LPUSH + LTRIM)
        verify(listOperations).leftPush(eq("alerts:history"), anyString());
        verify(listOperations).trim("alerts:history", 0, 99);
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

        // When
        alertService.checkAndAlert(event);

        // Then: no Redis interaction (no alert fired)
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Should NOT fire alert when less than 2 price points exist")
    void doesNotFireAlertWithInsufficientData() {
        // Given: only 1 data point in window
        Set<String> prices = orderedSet("100.00:1000000");
        when(priceStorageService.getPricesInLastNSeconds("BTCUSDT", 60)).thenReturn(prices);

        PriceEvent event = PriceEvent.builder()
                .symbol("BTCUSDT")
                .price(new BigDecimal("100.00"))
                .timestamp(Instant.now())
                .build();

        // When
        alertService.checkAndAlert(event);

        // Then
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
    @DisplayName("Should handle price drop (negative change) correctly")
    void firesAlertOnPriceDrop() {
        // Given: BTC dropped from 100.00 to 97.00 (3% drop)
        Set<String> prices = orderedSet("100.00:1000000", "98.00:1030000", "97.00:1060000");
        when(priceStorageService.getPricesInLastNSeconds("BTCUSDT", 60)).thenReturn(prices);
        when(redisTemplate.opsForList()).thenReturn(listOperations);

        PriceEvent event = PriceEvent.builder()
                .symbol("BTCUSDT")
                .price(new BigDecimal("97.00"))
                .timestamp(Instant.now())
                .build();

        alertService.checkAndAlert(event);

        // Alert message should contain "DOWN" direction
        verify(listOperations).leftPush(eq("alerts:history"), contains("DOWN"));
    }

    @Test
    @DisplayName("Should handle zero oldest price without error")
    void handlesZeroOldestPrice() {
        // Given: oldest price is zero (edge case)
        Set<String> prices = orderedSet("0:1000000", "100.00:1060000");
        when(priceStorageService.getPricesInLastNSeconds("BTCUSDT", 60)).thenReturn(prices);

        PriceEvent event = PriceEvent.builder()
                .symbol("BTCUSDT")
                .price(new BigDecimal("100.00"))
                .timestamp(Instant.now())
                .build();

        // Should not throw — just skip due to division by zero guard
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
