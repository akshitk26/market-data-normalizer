package com.akshit.marketdata.ingestion;

import com.akshit.marketdata.feed.CoinbaseLevel2WebSocketClient;
import com.akshit.marketdata.feed.LocalFixMarketDataBridge;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class LocalFixBridgeApp {
    private LocalFixBridgeApp() {
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : LocalFixMarketDataBridge.DEFAULT_PORT;
        List<String> products = args.length > 1
                ? parseProducts(args[1])
                : CoinbaseLevel2WebSocketClient.DEFAULT_PRODUCT_IDS;
        int maxMessages = args.length > 2 ? Integer.parseInt(args[2]) : 100;

        LocalFixMarketDataBridge.BridgeResult result =
                new LocalFixMarketDataBridge(port, products, maxMessages).run();
        System.out.println("source=coinbase-public-websocket-local-fix-bridge");
        System.out.println("products=" + String.join(",", result.products()));
        System.out.println("fix_messages=" + result.fixMessages());
    }

    private static List<String> parseProducts(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toList());
    }
}
