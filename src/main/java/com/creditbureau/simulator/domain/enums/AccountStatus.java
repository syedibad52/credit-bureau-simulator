package com.creditbureau.simulator.domain.enums;

/**
 * Represents the lifecycle status of a credit account as reported to the bureau.
 *
 * <p>Account status directly influences credit score calculations. Active accounts
 * in good standing contribute positively, while negative statuses such as
 * {@link #WRITTEN_OFF} or {@link #SUIT_FILED} severely impact the score.</p>
 */
public enum AccountStatus {

    /** Account is open and in regular use or repayment. */
    ACTIVE,

    /** Account has been closed in good standing. */
    CLOSED,

    /** Lender has written off the account as a loss (severe derogatory mark). */
    WRITTEN_OFF,

    /** Account has been settled for less than the full amount owed. */
    SETTLED,

    /** Legal action has been initiated by the lender to recover the debt. */
    SUIT_FILED,

    /** Account information is currently under dispute by the borrower. */
    DISPUTE;

    /**
     * Parses a string value to an {@code AccountStatus}, case-insensitively.
     * Underscores and hyphens are normalized so that both "WRITTEN_OFF"
     * and "WRITTEN-OFF" are accepted.
     *
     * @param value the string representation of the account status
     * @return the matching {@code AccountStatus}
     * @throws IllegalArgumentException if the value does not match any status
     */
    public static AccountStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "AccountStatus value must not be null or blank");
        }
        String normalized = value.trim().toUpperCase().replace('-', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown AccountStatus: '" + value + "'. Valid values: "
                            + "ACTIVE, CLOSED, WRITTEN_OFF, SETTLED, SUIT_FILED, DISPUTE", e);
        }
    }
}
