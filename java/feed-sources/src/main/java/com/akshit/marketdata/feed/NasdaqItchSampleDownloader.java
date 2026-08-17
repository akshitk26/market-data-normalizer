package com.akshit.marketdata.feed;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public final class NasdaqItchSampleDownloader {
    public static final URI DIRECTORY_URI = URI.create("https://emi.nasdaq.com/ITCH/Nasdaq%20ITCH/");
    private static final Pattern V50_FILE = Pattern.compile(
            "href=\"([^\"]*S(\\d{6})-v50\\.txt\\.gz)\"", Pattern.CASE_INSENSITIVE);
    private final HttpClient httpClient;

    public NasdaqItchSampleDownloader() {
        this(HttpClient.newHttpClient());
    }

    NasdaqItchSampleDownloader(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public List<SampleFile> discover() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(DIRECTORY_URI).GET().build();
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Nasdaq ITCH directory request failed with HTTP " + response.statusCode());
        }

        List<SampleFile> files = new ArrayList<>();
        Matcher matcher = V50_FILE.matcher(response.body());
        while (matcher.find()) {
            String href = matcher.group(1).replace("&amp;", "&");
            URI uri = DIRECTORY_URI.resolve(href);
            LocalDate sessionDate = LocalDate.parse(matcher.group(2), DateTimeFormatter.ofPattern("MMddyy"));
            files.add(new SampleFile(uri, uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1), sessionDate));
        }
        if (files.isEmpty()) {
            throw new IOException("No Nasdaq TotalView-ITCH 5.0 sample files found");
        }
        return Collections.unmodifiableList(files);
    }

    public CaptureResult captureRandomWindow(Path output, int maxMessages, int maxSkipMessages, long seed)
            throws IOException, InterruptedException {
        if (maxMessages <= 0 || maxSkipMessages < 0) {
            throw new IllegalArgumentException("maxMessages must be positive and maxSkipMessages cannot be negative");
        }
        List<SampleFile> files = discover();
        Random random = new Random(seed);
        SampleFile source = files.get(random.nextInt(files.size()));
        int skipMessages = maxSkipMessages == 0 ? 0 : random.nextInt(maxSkipMessages + 1);

        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        int captured = 0;
        int preambleMessages = 0;
        byte[] firstOrderFlow = null;
        try (InputStream responseStream = open(source.uri);
             DataInputStream input = new DataInputStream(
                     new BufferedInputStream(new GZIPInputStream(responseStream, 64 * 1024)));
             DataOutputStream outputStream = new DataOutputStream(Files.newOutputStream(output))) {
            while (true) {
                byte[] message = readMessage(input);
                if (message == null) {
                    break;
                }
                if (isOrderFlowMessage(message)) {
                    firstOrderFlow = message;
                    break;
                }
                preambleMessages++;
            }
            if (firstOrderFlow != null && captured < maxMessages) {
                writeMessage(outputStream, firstOrderFlow);
                captured++;
            }
            for (int index = 0; index < skipMessages; index++) {
                if (!skipMessage(input)) {
                    break;
                }
            }
            while (captured < maxMessages) {
                byte[] message = readMessage(input);
                if (message == null) {
                    break;
                }
                writeMessage(outputStream, message);
                captured++;
            }
        }
        return new CaptureResult(source, preambleMessages + skipMessages, captured, seed);
    }

    private InputStream open(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Accept-Encoding", "identity")
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) {
            response.body().close();
            throw new IOException("Nasdaq ITCH sample request failed with HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static boolean skipMessage(DataInputStream input) throws IOException {
        int length;
        try {
            length = input.readUnsignedShort();
        } catch (java.io.EOFException e) {
            return false;
        }
        int remaining = length;
        while (remaining > 0) {
            int skipped = input.skipBytes(remaining);
            if (skipped == 0) {
                if (input.read() < 0) {
                    throw new IOException("Truncated ITCH message while skipping");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
        return true;
    }

    private static byte[] readMessage(DataInputStream input) throws IOException {
        int length;
        try {
            length = input.readUnsignedShort();
        } catch (java.io.EOFException e) {
            return null;
        }
        byte[] message = new byte[length];
        input.readFully(message);
        return message;
    }

    private static void writeMessage(DataOutputStream output, byte[] message) throws IOException {
        if (message.length > 0xFFFF) {
            throw new IOException("ITCH message exceeds two-byte framing limit: " + message.length);
        }
        output.writeShort(message.length);
        output.write(message);
    }

    private static boolean isOrderFlowMessage(byte[] message) {
        if (message.length == 0) {
            return false;
        }
        switch (message[0]) {
            case 'A':
            case 'F':
            case 'P':
            case 'Q':
                return true;
            default:
                return false;
        }
    }

    public static final class SampleFile {
        private final URI uri;
        private final String name;
        private final LocalDate sessionDate;

        private SampleFile(URI uri, String name, LocalDate sessionDate) {
            this.uri = uri;
            this.name = name;
            this.sessionDate = sessionDate;
        }

        public URI uri() {
            return uri;
        }

        public String name() {
            return name;
        }

        public LocalDate sessionDate() {
            return sessionDate;
        }
    }

    public static final class CaptureResult {
        private final SampleFile source;
        private final int skippedMessages;
        private final int capturedMessages;
        private final long seed;

        private CaptureResult(SampleFile source, int skippedMessages, int capturedMessages, long seed) {
            this.source = source;
            this.skippedMessages = skippedMessages;
            this.capturedMessages = capturedMessages;
            this.seed = seed;
        }

        public SampleFile source() {
            return source;
        }

        public int skippedMessages() {
            return skippedMessages;
        }

        public int capturedMessages() {
            return capturedMessages;
        }

        public long seed() {
            return seed;
        }
    }
}
