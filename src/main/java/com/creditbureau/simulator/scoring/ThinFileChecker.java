package com.creditbureau.simulator.scoring;

import com.creditbureau.simulator.domain.model.CreditAccount;
import com.creditbureau.simulator.domain.model.DPDRecord;

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
            return ThinFileResult.thin("Thin file: fewer than 1 eligible tradeline", "BR-05");
        }

        // Count distinct report months across eligible accounts
        long distinctReportMonths = eligibleAccounts.stream()
                .flatMap(a -> a.getDpdHistory().stream())
                .map(DPDRecord::reportingMonth)
                .distinct()
                .count();

        // Check oldest account open date
        LocalDate oldestDate = null;
        for (CreditAccount acc : eligibleAccounts) {
            if (oldestDate == null || acc.getOpenDate().isBefore(oldestDate)) {
                oldestDate = acc.getOpenDate();
            }
        }

        long calendarMonths = 0;
        if (oldestDate != null && !oldestDate.isAfter(asOfDate)) {
            calendarMonths = Period.between(oldestDate, asOfDate).toTotalMonths();
        }

        long effectiveHistoryMonths = Math.max(distinctReportMonths, calendarMonths);

        // Exactly 6 months must NOT be classified as thin file (BR-06)
        if (effectiveHistoryMonths < 6) {
            return ThinFileResult.thin("Thin file: < 6 months history", "BR-04");
        }

        return ThinFileResult.notThin();
    }
}
