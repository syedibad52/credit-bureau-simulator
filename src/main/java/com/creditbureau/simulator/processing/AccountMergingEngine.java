package com.creditbureau.simulator.processing;

import com.creditbureau.simulator.domain.enums.AccountStatus;
import com.creditbureau.simulator.domain.model.AccountEvent;
import com.creditbureau.simulator.domain.model.CreditAccount;
import com.creditbureau.simulator.domain.model.DPDRecord;
import com.creditbureau.simulator.domain.model.Tradeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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
        if (!t1.lender().equalsIgnoreCase(t2.lender())) {
            return false;
        }

        if (t1.accountType() != t2.accountType()) {
            return false;
        }

        long daysBetween = Math.abs(ChronoUnit.DAYS.between(t1.openDate(), t2.openDate()));
        if (daysBetween > 30) {
            return false;
        }

        BigDecimal limit1 = t1.creditLimit();
        BigDecimal limit2 = t2.creditLimit();
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
        // diff * 100 <= max * 5
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

        // Sort tradelines deterministically: openDate, then submissionDate, then fileOrderIndex
        List<Tradeline> sorted = tradelines.stream()
                .sorted(Comparator.comparing(Tradeline::openDate)
                        .thenComparing(Tradeline::lender, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Tradeline::submissionDate)
                        .thenComparingInt(Tradeline::fileOrderIndex))
                .toList();

        for (Tradeline t : sorted) {
            CreditAccount matchedAccount = null;

            // Check against existing merged accounts
            for (CreditAccount account : mergedAccounts) {
                // Compare with the seed tradeline or any constituent tradeline
                Tradeline seed = account.getMergedTradelines().get(0);
                if (isSameAccount(t, seed)) {
                    matchedAccount = account;
                    break;
                }
            }

            if (matchedAccount == null) {
                // Create a new CreditAccount
                String accountId = CreditAccount.generateAccountId(t.lender(), t.accountType(), t.openDate());
                matchedAccount = new CreditAccount(
                        accountId,
                        t.consumerId(),
                        t.lender(),
                        t.accountType(),
                        t.openDate(),
                        t.creditLimit(),
                        t.outstandingBalance(),
                        t.status()
                );
                mergedAccounts.add(matchedAccount);
            }

            // Merge the tradeline into this account
            matchedAccount.mergeTradeline(t);

            // Record monthly DPD (reporting month = YearMonth of submissionDate)
            YearMonth reportingMonth = YearMonth.from(t.submissionDate());
            matchedAccount.addDPDRecord(new DPDRecord(reportingMonth, t.currentDPD(), t.submissionDate()));

            // Update closure date if closed
            if (t.status() == AccountStatus.CLOSED) {
                if (matchedAccount.getCloseDate() == null || t.submissionDate().isBefore(matchedAccount.getCloseDate())) {
                    matchedAccount.setCloseDate(t.submissionDate());
                }
            }
        }

        // Attach events to corresponding accounts
        if (events != null) {
            for (AccountEvent event : events) {
                for (CreditAccount account : mergedAccounts) {
                    // Match by accountId if specified, or by prefix
                    if (event.accountId() != null && !event.accountId().isBlank()) {
                        if (account.getAccountId().equalsIgnoreCase(event.accountId()) ||
                            event.accountId().toUpperCase().startsWith(account.getLender().toUpperCase())) {
                            account.addEvent(event);
                        }
                    } else {
                        // General account event for the consumer, attach to all matching accounts if appropriate
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

