package com.dumanch1.marketnotifier.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

// When you connect to Binance using the COMBINED stream endpoint:
//   wss://stream.binance.com:9443/stream?streams=btcusdt@trade/ethusdt@trade
//
// Each message is WRAPPED in this format:
//   {
//     "stream": "btcusdt@trade",
//     "data": { <the actual trade data> }
//   }
//
// The "stream" field tells you which symbol this message came from.
// The "data" field contains the raw BinanceTickerMessage we already parse.
//
// This wrapper exists because a single WebSocket connection can receive
// messages from multiple symbols, so Binance needs to identify the source.
//
// @JsonIgnoreProperties ensures that if Binance adds new fields to the
// wrapper format in the future, our code won't break.
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BinanceCombinedStreamMessage {

    // Stream identifier, e.g. "btcusdt@trade", "ethusdt@trade"
    private String stream;

    // The actual trade data — this is a BinanceTickerMessage.
    // Jackson will automatically deserialize this nested object.
    private BinanceTickerMessage data;
}