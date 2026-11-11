package com.creditbureau.simulator.domain.model;

import com.creditbureau.simulator.domain.enums.EnquiryType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Immutable record representing a credit enquiry made by a lender.
 * Supports both official 6-column format with timestamp and legacy 5-column format.
 */
public record Enquiry(
    String enquiryId,
    String consumerId,
    String lenderId,
    LocalDateTime enquiryTs,
    String purpose,
    EnquiryType type,
    int fileOrderIndex
) {
    public Enquiry {
        if (consumerId == null || consumerId.isBlank()) throw new IllegalArgumentException("consumerId must not be blank");
        if (lenderId == null || lenderId.isBlank()) throw new IllegalArgumentException("lenderId must not be blank");
        if (type == null) throw new IllegalArgumentException("enquiry type must not be null");
        if (enquiryTs == null) throw new IllegalArgumentException("enquiryTs must not be null");
    }

    // Backwards-compatible constructor for 5-field legacy format
    public Enquiry(
            String consumerId,
            String lender,
            String purpose,
            EnquiryType type,
            LocalDate enquiryDate,
            int fileOrderIndex
    ) {
        this(
                "ENQ-LEGACY-" + fileOrderIndex,
                consumerId,
                lender,
                enquiryDate.atStartOfDay(),
                purpose,
                type,
                fileOrderIndex
        );
    }

    // Compatibility getters
    public String lender() {
        return lenderId;
    }

    public LocalDate enquiryDate() {
        return enquiryTs.toLocalDate();
    }
}
