package com.dumanch1.marketnotifier.config;

import com.dumanch1.marketnotifier.model.PriceEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topic}")
    private String topicName;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // -------------------------------------------------------------------------
    // WHY WE DEFINE THIS BEAN MANUALLY:
    //
    // Spring Boot auto-configures a KafkaTemplate, but it creates it as a raw
    // KafkaTemplate<Object, Object> or KafkaTemplate<String, String> — it has
    // no way to know we want KafkaTemplate<String, PriceEvent> specifically.
    //
    // When PriceEventProducer declares:
    // private final KafkaTemplate<String, PriceEvent> kafkaTemplate;
    //
    // Spring tries to find a bean matching EXACTLY that generic type. The
    // auto-configured one doesn't match, so Spring throws:
    // "required a bean of type KafkaTemplate that could not be found"
    //
    // The fix: define the ProducerFactory and KafkaTemplate ourselves with
    // the exact generic types and serializers we need. Spring Boot backs off
    // its own auto-configuration when it sees we've defined our own.
    // -------------------------------------------------------------------------

    // ProducerFactory is the factory that creates the underlying Kafka producer
    // clients. It holds the configuration (broker address, serializers, etc.)
    // and is responsible for managing producer lifecycle.
    //
    // DefaultKafkaProducerFactory is the standard Spring Kafka implementation.
    // It takes a Map<String, Object> of Kafka producer configuration properties.
    @Bean
    public ProducerFactory<String, PriceEvent> producerFactory() {
        Map<String, Object> config = new HashMap<>();

        // BOOTSTRAP_SERVERS: where to find the Kafka broker
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // KEY_SERIALIZER: how to convert message keys (String) to bytes
        // Our keys are the trading symbol e.g. "BTCUSDT"
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // VALUE_SERIALIZER: how to convert message values (PriceEvent) to bytes
        // JsonSerializer converts the PriceEvent object → JSON string → bytes
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);

        // ACKS: acknowledgement level
        // "all" = wait for all in-sync replicas to confirm the write.
        // With 1 broker this is equivalent to "1", but it's explicit good practice.
        config.put(ProducerConfig.ACKS_CONFIG, "all");

        // RETRIES: how many times to retry a failed send before giving up
        config.put(ProducerConfig.RETRIES_CONFIG, 3);

        // ADD_TYPE_HEADERS: JsonSerializer by default adds a "__TypeId__" header
        // to every Kafka message, containing the full Java class name.
        // The consumer-side JsonDeserializer reads this header to know which
        // class to deserialize into — without it, deserialization would fail.
        // We explicitly set it to true here to be clear about this behavior.
        DefaultKafkaProducerFactory<String, PriceEvent> factory = new DefaultKafkaProducerFactory<>(config);
        factory.setValueSerializer(new JacksonJsonSerializer<>());

        return factory;
    }

    // KafkaTemplate wraps the ProducerFactory and provides the high-level
    // send() API we use in PriceEventProducer.
    // The generic type <String, PriceEvent> now exactly matches what
    // PriceEventProducer expects — Spring will inject this bean cleanly.
    @Bean
    public KafkaTemplate<String, PriceEvent> kafkaTemplate(
            ProducerFactory<String, PriceEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // Auto-create the "crypto-prices" topic on startup if it doesn't exist.
    // KafkaAdmin (auto-configured by Spring Boot) picks up all NewTopic beans
    // and creates them against the broker automatically.
    @Bean
    public NewTopic cryptoPricesTopic() {
        return TopicBuilder.name(topicName)
                .partitions(1)
                .replicas(1)
                .build();
    }
}