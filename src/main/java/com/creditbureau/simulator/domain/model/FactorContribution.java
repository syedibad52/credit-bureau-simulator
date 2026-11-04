package com.creditbureau.simulator.domain.model;

/**
 * Immutable record representing the contribution of a single scoring factor.
 * Used in the final credit report to show the factor-wise breakdown.
 */
public record FactorContribution(
    String factorName,
    int maxPoints,
    int awardedPoints,
    String explanation
) {
    public FactorContribution {
        if (factorName == null || factorName.isBlank()) throw new IllegalArgumentException("factorName must not be blank");
    }
}
