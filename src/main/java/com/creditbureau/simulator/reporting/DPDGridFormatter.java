package com.creditbureau.simulator.reporting;

import com.creditbureau.simulator.domain.model.CreditAccount;
import com.creditbureau.simulator.processing.DPDHistoryEngine;

import java.time.LocalDate;
import java.util.List;

/**
 * Formats the 36-month DPD grid for CLI report output.
 */
public class DPDGridFormatter {

    private final DPDHistoryEngine dpdEngine = new DPDHistoryEngine();

    /**
     * Format a compact, legible 36-month repayment grid for a credit account.
     * Displays in 3 rows of 12 months each:
     * - Row 1: Months 1–12 (Recent)
     * - Row 2: Months 13–24
     * - Row 3: Months 25–36 (Oldest)
     */
    public String formatGrid(CreditAccount account, LocalDate asOfDate) {
        List<DPDHistoryEngine.MonthlyDPDEntry> grid = dpdEngine.build36MonthGrid(account, asOfDate);
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("Account: %-25s Type: %-4s Lender: %s%n",
                account.getAccountId(), account.getAccountType(), account.getLender()));

        // Format in 3 blocks of 12 months
        sb.append("  [Months 01-12] ");
        for (int i = 0; i < 12; i++) {
            sb.append(formatCell(grid.get(i)));
        }
        sb.append(System.lineSeparator());

        sb.append("  [Months 13-24] ");
        for (int i = 12; i < 24; i++) {
            sb.append(formatCell(grid.get(i)));
        }
        sb.append(System.lineSeparator());

        sb.append("  [Months 25-36] ");
        for (int i = 24; i < 36; i++) {
            sb.append(formatCell(grid.get(i)));
        }
        sb.append(System.lineSeparator());

        return sb.toString();
    }

    private String formatCell(DPDHistoryEngine.MonthlyDPDEntry entry) {
        if (!entry.hasData()) {
            return "[ XXX ] ";
        }
        int val = entry.dpdValue();
        if (val == 0) {
            return "[ STD ] ";
        }
        return String.format("[%3d  ] ", val);
    }
}

