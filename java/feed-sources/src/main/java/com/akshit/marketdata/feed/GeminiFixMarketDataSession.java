package com.akshit.marketdata.feed;

import com.akshit.marketdata.proto.MarketDataEnvelope;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import javax.net.ssl.SSLParameters;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public final class GeminiFixMarketDataSession {
    private static final DateTimeFormatter FIX_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS")
            .withZone(ZoneOffset.UTC);

    private final GeminiFixSessionConfig config;
    private final FixTagValueMarketDataParser parser;
    private long nextOutgoingSequence;
    private long expectedIncomingSequence;
    private long lastSentAtMillis;
    private long lastReceivedAtMillis;
    private long lastTestRequestAtMillis;

    public GeminiFixMarketDataSession(GeminiFixSessionConfig config) {
        this.config = config;
        this.parser = new FixTagValueMarketDataParser();
    }

    public SessionResult capture() throws IOException {
        SequenceState state = SequenceState.load(config.sequenceFile());
        nextOutgoingSequence = config.resetSequenceOnLogon() ? 1 : state.nextOutgoingSequence;
        expectedIncomingSequence = config.resetSequenceOnLogon() ? 1 : state.nextIncomingSequence;

        Path parent = config.output().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Socket socket = openSocket();
             BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
             OutputStream output = socket.getOutputStream();
             BufferedWriter capture = Files.newBufferedWriter(config.output(), StandardCharsets.UTF_8)) {
            socket.setSoTimeout(1_000);
            long now = System.currentTimeMillis();
            lastSentAtMillis = now;
            lastReceivedAtMillis = now;
            send(output, logonMessage());

            int marketDataMessages = 0;
            boolean subscribed = false;
            while (marketDataMessages < config.maxMarketDataMessages()) {
                String rawMessage;
                try {
                    rawMessage = GeminiFixMessageCodec.read(input);
                } catch (SocketTimeoutException timeout) {
                    long nowInTimeout = System.currentTimeMillis();
                    long heartbeatMillis = config.heartbeatSeconds() * 1_000L;
                    if (nowInTimeout - lastSentAtMillis >= heartbeatMillis) {
                        send(output, heartbeatMessage(null));
                    }
                    if (nowInTimeout - lastReceivedAtMillis >= heartbeatMillis
                            && nowInTimeout - lastTestRequestAtMillis >= heartbeatMillis) {
                        send(output, testRequestMessage());
                        lastTestRequestAtMillis = nowInTimeout;
                    }
                    if (nowInTimeout - lastReceivedAtMillis >= heartbeatMillis * 2) {
                        throw new IOException("Gemini FIX session did not receive traffic after Test Request");
                    }
                    continue;
                }
                if (rawMessage == null) {
                    break;
                }
                lastReceivedAtMillis = System.currentTimeMillis();
                checkIncomingSequence(rawMessage, output);
                String messageType = GeminiFixMessageCodec.field(rawMessage, 35);
                if ("A".equals(messageType) && !subscribed) {
                    send(output, marketDataRequestMessage());
                    subscribed = true;
                } else if ("1".equals(messageType)) {
                    send(output, heartbeatMessage(GeminiFixMessageCodec.field(rawMessage, 112)));
                } else if ("5".equals(messageType)) {
                    break;
                } else if ("W".equals(messageType) || "X".equals(messageType)) {
                    capture.write(rawMessage.replace(GeminiFixMessageCodec.SOH, '|'));
                    capture.newLine();
                    capture.flush();
                    List<MarketDataEnvelope> events = parser.parse(rawMessage);
                    marketDataMessages++;
                    if (events.isEmpty()) {
                        throw new IOException("Gemini FIX market-data message produced no normalized events");
                    }
                }
            }
            return new SessionResult(config.output(), marketDataMessages, expectedIncomingSequence);
        } finally {
            state.nextOutgoingSequence = nextOutgoingSequence;
            state.nextIncomingSequence = expectedIncomingSequence;
            state.save(config.sequenceFile());
        }
    }

    private Socket openSocket() throws IOException {
        Socket socket;
        if (config.transportTls()) {
            socket = SSLSocketFactory.getDefault().createSocket();
        } else {
            socket = new Socket();
        }
        socket.connect(new InetSocketAddress(config.host(), config.port()), 15_000);
        if (config.transportTls()) {
            SSLSocket tlsSocket = (SSLSocket) socket;
            SSLParameters parameters = tlsSocket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            tlsSocket.setSSLParameters(parameters);
            tlsSocket.startHandshake();
        }
        return socket;
    }

    private String logonMessage() {
        List<String> fields = standardHeader("A");
        fields.add("98=0");
        fields.add("108=" + config.heartbeatSeconds());
        if (config.resetSequenceOnLogon()) {
            fields.add("141=Y");
        }
        return GeminiFixMessageCodec.build(fields);
    }

    private String marketDataRequestMessage() {
        List<String> fields = standardHeader("V");
        fields.add("262=" + System.currentTimeMillis());
        fields.add("263=1");
        fields.add("264=" + config.marketDepth());
        fields.add("146=" + config.symbols().size());
        for (String symbol : config.symbols()) {
            fields.add("55=" + symbol);
        }
        fields.add("267=" + config.entryTypes().size());
        for (String entryType : config.entryTypes()) {
            fields.add("269=" + entryType);
        }
        return GeminiFixMessageCodec.build(fields);
    }

    private String heartbeatMessage(String testRequestId) {
        List<String> fields = standardHeader("0");
        if (testRequestId != null && !testRequestId.isEmpty()) {
            fields.add("112=" + testRequestId);
        }
        return GeminiFixMessageCodec.build(fields);
    }

    private String testRequestMessage() {
        List<String> fields = standardHeader("1");
        fields.add("112=mdn-" + System.currentTimeMillis());
        return GeminiFixMessageCodec.build(fields);
    }

    private String resendRequestMessage(long fromSequence, long toSequence) {
        List<String> fields = standardHeader("2");
        fields.add("7=" + fromSequence);
        fields.add("16=" + toSequence);
        return GeminiFixMessageCodec.build(fields);
    }

    private List<String> standardHeader(String messageType) {
        return new ArrayList<>(Arrays.asList(
                "35=" + messageType,
                "34=" + nextOutgoingSequence++,
                "49=" + config.senderCompId(),
                "52=" + FIX_TIMESTAMP.format(Instant.now()),
                "56=" + config.targetCompId()));
    }

    private void send(OutputStream output, String message) throws IOException {
        output.write(message.getBytes(StandardCharsets.US_ASCII));
        output.flush();
        lastSentAtMillis = System.currentTimeMillis();
    }

    private void checkIncomingSequence(String rawMessage, OutputStream output) throws IOException {
        String sequence = GeminiFixMessageCodec.field(rawMessage, 34);
        if (sequence == null) {
            return;
        }
        long observed = Long.parseLong(sequence);
        if (observed > expectedIncomingSequence) {
            System.err.println("fix_sequence_warning expected=" + expectedIncomingSequence + " observed=" + observed);
            send(output, resendRequestMessage(expectedIncomingSequence, observed - 1));
        } else if (observed < expectedIncomingSequence) {
            System.err.println("fix_sequence_warning expected=" + expectedIncomingSequence + " observed=" + observed);
            return;
        }
        expectedIncomingSequence = observed + 1;
    }

    public static final class SessionResult {
        private final Path output;
        private final int marketDataMessages;
        private final long nextIncomingSequence;

        private SessionResult(Path output, int marketDataMessages, long nextIncomingSequence) {
            this.output = output;
            this.marketDataMessages = marketDataMessages;
            this.nextIncomingSequence = nextIncomingSequence;
        }

        public Path output() { return output; }
        public int marketDataMessages() { return marketDataMessages; }
        public long nextIncomingSequence() { return nextIncomingSequence; }
    }

    private static final class SequenceState {
        private long nextOutgoingSequence = 1;
        private long nextIncomingSequence = 1;
        private Path path;

        private static SequenceState load(Path path) throws IOException {
            SequenceState state = new SequenceState();
            state.path = path;
            if (!Files.exists(path)) {
                return state;
            }
            Properties properties = new Properties();
            try (java.io.Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            state.nextOutgoingSequence = Long.parseLong(properties.getProperty("nextOutgoingSequence", "1"));
            state.nextIncomingSequence = Long.parseLong(properties.getProperty("nextIncomingSequence", "1"));
            return state;
        }

        private void save(Path path) throws IOException {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Properties properties = new Properties();
            properties.setProperty("nextOutgoingSequence", Long.toString(nextOutgoingSequence));
            properties.setProperty("nextIncomingSequence", Long.toString(nextIncomingSequence));
            try (java.io.Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                properties.store(writer, "Gemini FIX session sequence state");
            }
        }
    }
}
