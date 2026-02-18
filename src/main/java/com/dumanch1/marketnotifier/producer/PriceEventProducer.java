package com.dumanch1.marketnotifier.producer;

import com.dumanch1.marketnotifier.model.PriceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

// @Slf4j injects a 'log' field: private static final Logger log = LoggerFactory.getLogger(...)
// We can then call log.info(), log.debug(), log.error() directly.
@Slf4j
// @RequiredArgsConstructor generates a constructor for all 'final' fields.
// Spring uses this constructor to inject dependencies (constructor injection).
// This is the preferred injection style over @Autowired field injection
// because:
// - Fields are final (immutable after construction)
// - Easier to test (just pass mocks in the constructor)
// - No Spring dependency in test code
@RequiredArgsConstructor
@Component
public class PriceEventProducer {

    // KafkaTemplate is Spring Kafka's core class for sending messages.
    // Spring Boot auto-configures it based on application.properties settings.
    // Generic type: <String, PriceEvent> means key=String, value=PriceEvent
    private final KafkaTemplate<String, PriceEvent> kafkaTemplate;

    @Value("${app.kafka.topic}")
    private String topic;

    // Sends a PriceEvent to the Kafka topic asynchronously.
    //
    // The key is the trading symbol ("BTCUSDT"). In Kafka, the key determines
    // which PARTITION the message goes to (same key always → same partition).
    // This ensures all BTCUSDT messages are ordered within their partition.
    //
    // kafkaTemplate.send() returns a CompletableFuture<SendResult>.
    // We attach callbacks to it:
    // - whenComplete: runs when the future finishes (success OR failure)
    // This is non-blocking — we don't sit and wait for Kafka to confirm.
    public void send(PriceEvent event) {
        CompletableFuture<SendResult<String, PriceEvent>> future = kafkaTemplate.send(topic, event.getSymbol(), event);

        future.whenComplete((result, exception) -> {
            if (exception != null) {
                // Log the error but don't crash — a single failed send
                // shouldn't take down the whole ingestion pipeline.
                log.error("Failed to send PriceEvent to Kafka topic '{}': {}",
                        topic, exception.getMessage());
            } else {
                // Only log at TRACE level for success — this fires 10-20 times/sec
                // and we don't want to flood the logs with INFO messages.
                log.trace("Sent PriceEvent to topic='{}' partition={} offset={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}