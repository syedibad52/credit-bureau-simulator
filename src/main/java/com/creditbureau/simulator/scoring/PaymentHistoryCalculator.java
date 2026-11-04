package com.creditbureau.simulator.scoring;

import com.creditbureau.simulator.domain.enums.AccountType;
import com.creditbureau.simulator.domain.model.CreditAccount;
import com.creditbureau.simulator.domain.model.FactorContribution;
import com.creditbureau.simulator.processing.DPDHistoryEngine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculates Payment History score contribution (Phase 8.B, BR-07 through BR-14).
 * Maximum 300 points. Starts with 300, applies deductions for worst DPD per account per period.
 */
public class PaymentHistoryCalculator {

    public static final int MAX_POINTS = 300;
    private final DPDHistoryEngine dpdEngine = new DPDHistoryEngine();

    public FactorContribution calculate(List<CreditAccount> accounts, LocalDate asOfDate) {
        int startingPoints = MAX_POINTS;
        double totalDeductions = 0.0;
        List<String> details = new ArrayList<>();

        for (CreditAccount acc : accounts) {
            // Disputed accounts excluded (BR-13)
            if (acc.isExcludedFromScoring(asOfDate)) {
                continue;
            }

            // Closed accounts contribute for 36 months after closure (BR-11)
            if (!acc.contributesToPaymentHistory(asOfDate)) {
                continue;
            }

            boolean isBNPL = acc.getAccountType() == AccountType.BNPL;
            double bnplMultiplier = isBNPL ? 0.5 : 1.0; // BR-12: BNPL receives 50% weight

            // --- Months 1–12 (Recent Window) ---
            int worstDpd1To12 = dpdEngine.getWorstDPD(acc, asOfDate, 1, 12);
            if (worstDpd1To12 > 0) {
                int baseDeduction = getBaseDeduction(worstDpd1To12);
                double deduction = baseDeduction * bnplMultiplier;
                totalDeductions += deduction;
                details.add(String.format("%s (M1-12, DPD %d): -%.1f", acc.getAccountId(), worstDpd1To12, deduction));
            }

            // --- Months 13–36 (Older Window, Half Weight BR-09) ---
            int worstDpd13To36 = dpdEngine.getWorstDPD(acc, asOfDate, 13, 36);
            if (worstDpd13To36 > 0) {
                int baseDeduction = getBaseDeduction(worstDpd13To36);
                // Stacks multiplicatively with BNPL (Ambiguity A-05)
                double deduction = baseDeduction * 0.5 * bnplMultiplier;
                totalDeductions += deduction;
                details.add(String.format("%s (M13-36, DPD %d): -%.1f", acc.getAccountId(), worstDpd13To36, deduction));
            }
        }

        // Round deduction using HALF_UP
        int roundedDeduction = (int) Math.round(totalDeductions);
        // Floor at 0 (BR-10)
        int awardedPoints = Math.max(0, startingPoints - roundedDeduction);

        String explanation;
        if (details.isEmpty()) {
            explanation = "Clean repayment record across all accounts (300/300)";
        } else {
            explanation = String.format("Starting 300 - deductions %d (details: %s) = %d",
                    roundedDeduction, String.join("; ", details), awardedPoints);
        }

        return new FactorContribution("Payment History", MAX_POINTS, awardedPoints, explanation);
    }

    private int getBaseDeduction(int dpd) {
        if (dpd <= 0) return 0;
        if (dpd <= 29) return 20;
        if (dpd <= 59) return 60;
        if (dpd <= 89) return 100;
        return 150; // 90+ DPD
    }
}

