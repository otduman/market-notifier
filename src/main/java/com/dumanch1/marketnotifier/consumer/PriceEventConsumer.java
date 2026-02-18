package com.dumanch1.marketnotifier.consumer;

import com.dumanch1.marketnotifier.model.PriceEvent;
import com.dumanch1.marketnotifier.service.AlertService;
import com.dumanch1.marketnotifier.service.PriceStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class PriceEventConsumer {

    private final PriceStorageService priceStorageService;
    private final AlertService alertService;

    // @KafkaListener is the heart of the consumer side.
    //
    // Spring Kafka creates a background thread (or thread pool) that
    // continuously polls the Kafka topic. When a message arrives, Spring
    // deserializes it (using the config from application.properties) and calls
    // this method automatically.
    //
    // topics: which topic(s) to listen on
    // groupId: the consumer group. Spring reads this from application.properties
    // spring.kafka.consumer.group-id, but we can override per-listener.
    // Using ${} syntax pulls it from properties — keeps it in one place.
    //
    // This method runs synchronously within the Kafka listener thread.
    // If it throws an exception, Spring Kafka will handle it according to
    // the error handler configuration (default: log and continue).
    @KafkaListener(topics = "${app.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(PriceEvent event) {
        log.debug("Consumed PriceEvent from Kafka: {} @ {} at {}",
                event.getSymbol(), event.getPrice(), event.getTimestamp());

        // Step 1: Store this price tick in Redis for the rolling window
        priceStorageService.savePrice(event);

        // Step 2: Check if this new price triggers any alert
        alertService.checkAndAlert(event);
    }
}