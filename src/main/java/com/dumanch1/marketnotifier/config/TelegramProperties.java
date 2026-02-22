package com.dumanch1.marketnotifier.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration for Telegram Bot notifications.
 *
 * Bound to the {@code app.telegram.*} namespace in application.properties.
 * Notifications are disabled by default — set {@code app.telegram.enabled=true}
 * and provide a valid bot token + chat ID to activate.
 *
 * @param enabled  Feature toggle — when false, no Telegram bean is created
 * @param botToken The Bot API token from @BotFather
 * @param chatId   The target chat/group/channel ID to send alerts to
 */
@ConfigurationProperties(prefix = "app.telegram")
public record TelegramProperties(
        boolean enabled,
        String botToken,
        String chatId) {
}
