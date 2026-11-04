package com.creditbureau.simulator.domain.model;

import com.creditbureau.simulator.domain.enums.DisputeOutcome;
import com.creditbureau.simulator.domain.enums.EventType;
import java.time.LocalDate;

/**
 * Immutable record representing an account-level event such as write-off, settlement,
 * suit filed, or dispute (with optional resolution).
 */
public record AccountEvent(
    String consumerId,
    String accountId,
    EventType eventType,
    LocalDate eventDate,
    LocalDate resolutionDate,
    DisputeOutcome disputeOutcome,
    String details
) {
    public AccountEvent {
        if (consumerId == null || consumerId.isBlank()) throw new IllegalArgumentException("consumerId must not be blank");
        if (eventType == null) throw new IllegalArgumentException("eventType must not be null");
        if (eventDate == null) throw new IllegalArgumentException("eventDate must not be null");
        if (eventType == EventType.DISPUTE_RESOLVED && disputeOutcome == null) {
            throw new IllegalArgumentException("DISPUTE_RESOLVED event must have a disputeOutcome");
        }
    }

    /** Check if this event represents an active dispute at the given as-of date. */
    public boolean isActiveDisputeAt(LocalDate asOfDate) {
        if (eventType != EventType.DISPUTE) return false;
        if (eventDate.isAfter(asOfDate)) return false;
        // Active if no resolution date, or resolution is after as-of
        return resolutionDate == null || resolutionDate.isAfter(asOfDate);
    }

    /** 
     * Check if this dispute was resolved as VALID on or before the as-of date.
     * Used for retroactive recalculation (BR-43).
     */
    public boolean isDisputeResolvedValidAt(LocalDate asOfDate) {
        if (eventType != EventType.DISPUTE) return false;
        if (resolutionDate == null || resolutionDate.isAfter(asOfDate)) return false;
        return disputeOutcome == DisputeOutcome.VALID;
    }

    /** Check if dispute was resolved as REMOVED on or before as-of date. */
    public boolean isDisputeResolvedRemovedAt(LocalDate asOfDate) {
        if (eventType != EventType.DISPUTE) return false;
        if (resolutionDate == null || resolutionDate.isAfter(asOfDate)) return false;
        return disputeOutcome == DisputeOutcome.REMOVED;
    }
}
