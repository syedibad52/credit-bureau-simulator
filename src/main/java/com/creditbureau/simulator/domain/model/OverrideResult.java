package com.creditbureau.simulator.domain.model;

/**
 * Immutable record representing the result of an override rule evaluation.
 * Tracks whether the override was triggered and what cap it would impose.
 */
public record OverrideResult(
    String ruleId,
    String overrideName,
    int capValue,
    boolean applied,
    String reason
) {}
