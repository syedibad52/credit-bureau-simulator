package com.creditbureau.simulator.processing;

import com.creditbureau.simulator.domain.enums.AccountStatus;
import com.creditbureau.simulator.domain.model.AccountEvent;
import com.creditbureau.simulator.domain.model.CreditAccount;
import com.creditbureau.simulator.domain.model.DPDRecord;
import com.creditbureau.simulator.domain.model.Tradeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Identifies and merges duplicate tradeline submissions into logical credit accounts (BR-01, BR-02, BR-03).
 */
public class AccountMergingEngine {

    private static final Logger logger = LoggerFactory.getLogger(AccountMergingEngine.class);

    /**
     * Check if two tradelines belong to the same logical account (BR-01).
     * Criteria:
     * 1. Lender is identical (case-insensitive)
     * 2. Account type is identical
     * 3. Open dates within 30 calendar days (inclusive)
     * 4. Credit limit / sanctioned amount differs by <= 5% relative to the larger amount
     */
    public boolean isSameAccount(Tradeline t1, Tradeline t2) {
        if (!t1.lenderId().equalsIgnoreCase(t2.lenderId())) {
            return false;
        }

        if (t1.accountType() != t2.accountType()) {
            return false;
        }

        long daysBetween = Math.abs(ChronoUnit.DAYS.between(t1.openDate(), t2.openDate()));
        if (daysBetween > 30) {
            return false;
        }

        BigDecimal limit1 = t1.sanctionedAmount();
        BigDecimal limit2 = t2.sanctionedAmount();
        if (!isAmountWithinFivePercent(limit1, limit2)) {
            return false;
        }

        return true;
    }

    /**
     * Check if amount differs by <= 5% relative to the larger amount (A-02).
     * Exact arithmetic: |a - b| * 100 <= max(a, b) * 5
     */
    public boolean isAmountWithinFivePercent(BigDecimal a, BigDecimal b) {
        if (a.compareTo(b) == 0) {
            return true;
        }
        BigDecimal diff = a.subtract(b).abs();
        BigDecimal max = a.max(b);
        if (max.compareTo(BigDecimal.ZERO) == 0) {
            return true;
        }
        return diff.multiply(BigDecimal.valueOf(100)).compareTo(max.multiply(BigDecimal.valueOf(5))) <= 0;
    }

    /**
     * Merge a list of tradelines for a consumer into distinct logical CreditAccounts.
     *
     * @param tradelines list of tradelines for a single consumer (already filtered for as-of date)
     * @param events     account events for the consumer
     * @return list of merged CreditAccounts
     */
    public List<CreditAccount> mergeAccounts(List<Tradeline> tradelines, List<AccountEvent> events) {
        List<CreditAccount> mergedAccounts = new ArrayList<>();

        // Sort tradelines deterministically: openDate, then lender, then receivedDate, then submissionId
        List<Tradeline> sorted = tradelines.stream()
                .sorted(Comparator.comparing(Tradeline::openDate)
                        .thenComparing(Tradeline::lenderId, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Tradeline::receivedDate)
                        .thenComparing(Tradeline::submissionId))
                .toList();

        for (Tradeline t : sorted) {
            CreditAccount matchedAccount = null;

            // Check against existing merged accounts
            for (CreditAccount account : mergedAccounts) {
                Tradeline seed = account.getMergedTradelines().get(0);
                if (isSameAccount(t, seed)) {
                    matchedAccount = account;
                    break;
                }
            }

            if (matchedAccount == null) {
                String mergedAccountId = t.lenderId().toUpperCase() + "-" + t.accountNo();
                matchedAccount = new CreditAccount(
                        mergedAccountId,
                        t.consumerId(),
                        t.lenderId(),
                        t.accountType(),
                        t.openDate(),
                        t.sanctionedAmount(),
                        t.balance(),
                        t.status()
                );
                mergedAccounts.add(matchedAccount);
            }

            // Merge the tradeline into this account
            matchedAccount.mergeTradeline(t);

            // Record monthly DPD for report_month
            matchedAccount.addDPDRecord(new DPDRecord(t.reportMonth(), t.dpd(), t.receivedDate()));

            // Update closure date if closed
            if (t.status() == AccountStatus.CLOSED) {
                if (t.closedDate() != null) {
                    matchedAccount.setCloseDate(t.closedDate());
                } else if (matchedAccount.getCloseDate() == null) {
                    matchedAccount.setCloseDate(t.receivedDate());
                }
            }
        }

        // Attach events to corresponding accounts
        if (events != null) {
            for (AccountEvent event : events) {
                for (CreditAccount account : mergedAccounts) {
                    if (event.accountId() != null && !event.accountId().isBlank()) {
                        if (account.getMemberAccountNos().contains(event.accountId()) ||
                            account.getMergedAccountId().equalsIgnoreCase(event.accountId()) ||
                            event.accountId().equalsIgnoreCase(account.getAccountId())) {
                            account.addEvent(event);
                        }
                    } else {
                        account.addEvent(event);
                    }
                }
            }
        }

        logger.info("Merged {} tradelines into {} logical accounts",
                tradelines.size(), mergedAccounts.size());

        return mergedAccounts;
    }
}
