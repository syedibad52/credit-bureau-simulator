package com.creditbureau.simulator.domain.model;

import com.creditbureau.simulator.domain.enums.AccountStatus;
import com.creditbureau.simulator.domain.enums.AccountType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable record representing a single monthly tradeline submission from a lender.
 * This is the raw input data before merging.
 */
public record Tradeline(
    String consumerId,
    String lender,
    AccountType accountType,
    LocalDate openDate,
    BigDecimal creditLimit,
    BigDecimal outstandingBalance,
    int currentDPD,
    AccountStatus status,
    LocalDate submissionDate,
    int fileOrderIndex
) {
    public Tradeline {
        if (consumerId == null || consumerId.isBlank()) throw new IllegalArgumentException("consumerId must not be blank");
        if (lender == null || lender.isBlank()) throw new IllegalArgumentException("lender must not be blank");
        if (accountType == null) throw new IllegalArgumentException("accountType must not be null");
        if (openDate == null) throw new IllegalArgumentException("openDate must not be null");
        if (creditLimit == null) throw new IllegalArgumentException("creditLimit must not be null");
        if (outstandingBalance == null) throw new IllegalArgumentException("outstandingBalance must not be null");
        if (currentDPD < 0) throw new IllegalArgumentException("currentDPD must not be negative");
        if (status == null) throw new IllegalArgumentException("status must not be null");
        if (submissionDate == null) throw new IllegalArgumentException("submissionDate must not be null");
    }
}
