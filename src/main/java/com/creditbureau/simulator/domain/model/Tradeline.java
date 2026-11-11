package com.creditbureau.simulator.domain.model;

import com.creditbureau.simulator.domain.enums.AccountStatus;
import com.creditbureau.simulator.domain.enums.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Immutable record representing a single monthly tradeline submission from a lender.
 * Supports both the 14-column official PS-022 format and legacy format.
 */
public record Tradeline(
    String submissionId,
    String fileId,
    LocalDate receivedDate,
    YearMonth reportMonth,
    String lenderId,
    String consumerId,
    String accountNo,
    AccountType accountType,
    LocalDate openDate,
    BigDecimal sanctionedAmount,
    BigDecimal balance,
    int dpd,
    AccountStatus status,
    LocalDate closedDate,
    int fileOrderIndex
) {
    public Tradeline {
        if (consumerId == null || consumerId.isBlank()) throw new IllegalArgumentException("consumerId must not be blank");
        if (lenderId == null || lenderId.isBlank()) throw new IllegalArgumentException("lenderId must not be blank");
        if (accountType == null) throw new IllegalArgumentException("accountType must not be null");
        if (openDate == null) throw new IllegalArgumentException("openDate must not be null");
        if (sanctionedAmount == null) throw new IllegalArgumentException("sanctionedAmount must not be null");
        if (balance == null) throw new IllegalArgumentException("balance must not be null");
        if (dpd < 0) throw new IllegalArgumentException("dpd must not be negative");
        if (status == null) throw new IllegalArgumentException("status must not be null");
        if (receivedDate == null) throw new IllegalArgumentException("receivedDate must not be null");
    }

    // Backwards-compatible constructor for 9-field legacy format
    public Tradeline(
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
        this(
                "TL-LEGACY-" + fileOrderIndex,
                "TLF-" + YearMonth.from(submissionDate).toString().replace("-", ""),
                submissionDate,
                YearMonth.from(submissionDate),
                lender,
                consumerId,
                lender + "-" + accountType + "-" + openDate.toString().replace("-", ""),
                accountType,
                openDate,
                creditLimit,
                outstandingBalance,
                currentDPD,
                status,
                null,
                fileOrderIndex
        );
    }

    // Compatibility getters
    public String lender() {
        return lenderId;
    }

    public BigDecimal creditLimit() {
        return sanctionedAmount;
    }

    public BigDecimal outstandingBalance() {
        return balance;
    }

    public int currentDPD() {
        return dpd;
    }

    public LocalDate submissionDate() {
        return receivedDate;
    }
}
