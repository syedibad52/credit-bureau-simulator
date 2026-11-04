package com.creditbureau.simulator.processing;

import com.creditbureau.simulator.domain.enums.AccountStatus;
import com.creditbureau.simulator.domain.enums.AccountType;
import com.creditbureau.simulator.domain.model.CreditAccount;
import com.creditbureau.simulator.domain.model.Tradeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AccountMergingEngineTest {

    private final AccountMergingEngine mergingEngine = new AccountMergingEngine();

    @Test
    @DisplayName("T-12: Account merging with exactly 30 days between open dates (BR-01 boundary)")
    void testMergeExactly30Days() {
        Tradeline t1 = new Tradeline(
                "CUST-1", "HDFC", AccountType.CC,
                LocalDate.of(2023, 1, 1),
                new BigDecimal("100000"), new BigDecimal("20000"),
                0, AccountStatus.ACTIVE, LocalDate.of(2023, 2, 1), 1
        );
        Tradeline t2 = new Tradeline(
                "CUST-1", "HDFC", AccountType.CC,
                LocalDate.of(2023, 1, 31), // Exactly 30 days apart
                new BigDecimal("100000"), new BigDecimal("25000"),
                0, AccountStatus.ACTIVE, LocalDate.of(2023, 3, 1), 2
        );

        assertTrue(mergingEngine.isSameAccount(t1, t2), "Accounts 30 days apart must merge");

        // 31 days apart should NOT merge
        Tradeline t3 = new Tradeline(
                "CUST-1", "HDFC", AccountType.CC,
                LocalDate.of(2023, 2, 1), // 31 days apart
                new BigDecimal("100000"), new BigDecimal("25000"),
                0, AccountStatus.ACTIVE, LocalDate.of(2023, 3, 1), 3
        );
        assertFalse(mergingEngine.isSameAccount(t1, t3), "Accounts 31 days apart must NOT merge");
    }

    @Test
    @DisplayName("T-13: Account merging at exactly 5% sanctioned amount difference (BR-01 boundary)")
    void testMergeExactly5PercentDifference() {
        // Larger amount is 100,000, 5% is 5,000 -> 95,000 is exactly 5% difference
        Tradeline t1 = new Tradeline(
                "CUST-1", "SBI", AccountType.PL,
                LocalDate.of(2023, 5, 10),
                new BigDecimal("100000"), new BigDecimal("80000"),
                0, AccountStatus.ACTIVE, LocalDate.of(2023, 6, 1), 1
        );
        Tradeline t2 = new Tradeline(
                "CUST-1", "SBI", AccountType.PL,
                LocalDate.of(2023, 5, 10),
                new BigDecimal("95000"), new BigDecimal("75000"), // Exactly 5% diff
                0, AccountStatus.ACTIVE, LocalDate.of(2023, 7, 1), 2
        );

        assertTrue(mergingEngine.isSameAccount(t1, t2), "Accounts differing by exactly 5% must merge");

        // 5.01% diff (94,990) should NOT merge
        Tradeline t3 = new Tradeline(
                "CUST-1", "SBI", AccountType.PL,
                LocalDate.of(2023, 5, 10),
                new BigDecimal("94990"), new BigDecimal("75000"),
                0, AccountStatus.ACTIVE, LocalDate.of(2023, 7, 1), 3
        );
        assertFalse(mergingEngine.isSameAccount(t1, t3), "Accounts differing by >5% must NOT merge");
    }

    @Test
    @DisplayName("T-07: Two submissions in same month with different balances - later wins (BR-02, BR-14)")
    void testMergeSameMonthDifferentBalance() {
        Tradeline sub1 = new Tradeline(
                "CUST-1", "ICICI", AccountType.CC,
                LocalDate.of(2024, 1, 1),
                new BigDecimal("200000"), new BigDecimal("40000"),
                0, AccountStatus.ACTIVE, LocalDate.of(2024, 5, 10), 1
        );
        Tradeline sub2 = new Tradeline(
                "CUST-1", "ICICI", AccountType.CC,
                LocalDate.of(2024, 1, 1),
                new BigDecimal("200000"), new BigDecimal("45000"), // Later submission with different balance
                0, AccountStatus.ACTIVE, LocalDate.of(2024, 5, 25), 2
        );

        List<CreditAccount> accounts = mergingEngine.mergeAccounts(List.of(sub1, sub2), List.of());

        assertEquals(1, accounts.size(), "Should merge into 1 account");
        CreditAccount account = accounts.get(0);
        assertEquals(new BigDecimal("45000"), account.getOutstandingBalance(), "Later submission balance must win (BR-02)");
        assertEquals(1, account.getDpdHistory().size(), "Only one DPD entry per calendar month (BR-14)");
    }
}

