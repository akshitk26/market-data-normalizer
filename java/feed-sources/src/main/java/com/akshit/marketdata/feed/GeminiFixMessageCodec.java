package com.akshit.marketdata.feed;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.stream.Collectors;

final class GeminiFixMessageCodec {
    static final char SOH = '\u0001';
    private static final int MAX_FIELD_LENGTH = 1_000_000;

    private GeminiFixMessageCodec() {
    }

    static String build(List<String> bodyFields) {
        String body = bodyFields.stream().collect(Collectors.joining(String.valueOf(SOH))) + SOH;
        byte[] bodyBytes = body.getBytes(StandardCharsets.US_ASCII);
        String header = "8=FIX.4.4" + SOH + "9=" + bodyBytes.length + SOH;
        String messageWithoutChecksum = header + body;
        int checksum = checksum(messageWithoutChecksum.getBytes(StandardCharsets.US_ASCII));
        return messageWithoutChecksum + "10=" + String.format(Locale.ROOT, "%03d", checksum) + SOH;
    }

    static String read(InputStream input) throws IOException {
        String beginString = readField(input);
        if (beginString == null) {
            return null;
        }
        if (!beginString.equals("8=FIX.4.4")) {
            throw new IOException("Unsupported FIX BeginString: " + beginString);
        }
        String bodyLengthField = readField(input);
        if (bodyLengthField == null || !bodyLengthField.startsWith("9=")) {
            throw new IOException("FIX message is missing tag 9 BodyLength");
        }
        int bodyLength;
        try {
            bodyLength = Integer.parseInt(bodyLengthField.substring(2));
        } catch (NumberFormatException e) {
            throw new IOException("Invalid FIX BodyLength: " + bodyLengthField, e);
        }
        byte[] body = readExactly(input, bodyLength);
        String checksum = readField(input);
        if (checksum == null || !checksum.matches("10=\\d{3}")) {
            throw new IOException("FIX message is missing tag 10 CheckSum");
        }
        String messageWithoutChecksum = beginString + SOH + bodyLengthField + SOH
                + new String(body, StandardCharsets.US_ASCII);
        int observedChecksum;
        try {
            observedChecksum = Integer.parseInt(checksum.substring(3));
        } catch (NumberFormatException e) {
            throw new IOException("Invalid FIX CheckSum: " + checksum, e);
        }
        int expectedChecksum = checksum(messageWithoutChecksum.getBytes(StandardCharsets.US_ASCII));
        if (observedChecksum != expectedChecksum) {
            throw new IOException("FIX CheckSum mismatch expected=" + expectedChecksum + " observed=" + observedChecksum);
        }
        return messageWithoutChecksum + checksum + SOH;
    }

    static String field(String message, int tag) {
        String normalized = message.replace('|', SOH);
        String prefix = tag + "=";
        for (String field : normalized.split(String.valueOf(SOH))) {
            if (field.startsWith(prefix)) {
                return field.substring(prefix.length());
            }
        }
        return null;
    }

    static List<String> fields(String message, int tag) {
        String normalized = message.replace('|', SOH);
        String prefix = tag + "=";
        List<String> values = new ArrayList<>();
        for (String field : normalized.split(String.valueOf(SOH))) {
            if (field.startsWith(prefix)) {
                values.add(field.substring(prefix.length()));
            }
        }
        return values;
    }

    private static String readField(InputStream input) throws IOException {
        StringBuilder field = new StringBuilder();
        int value;
        while ((value = input.read()) >= 0) {
            if (value == SOH) {
                return field.toString();
            }
            if (field.length() >= MAX_FIELD_LENGTH) {
                throw new IOException("FIX field exceeds maximum length");
            }
            field.append((char) value);
        }
        if (field.length() == 0) {
            return null;
        }
        throw new EOFException("Truncated FIX field");
    }

    private static byte[] readExactly(InputStream input, int length) throws IOException {
        if (length < 0 || length > 10_000_000) {
            throw new IOException("Invalid FIX BodyLength: " + length);
        }
        byte[] result = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(result, offset, length - offset);
            if (read < 0) {
                throw new EOFException("Truncated FIX message body");
            }
            offset += read;
        }
        return result;
    }

    private static int checksum(byte[] bytes) {
        int sum = 0;
        for (byte value : bytes) {
            sum = (sum + Byte.toUnsignedInt(value)) & 0xFF;
        }
        return sum;
    }
}
