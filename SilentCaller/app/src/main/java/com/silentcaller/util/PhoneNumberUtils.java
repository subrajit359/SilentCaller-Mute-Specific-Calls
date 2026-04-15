package com.silentcaller.util;

/**
 * Utility class for normalizing phone numbers before database lookups.
 * Handles spaces, dashes, brackets, and common country code prefixes.
 */
public class PhoneNumberUtils {

    /**
     * Normalize a phone number for consistent storage and comparison.
     *
     * Transformations applied:
     * 1. Remove all whitespace
     * 2. Remove dashes, dots, parentheses, brackets
     * 3. Remove leading + sign (international prefix)
     * 4. Strip common country code prefixes: +91 (India), +1 (US/CA), 00 (international)
     * 5. Strip leading zero (local format)
     *
     * The resulting number is digits-only and stripped of any country code or formatting.
     */
    public static String normalize(String phoneNumber) {
        if (phoneNumber == null) return "";

        // Remove all formatting characters
        String normalized = phoneNumber
                .replaceAll("[\\s\\-\\.\\(\\)\\[\\]]", "") // spaces, dashes, dots, brackets
                .replaceAll("^\\+", "");                   // remove leading +

        // Strip common country codes
        // Handle +91 (India) — 10-digit local numbers
        if (normalized.startsWith("91") && normalized.length() == 12) {
            normalized = normalized.substring(2);
        }
        // Handle +1 (US/Canada) — 10-digit local numbers
        else if (normalized.startsWith("1") && normalized.length() == 11) {
            normalized = normalized.substring(1);
        }
        // Handle 00 international dialing prefix
        else if (normalized.startsWith("00")) {
            normalized = normalized.substring(2);
            // After stripping 00, strip country code if it makes it 12 chars (e.g., 0091...)
            if (normalized.startsWith("91") && normalized.length() == 12) {
                normalized = normalized.substring(2);
            } else if (normalized.startsWith("1") && normalized.length() == 11) {
                normalized = normalized.substring(1);
            }
        }
        // Strip leading single zero (local format like 0xxxxxxxxxx)
        else if (normalized.startsWith("0") && normalized.length() > 10) {
            normalized = normalized.substring(1);
        }

        return normalized;
    }

    /**
     * Compare two phone numbers by normalizing both before comparison.
     */
    public static boolean areNumbersEqual(String number1, String number2) {
        return normalize(number1).equals(normalize(number2));
    }
}
