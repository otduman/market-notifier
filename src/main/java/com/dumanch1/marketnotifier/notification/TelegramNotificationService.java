package com.dumanch1.marketnotifier.notification;

import com.dumanch1.marketnotifier.config.TelegramProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Sends alert messages to a Telegram chat via the Bot API.
 *
 * This bean is only created when {@code app.telegram.enabled=true}.
 * When disabled, no HTTP client is created and AlertService's
 * {@code Optional<NotificationService>} stays empty.
 *
 * Uses the Telegram Bot API endpoint:
 * POST https://api.telegram.org/bot{token}/sendMessage
 * Body: chat_id={chatId}&text={message}&parse_mode=HTML
 *
 * All calls are wrapped in try-catch — a failed Telegram send must
 * NEVER crash the Kafka consumer pipeline. We log the failure and move on.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.telegram.enabled", havingValue = "true")
public class TelegramNotificationService implements NotificationService {

    private final RestClient restClient;
    private final TelegramProperties telegramProperties;

    public TelegramNotificationService(TelegramProperties telegramProperties) {
        this.telegramProperties = telegramProperties;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.telegram.org")
                .build();

        log.info("Telegram notifications ENABLED — alerts will be sent to chat ID: {}",
                telegramProperties.chatId());
    }

    @Override
    public void send(String message) {
        try {
            // POST /bot<token>/sendMessage
            // Telegram accepts form-urlencoded or JSON. We use URI query params
            // for simplicity — no request body serialization needed.
            String response = restClient.post()
                    .uri("/bot{token}/sendMessage?chat_id={chatId}&text={text}",
                            telegramProperties.botToken(),
                            telegramProperties.chatId(),
                            message)
                    .retrieve()
                    .body(String.class);

            log.debug("Telegram message sent successfully: {}", response);

        } catch (Exception e) {
            // CRITICAL: Never let a notification failure crash the consumer.
            // The price pipeline must keep running even if Telegram is down.
            log.error("Failed to send Telegram notification: {}", e.getMessage());
        }
    }
}
