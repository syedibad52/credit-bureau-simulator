package com.creditbureau.simulator.processing;

import com.creditbureau.simulator.domain.model.CreditAccount;
import com.creditbureau.simulator.domain.model.DPDRecord;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Builds and analyzes the 36-month repayment (DPD) history for credit accounts (Phase 6).
 */
public class DPDHistoryEngine {

    public static final int HISTORY_WINDOW_MONTHS = 36;

    /**
     * Entry in a 36-month DPD timeline for reporting and analysis.
     *
     * @param monthIndex 1 to 36 (1 = as-of month, 2 = 1 month prior, ..., 36 = 35 months prior)
     * @param yearMonth  the calendar YearMonth
     * @param dpdValue   the DPD value, or null if no submission was made for this month
     * @param category   display code: STD, SMA-0, SMA-1, SMA-2, NPA, or XXX (missing)
     */
    public record MonthlyDPDEntry(
            int monthIndex,
            YearMonth yearMonth,
            Integer dpdValue,
            String category
    ) {
        public boolean hasData() {
            return dpdValue != null;
        }
    }

    /**
     * Build the 36-month repayment grid for a given account ending at the as-of date.
     *
     * @param account  the credit account
     * @param asOfDate the evaluation date
     * @return 36 entries from month 1 (most recent) to month 36 (oldest)
     */
    public List<MonthlyDPDEntry> build36MonthGrid(CreditAccount account, LocalDate asOfDate) {
        List<MonthlyDPDEntry> grid = new ArrayList<>(HISTORY_WINDOW_MONTHS);
        YearMonth asOfMonth = YearMonth.from(asOfDate);

        for (int i = 1; i <= HISTORY_WINDOW_MONTHS; i++) {
            YearMonth targetMonth = asOfMonth.minusMonths(i - 1);

            Optional<DPDRecord> recordOpt = account.getDpdHistory().stream()
                    .filter(r -> r.reportingMonth().equals(targetMonth))
                    .findFirst();

            if (recordOpt.isPresent()) {
                int dpd = recordOpt.get().dpdValue();
                grid.add(new MonthlyDPDEntry(i, targetMonth, dpd, categorizeDPD(dpd)));
            } else {
                // Missing month
                grid.add(new MonthlyDPDEntry(i, targetMonth, null, "XXX"));
            }
        }

        return Collections.unmodifiableList(grid);
    }

    /**
     * Map numerical DPD to standard credit bureau classification category.
     */
    public static String categorizeDPD(int dpd) {
        if (dpd == 0) return "STD";       // Standard / No delay
        if (dpd < 30) return "SMA-0";     // 1-29 Minor delay
        if (dpd < 60) return "SMA-1";     // 30-59 Moderate delay
        if (dpd < 90) return "SMA-2";     // 60-89 Serious delay
        return "NPA";                     // 90+ Major delay
    }

    /**
     * Find the worst (highest) DPD value for an account within month indices [startMonth, endMonth] (inclusive).
     * Month 1 is the as-of month.
     *
     * @param account    credit account
     * @param asOfDate   as-of date
     * @param startMonth 1-based start month index (e.g. 1)
     * @param endMonth   1-based end month index (e.g. 12 or 6)
     * @return worst DPD value, or -1 if no records exist in that window
     */
    public int getWorstDPD(CreditAccount account, LocalDate asOfDate, int startMonth, int endMonth) {
        YearMonth asOfMonth = YearMonth.from(asOfDate);
        int worst = -1;

        for (int i = startMonth; i <= endMonth; i++) {
            YearMonth targetMonth = asOfMonth.minusMonths(i - 1);
            for (DPDRecord r : account.getDpdHistory()) {
                if (r.reportingMonth().equals(targetMonth)) {
                    if (r.dpdValue() > worst) {
                        worst = r.dpdValue();
                    }
                }
            }
        }

        return worst;
    }
}

