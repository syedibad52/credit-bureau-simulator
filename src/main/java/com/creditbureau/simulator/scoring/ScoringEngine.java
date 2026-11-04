package com.creditbureau.simulator.scoring;

import com.creditbureau.simulator.domain.model.AccountEvent;
import com.creditbureau.simulator.domain.model.CreditAccount;
import com.creditbureau.simulator.domain.model.Enquiry;
import com.creditbureau.simulator.domain.model.FactorContribution;
import com.creditbureau.simulator.domain.model.OverrideResult;
import com.creditbureau.simulator.domain.model.ScoreResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the credit scoring pipeline (Phase 8, 9, 10).
 * <pre>
 *   Base Score: 300 (BR-44)
 *   + Payment History (max 300)
 *   + Utilisation (max 200)
 *   + Credit Age (max 100)
 *   + Credit Mix (max 100)
 *   + Enquiries (deduction max -100)
 *   ---------------------------------
 *   = Pre-Override Score (capped at 900, BR-45)
 *   Applied Overrides (strictest cap wins, BR-36 to BR-39)
 *   ---------------------------------
 *   = Final Credit Score (300 to 900)
 * </pre>
 */
public class ScoringEngine {

    public static final int BASE_SCORE = 300;
    public static final int MAX_SCORE = 900;

    private final ThinFileChecker thinFileChecker = new ThinFileChecker();
    private final PaymentHistoryCalculator paymentHistoryCalculator = new PaymentHistoryCalculator();
    private final UtilisationCalculator utilisationCalculator = new UtilisationCalculator();
    private final CreditAgeCalculator creditAgeCalculator = new CreditAgeCalculator();
    private final CreditMixCalculator creditMixCalculator = new CreditMixCalculator();
    private final EnquiryCalculator enquiryCalculator = new EnquiryCalculator();
    private final OverrideEngine overrideEngine = new OverrideEngine();

    /**
     * Calculate credit score for a consumer at the given as-of date.
     *
     * @param consumerId target consumer ID
     * @param accounts   merged credit accounts for this consumer
     * @param enquiries  enquiries for this consumer
     * @param events     account events for this consumer
     * @param asOfDate   evaluation as-of date
     * @return ScoreResult containing score, factors, overrides, and applied rule IDs
     */
    public ScoreResult calculateScore(
            String consumerId,
            List<CreditAccount> accounts,
            List<Enquiry> enquiries,
            List<AccountEvent> events,
            LocalDate asOfDate
    ) {
        List<String> appliedRuleIds = new ArrayList<>();
        appliedRuleIds.add("BR-44"); // Base score of 300 always applies

        // 1. Thin File Check (BR-04, BR-05, BR-06)
        ThinFileChecker.ThinFileResult thinCheck = thinFileChecker.check(accounts, asOfDate);
        if (thinCheck.isThinFile()) {
            appliedRuleIds.add(thinCheck.ruleId());
            return new ScoreResult(
                    consumerId,
                    asOfDate,
                    true,
                    thinCheck.reason(),
                    BASE_SCORE,
                    new FactorContribution("Payment History", 300, 0, "Thin File: Not evaluated"),
                    new FactorContribution("Credit Utilisation", 200, 0, "Thin File: Not evaluated"),
                    new FactorContribution("Credit Age", 100, 0, "Thin File: Not evaluated"),
                    new FactorContribution("Credit Mix", 100, 0, "Thin File: Not evaluated"),
                    new FactorContribution("Enquiries", 0, 0, "Thin File: Not evaluated"),
                    BASE_SCORE,
                    List.of(),
                    -1, // -1 denotes NA
                    appliedRuleIds
            );
        } else {
            appliedRuleIds.add("BR-06"); // Passed thin file threshold
        }

        // 2. Factor Contributions
        FactorContribution payment = paymentHistoryCalculator.calculate(accounts, asOfDate);
        appliedRuleIds.add("BR-07");
        if (payment.awardedPoints() < 300) appliedRuleIds.add("BR-08");

        FactorContribution util = utilisationCalculator.calculate(accounts, asOfDate);
        appliedRuleIds.add("BR-15");
        appliedRuleIds.add("BR-16");

        FactorContribution age = creditAgeCalculator.calculate(accounts, asOfDate);
        appliedRuleIds.add("BR-22");
        appliedRuleIds.add("BR-23");

        FactorContribution mix = creditMixCalculator.calculate(accounts, asOfDate);
        appliedRuleIds.add("BR-25");

        FactorContribution enq = enquiryCalculator.calculate(enquiries, asOfDate);
        appliedRuleIds.add("BR-30");
        appliedRuleIds.add("BR-33");

        // 3. Pre-Override Calculation
        int rawScore = BASE_SCORE +
                payment.awardedPoints() +
                util.awardedPoints() +
                age.awardedPoints() +
                mix.awardedPoints() +
                enq.awardedPoints(); // Note: enquiry points are negative or zero

        // Cap at 900 (BR-45, T-11)
        int preOverrideScore = rawScore;
        if (rawScore > MAX_SCORE) {
            appliedRuleIds.add("BR-45");
        }

        // 4. Overrides (BR-36 through BR-39)
        OverrideEngine.Evaluation overrideEval = overrideEngine.evaluate(accounts, events, asOfDate);
        int finalScore = Math.min(preOverrideScore, MAX_SCORE);

        if (overrideEval.strictestCap() != null) {
            if (finalScore > overrideEval.strictestCap()) {
                finalScore = overrideEval.strictestCap();
            }
            appliedRuleIds.add("BR-39"); // Strictest cap applied
            for (OverrideResult or : overrideEval.appliedOverrides()) {
                appliedRuleIds.add(or.ruleId());
            }
        }

        return new ScoreResult(
                consumerId,
                asOfDate,
                false,
                null,
                BASE_SCORE,
                payment,
                util,
                age,
                mix,
                enq,
                preOverrideScore,
                overrideEval.allResults(),
                finalScore,
                appliedRuleIds.stream().distinct().toList()
        );
    }
}

