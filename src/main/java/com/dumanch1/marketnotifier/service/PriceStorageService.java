package com.dumanch1.marketnotifier.service;

import com.dumanch1.marketnotifier.config.RedisKeys;
import com.dumanch1.marketnotifier.model.PriceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Service
public class PriceStorageService {

    private final RedisTemplate<String, String> redisTemplate;

    // Using shared RedisKeys constant for the price sorted set prefix.

    // How long to keep price history in Redis (5 minutes = 300 seconds).
    // We only need 60 seconds for alerts, but we keep 5 minutes for the
    // REST API endpoint that returns price history.
    private static final long HISTORY_DURATION_SECONDS = 300;

    // Stores a PriceEvent in a Redis Sorted Set.
    //
    // A Redis Sorted Set stores unique members, each with a numeric SCORE.
    // Members are always kept ordered by score. This is perfect for time-series:
    // - Member: the price value (as string, e.g., "65432.10")
    // - Score: the Unix timestamp in milliseconds (e.g., 1712345678900)
    //
    // This lets us query: "give me all prices between timestamp X and timestamp Y"
    // using the ZRANGEBYSCORE command — a single O(log N) Redis operation.
    //
    // Commands used:
    // ZADD prices:BTCUSDT <timestamp_ms> "<price>"
    // ZREMRANGEBYSCORE prices:BTCUSDT 0 <cutoff_ms> ← cleanup old entries
    public void savePrice(PriceEvent event) {
        String key = RedisKeys.PRICE_KEY_PREFIX + event.getSymbol();
        long timestampMs = event.getTimestamp().toEpochMilli();
        String priceStr = event.getPrice().toPlainString();

        // ZADD: add the price with timestamp as score
        // We use the timestamp as both the score AND part of the member value.
        // Why? Because Sorted Sets require UNIQUE members. If two trades happen
        // at the same millisecond with the same price, only one would be stored.
        // By appending the timestamp to the member, we guarantee uniqueness.
        String member = priceStr + ":" + timestampMs;
        redisTemplate.opsForZSet().add(key, member, timestampMs);

        // ZREMRANGEBYSCORE: remove all entries older than HISTORY_DURATION_SECONDS
        // This prevents Redis from growing infinitely.
        // "0" = from the beginning of time, cutoffMs = anything older than 5 min ago
        long cutoffMs = Instant.now().minusSeconds(HISTORY_DURATION_SECONDS).toEpochMilli();
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoffMs);

        log.trace("Saved price to Redis: key={} price={} ts={}", key, priceStr, timestampMs);
    }

    // Retrieves all prices from the last N seconds for a given symbol.
    // Returns them as a Set of BigDecimal values, from oldest to newest.
    //
    // Uses: ZRANGEBYSCORE prices:BTCUSDT <(now - windowSeconds)*1000> <now*1000>
    public Set<String> getPricesInLastNSeconds(String symbol, int windowSeconds) {
        String key = RedisKeys.PRICE_KEY_PREFIX + symbol;
        long nowMs = Instant.now().toEpochMilli();
        long windowStartMs = nowMs - (windowSeconds * 1000L);

        // ZRANGEBYSCORE returns all members whose score (timestamp) is between
        // windowStartMs and nowMs, in ascending score order (oldest first).
        return redisTemplate.opsForZSet().rangeByScore(key, windowStartMs, nowMs);
    }

    // Retrieves the single most recent price for a symbol.
    // Uses ZREVRANGEBYSCORE with LIMIT 1 to get just the latest entry.
    public BigDecimal getLatestPrice(String symbol) {
        String key = RedisKeys.PRICE_KEY_PREFIX + symbol;

        // ZREVRANGEBYSCORE: highest score (most recent) first, limit to 1 result
        Set<ZSetOperations.TypedTuple<String>> result = redisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0,
                Double.MAX_VALUE, 0, 1);

        if (result == null || result.isEmpty()) {
            return null;
        }

        // Parse the price from the "price:timestamp" member format
        String member = result.iterator().next().getValue();
        if (member == null)
            return null;

        String priceStr = member.split(":")[0];
        return new BigDecimal(priceStr);
    }
}