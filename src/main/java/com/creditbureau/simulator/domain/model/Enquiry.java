package com.creditbureau.simulator.domain.model;

import com.creditbureau.simulator.domain.enums.EnquiryType;
import java.time.LocalDate;

/**
 * Immutable record representing a credit enquiry made by a lender.
 */
public record Enquiry(
    String consumerId,
    String lender,
    String purpose,
    EnquiryType type,
    LocalDate enquiryDate,
    int fileOrderIndex
) {
    public Enquiry {
        if (consumerId == null || consumerId.isBlank()) throw new IllegalArgumentException("consumerId must not be blank");
        if (lender == null || lender.isBlank()) throw new IllegalArgumentException("lender must not be blank");
        if (type == null) throw new IllegalArgumentException("enquiry type must not be null");
        if (enquiryDate == null) throw new IllegalArgumentException("enquiryDate must not be null");
    }
}
