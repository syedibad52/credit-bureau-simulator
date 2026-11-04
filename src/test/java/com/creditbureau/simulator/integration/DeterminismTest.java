package com.creditbureau.simulator.integration;

import com.creditbureau.simulator.domain.enums.AccountStatus;
import com.creditbureau.simulator.domain.enums.AccountType;
import com.creditbureau.simulator.domain.enums.EnquiryType;
import com.creditbureau.simulator.domain.model.AccountEvent;
import com.creditbureau.simulator.domain.model.CreditAccount;
import com.creditbureau.simulator.domain.model.Enquiry;
import com.creditbureau.simulator.domain.model.ScoreResult;
import com.creditbureau.simulator.domain.model.Tradeline;
import com.creditbureau.simulator.processing.AccountMergingEngine;
import com.creditbureau.simulator.scoring.ScoringEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeterminismTest {

    private final AccountMergingEngine mergingEngine = new AccountMergingEngine();
    private final ScoringEngine scoringEngine = new ScoringEngine();
    private final LocalDate asOf = LocalDate.of(2026, 10, 31);

    @Test
    @DisplayName("Deterministic Results: Shuffled inputs produce identical final scores and factors (BR-47)")
    void testDeterminismAcrossShuffledInputs() {
        Tradeline t1 = new Tradeline(
                "CUST-100", "HDFC", AccountType.CC,
                LocalDate.of(2022, 1, 1), new BigDecimal("200000"), new BigDecimal("30000"),
                0, AccountStatus.ACTIVE, LocalDate.of(2026, 9, 1), 1
        );
        Tradeline t2 = new Tradeline(
                "CUST-100", "HDFC", AccountType.CC,
                LocalDate.of(2022, 1, 1), new BigDecimal("200000"), new BigDecimal("35000"),
                0, AccountStatus.ACTIVE, LocalDate.of(2026, 10, 1), 2
        );
        Tradeline t3 = new Tradeline(
                "CUST-100", "SBI", AccountType.HL,
                LocalDate.of(2020, 6, 15), new BigDecimal("4000000"), new BigDecimal("3500000"),
                0, AccountStatus.ACTIVE, LocalDate.of(2026, 10, 1), 3
        );

        Enquiry e1 = new Enquiry("CUST-100", "AXIS", "PL", EnquiryType.HARD, LocalDate.of(2026, 8, 1), 1);
        Enquiry e2 = new Enquiry("CUST-100", "KOTAK", "CC", EnquiryType.HARD, LocalDate.of(2026, 9, 1), 2);

        List<Tradeline> orderA = List.of(t1, t2, t3);
        List<Tradeline> orderB = new ArrayList<>(orderA);
        Collections.reverse(orderB);

        List<CreditAccount> accountsA = mergingEngine.mergeAccounts(orderA, List.of());
        List<CreditAccount> accountsB = mergingEngine.mergeAccounts(orderB, List.of());

        ScoreResult scoreA = scoringEngine.calculateScore("CUST-100", accountsA, List.of(e1, e2), List.of(), asOf);
        ScoreResult scoreB = scoringEngine.calculateScore("CUST-100", accountsB, List.of(e2, e1), List.of(), asOf);

        assertEquals(scoreA.finalScore(), scoreB.finalScore(), "Final scores must be identical regardless of input ordering");
        assertEquals(scoreA.preOverrideScore(), scoreB.preOverrideScore(), "Pre-override scores must match");
        assertEquals(scoreA.paymentHistory().awardedPoints(), scoreB.paymentHistory().awardedPoints());
        assertEquals(scoreA.utilisation().awardedPoints(), scoreB.utilisation().awardedPoints());
        assertEquals(scoreA.creditAge().awardedPoints(), scoreB.creditAge().awardedPoints());
        assertEquals(scoreA.creditMix().awardedPoints(), scoreB.creditMix().awardedPoints());
        assertEquals(scoreA.enquiries().awardedPoints(), scoreB.enquiries().awardedPoints());
    }
}

