package com.creditbureau.simulator.scoring;

import com.creditbureau.simulator.domain.enums.EnquiryType;
import com.creditbureau.simulator.domain.model.Enquiry;
import com.creditbureau.simulator.domain.model.FactorContribution;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Calculates credit enquiry deductions (Phase 8.F, BR-30 through BR-35).
 * Maximum 100-point deduction.
 * - Hard enquiries in last 6 months: -15 each
 * - Hard enquiries in months 7-12: -5 each
 * - Soft enquiries: 0 deduction
 * - Same-lender deduplication within 14 calendar days (inclusive)
 * - Maximum total deduction: 100
 */
public class EnquiryCalculator {

    public static final int MAX_DEDUCTION = 100;

    public FactorContribution calculate(List<Enquiry> rawEnquiries, LocalDate asOfDate) {
        // BR-35: Exclude enquiries after asOfDate
        List<Enquiry> eligible = rawEnquiries.stream()
                .filter(e -> !e.enquiryDate().isAfter(asOfDate))
                .toList();

        // BR-32: Separate soft and hard enquiries (soft = 0 deduction)
        List<Enquiry> hardEnquiries = eligible.stream()
                .filter(e -> e.type() == EnquiryType.HARD)
                .toList();

        // BR-33: Deduplicate hard enquiries from same lender within 14 calendar days
        List<Enquiry> deduplicatedHard = deduplicate(hardEnquiries);

        int totalDeduction = 0;
        int m1To6Count = 0;
        int m7To12Count = 0;

        LocalDate sixMonthsAgo = asOfDate.minusMonths(6);
        LocalDate twelveMonthsAgo = asOfDate.minusMonths(12);

        for (Enquiry enq : deduplicatedHard) {
            LocalDate date = enq.enquiryDate();

            if (date.isBefore(twelveMonthsAgo)) {
                // Older than 12 months -> no deduction
                continue;
            }

            // A-12: Calendar month boundary
            // If date is strictly after sixMonthsAgo -> last 6 months
            // If date is on or before sixMonthsAgo and on or after twelveMonthsAgo -> months 7-12
            if (date.isAfter(sixMonthsAgo)) {
                totalDeduction += 15;
                m1To6Count++;
            } else {
                totalDeduction += 5;
                m7To12Count++;
            }
        }

        // BR-34: Maximum deduction capped at 100 points
        int finalDeduction = Math.min(totalDeduction, MAX_DEDUCTION);
        int awardedPoints = -finalDeduction;

        String explanation = String.format(
                "Hard enquiries: %d in M1-6 (-15 each), %d in M7-12 (-5 each) -> total deduction -%d (capped at -100)",
                m1To6Count, m7To12Count, finalDeduction);

        return new FactorContribution("Enquiries", 0, awardedPoints, explanation);
    }

    /**
     * Deduplicate hard enquiries from the same lender within 14 calendar days (inclusive).
     * Clusters enquiries that occur within 14 days of the previous enquiry in the cluster.
     */
    public List<Enquiry> deduplicate(List<Enquiry> enquiries) {
        List<Enquiry> result = new ArrayList<>();

        // Group by lender (case-insensitive)
        Map<String, List<Enquiry>> byLender = enquiries.stream()
                .collect(Collectors.groupingBy(e -> e.lender().toUpperCase()));

        for (List<Enquiry> lenderEnquiries : byLender.values()) {
            // Sort by enquiry date, then by file order index
            List<Enquiry> sorted = lenderEnquiries.stream()
                    .sorted(Comparator.comparing(Enquiry::enquiryDate)
                            .thenComparingInt(Enquiry::fileOrderIndex))
                    .toList();

            Enquiry currentClusterRep = null;
            LocalDate clusterLastDate = null;

            for (Enquiry enq : sorted) {
                if (currentClusterRep == null) {
                    currentClusterRep = enq;
                    clusterLastDate = enq.enquiryDate();
                    result.add(enq);
                } else {
                    long daysDiff = ChronoUnit.DAYS.between(clusterLastDate, enq.enquiryDate());
                    // BR-33, A-08: within 14 days inclusive
                    if (daysDiff <= 14) {
                        // Belongs to current cluster, update last seen date
                        clusterLastDate = enq.enquiryDate();
                    } else {
                        // Start a new cluster
                        currentClusterRep = enq;
                        clusterLastDate = enq.enquiryDate();
                        result.add(enq);
                    }
                }
            }
        }

        return result;
    }
}

