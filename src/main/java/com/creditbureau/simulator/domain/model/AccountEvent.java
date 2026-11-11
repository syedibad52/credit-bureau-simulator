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

    public static AccountEvent disputeRaised(String accountId, String consumerId, LocalDate disputeDate) {
        return new AccountEvent(consumerId, accountId, EventType.DISPUTE, disputeDate, null, null, "Dispute raised");
    }

    public static AccountEvent disputeResolvedRemoved(String accountId, String consumerId, LocalDate disputeDate, LocalDate resolutionDate) {
        return new AccountEvent(consumerId, accountId, EventType.DISPUTE, disputeDate, resolutionDate, DisputeOutcome.REMOVED, "Resolved removed");
    }

    public static AccountEvent disputeResolvedValid(String accountId, String consumerId, LocalDate disputeDate, LocalDate resolutionDate) {
        return new AccountEvent(consumerId, accountId, EventType.DISPUTE, disputeDate, resolutionDate, DisputeOutcome.VALID, "Resolved valid");
    }

    public static AccountEvent override(String accountId, String consumerId, EventType eventType, LocalDate eventDate) {
        return new AccountEvent(consumerId, accountId, eventType, eventDate, null, null, "Override");
    }

    /** Check if this event represents an active dispute at the given as-of date. */
    public boolean isActiveDisputeAt(LocalDate asOfDate) {
        if (eventType != EventType.DISPUTE) return false;
        if (eventDate.isAfter(asOfDate)) return false;
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
