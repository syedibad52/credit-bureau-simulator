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

class CreditMixCalculatorTest {

    private final CreditMixCalculator calculator = new CreditMixCalculator();
    private final LocalDate asOf = LocalDate.of(2026, 10, 31);

    @Test
    @DisplayName("Both secured and unsecured active accounts receives 100 points (BR-25)")
    void testSecuredAndUnsecuredMix() {
        CreditAccount secured = new CreditAccount(
                "HL-1", "CUST-1", "SBI", AccountType.HL,
                LocalDate.of(2022, 1, 1), new BigDecimal("5000000"), BigDecimal.ZERO, AccountStatus.ACTIVE
        );
        CreditAccount unsecured = new CreditAccount(
                "CC-1", "CUST-1", "HDFC", AccountType.CC,
                LocalDate.of(2023, 1, 1), new BigDecimal("200000"), BigDecimal.ZERO, AccountStatus.ACTIVE
        );

        FactorContribution res = calculator.calculate(List.of(secured, unsecured), asOf);
        assertEquals(100, res.awardedPoints());
    }

    @Test
    @DisplayName("Only one category (e.g. Unsecured CC + PL) receives 60 points (BR-26)")
    void testOnlyUnsecuredCategory() {
        CreditAccount cc = new CreditAccount(
                "CC-1", "CUST-1", "HDFC", AccountType.CC,
                LocalDate.of(2023, 1, 1), new BigDecimal("200000"), BigDecimal.ZERO, AccountStatus.ACTIVE
        );
        CreditAccount pl = new CreditAccount(
                "PL-1", "CUST-1", "ICICI", AccountType.PL,
                LocalDate.of(2024, 1, 1), new BigDecimal("300000"), BigDecimal.ZERO, AccountStatus.ACTIVE
        );

        FactorContribution res = calculator.calculate(List.of(cc, pl), asOf);
        assertEquals(60, res.awardedPoints());
    }

    @Test
    @DisplayName("Only BNPL accounts active receives 30 points (BR-27, A-14)")
    void testOnlyBnplAccounts() {
        CreditAccount bnpl1 = new CreditAccount(
                "BNPL-1", "CUST-1", "LAZYPAY", AccountType.BNPL,
                LocalDate.of(2024, 1, 1), new BigDecimal("25000"), BigDecimal.ZERO, AccountStatus.ACTIVE
        );
        CreditAccount bnpl2 = new CreditAccount(
                "BNPL-2", "CUST-1", "SIMPL", AccountType.BNPL,
                LocalDate.of(2024, 6, 1), new BigDecimal("15000"), BigDecimal.ZERO, AccountStatus.ACTIVE
        );

        FactorContribution res = calculator.calculate(List.of(bnpl1, bnpl2), asOf);
        assertEquals(30, res.awardedPoints(), "Only BNPL accounts should receive 30 points (BR-27)");
    }
}

