package com.creditbureau.simulator.ingestion;

import com.creditbureau.simulator.domain.model.Rejection;

import java.util.List;

/**
 * Result of parsing/validating a CSV file. Contains successfully parsed records,
 * a list of validation errors, and structured rejection records.
 *
 * @param <T> the type of successfully parsed records
 */
public record ValidationResult<T>(
    List<T> validRecords,
    List<String> errors,
    List<Rejection> rejections
) {
    public ValidationResult(List<T> validRecords, List<String> errors) {
        this(validRecords, errors, List.of());
    }

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
