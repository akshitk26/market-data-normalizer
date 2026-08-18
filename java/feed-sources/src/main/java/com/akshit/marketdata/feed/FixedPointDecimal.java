package com.akshit.marketdata.feed;

/** Parses decimal values into the protobuf schema's nine-decimal integer scale. */
final class FixedPointDecimal {
    private static final long SCALE = 1_000_000_000L;

    private FixedPointDecimal() {
    }

    static long toNanos(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            throw new IllegalArgumentException("decimal value must not be empty");
        }
        String value = rawValue.trim();
        boolean negative = value.charAt(0) == '-';
        int start = (value.charAt(0) == '-' || value.charAt(0) == '+') ? 1 : 0;
        if (start == value.length()) {
            throw new IllegalArgumentException("invalid decimal value: " + rawValue);
        }

        int decimalPoint = value.indexOf('.', start);
        String wholeText = decimalPoint < 0 ? value.substring(start) : value.substring(start, decimalPoint);
        String fractionText = decimalPoint < 0 ? "" : value.substring(decimalPoint + 1);
        if (decimalPoint >= 0 && wholeText.isEmpty() && fractionText.isEmpty()) {
            throw new IllegalArgumentException("invalid decimal value: " + rawValue);
        }
        if (wholeText.isEmpty()) {
            wholeText = "0";
        }
        if (fractionText.length() > 9) {
            throw new IllegalArgumentException("decimal value has more than 9 fractional digits: " + rawValue);
        }
        if (!digits(wholeText) || !digits(fractionText)) {
            throw new IllegalArgumentException("invalid decimal value: " + rawValue);
        }

        long whole = Long.parseLong(wholeText);
        long fraction = fractionText.isEmpty() ? 0 : Long.parseLong(fractionText);
        for (int index = fractionText.length(); index < 9; index++) {
            fraction = Math.multiplyExact(fraction, 10);
        }
        long nanos = Math.addExact(Math.multiplyExact(whole, SCALE), fraction);
        return negative ? Math.negateExact(nanos) : nanos;
    }

    private static boolean digits(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }
}
