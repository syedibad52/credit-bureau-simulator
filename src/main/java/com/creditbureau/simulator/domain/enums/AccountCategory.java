package com.creditbureau.simulator.domain.enums;

/**
 * Classifies credit accounts as either secured (backed by collateral)
 * or unsecured (no collateral backing).
 *
 * <p>Secured accounts (e.g., home loans, auto loans, gold loans) typically
 * carry lower risk and receive more favorable scoring treatment.
 * Unsecured accounts (e.g., credit cards, personal loans, BNPL) carry
 * higher risk due to the absence of collateral.</p>
 */
public enum AccountCategory {

    /** Account backed by collateral such as property, vehicle, or gold. */
    SECURED,

    /** Account with no collateral backing. */
    UNSECURED;

    /**
     * Parses a string value to an {@code AccountCategory}, case-insensitively.
     *
     * @param value the string representation of the account category
     * @return the matching {@code AccountCategory}
     * @throws IllegalArgumentException if the value does not match any category
     */
    public static AccountCategory fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "AccountCategory value must not be null or blank");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown AccountCategory: '" + value + "'. Valid values: SECURED, UNSECURED", e);
        }
    }
}
