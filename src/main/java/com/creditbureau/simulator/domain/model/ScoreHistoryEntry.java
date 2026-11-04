package com.creditbureau.simulator.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Immutable record representing a historical score entry for a consumer.
 */
public record ScoreHistoryEntry(
    String consumerId,
    LocalDate asOfDate,
    int score,
    boolean isThinFile,
    LocalDateTime calculatedAt
) {}
