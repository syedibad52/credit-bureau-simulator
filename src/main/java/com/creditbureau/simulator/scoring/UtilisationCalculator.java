package com.creditbureau.simulator.scoring;

import com.creditbureau.simulator.domain.enums.AccountStatus;
import com.creditbureau.simulator.domain.enums.AccountType;
import com.creditbureau.simulator.domain.model.CreditAccount;
import com.creditbureau.simulator.domain.model.FactorContribution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Calculates Credit Utilisation score contribution (Phase 8.C, BR-15 through BR-21).
 * Maximum 200 points.
 */
public class UtilisationCalculator {

    public static final int MAX_POINTS = 200;
    public static final int NO_CC_POINTS = 120; // BR-17
    private static final BigDecimal BNPL_THRESHOLD = new BigDecimal("20000"); // BR-19

    public FactorContribution calculate(List<CreditAccount> accounts, LocalDate asOfDate) {
        BigDecimal totalLimit = BigDecimal.ZERO;
        BigDecimal totalBalance = BigDecimal.ZERO;
        int eligibleRevolvingAccounts = 0;

        for (CreditAccount acc : accounts) {
            // Disputed accounts excluded (BR-21)
            if (acc.isExcludedFromScoring(asOfDate)) {
                continue;
            }

            // Closed accounts excluded (BR-20)
            if (acc.getCurrentStatus() == AccountStatus.CLOSED ||
                    (acc.getCloseDate() != null && !acc.getCloseDate().isAfter(asOfDate))) {
                continue;
            }

            if (acc.getAccountType() == AccountType.CC) {
                // CC accounts with zero limit: Excluded (BR-18)
                if (acc.getCreditLimit().compareTo(BigDecimal.ZERO) > 0) {
                    totalLimit = totalLimit.add(acc.getCreditLimit());
                    totalBalance = totalBalance.add(acc.getOutstandingBalance());
                    eligibleRevolvingAccounts++;
                }
            } else if (acc.getAccountType() == AccountType.BNPL) {
                // BNPL counts towards utilisation ONLY if limit >= 20,000 (BR-19)
                if (acc.getCreditLimit().compareTo(BNPL_THRESHOLD) >= 0) {
                    totalLimit = totalLimit.add(acc.getCreditLimit());
                    totalBalance = totalBalance.add(acc.getOutstandingBalance());
                    eligibleRevolvingAccounts++;
                }
            }
        }

        // BR-17: No eligible CC / revolving accounts -> Fixed 120 points
        if (eligibleRevolvingAccounts == 0 || totalLimit.compareTo(BigDecimal.ZERO) == 0) {
            return new FactorContribution(
                    "Credit Utilisation",
                    MAX_POINTS,
                    NO_CC_POINTS,
                    "No eligible revolving credit accounts (fixed 120 points, BR-17)"
            );
        }

        // Utilisation percentage = (totalBalance / totalLimit) * 100
        BigDecimal utilisationPct = totalBalance.multiply(BigDecimal.valueOf(100))
                .divide(totalLimit, 4, RoundingMode.HALF_UP);

        int awardedPoints;
        String bandExplanation;

        BigDecimal thirty = new BigDecimal("30.0000");
        BigDecimal fifty = new BigDecimal("50.0000");
        BigDecimal seventyFive = new BigDecimal("75.0000");
        BigDecimal hundred = new BigDecimal("100.0000");

        if (utilisationPct.compareTo(thirty) <= 0) {
            awardedPoints = 200;
            bandExplanation = "<= 30% utilisation (200 pts)";
        } else if (utilisationPct.compareTo(fifty) <= 0) {
            awardedPoints = 150;
            bandExplanation = "> 30% and <= 50% utilisation (150 pts)";
        } else if (utilisationPct.compareTo(seventyFive) <= 0) {
            awardedPoints = 90;
            bandExplanation = "> 50% and <= 75% utilisation (90 pts)";
        } else if (utilisationPct.compareTo(hundred) <= 0) {
            awardedPoints = 40;
            bandExplanation = "> 75% and <= 100% utilisation (40 pts)";
        } else {
            awardedPoints = 0;
            bandExplanation = "> 100% over-utilisation (0 pts)";
        }

        String explanation = String.format("Balance: ₹%s / Limit: ₹%s = %.2f%% (%s)",
                totalBalance.toPlainString(), totalLimit.toPlainString(),
                utilisationPct.doubleValue(), bandExplanation);

        return new FactorContribution("Credit Utilisation", MAX_POINTS, awardedPoints, explanation);
    }
}

