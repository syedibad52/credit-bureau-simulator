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

class UtilisationCalculatorTest {

    private final UtilisationCalculator calculator = new UtilisationCalculator();
    private final LocalDate asOf = LocalDate.of(2026, 10, 31);

    @Test
    @DisplayName("T-01: Utilisation exactly 30.0% receives full 200 points (BR-16 boundary)")
    void testUtilisationExactlyThirtyPercent() {
        // Limit: 100,000, Balance: 30,000 -> Exactly 30.0%
        CreditAccount acc = new CreditAccount(
                "CC-1", "CUST-1", "HDFC", AccountType.CC,
                LocalDate.of(2023, 1, 1), new BigDecimal("100000"), new BigDecimal("30000"), AccountStatus.ACTIVE
        );

        FactorContribution res = calculator.calculate(List.of(acc), asOf);
        assertEquals(200, res.awardedPoints(), "Utilisation exactly 30% must receive 200 points");
    }

    @Test
    @DisplayName("T-02: Utilisation exactly 100.0% receives 40 points (BR-16 boundary)")
    void testUtilisationExactlyHundredPercent() {
        // Limit: 100,000, Balance: 100,000 -> Exactly 100.0%
        CreditAccount acc = new CreditAccount(
                "CC-1", "CUST-1", "HDFC", AccountType.CC,
                LocalDate.of(2023, 1, 1), new BigDecimal("100000"), new BigDecimal("100000"), AccountStatus.ACTIVE
        );

        FactorContribution res = calculator.calculate(List.of(acc), asOf);
        assertEquals(40, res.awardedPoints(), "Utilisation exactly 100% must receive 40 points");
    }

    @Test
    @DisplayName("Utilisation > 100% receives 0 points (BR-16)")
    void testUtilisationOverHundredPercent() {
        CreditAccount acc = new CreditAccount(
                "CC-1", "CUST-1", "HDFC", AccountType.CC,
                LocalDate.of(2023, 1, 1), new BigDecimal("100000"), new BigDecimal("105000"), AccountStatus.ACTIVE
        );

        FactorContribution res = calculator.calculate(List.of(acc), asOf);
        assertEquals(0, res.awardedPoints(), "Utilisation > 100% must receive 0 points");
    }

    @Test
    @DisplayName("T-15: Closed account excluded from utilisation calculation (BR-20)")
    void testClosedAccountExcludedFromUtilisation() {
        CreditAccount closedAcc = new CreditAccount(
                "CC-CLOSED", "CUST-1", "HDFC", AccountType.CC,
                LocalDate.of(2023, 1, 1), new BigDecimal("200000"), new BigDecimal("150000"), AccountStatus.CLOSED
        );
        closedAcc.setCloseDate(LocalDate.of(2026, 1, 1));

        CreditAccount activeAcc = new CreditAccount(
                "CC-ACTIVE", "CUST-1", "SBI", AccountType.CC,
                LocalDate.of(2024, 1, 1), new BigDecimal("100000"), new BigDecimal("20000"), AccountStatus.ACTIVE
        );

        FactorContribution res = calculator.calculate(List.of(closedAcc, activeAcc), asOf);
        // Only active acc: 20,000 / 100,000 = 20% -> <= 30% -> 200 pts
        assertEquals(200, res.awardedPoints(), "Closed account balances must NOT be included in utilisation");
    }

    @Test
    @DisplayName("T-16: BNPL account with limit below ₹20,000 is excluded from utilisation (BR-19)")
    void testBnplBelow20kExcluded() {
        CreditAccount bnplAcc = new CreditAccount(
                "BNPL-1", "CUST-1", "LAZYPAY", AccountType.BNPL,
                LocalDate.of(2024, 1, 1), new BigDecimal("19999"), new BigDecimal("15000"), AccountStatus.ACTIVE
        );

        // No CC accounts and BNPL < 20,000 -> Fixed 120 points (BR-17)
        FactorContribution res = calculator.calculate(List.of(bnplAcc), asOf);
        assertEquals(120, res.awardedPoints(), "BNPL with limit < 20k must be excluded, giving fixed 120 points");
    }

    @Test
    @DisplayName("T-17: BNPL account with limit exactly ₹20,000 contributes to utilisation (BR-19)")
    void testBnplExactly20kIncluded() {
        // Limit: 20,000, Balance: 4,000 -> 20% utilisation -> 200 points
        CreditAccount bnplAcc = new CreditAccount(
                "BNPL-1", "CUST-1", "SIMPL", AccountType.BNPL,
                LocalDate.of(2024, 1, 1), new BigDecimal("20000"), new BigDecimal("4000"), AccountStatus.ACTIVE
        );

        FactorContribution res = calculator.calculate(List.of(bnplAcc), asOf);
        assertEquals(200, res.awardedPoints(), "BNPL with limit exactly 20k must contribute to utilisation");
    }
}

