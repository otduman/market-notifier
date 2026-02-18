package com.dumanch1.marketnotifier.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

// This class represents the raw JSON that Binance sends over the WebSocket
// for each trade event. Example of what Binance actually sends:
//
// {
//   "e": "trade",        ← event type
//   "E": 1712345678901,  ← event time (ms)
//   "s": "BTCUSDT",      ← symbol
//   "t": 123456789,      ← trade ID
//   "p": "65432.10000000", ← price as string (Binance always sends prices as strings!)
//   "q": "0.00100000",   ← quantity
//   "T": 1712345678900,  ← trade time (ms) — we use THIS as our timestamp
//   "m": true,           ← was the buyer the market maker?
//   "M": true            ← was the trade the best price match?
// }
//
// @JsonIgnoreProperties(ignoreUnknown = true) is CRITICAL here.
// Binance sends many fields we don't care about. Without this annotation,
// Jackson would throw an exception every time it encounters an unknown field.
// With it, Jackson silently ignores any field not mapped in this class.
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BinanceTickerMessage {

    // @JsonProperty maps the short Binance field name to a readable Java field name
    @JsonProperty("e")
    private String eventType;       // "trade"

    @JsonProperty("s")
    private String symbol;          // "BTCUSDT"

    // IMPORTANT: Binance sends price as a String, not a number.
    // This is intentional on Binance's side to preserve full decimal precision.
    // We keep it as String here and convert to BigDecimal in PriceEvent.
    @JsonProperty("p")
    private String price;           // e.g., "65432.10000000"

    // Trade time — the exact millisecond timestamp of the trade.
    // We use this (not event time) as it represents when the trade actually occurred.
    @JsonProperty("T")
    private Long tradeTime;         // e.g., 1712345678900
}