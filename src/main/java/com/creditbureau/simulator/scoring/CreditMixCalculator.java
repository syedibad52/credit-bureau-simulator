package com.creditbureau.simulator.scoring;

import com.creditbureau.simulator.domain.enums.AccountCategory;
import com.creditbureau.simulator.domain.enums.AccountStatus;
import com.creditbureau.simulator.domain.enums.AccountType;
import com.creditbureau.simulator.domain.model.CreditAccount;
import com.creditbureau.simulator.domain.model.FactorContribution;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Calculates Credit Mix score contribution (Phase 8.E, BR-25 through BR-29).
 * Maximum 100 points.
 * - Both secured and unsecured active accounts: 100 points
 * - Only one category: 60 points
 * - Only BNPL: 30 points (A-14 priority)
 * Closed and disputed accounts excluded.
 */
public class CreditMixCalculator {

    public static final int MAX_POINTS = 100;

    public FactorContribution calculate(List<CreditAccount> accounts, LocalDate asOfDate) {
        boolean hasSecured = false;
        boolean hasUnsecured = false;
        boolean onlyBNPL = true;
        Set<AccountType> activeTypes = new HashSet<>();

        for (CreditAccount acc : accounts) {
            // Disputed accounts excluded (BR-29)
            if (acc.isExcludedFromScoring(asOfDate)) {
                continue;
            }

            // Closed accounts excluded (BR-28)
            if (acc.getCurrentStatus() == AccountStatus.CLOSED ||
                    (acc.getCloseDate() != null && !acc.getCloseDate().isAfter(asOfDate))) {
                continue;
            }

            activeTypes.add(acc.getAccountType());

            if (acc.getAccountType() != AccountType.BNPL) {
                onlyBNPL = false;
            }

            if (acc.getCategory() == AccountCategory.SECURED) {
                hasSecured = true;
            } else {
                hasUnsecured = true;
            }
        }

        if (activeTypes.isEmpty()) {
            return new FactorContribution(
                    "Credit Mix",
                    MAX_POINTS,
                    0,
                    "No eligible active credit accounts (0 pts)"
            );
        }

        int awardedPoints;
        String explanation;

        // Evaluation order (Ambiguity A-14):
        if (hasSecured && hasUnsecured) {
            // BR-25: At least one secured and one unsecured
            awardedPoints = 100;
            explanation = String.format("Balanced mix: Secured + Unsecured accounts (%s) -> 100 pts", activeTypes);
        } else if (onlyBNPL) {
            // BR-27: Only BNPL accounts active
            awardedPoints = 30;
            explanation = "Single unsecured product type: Only BNPL accounts active -> 30 pts (BR-27)";
        } else {
            // BR-26: Only one category (secured only, or unsecured with non-BNPL)
            awardedPoints = 60;
            String cat = hasSecured ? "Secured only" : "Unsecured only";
            explanation = String.format("%s (%s) -> 60 pts (BR-26)", cat, activeTypes);
        }

        return new FactorContribution("Credit Mix", MAX_POINTS, awardedPoints, explanation);
    }
}

