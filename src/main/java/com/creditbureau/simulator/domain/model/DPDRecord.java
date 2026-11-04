package com.creditbureau.simulator.domain.model;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Immutable record representing a single month's Days Past Due observation.
 * Tracks the reporting month, actual DPD value, and when this data was submitted.
 */
public record DPDRecord(
    YearMonth reportingMonth,
    int dpdValue,
    LocalDate submissionDate
) {
    public DPDRecord {
        if (reportingMonth == null) throw new IllegalArgumentException("reportingMonth must not be null");
        if (dpdValue < 0) throw new IllegalArgumentException("dpdValue must not be negative");
        if (submissionDate == null) throw new IllegalArgumentException("submissionDate must not be null");
    }

    /** Returns the DPD category label for display purposes. */
    public String getDPDCategory() {
        if (dpdValue == 0) return "STD";       // Standard / no delay
        if (dpdValue < 30) return "SMA-0";     // Minor delay
        if (dpdValue < 60) return "SMA-1";     // Moderate delay
        if (dpdValue < 90) return "SMA-2";     // Serious delay
        return "NPA";                           // Major delay (90+)
    }
}
