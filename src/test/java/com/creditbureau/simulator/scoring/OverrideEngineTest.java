package com.creditbureau.simulator.scoring;

import com.creditbureau.simulator.domain.enums.AccountStatus;
import com.creditbureau.simulator.domain.enums.AccountType;
import com.creditbureau.simulator.domain.enums.EventType;
import com.creditbureau.simulator.domain.model.AccountEvent;
import com.creditbureau.simulator.domain.model.CreditAccount;
import com.creditbureau.simulator.domain.model.DPDRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OverrideEngineTest {

    private final OverrideEngine engine = new OverrideEngine();
    private final LocalDate asOf = LocalDate.of(2026, 10, 31); // As-of: 2026-10

    @Test
    @DisplayName("T-05: DPD 90 in month 6 before the as-of date triggers 650 cap (BR-36 inside window)")
    void testDPD90InMonth6TriggersCap() {
        // As-of is 2026-10 (Month 1). Month 6 is 2026-05.
        YearMonth month6 = YearMonth.of(2026, 5);

        CreditAccount acc = new CreditAccount(
                "ACC-1", "CUST-1", "HDFC", AccountType.CC,
                LocalDate.of(2023, 1, 1), new BigDecimal("100000"), new BigDecimal("10000"), AccountStatus.ACTIVE
        );
        acc.addDPDRecord(new DPDRecord(month6, 90, month6.atDay(1)));

        OverrideEngine.Evaluation eval = engine.evaluate(List.of(acc), List.of(), asOf);
        assertNotNull(eval.strictestCap(), "Cap should be triggered");
        assertEquals(650, eval.strictestCap(), "DPD 90 in month 6 must trigger 650 cap (BR-36)");
    }

    @Test
    @DisplayName("T-06: DPD 90 in month 7 before the as-of date does NOT trigger 650 cap (BR-36 outside window)")
    void testDPD90InMonth7DoesNotTriggerCap() {
        // Month 7 is 2026-04 (outside the 6-month window).
        YearMonth month7 = YearMonth.of(2026, 4);

        CreditAccount acc = new CreditAccount(
                "ACC-1", "CUST-1", "HDFC", AccountType.CC,
                LocalDate.of(2023, 1, 1), new BigDecimal("100000"), new BigDecimal("10000"), AccountStatus.ACTIVE
        );
        acc.addDPDRecord(new DPDRecord(month7, 90, month7.atDay(1)));

        OverrideEngine.Evaluation eval = engine.evaluate(List.of(acc), List.of(), asOf);
        assertNull(eval.strictestCap(), "DPD 90 in month 7 must NOT trigger 650 cap (outside 6-month window)");
    }

    @Test
    @DisplayName("T-18: Multiple simultaneous overrides apply strictest (lowest) cap (BR-39)")
    void testMultipleSimultaneousOverrides() {
        // 1. DPD 90 in Month 2 -> 650 cap
        CreditAccount acc = new CreditAccount(
                "ACC-1", "CUST-1", "HDFC", AccountType.CC,
                LocalDate.of(2023, 1, 1), new BigDecimal("100000"), new BigDecimal("10000"), AccountStatus.ACTIVE
        );
        acc.addDPDRecord(new DPDRecord(YearMonth.of(2026, 9), 95, LocalDate.of(2026, 9, 1)));

        // 2. SETTLED event in last 24 months -> 600 cap
        AccountEvent settleEvent = new AccountEvent(
                "CUST-1", "ACC-1", EventType.SETTLED, LocalDate.of(2025, 12, 1), null, null, "Settled"
        );

        // 3. SUIT_FILED event -> 550 cap
        AccountEvent suitEvent = new AccountEvent(
                "CUST-1", "ACC-1", EventType.SUIT_FILED, LocalDate.of(2025, 6, 1), null, null, "Suit filed in court"
        );

        OverrideEngine.Evaluation eval = engine.evaluate(List.of(acc), List.of(settleEvent, suitEvent), asOf);

        assertEquals(3, eval.appliedOverrides().size(), "All 3 overrides should be detected as active");
        assertEquals(550, eval.strictestCap(), "Strictest cap (550) must win (BR-39)");
    }
}

