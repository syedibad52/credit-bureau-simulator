package com.creditbureau.simulator.domain.enums;

/**
 * Enumerates the types of credit accounts tracked by the bureau.
 *
 * <p>Each account type is classified into a {@link AccountCategory} (secured or
 * unsecured) which influences risk weighting and score calculations. Account
 * types also carry metadata such as a human-readable display name and whether
 * the account represents revolving credit.</p>
 *
 * <table>
 *     <caption>Account Types and Categories</caption>
 *     <tr><th>Code</th><th>Display Name</th><th>Category</th></tr>
 *     <tr><td>CC</td><td>Credit Card</td><td>UNSECURED</td></tr>
 *     <tr><td>PL</td><td>Personal Loan</td><td>UNSECURED</td></tr>
 *     <tr><td>HL</td><td>Home Loan</td><td>SECURED</td></tr>
 *     <tr><td>AL</td><td>Auto Loan</td><td>SECURED</td></tr>
 *     <tr><td>GOLD</td><td>Gold Loan</td><td>SECURED</td></tr>
 *     <tr><td>BNPL</td><td>Buy Now Pay Later</td><td>UNSECURED</td></tr>
 * </table>
 */
public enum AccountType {

    /** Credit Card — unsecured, revolving credit facility. */
    CC("Credit Card", AccountCategory.UNSECURED),

    /** Personal Loan — unsecured, term loan. */
    PL("Personal Loan", AccountCategory.UNSECURED),

    /** Home Loan — secured by residential or commercial property. */
    HL("Home Loan", AccountCategory.SECURED),

    /** Auto Loan — secured by the financed vehicle. */
    AL("Auto Loan", AccountCategory.SECURED),

    /** Gold Loan — secured by gold collateral. */
    GOLD("Gold Loan", AccountCategory.SECURED),

    /** Buy Now Pay Later — unsecured, short-term credit facility. */
    BNPL("Buy Now Pay Later", AccountCategory.UNSECURED);

    private final String displayName;
    private final AccountCategory category;

    AccountType(String displayName, AccountCategory category) {
        this.displayName = displayName;
        this.category = category;
    }

    /**
     * Returns the human-readable display name for this account type.
     *
     * @return the display name (e.g., "Credit Card", "Home Loan")
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the category (secured or unsecured) for this account type.
     *
     * @return the {@link AccountCategory} classification
     */
    public AccountCategory getCategory() {
        return category;
    }

    /**
     * Checks whether this account type is backed by collateral.
     *
     * @return {@code true} if the account is secured, {@code false} otherwise
     */
    public boolean isSecured() {
        return category == AccountCategory.SECURED;
    }

    /**
     * Checks whether this account type has no collateral backing.
     *
     * @return {@code true} if the account is unsecured, {@code false} otherwise
     */
    public boolean isUnsecured() {
        return category == AccountCategory.UNSECURED;
    }

    /**
     * Checks whether this account type represents a revolving credit facility.
     *
     * <p>Credit cards ({@link #CC}) are the primary revolving credit type.
     * Note that BNPL may exhibit revolving characteristics in some contexts,
     * but that determination is handled by the scoring engine rather than
     * this enum.</p>
     *
     * @return {@code true} if the account type is revolving credit
     */
    public boolean isRevolvingCredit() {
        return this == CC;
    }

    /**
     * Parses a string value to an {@code AccountType}, case-insensitively.
     *
     * @param value the string representation of the account type
     *              (e.g., "CC", "cc", "HL", "bnpl")
     * @return the matching {@code AccountType}
     * @throws IllegalArgumentException if the value does not match any type
     */
    public static AccountType fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "AccountType value must not be null or blank");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown AccountType: '" + value + "'. Valid values: "
                            + "CC, PL, HL, AL, GOLD, BNPL", e);
        }
    }

    /**
     * Returns the display name of this account type.
     *
     * @return the display name string
     */
    @Override
    public String toString() {
        return displayName;
    }
}
