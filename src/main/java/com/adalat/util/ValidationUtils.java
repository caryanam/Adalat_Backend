package com.adalat.util;

import java.util.regex.Pattern;

public final class ValidationUtils {

    private static final Pattern INDIAN_MOBILE_PATTERN = Pattern.compile("^[6-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern UPI_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{2,256}@[a-zA-Z]{2,64}$");
    private static final Pattern NAME_VALID_CHARS_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z\\s.'-]*[a-zA-Z.]$");

    private ValidationUtils() {
        // Utility class
    }

    /**
     * Normalizes a mobile number by stripping spaces, hyphens, country code (+91 / 91), or leading 0.
     * Returns a 10-digit numeric string if possible, or the stripped digits.
     */
    public static String normalizeMobile(String mobile) {
        if (mobile == null) {
            return "";
        }
        String cleaned = mobile.replaceAll("[^0-9]", "");
        // If 12 digits and starts with 91 (India country code), strip 91
        if (cleaned.length() == 12 && cleaned.startsWith("91")) {
            cleaned = cleaned.substring(2);
        }
        // If 11 digits and starts with 0, strip 0
        if (cleaned.length() == 11 && cleaned.startsWith("0")) {
            cleaned = cleaned.substring(1);
        }
        return cleaned;
    }

    /**
     * Validates if the normalized mobile number is a valid 10-digit Indian number starting with 6, 7, 8, or 9.
     */
    public static boolean isValidMobile(String mobile) {
        if (mobile == null) {
            return false;
        }
        String normalized = normalizeMobile(mobile);
        return INDIAN_MOBILE_PATTERN.matcher(normalized).matches();
    }

    /**
     * Normalizes an email address by trimming whitespace and converting to lowercase.
     */
    public static String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase();
    }

    /**
     * Validates email format.
     */
    public static boolean isValidEmail(String email) {
        if (email == null) {
            return false;
        }
        String normalized = normalizeEmail(email);
        return EMAIL_PATTERN.matcher(normalized).matches();
    }

    /**
     * Validates UPI ID format (e.g. name@upi, 9876543210@paytm).
     */
    public static boolean isValidUpiId(String upiId) {
        if (upiId == null || upiId.isBlank()) {
            return false;
        }
        String trimmed = upiId.trim();
        return UPI_PATTERN.matcher(trimmed).matches();
    }

    /**
     * Validates person/account name.
     * Rejects single-letter names, single or multiple dots alone (e.g. '.', 'A.', 'AB', '...').
     * Requires minimum 3 alphabetic letters.
     */
    public static boolean isValidName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String trimmed = name.trim();
        if (trimmed.length() < 3) {
            return false;
        }
        // Must contain at least 3 alphabetic characters (A-Z, a-z)
        long letterCount = trimmed.chars().filter(Character::isLetter).count();
        if (letterCount < 3) {
            return false;
        }
        return NAME_VALID_CHARS_PATTERN.matcher(trimmed).matches();
    }

    /**
     * Normalizes names by trimming and collapsing multiple spaces into a single space.
     */
    public static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().replaceAll("\\s+", " ");
    }
}
