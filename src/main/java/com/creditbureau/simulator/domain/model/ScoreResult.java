package com.creditbureau.simulator.domain.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Immutable record representing the complete result of a credit score calculation.
 * Contains all factor contributions, overrides, and the final score.
 */
public record ScoreResult(
    String consumerId,
    LocalDate asOfDate,
    boolean isThinFile,
    String thinFileReason,
    int baseScore,
    FactorContribution paymentHistory,
    FactorContribution utilisation,
    FactorContribution creditAge,
    FactorContribution creditMix,
    FactorContribution enquiries,
    int preOverrideScore,
    List<OverrideResult> appliedOverrides,
    int finalScore,
    List<String> appliedRuleIds
) {
    public int rawScore() {
        return preOverrideScore;
    }

    public List<OverrideResult> overrides() {
        return appliedOverrides;
    }

    public FactorContribution paymentContribution() {
        return paymentHistory;
    }

    public FactorContribution utilisationContribution() {
        return utilisation;
    }

    public FactorContribution creditAgeContribution() {
        return creditAge;
    }

    public FactorContribution creditMixContribution() {
        return creditMix;
    }

    public FactorContribution enquiryContribution() {
        return enquiries;
    }
}
