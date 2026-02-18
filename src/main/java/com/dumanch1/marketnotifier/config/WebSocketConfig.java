package com.dumanch1.marketnotifier.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;

@Configuration
public class WebSocketConfig {

    // We define the WebSocketClient as a Spring bean, so it can be
    // injected via @Autowired / constructor injection wherever needed.
    //
    // ReactorNettyWebSocketClient is the WebFlux implementation backed by
    // Reactor Netty — a non-blocking networking library.
    //
    // "Non-blocking" here means: when we connect to Binance and wait for
    // the next message, we do NOT block a JVM thread. Instead, Netty uses
    // an event loop — a small pool of threads that handle thousands of
    // connections by registering callbacks, not by sitting idle waiting.
    //
    // This is important because Binance sends ~10-20 messages per second.
    // If each message blocked a thread for even 50ms, we'd exhaust the
    // thread pool quickly. Non-blocking I/O handles this with near-zero overhead.
    @Bean
    public WebSocketClient webSocketClient() {
        return new ReactorNettyWebSocketClient();
    }
}