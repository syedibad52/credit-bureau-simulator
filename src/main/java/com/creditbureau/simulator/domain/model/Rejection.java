package com.creditbureau.simulator.domain.model;

/**
 * Record representing an input record rejected during validation or ingestion.
 */
public record Rejection(
        String sourceFile,
        String recordId,
        String reason
) {
}

