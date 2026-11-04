package com.creditbureau.simulator.domain.enums;

/**
 * Classifies credit enquiries by their impact on the borrower's credit score.
 *
 * <p>A {@link #HARD} enquiry is triggered when a borrower formally applies for
 * credit and the lender pulls the credit report. Multiple hard enquiries in a
 * short period can negatively affect the credit score.</p>
 *
 * <p>A {@link #SOFT} enquiry occurs when the borrower checks their own score,
 * or when a lender performs a pre-qualification check. Soft enquiries do not
 * affect the credit score.</p>
 */
public enum EnquiryType {

    /** Lender-initiated enquiry for a formal credit application; affects score. */
    HARD,

    /** Self-check or pre-qualification enquiry; does not affect score. */
    SOFT;

    /**
     * Parses a string value to an {@code EnquiryType}, case-insensitively.
     *
     * @param value the string representation of the enquiry type
     * @return the matching {@code EnquiryType}
     * @throws IllegalArgumentException if the value does not match any type
     */
    public static EnquiryType fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "EnquiryType value must not be null or blank");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown EnquiryType: '" + value + "'. Valid values: HARD, SOFT", e);
        }
    }
}
