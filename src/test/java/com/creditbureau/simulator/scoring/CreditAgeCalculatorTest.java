package com.creditbureau.simulator.scoring;

import com.creditbureau.simulator.domain.enums.AccountStatus;
import com.creditbureau.simulator.domain.enums.AccountType;
import com.creditbureau.simulator.domain.model.CreditAccount;
import com.creditbureau.simulator.domain.model.FactorContribution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CreditAgeCalculatorTest {

    private final CreditAgeCalculator calculator = new CreditAgeCalculator();

    @Test
    @DisplayName("T-03: Oldest account exactly 1 year old receives 50 points (BR-24 boundary)")
    void testOldestAccountExactlyOneYear() {
        LocalDate asOf = LocalDate.of(2026, 10, 31);
        LocalDate openDate = LocalDate.of(2025, 10, 31); // Exactly 1 calendar year

        CreditAccount acc = new CreditAccount(
                "ACC-1", "CUST-1", "HDFC", AccountType.CC,
                openDate, new BigDecimal("100000"), BigDecimal.ZERO, AccountStatus.ACTIVE
        );

        FactorContribution res = calculator.calculate(List.of(acc), asOf);
        assertEquals(50, res.awardedPoints(), "Account exactly 1 year old must receive 50 points (BR-24)");
    }

    @Test
    @DisplayName("Account 364 days old (< 1 year) receives 20 points (BR-23)")
    void testAccountUnderOneYear() {
        LocalDate asOf = LocalDate.of(2026, 10, 31);
        LocalDate openDate = LocalDate.of(2025, 11, 1); // 1 day short of 1 full year

        CreditAccount acc = new CreditAccount(
                "ACC-1", "CUST-1", "HDFC", AccountType.CC,
                openDate, new BigDecimal("100000"), BigDecimal.ZERO, AccountStatus.ACTIVE
        );

        FactorContribution res = calculator.calculate(List.of(acc), asOf);
        assertEquals(20, res.awardedPoints(), "Account < 1 year must receive 20 points");
    }

    @Test
    @DisplayName("Account 7+ years old receives full 100 points (BR-23)")
    void testAccountSevenPlusYears() {
        LocalDate asOf = LocalDate.of(2026, 10, 31);
        LocalDate openDate = LocalDate.of(2018, 1, 1); // > 8 years

        CreditAccount acc = new CreditAccount(
                "ACC-1", "CUST-1", "HDFC", AccountType.HL,
                openDate, new BigDecimal("5000000"), BigDecimal.ZERO, AccountStatus.ACTIVE
        );

        FactorContribution res = calculator.calculate(List.of(acc), asOf);
        assertEquals(100, res.awardedPoints(), "Account >= 7 years must receive 100 points");
    }
}

