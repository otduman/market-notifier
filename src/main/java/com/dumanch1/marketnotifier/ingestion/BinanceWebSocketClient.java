package com.dumanch1.marketnotifier.ingestion;

import com.dumanch1.marketnotifier.config.BinanceProperties;
import com.dumanch1.marketnotifier.model.BinanceCombinedStreamMessage;
import com.dumanch1.marketnotifier.model.BinanceTickerMessage;
import com.dumanch1.marketnotifier.model.PriceEvent;
import com.dumanch1.marketnotifier.producer.PriceEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class BinanceWebSocketClient {

    private final WebSocketClient webSocketClient;
    private final PriceEventProducer priceEventProducer;
    private final ObjectMapper objectMapper;
    private final BinanceProperties binanceProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        // Build the combined stream URL from the symbol list.
        // Format:
        // wss://stream.binance.com:9443/stream?streams=btcusdt@trade/ethusdt@trade/solusdt@trade
        //
        // Why @trade stream?
        // Binance offers multiple stream types per symbol:
        // - @trade → individual trades (what we use — real-time, every ~100ms)
        // - @aggTrade → aggregated trades (lower frequency)
        // - @ticker → 24hr rolling window stats (once per second)
        // - @kline_1m → 1-minute candlesticks
        //
        // We use @trade because we want the highest-frequency price ticks.
        String streams = binanceProperties.symbols().stream()
                .map(symbol -> symbol.toLowerCase() + "@trade")
                .collect(Collectors.joining("/"));

        String fullUrl = binanceProperties.websocketBaseUrl() + "/stream?streams=" + streams;

        log.info("Connecting to Binance combined WebSocket stream with {} symbols: {}",
                binanceProperties.symbols().size(), binanceProperties.symbols());
        log.debug("Full WebSocket URL: {}", fullUrl);

        connectWithRetry(fullUrl);
    }

    private void connectWithRetry(String url) {
        Mono<Void> connection = webSocketClient.execute(
                URI.create(url),
                session -> session.receive()
                        .map(WebSocketMessage::getPayloadAsText)
                        // Parse the WRAPPER format first
                        .map(this::parseWrapper)
                        // Filter out nulls (failed parses)
                        .filter(wrapper -> wrapper != null && wrapper.getData() != null)
                        // Extract the actual trade data from the wrapper
                        .map(BinanceCombinedStreamMessage::getData)
                        // Process the trade data exactly as before
                        .doOnNext(this::processMessage)
                        .then());

        connection
                .retryWhen(
                        Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                                .maxBackoff(Duration.ofSeconds(30))
                                .doBeforeRetry(signal -> log.warn("WebSocket disconnected. Reconnecting... attempt {}",
                                        signal.totalRetries() + 1)))
                .doOnError(error -> log.error("WebSocket fatal error: {}", error.getMessage()))
                // IMPORTANT: If retries are exhausted or an unrecoverable error occurs,
                // the subscription completes silently. This callback ensures we log a
                // critical message so operators know price ingestion has stopped.
                .doOnTerminate(() -> log.error("=== CRITICAL === WebSocket connection terminated permanently. " +
                        "Price ingestion has STOPPED. Application restart may be required."))
                .subscribe();
    }

    // Parse the combined stream wrapper: {"stream": "btcusdt@trade", "data": {...}}
    // Returns null on parse failure to be filtered out in the pipeline.
    private BinanceCombinedStreamMessage parseWrapper(String rawJson) {
        try {
            BinanceCombinedStreamMessage wrapper = objectMapper.readValue(rawJson, BinanceCombinedStreamMessage.class);

            // Log which symbol we received data from (only at TRACE level — happens
            // 10-20x/sec)
            log.trace("Received message from stream: {}", wrapper.getStream());
            return wrapper;

        } catch (Exception e) {
            log.warn("Failed to parse Binance wrapper: {} | Error: {}", rawJson, e.getMessage());
            return null;
        }
    }

    // This method processes BinanceTickerMessage and publishes a PriceEvent.
    // Symbol is normalized to UPPERCASE here — the single source-of-truth for
    // casing.
    // All downstream code (Kafka, Redis, REST API) sees uppercase symbols.
    private void processMessage(BinanceTickerMessage message) {
        try {
            PriceEvent event = PriceEvent.builder()
                    .symbol(message.getSymbol().toUpperCase())
                    .price(new BigDecimal(message.getPrice()))
                    .timestamp(Instant.ofEpochMilli(message.getTradeTime()))
                    .build();

            log.debug("Received price tick: {} @ {}", event.getSymbol(), event.getPrice());
            priceEventProducer.send(event);

        } catch (Exception e) {
            log.error("Failed to process Binance message: {}", e.getMessage());
        }
    }
}