package com.creditbureau.simulator.integration;

import com.creditbureau.simulator.domain.enums.AccountStatus;
import com.creditbureau.simulator.domain.enums.AccountType;
import com.creditbureau.simulator.domain.enums.DisputeOutcome;
import com.creditbureau.simulator.domain.enums.EventType;
import com.creditbureau.simulator.domain.model.AccountEvent;
import com.creditbureau.simulator.domain.model.ScoreResult;
import com.creditbureau.simulator.domain.model.Tradeline;
import com.creditbureau.simulator.scoring.ScoreHistoryEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalScoreTest {

    private final ScoreHistoryEngine engine = new ScoreHistoryEngine();

    @Test
    @DisplayName("T-21: Records submitted after the as-of date do not affect historical calculations (BR-46)")
    void testFutureRecordsDoNotAffectPastScore() {
        // Consumer opened account in 2023, clean until 2026-06
        Tradeline tPast = new Tradeline(
                "CUST-HIST", "HDFC", AccountType.CC,
                LocalDate.of(2023, 1, 1), new BigDecimal("100000"), new BigDecimal("10000"),
                0, AccountStatus.ACTIVE, LocalDate.of(2025, 12, 1), 1
        );

        // Later in 2026-08, defaulted with DPD 95
        Tradeline tFuture = new Tradeline(
                "CUST-HIST", "HDFC", AccountType.CC,
                LocalDate.of(2023, 1, 1), new BigDecimal("100000"), new BigDecimal("10000"),
                95, AccountStatus.ACTIVE, LocalDate.of(2026, 8, 1), 2
        );

        List<Tradeline> allTradelines = List.of(tPast, tFuture);

        // As of 2026-01-01, the default had NOT occurred yet
        ScoreResult pastScore = engine.computeScoreAt("CUST-HIST", allTradelines, List.of(), List.of(), LocalDate.of(2026, 1, 1));

        // As of 2026-09-01, the default had occurred
        ScoreResult currentScore = engine.computeScoreAt("CUST-HIST", allTradelines, List.of(), List.of(), LocalDate.of(2026, 9, 1));

        // In 2026-01, payment history should be clean (300)
        assertEquals(300, pastScore.paymentHistory().awardedPoints(), "Past score must NOT be contaminated by future default (BR-46)");

        // In 2026-09, payment history should be penalized (300 - 150 = 150)
        assertEquals(150, currentScore.paymentHistory().awardedPoints());
        assertTrue(currentScore.finalScore() < pastScore.finalScore(), "Score should decline after default");
    }

    @Test
    @DisplayName("Retroactive recalculation: Dispute resolved as valid re-includes account in historical scoring (BR-43)")
    void testDisputeRetroactiveRecalculation() {
        Tradeline t = new Tradeline(
                "CUST-DISP", "HDFC", AccountType.CC,
                LocalDate.of(2023, 1, 1), new BigDecimal("100000"), new BigDecimal("10000"),
                0, AccountStatus.ACTIVE, LocalDate.of(2026, 1, 1), 1
        );

        // Dispute filed 2026-03-01, resolved as VALID on 2026-07-01
        AccountEvent disputeResolvedValid = new AccountEvent(
                "CUST-DISP", "HDFC-CC-20230101", EventType.DISPUTE,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 1), DisputeOutcome.VALID, "Resolved valid"
        );

        // When evaluating as of 2026-08-01 (after resolution):
        // Account should be included and score calculated normally
        ScoreResult scoreAfterResolution = engine.computeScoreAt(
                "CUST-DISP", List.of(t), List.of(), List.of(disputeResolvedValid), LocalDate.of(2026, 8, 1)
        );

        assertFalse(scoreAfterResolution.isThinFile(), "Account must be included after dispute resolved valid");
        assertTrue(scoreAfterResolution.finalScore() > 0);
    }
}

