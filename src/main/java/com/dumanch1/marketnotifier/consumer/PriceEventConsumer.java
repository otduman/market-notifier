package com.dumanch1.marketnotifier.consumer;

import com.dumanch1.marketnotifier.model.PriceEvent;
import com.dumanch1.marketnotifier.service.AlertService;
import com.dumanch1.marketnotifier.service.PriceStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
@Component
public class PriceEventConsumer {

    private final PriceStorageService priceStorageService;
    private final AlertService alertService;

    // Events older than this are skipped entirely.
    // Must be >= PriceStorageService.HISTORY_DURATION_SECONDS (300s) to avoid
    // writing entries that are immediately cleaned up on the next savePrice() call.
    private static final Duration MAX_EVENT_AGE = Duration.ofMinutes(5);

    @KafkaListener(topics = "${app.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(PriceEvent event) {
        // Skip stale events from Kafka replay.
        // When the app restarts, the consumer may replay old messages
        // (auto-offset-reset=earliest). These have timestamps from hours/days ago.
        // Saving them to Redis is pointless — they'd be added and immediately
        // deleted by the ZREMRANGEBYSCORE cleanup in savePrice().
        // Worse, the alert engine sees 0 data points because everything gets purged.
        if (isStale(event)) {
            return;
        }

        log.debug("Consumed PriceEvent from Kafka: {} @ {} at {}",
                event.getSymbol(), event.getPrice(), event.getTimestamp());

        // Step 1: Store this price tick in Redis for the rolling window
        priceStorageService.savePrice(event);

        // Step 2: Check if this new price triggers any alert
        alertService.checkAndAlert(event);
    }

    private boolean isStale(PriceEvent event) {
        Instant cutoff = Instant.now().minus(MAX_EVENT_AGE);
        return event.getTimestamp().isBefore(cutoff);
    }
}