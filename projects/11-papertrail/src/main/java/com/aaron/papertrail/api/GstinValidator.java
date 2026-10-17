package com.aaron.papertrail.api;

import java.util.regex.Pattern;

/** Offline GSTIN structure and mod-36 check-character validation; does not check registration status. */
final class GstinValidator {
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final Pattern STRUCTURE = Pattern.compile("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]");

    private GstinValidator() { }

    static boolean isValid(String gstin) {
        if (gstin == null || !STRUCTURE.matcher(gstin).matches()) return false;
        int sum = 0;
        int factor = 1;
        for (int i = 0; i < 14; i++) {
            int value = ALPHABET.indexOf(gstin.charAt(i));
            if (value < 0) return false;
            int product = value * factor;
            sum += product / ALPHABET.length() + product % ALPHABET.length();
            factor = factor == 1 ? 2 : 1;
        }
        int checkIndex = (ALPHABET.length() - sum % ALPHABET.length()) % ALPHABET.length();
        return gstin.charAt(14) == ALPHABET.charAt(checkIndex);
    }
}
