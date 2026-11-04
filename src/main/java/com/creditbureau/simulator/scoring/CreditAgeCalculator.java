package com.creditbureau.simulator.scoring;

import com.creditbureau.simulator.domain.enums.AccountStatus;
import com.creditbureau.simulator.domain.model.CreditAccount;
import com.creditbureau.simulator.domain.model.FactorContribution;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

/**
 * Calculates Credit Age score contribution (Phase 8.D, BR-22 through BR-24).
 * Maximum 100 points.
 * Uses the oldest eligible open account. Calendar-date calculations using Period.
 */
public class CreditAgeCalculator {

    public static final int MAX_POINTS = 100;

    public FactorContribution calculate(List<CreditAccount> accounts, LocalDate asOfDate) {
        LocalDate oldestOpenDate = null;
        String oldestAccountId = null;

        for (CreditAccount acc : accounts) {
            // Disputed accounts excluded (BR-40)
            if (acc.isExcludedFromScoring(asOfDate)) {
                continue;
            }

            // Must be open account (closed accounts do not contribute to credit age, BR-22)
            if (acc.getCurrentStatus() == AccountStatus.CLOSED ||
                    (acc.getCloseDate() != null && !acc.getCloseDate().isAfter(asOfDate))) {
                continue;
            }

            if (oldestOpenDate == null || acc.getOpenDate().isBefore(oldestOpenDate)) {
                oldestOpenDate = acc.getOpenDate();
                oldestAccountId = acc.getAccountId();
            }
        }

        if (oldestOpenDate == null) {
            return new FactorContribution(
                    "Credit Age",
                    MAX_POINTS,
                    20,
                    "No eligible open accounts found (< 1 year: 20 pts)"
            );
        }

        Period agePeriod = Period.between(oldestOpenDate, asOfDate);
        int years = agePeriod.getYears();
        int months = agePeriod.getMonths();
        int days = agePeriod.getDays();

        int awardedPoints;
        String bandExplanation;

        // BR-23, BR-24: Exactly 1 year receives 50 points
        if (years >= 7) {
            awardedPoints = 100;
            bandExplanation = "7+ years (100 pts)";
        } else if (years >= 3) {
            awardedPoints = 80;
            bandExplanation = "3–7 years (80 pts)";
        } else if (years >= 1) {
            awardedPoints = 50;
            bandExplanation = "1–3 years (50 pts)";
        } else {
            awardedPoints = 20;
            bandExplanation = "Less than 1 year (20 pts)";
        }

        String explanation = String.format("Oldest open account: %s (opened %s, age: %d yrs %d mos %d days) -> %s",
                oldestAccountId, oldestOpenDate, years, months, days, bandExplanation);

        return new FactorContribution("Credit Age", MAX_POINTS, awardedPoints, explanation);
    }
}

