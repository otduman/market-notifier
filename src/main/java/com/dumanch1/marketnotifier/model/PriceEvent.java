package com.dumanch1.marketnotifier.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

// PriceEvent is OUR internal representation of a Bitcoin price tick.
// This is what travels on the Kafka topic "crypto-prices".
//
// Key design decisions:
//
// 1. We use BigDecimal for price (NOT double or float).
//    Financial amounts must NEVER be stored as floating-point types.
//    Example: 0.1 + 0.2 in double = 0.30000000000000004 (floating point error).
//    BigDecimal gives us exact decimal arithmetic. Always use it for money/prices.
//
// 2. We use Instant for timestamp (NOT long milliseconds directly).
//    Instant is Java's standard type for a point in time on the UTC timeline.
//    It's timezone-safe, serializes cleanly to JSON (ISO-8601 string), and is
//    easier to work with in time-based calculations.
//
// 3. @Builder lets us construct PriceEvent with a fluent API:
//    PriceEvent.builder().symbol("BTCUSDT").price(new BigDecimal("65432.10")).build()
//    This is safer than a constructor (argument order bugs are impossible).
//
// 4. @NoArgsConstructor is required by Jackson for deserialization.
//    When Jackson receives a JSON string from Kafka, it needs a no-arg constructor
//    to create an empty object and then populate fields. Without this, Kafka
//    deserialization would throw an exception.
//
// 5. @AllArgsConstructor is required by @Builder internally.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceEvent {

    // Trading pair symbol, e.g. "BTCUSDT"
    private String symbol;

    // The exact Bitcoin price at the moment of this trade
    private BigDecimal price;

    // When this trade happened (UTC)
    private Instant timestamp;
}