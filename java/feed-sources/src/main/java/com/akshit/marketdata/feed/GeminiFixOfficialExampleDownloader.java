package com.akshit.marketdata.feed;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GeminiFixOfficialExampleDownloader {
    public static final URI RESPONSES_URI = URI.create(
            "https://developer.gemini.com/trading/fix/market-data/examples/market-data-responses");
    public static final URI REQUESTS_URI = URI.create(
            "https://developer.gemini.com/trading/fix/market-data/examples/market-data-requests");

    private static final Pattern RAW_FIX_MESSAGE = Pattern.compile(
            "8=FIX\\.4\\.4\\|[^\\r\\n<]+?10=\\d{3}\\|");
    private final HttpClient httpClient;

    public GeminiFixOfficialExampleDownloader() {
        this(HttpClient.newHttpClient());
    }

    GeminiFixOfficialExampleDownloader(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public List<String> download() throws IOException, InterruptedException {
        Set<String> messages = new LinkedHashSet<>();
        messages.addAll(extract(fetch(REQUESTS_URI)));
        messages.addAll(extract(fetch(RESPONSES_URI)));
        return new ArrayList<>(messages);
    }

    public void downloadTo(Path output) throws IOException, InterruptedException {
        List<String> messages = download();
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(output, messages, StandardCharsets.UTF_8);
    }

    private String fetch(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Accept", "text/html")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Gemini FIX examples request failed with HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static List<String> extract(String html) {
        List<String> messages = new ArrayList<>();
        Matcher matcher = RAW_FIX_MESSAGE.matcher(html);
        while (matcher.find()) {
            messages.add(matcher.group());
        }
        return messages;
    }
}
