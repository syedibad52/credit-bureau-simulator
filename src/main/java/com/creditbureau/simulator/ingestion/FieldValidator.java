package com.creditbureau.simulator.ingestion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates individual fields from CSV records.
 * Collects all validation errors for a single record before rejecting it.
 */
public class FieldValidator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE; // YYYY-MM-DD
    private final List<String> errors = new ArrayList<>();
    private final int lineNumber;

    public FieldValidator(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    /** Validate a required non-blank string field. */
    public String requireString(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            errors.add(String.format("Line %d: '%s' is required but missing or blank", lineNumber, fieldName));
            return null;
        }
        return value.trim();
    }

    /** Validate and parse a required date field in YYYY-MM-DD format. */
    public LocalDate requireDate(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            errors.add(String.format("Line %d: '%s' is required but missing", lineNumber, fieldName));
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            errors.add(String.format("Line %d: '%s' has invalid date format '%s'. Expected YYYY-MM-DD", lineNumber, fieldName, value));
            return null;
        }
    }

    /** Validate and parse an optional date field. Returns null if blank. */
    public LocalDate optionalDate(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            errors.add(String.format("Line %d: '%s' has invalid date format '%s'. Expected YYYY-MM-DD", lineNumber, fieldName, value));
            return null;
        }
    }

    /** Validate and parse a required BigDecimal field. */
    public BigDecimal requireBigDecimal(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            errors.add(String.format("Line %d: '%s' is required but missing", lineNumber, fieldName));
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            errors.add(String.format("Line %d: '%s' has invalid numeric value '%s'", lineNumber, fieldName, value));
            return null;
        }
    }

    /** Validate and parse a required non-negative integer field. */
    public int requireNonNegativeInt(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            errors.add(String.format("Line %d: '%s' is required but missing", lineNumber, fieldName));
            return -1;
        }
        try {
            int result = Integer.parseInt(value.trim());
            if (result < 0) {
                errors.add(String.format("Line %d: '%s' must be non-negative, got %d", lineNumber, fieldName, result));
                return -1;
            }
            return result;
        } catch (NumberFormatException e) {
            errors.add(String.format("Line %d: '%s' has invalid integer value '%s'", lineNumber, fieldName, value));
            return -1;
        }
    }

    /** Validate and parse an enum value. */
    public <E extends Enum<E>> E requireEnum(String value, String fieldName, Class<E> enumClass) {
        if (value == null || value.isBlank()) {
            errors.add(String.format("Line %d: '%s' is required but missing", lineNumber, fieldName));
            return null;
        }
        try {
            String normalized = value.trim().toUpperCase().replace("-", "_");
            return Enum.valueOf(enumClass, normalized);
        } catch (IllegalArgumentException e) {
            errors.add(String.format("Line %d: '%s' has invalid value '%s'. Valid values: %s",
                    lineNumber, fieldName, value, java.util.Arrays.toString(enumClass.getEnumConstants())));
            return null;
        }
    }

    /** Validate and parse an optional enum value. Returns null if blank. */
    public <E extends Enum<E>> E optionalEnum(String value, String fieldName, Class<E> enumClass) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireEnum(value, fieldName, enumClass);
    }

    /** Check if any validation errors occurred. */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /** Add a custom error message. */
    public void addCustomError(String message) {
        errors.add(String.format("Line %d: %s", lineNumber, message));
    }

    /** Get all collected validation errors. */
    public List<String> getErrors() {
        return errors;
    }
}

