package com.creditbureau.simulator.scoring;

import com.creditbureau.simulator.domain.enums.AccountStatus;
import com.creditbureau.simulator.domain.enums.AccountType;
import com.creditbureau.simulator.domain.model.CreditAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ThinFileCheckerTest {

    private final ThinFileChecker checker = new ThinFileChecker();
    private final LocalDate asOf = LocalDate.of(2026, 10, 31);

    @Test
    @DisplayName("T-10: Exactly 6 months of credit history is NOT thin file (BR-06)")
    void testExactlySixMonthsHistoryNotThinFile() {
        // Open date exactly 6 calendar months before as-of date (2026-04-30 to 2026-10-31 is 6 months)
        LocalDate openDate = LocalDate.of(2026, 4, 30);
        CreditAccount acc = new CreditAccount(
                "ACC-1", "CUST-1", "HDFC", AccountType.CC,
                openDate, new BigDecimal("100000"), BigDecimal.ZERO, AccountStatus.ACTIVE
        );

        ThinFileChecker.ThinFileResult res = checker.check(List.of(acc), asOf);
        assertFalse(res.isThinFile(), "Exactly 6 months history must NOT be classified as thin file (BR-06)");
    }

    @Test
    @DisplayName("Credit history under 6 months is thin file (BR-04)")
    void testUnderSixMonthsHistoryIsThinFile() {
        LocalDate openDate = LocalDate.of(2026, 6, 1); // 4 months history
        CreditAccount acc = new CreditAccount(
                "ACC-1", "CUST-1", "HDFC", AccountType.CC,
                openDate, new BigDecimal("100000"), BigDecimal.ZERO, AccountStatus.ACTIVE
        );

        ThinFileChecker.ThinFileResult res = checker.check(List.of(acc), asOf);
        assertTrue(res.isThinFile(), "< 6 months history must be thin file");
        assertEquals("BR-04", res.ruleId());
    }

    @Test
    @DisplayName("Zero eligible accounts is thin file (BR-05)")
    void testZeroEligibleAccountsIsThinFile() {
        ThinFileChecker.ThinFileResult res = checker.check(List.of(), asOf);
        assertTrue(res.isThinFile(), "Zero tradelines must be thin file (BR-05)");
        assertEquals("BR-05", res.ruleId());
    }
}

