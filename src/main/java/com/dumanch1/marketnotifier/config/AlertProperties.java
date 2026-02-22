package com.dumanch1.marketnotifier.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Type-safe configuration for the Smart Alert engine.
 *
 * Bound to the {@code app.alert.*} namespace in application.properties.
 * Using a Java record makes the properties immutable and injectable
 * as a final field via constructor injection.
 *
 * @param thresholdPercent Minimum price change (%) to trigger an alert
 * @param windowSeconds    Rolling time window to calculate price change over
 * @param maxHistory       Maximum number of recent alerts to keep in Redis
 * @param cooldownSeconds  How long (in seconds) a trend must go without a new
 *                         extreme before a summary notification is sent
 */
@ConfigurationProperties(prefix = "app.alert")
public record AlertProperties(
                BigDecimal thresholdPercent,
                int windowSeconds,
                int maxHistory,
                int cooldownSeconds) {
}
