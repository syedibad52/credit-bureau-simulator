package com.creditbureau.simulator.scoring;

import com.creditbureau.simulator.domain.enums.EnquiryType;
import com.creditbureau.simulator.domain.model.Enquiry;
import com.creditbureau.simulator.domain.model.FactorContribution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnquiryCalculatorTest {

    private final EnquiryCalculator calculator = new EnquiryCalculator();
    private final LocalDate asOf = LocalDate.of(2026, 10, 31);

    @Test
    @DisplayName("T-04: Enquiries exactly 14 days apart from same lender deduplicate to 1 enquiry (BR-33 boundary)")
    void testEnquiriesExactly14DaysApart() {
        // Enquiries on Oct 1 and Oct 15 -> exactly 14 days apart
        Enquiry e1 = new Enquiry("CUST-1", "HDFC", "CC_APP", EnquiryType.HARD, LocalDate.of(2026, 10, 1), 1);
        Enquiry e2 = new Enquiry("CUST-1", "HDFC", "CC_APP", EnquiryType.HARD, LocalDate.of(2026, 10, 15), 2);

        FactorContribution res = calculator.calculate(List.of(e1, e2), asOf);
        // Deduplicated to 1 hard enquiry in M1-6 -> -15 deduction
        assertEquals(-15, res.awardedPoints(), "Two enquiries exactly 14 days apart must count as one (-15 pts)");

        // 15 days apart should NOT deduplicate -> 2 enquiries = -30 pts
        Enquiry e3 = new Enquiry("CUST-1", "HDFC", "CC_APP", EnquiryType.HARD, LocalDate.of(2026, 10, 16), 3);
        FactorContribution res2 = calculator.calculate(List.of(e1, e3), asOf);
        assertEquals(-30, res2.awardedPoints(), "Two enquiries 15 days apart must NOT deduplicate (-30 pts)");
    }

    @Test
    @DisplayName("T-19: Soft enquiries produce zero score deduction (BR-32)")
    void testSoftEnquiryZeroDeduction() {
        Enquiry soft1 = new Enquiry("CUST-1", "HDFC", "ANNUAL_REVIEW", EnquiryType.SOFT, LocalDate.of(2026, 10, 15), 1);
        Enquiry soft2 = new Enquiry("CUST-1", "CRED", "PRE_APPROVED", EnquiryType.SOFT, LocalDate.of(2026, 9, 10), 2);

        FactorContribution res = calculator.calculate(List.of(soft1, soft2), asOf);
        assertEquals(0, res.awardedPoints(), "Soft enquiries must result in 0 point deduction");
    }

    @Test
    @DisplayName("T-20: Enquiries after the as-of date are excluded (BR-35)")
    void testFutureEnquiriesExcluded() {
        // As-of is 2026-10-31; enquiry is on 2026-11-05
        Enquiry future = new Enquiry("CUST-1", "AXIS", "PL_APP", EnquiryType.HARD, LocalDate.of(2026, 11, 5), 1);
        Enquiry past = new Enquiry("CUST-1", "AXIS", "PL_APP", EnquiryType.HARD, LocalDate.of(2026, 10, 10), 2);

        FactorContribution res = calculator.calculate(List.of(future, past), asOf);
        // Only past enquiry is evaluated (-15)
        assertEquals(-15, res.awardedPoints(), "Future enquiry must be excluded");
    }
}

