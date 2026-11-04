package com.creditbureau.simulator.processing;

import com.creditbureau.simulator.domain.model.AccountEvent;
import com.creditbureau.simulator.domain.model.Enquiry;
import com.creditbureau.simulator.domain.model.Tradeline;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Filters and deterministically sorts input records based on the requested as-of date (BR-46, BR-47).
 * Any record with a date strictly after the as-of date is excluded.
 */
public class AsOfDateFilter {

    /**
     * Filter tradelines submitted on or before asOfDate, sorted deterministically.
     */
    public List<Tradeline> filterTradelines(List<Tradeline> tradelines, LocalDate asOfDate) {
        return tradelines.stream()
                .filter(t -> !t.submissionDate().isAfter(asOfDate))
                .sorted(Comparator.comparing(Tradeline::submissionDate)
                        .thenComparing(Tradeline::lender, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(t -> t.accountType().name())
                        .thenComparingInt(Tradeline::fileOrderIndex))
                .collect(Collectors.toList());
    }

    /**
     * Filter enquiries occurring on or before asOfDate, sorted deterministically (BR-35).
     */
    public List<Enquiry> filterEnquiries(List<Enquiry> enquiries, LocalDate asOfDate) {
        return enquiries.stream()
                .filter(e -> !e.enquiryDate().isAfter(asOfDate))
                .sorted(Comparator.comparing(Enquiry::enquiryDate)
                        .thenComparing(Enquiry::lender, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(Enquiry::fileOrderIndex))
                .collect(Collectors.toList());
    }

    /**
     * Filter account events occurring on or before asOfDate, sorted deterministically.
     */
    public List<AccountEvent> filterEvents(List<AccountEvent> events, LocalDate asOfDate) {
        return events.stream()
                .filter(e -> !e.eventDate().isAfter(asOfDate))
                .sorted(Comparator.comparing(AccountEvent::eventDate)
                        .thenComparing(e -> e.eventType().name())
                        .thenComparing(AccountEvent::details))
                .collect(Collectors.toList());
    }
}

