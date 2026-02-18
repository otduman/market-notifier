package com.dumanch1.marketnotifier.service;

import com.dumanch1.marketnotifier.model.PriceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PriceStorageService}.
 * Uses Mockito to isolate the service from Redis.
 */
@ExtendWith(MockitoExtension.class)
class PriceStorageServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private PriceStorageService priceStorageService;

    @BeforeEach
    void setUp() {
        priceStorageService = new PriceStorageService(redisTemplate);
    }

    @Test
    @DisplayName("savePrice creates correct Redis key and member format")
    void savePriceCreatesCorrectKeyAndMember() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        long now = Instant.now().toEpochMilli();
        PriceEvent event = PriceEvent.builder()
                .symbol("BTCUSDT")
                .price(new BigDecimal("65432.10"))
                .timestamp(Instant.ofEpochMilli(now))
                .build();

        priceStorageService.savePrice(event);

        // Verify ZADD: key = "prices:BTCUSDT", member = "65432.10:<timestamp>", score = timestamp
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> memberCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);

        verify(zSetOperations).add(keyCaptor.capture(), memberCaptor.capture(), scoreCaptor.capture());

        assertEquals("prices:BTCUSDT", keyCaptor.getValue());
        assertEquals("65432.10:" + now, memberCaptor.getValue());
        assertEquals((double) now, scoreCaptor.getValue(), 0.001);
    }

    @Test
    @DisplayName("savePrice removes entries older than history duration")
    void savePriceRemovesOldEntries() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        PriceEvent event = PriceEvent.builder()
                .symbol("ETHUSDT")
                .price(new BigDecimal("3456.78"))
                .timestamp(Instant.now())
                .build();

        priceStorageService.savePrice(event);

        // Verify ZREMRANGEBYSCORE was called to trim old entries
        verify(zSetOperations).removeRangeByScore(eq("prices:ETHUSDT"), eq(0.0), anyDouble());
    }

    @Test
    @DisplayName("getLatestPrice returns null when no data exists")
    void getLatestPriceReturnsNullWhenEmpty() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRangeByScoreWithScores(anyString(), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .thenReturn(Set.of());

        BigDecimal result = priceStorageService.getLatestPrice("BTCUSDT");

        assertNull(result);
    }

    @Test
    @DisplayName("getLatestPrice returns null when result set is null")
    void getLatestPriceReturnsNullOnNullResult() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRangeByScoreWithScores(anyString(), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .thenReturn(null);

        BigDecimal result = priceStorageService.getLatestPrice("BTCUSDT");

        assertNull(result);
    }

    @Test
    @DisplayName("getLatestPrice correctly parses price from member format")
    void getLatestPriceParsesCorrectly() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        // Simulate a Redis sorted set entry: "65432.10:1712345678900"
        @SuppressWarnings("unchecked")
        TypedTuple<String> mockTuple = mock(TypedTuple.class);
        when(mockTuple.getValue()).thenReturn("65432.10:1712345678900");

        Set<TypedTuple<String>> resultSet = new LinkedHashSet<>();
        resultSet.add(mockTuple);

        when(zSetOperations.reverseRangeByScoreWithScores(
                eq("prices:BTCUSDT"), eq(0.0), eq(Double.MAX_VALUE), eq(0L), eq(1L)))
                .thenReturn(resultSet);

        BigDecimal result = priceStorageService.getLatestPrice("BTCUSDT");

        assertNotNull(result);
        assertEquals(new BigDecimal("65432.10"), result);
    }

    @Test
    @DisplayName("getPricesInLastNSeconds queries correct time range")
    void getPricesQueriesCorrectRange() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByScore(anyString(), anyDouble(), anyDouble()))
                .thenReturn(Set.of("100:1000", "101:2000"));

        Set<String> result = priceStorageService.getPricesInLastNSeconds("BTCUSDT", 60);

        assertNotNull(result);
        assertEquals(2, result.size());

        // Verify the key and that a time range was queried
        verify(zSetOperations).rangeByScore(eq("prices:BTCUSDT"), anyDouble(), anyDouble());
    }
}
