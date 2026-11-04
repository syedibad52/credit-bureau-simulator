package com.creditbureau.simulator.ingestion;

import java.util.List;

/**
 * Result of parsing/validating a CSV file. Contains successfully parsed records
 * and a list of validation errors for rejected records.
 *
 * @param <T> the type of successfully parsed records
 */
public record ValidationResult<T>(
    List<T> validRecords,
    List<String> errors
) {
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    public int errorCount() {
        return errors == null ? 0 : errors.size();
    }

    public int validCount() {
        return validRecords == null ? 0 : validRecords.size();
    }
}

