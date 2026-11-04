package com.creditbureau.simulator.scoring;

import com.creditbureau.simulator.domain.enums.AccountStatus;
import com.creditbureau.simulator.domain.enums.AccountType;
import com.creditbureau.simulator.domain.model.CreditAccount;
import com.creditbureau.simulator.domain.model.DPDRecord;
import com.creditbureau.simulator.domain.model.FactorContribution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaymentHistoryCalculatorTest {

    private final PaymentHistoryCalculator calculator = new PaymentHistoryCalculator();

    @Test
    @DisplayName("Clean payment history should receive full 300 points (BR-07)")
    void testCleanPaymentHistory() {
        CreditAccount acc = new CreditAccount(
                "ACC-1", "CUST-1", "HDFC", AccountType.CC,
                LocalDate.of(2023, 1, 1), new BigDecimal("100000"), new BigDecimal("10000"), AccountStatus.ACTIVE
        );
        acc.addDPDRecord(new DPDRecord(YearMonth.of(2026, 10), 0, LocalDate.of(2026, 10, 1)));

        FactorContribution res = calculator.calculate(List.of(acc), LocalDate.of(2026, 10, 31));
        assertEquals(300, res.awardedPoints());
    }

    @Test
    @DisplayName("T-14: Closed account contributes to payment history for 36 months after closure (BR-11)")
    void testClosedAccountContributingToPaymentHistory() {
        LocalDate asOf = LocalDate.of(2026, 10, 31);
        CreditAccount acc = new CreditAccount(
                "ACC-1", "CUST-1", "HDFC", AccountType.PL,
                LocalDate.of(2023, 1, 1), new BigDecimal("100000"), BigDecimal.ZERO, AccountStatus.CLOSED
        );
        // Closed 12 months ago -> within 36 months of closure (BR-11)
        acc.setCloseDate(LocalDate.of(2025, 10, 1));
        // Had 30 DPD in month 3 before asOfDate (inside M1-12 window: -60 pts)
        acc.addDPDRecord(new DPDRecord(YearMonth.of(2026, 8), 35, LocalDate.of(2026, 8, 1)));

        FactorContribution res = calculator.calculate(List.of(acc), asOf);
        // 300 - 60 = 240
        assertEquals(240, res.awardedPoints(), "Closed account within 36 months must contribute DPD deductions");
    }

    @Test
    @DisplayName("BNPL accounts receive 50% DPD deduction weight (BR-12)")
    void testBnplHalfWeightDeduction() {
        LocalDate asOf = LocalDate.of(2026, 10, 31);
        CreditAccount bnplAcc = new CreditAccount(
                "BNPL-1", "CUST-1", "LAZYPAY", AccountType.BNPL,
                LocalDate.of(2024, 1, 1), new BigDecimal("30000"), new BigDecimal("5000"), AccountStatus.ACTIVE
        );
        // 90+ DPD in Month 2 (M1-12 window normally -150 pts, with BNPL 50% -> -75 pts)
        bnplAcc.addDPDRecord(new DPDRecord(YearMonth.of(2026, 9), 92, LocalDate.of(2026, 9, 1)));

        FactorContribution res = calculator.calculate(List.of(bnplAcc), asOf);
        // 300 - 75 = 225
        assertEquals(225, res.awardedPoints(), "BNPL 90+ DPD should deduct 75 points (50% of 150)");
    }

    @Test
    @DisplayName("Months 13-36 deductions apply at half weight (BR-09)")
    void testMonths13To36HalfWeight() {
        LocalDate asOf = LocalDate.of(2026, 10, 31);
        CreditAccount acc = new CreditAccount(
                "ACC-1", "CUST-1", "SBI", AccountType.PL,
                LocalDate.of(2023, 1, 1), new BigDecimal("100000"), new BigDecimal("50000"), AccountStatus.ACTIVE
        );
        // 90+ DPD in Month 15 (M13-36 window: normally -150 pts, half weight -> -75 pts)
        YearMonth m15 = YearMonth.from(asOf).minusMonths(14);
        acc.addDPDRecord(new DPDRecord(m15, 95, m15.atDay(1)));

        FactorContribution res = calculator.calculate(List.of(acc), asOf);
        // 300 - 75 = 225
        assertEquals(225, res.awardedPoints(), "M13-36 90+ DPD should deduct 75 points");
    }

    @Test
    @DisplayName("Payment history score never goes below 0 (BR-10 floor)")
    void testFloorAtZero() {
        LocalDate asOf = LocalDate.of(2026, 10, 31);
        CreditAccount acc1 = new CreditAccount(
                "ACC-1", "CUST-1", "SBI", AccountType.PL,
                LocalDate.of(2023, 1, 1), new BigDecimal("100000"), new BigDecimal("50000"), AccountStatus.ACTIVE
        );
        CreditAccount acc2 = new CreditAccount(
                "ACC-2", "CUST-1", "HDFC", AccountType.CC,
                LocalDate.of(2023, 1, 1), new BigDecimal("100000"), new BigDecimal("50000"), AccountStatus.ACTIVE
        );
        CreditAccount acc3 = new CreditAccount(
                "ACC-3", "CUST-1", "ICICI", AccountType.AL,
                LocalDate.of(2023, 1, 1), new BigDecimal("100000"), new BigDecimal("50000"), AccountStatus.ACTIVE
        );

        // All 3 accounts have 90+ DPD in M1-12 (-150 * 3 = -450 deduction)
        acc1.addDPDRecord(new DPDRecord(YearMonth.of(2026, 10), 95, LocalDate.of(2026, 10, 1)));
        acc2.addDPDRecord(new DPDRecord(YearMonth.of(2026, 10), 95, LocalDate.of(2026, 10, 1)));
        acc3.addDPDRecord(new DPDRecord(YearMonth.of(2026, 10), 95, LocalDate.of(2026, 10, 1)));

        FactorContribution res = calculator.calculate(List.of(acc1, acc2, acc3), asOf);
        assertEquals(0, res.awardedPoints(), "Payment history floor must be 0 (BR-10)");
    }
}

