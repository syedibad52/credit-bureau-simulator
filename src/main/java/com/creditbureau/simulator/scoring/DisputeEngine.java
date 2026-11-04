package com.creditbureau.simulator.scoring;

import com.creditbureau.simulator.domain.enums.DisputeOutcome;
import com.creditbureau.simulator.domain.enums.EventType;
import com.creditbureau.simulator.domain.model.AccountEvent;
import com.creditbureau.simulator.domain.model.CreditAccount;

import java.time.LocalDate;
import java.util.List;

/**
 * Manages dispute evaluation and resolution status (Phase 10, BR-40 through BR-43).
 */
public class DisputeEngine {

    /**
     * Check if an account is excluded from scoring at asOfDate due to dispute status.
     *
     * <p>Rules:
     * - Under active dispute (eventDate <= asOfDate and (resolutionDate == null or resolutionDate > asOfDate)) -> Excluded (BR-40)
     * - Dispute resolved as REMOVED on or before asOfDate -> Permanently excluded (BR-42)
     * - Dispute resolved as VALID on or before asOfDate -> Included (BR-41, BR-43)
     */
    public boolean isAccountExcluded(CreditAccount account, LocalDate asOfDate) {
        return account.isExcludedFromScoring(asOfDate);
    }

    /**
     * Inspect all dispute events for an account.
     */
    public List<AccountEvent> getDisputes(CreditAccount account) {
        return account.getEvents().stream()
                .filter(e -> e.eventType() == EventType.DISPUTE)
                .toList();
    }

    /**
     * Check if an account had a dispute resolved as VALID on or before asOfDate (BR-41).
     */
    public boolean isResolvedValid(CreditAccount account, LocalDate asOfDate) {
        return account.getEvents().stream()
                .anyMatch(e -> e.isDisputeResolvedValidAt(asOfDate));
    }

    /**
     * Check if an account had a dispute resolved as REMOVED on or before asOfDate (BR-42).
     */
    public boolean isResolvedRemoved(CreditAccount account, LocalDate asOfDate) {
        return account.getEvents().stream()
                .anyMatch(e -> e.isDisputeResolvedRemovedAt(asOfDate));
    }
}

