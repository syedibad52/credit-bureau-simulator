package com.creditbureau.simulator.reporting;

import com.creditbureau.simulator.domain.model.AccountEvent;
import com.creditbureau.simulator.domain.model.CreditAccount;
import com.creditbureau.simulator.domain.model.CreditProfile;
import com.creditbureau.simulator.domain.model.Enquiry;
import com.creditbureau.simulator.domain.model.OverrideResult;
import com.creditbureau.simulator.domain.model.ScoreResult;

import java.time.LocalDate;
import java.util.List;

/**
 * Generates the human-readable CLI credit report (Phase 12).
 */
public class CreditReportGenerator {

    private final DPDGridFormatter dpdFormatter = new DPDGridFormatter();

    public String generateReport(
            String consumerId,
            LocalDate asOfDate,
            CreditProfile profile,
            List<CreditAccount> accounts,
            List<Enquiry> enquiries,
            List<AccountEvent> events,
            ScoreResult scoreResult
    ) {
        StringBuilder sb = new StringBuilder();

        // 1. Header
        sb.append(ReportFormatter.header("CREDIT BUREAU SIMULATION REPORT"));
        sb.append(String.format("Consumer ID:    %s%n", consumerId));
        sb.append(String.format("As-of Date:     %s%n", asOfDate));
        sb.append(String.format("File Status:    %s%n", scoreResult.isThinFile() ? "THIN FILE (NA)" : "ACTIVE CREDIT FILE"));

        // 2. Account Summary
        sb.append(ReportFormatter.subHeader("ACCOUNT SUMMARY"));
        sb.append(String.format("Total Merged Accounts:    %d%n", profile.getMergedAccountCount()));
        sb.append(String.format("Active Accounts:          %d%n", profile.getActiveAccountCount()));
        sb.append(String.format("Closed Accounts:          %d%n", profile.getClosedAccountCount()));
        sb.append(String.format("Product Types:            %s%n", profile.getAccountTypes()));
        sb.append(String.format("Total Revolving Limit:    %s%n", ReportFormatter.formatCurrency(profile.getTotalRevolvingLimit())));
        sb.append(String.format("Total Revolving Balance:  %s%n", ReportFormatter.formatCurrency(profile.getTotalRevolvingBalance())));
        sb.append(String.format("Revolving Utilisation:    %.2f%%%n", profile.getRevolvingUtilisation().doubleValue()));
        if (profile.getOldestAccountOpenDate() != null) {
            sb.append(String.format("Oldest Account Age:       %d years, %d months (Opened %s)%n",
                    profile.getOldestAccountAge().getYears(),
                    profile.getOldestAccountAge().getMonths(),
                    profile.getOldestAccountOpenDate()));
        } else {
            sb.append("Oldest Account Age:       None (No open accounts)\n");
        }
        sb.append(String.format("Credit Mix Status:        %s%n", profile.isHasMixedCredit() ? "Secured + Unsecured" : "Single Category"));
        sb.append(String.format("Hard Enquiries (12 mos):  %d%n", profile.getHardEnquiryCount()));
        sb.append(String.format("Soft Enquiries (12 mos):  %d%n", profile.getSoftEnquiryCount()));

        // 3. Merged Tradelines & Accounts Detail
        sb.append(ReportFormatter.subHeader("MERGED TRADELINE ACCOUNTS"));
        sb.append(String.format("%-24s %-6s %-12s %-12s %-14s %-14s %-10s%n",
                "Account ID", "Type", "Lender", "Opened", "Limit", "Balance", "Status"));
        sb.append(ReportFormatter.divider('-', 96)).append(System.lineSeparator());
        for (CreditAccount acc : accounts) {
            sb.append(String.format("%-24s %-6s %-12s %-12s %-14s %-14s %-10s%n",
                    acc.getAccountId(),
                    acc.getAccountType(),
                    acc.getLender(),
                    acc.getOpenDate(),
                    ReportFormatter.formatCurrency(acc.getCreditLimit()),
                    ReportFormatter.formatCurrency(acc.getOutstandingBalance()),
                    acc.getCurrentStatus()));
        }

        // 4. 36-Month Repayment (DPD) Grid
        sb.append(ReportFormatter.subHeader("36-MONTH REPAYMENT HISTORY (DPD GRID)"));
        sb.append("Key: [ STD ] = Standard/No delay, [01-29] = Minor, [30-59] = Moderate, [60-89] = Serious, [90+] = NPA, [ XXX ] = No data\n\n");
        for (CreditAccount acc : accounts) {
            sb.append(dpdFormatter.formatGrid(acc, asOfDate));
            sb.append(System.lineSeparator());
        }

        // 5. Score Breakdown
        sb.append(ReportFormatter.subHeader("SCORE BREAKDOWN"));
        if (scoreResult.isThinFile()) {
            sb.append(String.format("CREDIT SCORE: NA%n"));
            sb.append(String.format("Thin File Reason: %s%n", scoreResult.thinFileReason()));
        } else {
            sb.append(String.format("Base Score:             %4d%n", scoreResult.baseScore()));
            sb.append(String.format("Payment History:        %4d / %-3d  (%s)%n",
                    scoreResult.paymentHistory().awardedPoints(),
                    scoreResult.paymentHistory().maxPoints(),
                    scoreResult.paymentHistory().explanation()));
            sb.append(String.format("Credit Utilisation:     %4d / %-3d  (%s)%n",
                    scoreResult.utilisation().awardedPoints(),
                    scoreResult.utilisation().maxPoints(),
                    scoreResult.utilisation().explanation()));
            sb.append(String.format("Credit Age:             %4d / %-3d  (%s)%n",
                    scoreResult.creditAge().awardedPoints(),
                    scoreResult.creditAge().maxPoints(),
                    scoreResult.creditAge().explanation()));
            sb.append(String.format("Credit Mix:             %4d / %-3d  (%s)%n",
                    scoreResult.creditMix().awardedPoints(),
                    scoreResult.creditMix().maxPoints(),
                    scoreResult.creditMix().explanation()));
            sb.append(String.format("Enquiry Deduction:      %4d        (%s)%n",
                    scoreResult.enquiries().awardedPoints(),
                    scoreResult.enquiries().explanation()));
            sb.append(ReportFormatter.divider('-', 72)).append(System.lineSeparator());
            sb.append(String.format("Pre-Override Score:     %4d%n", scoreResult.preOverrideScore()));

            // 6. Applied Overrides
            sb.append(ReportFormatter.subHeader("APPLIED OVERRIDES"));
            boolean anyOverrideApplied = false;
            for (OverrideResult ov : scoreResult.appliedOverrides()) {
                if (ov.applied()) {
                    anyOverrideApplied = true;
                    sb.append(String.format("[%s] %-42s Cap: %3d  Reason: %s%n",
                            ov.ruleId(), ov.overrideName(), ov.capValue(), ov.reason()));
                }
            }
            if (!anyOverrideApplied) {
                sb.append("None (No risk caps triggered)\n");
            }

            // 7. Final Score
            sb.append(ReportFormatter.divider('=', 72)).append(System.lineSeparator());
            sb.append(String.format("FINAL CREDIT SCORE:     %d%n", scoreResult.finalScore()));
            sb.append(ReportFormatter.divider('=', 72)).append(System.lineSeparator());
        }

        // 8. Applied Business Rules
        sb.append(ReportFormatter.subHeader("APPLIED BUSINESS RULES"));
        for (String ruleId : scoreResult.appliedRuleIds()) {
            sb.append(" - ").append(ruleId).append(System.lineSeparator());
        }

        return sb.toString();
    }
}

