package com.creditbureau.simulator.scoring;

import com.creditbureau.simulator.domain.enums.AccountStatus;
import com.creditbureau.simulator.domain.enums.AccountType;
import com.creditbureau.simulator.domain.enums.DisputeOutcome;
import com.creditbureau.simulator.domain.enums.EventType;
import com.creditbureau.simulator.domain.model.AccountEvent;
import com.creditbureau.simulator.domain.model.CreditAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DisputeEngineTest {

    private final DisputeEngine engine = new DisputeEngine();
    private final LocalDate asOf = LocalDate.of(2026, 10, 31);

    @Test
    @DisplayName("T-22: Active unresolved dispute excludes account from scoring (BR-40)")
    void testActiveDisputeExcludesAccount() {
        CreditAccount acc = new CreditAccount(
                "ACC-1", "CUST-1", "HDFC", AccountType.CC,
                LocalDate.of(2023, 1, 1), new BigDecimal("100000"), BigDecimal.ZERO, AccountStatus.DISPUTE
        );
        // Dispute filed on 2026-06-01, not resolved yet at asOf (2026-10-31)
        AccountEvent event = new AccountEvent(
                "CUST-1", "ACC-1", EventType.DISPUTE, LocalDate.of(2026, 6, 1), null, null, "Disputed billing"
        );
        acc.addEvent(event);

        assertTrue(engine.isAccountExcluded(acc, asOf), "Active unresolved dispute must exclude account (BR-40)");
    }

    @Test
    @DisplayName("T-08: Dispute resolved as VALID includes account in scoring (BR-41, BR-43)")
    void testDisputeResolvedAsValid() {
        CreditAccount acc = new CreditAccount(
                "ACC-1", "CUST-1", "HDFC", AccountType.CC,
                LocalDate.of(2023, 1, 1), new BigDecimal("100000"), BigDecimal.ZERO, AccountStatus.ACTIVE
        );
        // Dispute filed on 2026-06-01, resolved as VALID on 2026-09-01 (before as-of date)
        AccountEvent event = new AccountEvent(
                "CUST-1", "ACC-1", EventType.DISPUTE, LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 9, 1), DisputeOutcome.VALID, "Valid dispute, resolved"
        );
        acc.addEvent(event);

        assertFalse(engine.isAccountExcluded(acc, asOf), "Dispute resolved as VALID must include account (BR-41)");
        assertTrue(engine.isResolvedValid(acc, asOf));
    }

    @Test
    @DisplayName("T-09: Dispute resolved as REMOVED leaves account excluded from scoring (BR-42)")
    void testDisputeResolvedAsRemoved() {
        CreditAccount acc = new CreditAccount(
                "ACC-1", "CUST-1", "HDFC", AccountType.CC,
                LocalDate.of(2023, 1, 1), new BigDecimal("100000"), BigDecimal.ZERO, AccountStatus.ACTIVE
        );
        // Dispute filed on 2026-06-01, resolved as REMOVED on 2026-09-01
        AccountEvent event = new AccountEvent(
                "CUST-1", "ACC-1", EventType.DISPUTE, LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 9, 1), DisputeOutcome.REMOVED, "Account removed"
        );
        acc.addEvent(event);

        assertTrue(engine.isAccountExcluded(acc, asOf), "Dispute resolved as REMOVED must remain excluded (BR-42)");
        assertTrue(engine.isResolvedRemoved(acc, asOf));
    }
}

