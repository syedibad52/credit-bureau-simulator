package com.creditbureau.simulator.scoring;

import com.creditbureau.simulator.domain.enums.AccountStatus;
import com.creditbureau.simulator.domain.enums.AccountType;
import com.creditbureau.simulator.domain.model.CreditAccount;
import com.creditbureau.simulator.domain.model.DPDRecord;
import com.creditbureau.simulator.domain.model.ScoreResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScoringEngineIntegrationTest {

    private final ScoringEngine engine = new ScoringEngine();
    private final LocalDate asOf = LocalDate.of(2026, 10, 31);

    @Test
    @DisplayName("T-11: Score exceeding 900 is capped at 900 (BR-45)")
    void testScoreCappedAt900() {
        // Base: 300
        // Payment History: 300 (clean)
        // Utilisation: 200 (<= 30%)
        // Credit Age: 100 (7+ years)
        // Credit Mix: 100 (Secured HL + Unsecured CC)
        // Enquiries: 0
        // Raw Sum = 300 + 300 + 200 + 100 + 100 + 0 = 1000!
        // Final score must be capped at 900 (BR-45)

        CreditAccount hl = new CreditAccount(
                "HL-1", "CUST-1", "SBI", AccountType.HL,
                LocalDate.of(2015, 1, 1), new BigDecimal("5000000"), BigDecimal.ZERO, AccountStatus.ACTIVE
        );
        hl.addDPDRecord(new DPDRecord(YearMonth.of(2026, 10), 0, LocalDate.of(2026, 10, 1)));

        CreditAccount cc = new CreditAccount(
                "CC-1", "CUST-1", "HDFC", AccountType.CC,
                LocalDate.of(2018, 1, 1), new BigDecimal("200000"), new BigDecimal("20000"), AccountStatus.ACTIVE
        );
        cc.addDPDRecord(new DPDRecord(YearMonth.of(2026, 10), 0, LocalDate.of(2026, 10, 1)));

        ScoreResult result = engine.calculateScore("CUST-1", List.of(hl, cc), List.of(), List.of(), asOf);

        assertFalse(result.isThinFile());
        assertEquals(1000, result.preOverrideScore(), "Raw pre-override score should be 1000");
        assertEquals(900, result.finalScore(), "Final score must be capped at 900 (BR-45)");
        assertTrue(result.appliedRuleIds().contains("BR-45"));
    }
}

