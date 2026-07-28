package com.recruitment.validation;

import java.util.regex.Pattern;

public final class PhoneValidator {

    public static final String PATTERN = "^\\+216\\d{8}$";
    public static final String MESSAGE = "Phone must be +216 followed by 8 digits (e.g. +21612345678)";
    private static final Pattern REGEX = Pattern.compile(PATTERN);

    private PhoneValidator() {}

    public static boolean isValid(String phone) {
        if (phone == null || phone.isBlank()) {
            return true;
        }
        return REGEX.matcher(phone.trim()).matches();
    }

    public static String normalize(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String trimmed = phone.trim();
        if (!isValid(trimmed)) {
            throw new IllegalArgumentException(MESSAGE);
        }
        return trimmed;
    }
}
