package com.creditbureau.simulator.domain.enums;

/**
 * Represents the outcome of a credit account dispute resolution.
 *
 * <p>When a borrower disputes account information reported to the bureau,
 * the dispute is investigated and resolved with one of these outcomes:</p>
 * <ul>
 *     <li>{@link #VALID} — the disputed information is confirmed as accurate
 *         and remains on the report.</li>
 *     <li>{@link #REMOVED} — the disputed information is found to be inaccurate
 *         and is removed from the report, potentially improving the score.</li>
 * </ul>
 */
public enum DisputeOutcome {

    /** Dispute investigation confirmed the reported information is accurate. */
    VALID,

    /** Dispute investigation found the information inaccurate; it is removed from the report. */
    REMOVED;

    /**
     * Parses a string value to a {@code DisputeOutcome}, case-insensitively.
     *
     * @param value the string representation of the dispute outcome
     * @return the matching {@code DisputeOutcome}
     * @throws IllegalArgumentException if the value does not match any outcome
     */
    public static DisputeOutcome fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "DisputeOutcome value must not be null or blank");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown DisputeOutcome: '" + value + "'. Valid values: VALID, REMOVED", e);
        }
    }
}
