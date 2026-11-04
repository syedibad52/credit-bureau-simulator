package com.creditbureau.simulator.domain.enums;

/**
 * Represents significant account-level events that affect credit bureau reporting
 * and score calculations.
 *
 * <p>These events model real-world actions taken by lenders or borrowers that
 * alter the state of a credit account and may trigger score recalculations.</p>
 */
public enum EventType {

    /** Lender has written off the account as an irrecoverable loss. */
    WRITTEN_OFF,

    /** Account has been settled for less than the full outstanding balance. */
    SETTLED,

    /** Lender has filed a legal suit to recover the outstanding debt. */
    SUIT_FILED,

    /** Borrower has raised a dispute on the account information. */
    DISPUTE,

    /** A previously raised dispute has been resolved. */
    DISPUTE_RESOLVED;

    /**
     * Parses a string value to an {@code EventType}, case-insensitively.
     * Underscores and hyphens are normalized so that both "SUIT_FILED"
     * and "SUIT-FILED" are accepted.
     *
     * @param value the string representation of the event type
     * @return the matching {@code EventType}
     * @throws IllegalArgumentException if the value does not match any type
     */
    public static EventType fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "EventType value must not be null or blank");
        }
        String normalized = value.trim().toUpperCase().replace('-', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown EventType: '" + value + "'. Valid values: "
                            + "WRITTEN_OFF, SETTLED, SUIT_FILED, DISPUTE, DISPUTE_RESOLVED", e);
        }
    }
}
