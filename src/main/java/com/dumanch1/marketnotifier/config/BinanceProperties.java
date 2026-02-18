package com.dumanch1.marketnotifier.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Type-safe configuration for Binance WebSocket integration.
 *
 * Bound to the {@code app.binance.*} namespace in application.properties.
 * Using a Java record makes the properties immutable and injectable
 * as a final field via constructor injection.
 *
 * @param websocketBaseUrl Binance public WebSocket endpoint base URL
 * @param symbols          List of cryptocurrency trading pair symbols to track
 */
@ConfigurationProperties(prefix = "app.binance")
public record BinanceProperties(
        String websocketBaseUrl,
        List<String> symbols) {
}
