package com.creditbureau.simulator.scoring;

import com.creditbureau.simulator.domain.model.CreditAccount;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

/**
 * Checks if a consumer has a thin file (BR-04, BR-05, BR-06).
 */
public class ThinFileChecker {

    public record ThinFileResult(boolean isThinFile, String reason, String ruleId) {
        public static ThinFileResult notThin() {
            return new ThinFileResult(false, null, null);
        }

        public static ThinFileResult thin(String reason, String ruleId) {
            return new ThinFileResult(true, reason, ruleId);
        }
    }

    /**
     * Check if the consumer profile qualifies as thin file.
     *
     * @param accounts all merged accounts
     * @param asOfDate evaluation as-of date
     * @return ThinFileResult
     */
    public ThinFileResult check(List<CreditAccount> accounts, LocalDate asOfDate) {
        // Filter eligible accounts (not excluded by active dispute or removed dispute)
        List<CreditAccount> eligibleAccounts = accounts.stream()
                .filter(a -> !a.isExcludedFromScoring(asOfDate))
                .toList();

        // BR-05: Fewer than 1 eligible tradeline
        if (eligibleAccounts.isEmpty()) {
            return ThinFileResult.thin("No eligible credit accounts found (fewer than 1 tradeline)", "BR-05");
        }

        // Find oldest account open date among eligible accounts
        LocalDate oldestDate = null;
        for (CreditAccount acc : eligibleAccounts) {
            if (oldestDate == null || acc.getOpenDate().isBefore(oldestDate)) {
                oldestDate = acc.getOpenDate();
            }
        }

        if (oldestDate == null) {
            return ThinFileResult.thin("No eligible tradelines with valid open date", "BR-05");
        }

        // BR-04 & BR-06: Check credit history duration in calendar months
        Period historyPeriod = Period.between(oldestDate, asOfDate);
        long totalMonths = historyPeriod.toTotalMonths();

        // Exactly 6 months must NOT be classified as thin file (BR-06)
        if (totalMonths < 6) {
            return ThinFileResult.thin(
                    String.format("Credit history duration is %d months (less than required 6 months)", totalMonths),
                    "BR-04"
            );
        }

        return ThinFileResult.notThin();
    }
}

