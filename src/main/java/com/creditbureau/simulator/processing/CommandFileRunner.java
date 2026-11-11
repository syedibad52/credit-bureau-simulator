package com.creditbureau.simulator.processing;

import com.creditbureau.simulator.domain.enums.AccountStatus;
import com.creditbureau.simulator.domain.enums.AccountType;
import com.creditbureau.simulator.domain.enums.EventType;
import com.creditbureau.simulator.domain.model.AccountEvent;
import com.creditbureau.simulator.domain.model.CreditAccount;
import com.creditbureau.simulator.domain.model.Enquiry;
import com.creditbureau.simulator.domain.model.Rejection;
import com.creditbureau.simulator.domain.model.ScoreResult;
import com.creditbureau.simulator.domain.model.Tradeline;
import com.creditbureau.simulator.ingestion.EnquiryIngester;
import com.creditbureau.simulator.ingestion.TradelineIngester;
import com.creditbureau.simulator.ingestion.ValidationResult;
import com.creditbureau.simulator.scoring.ScoringEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executes a PS-022 commands.txt command stream against ingested data and
 * generates the 5 required evaluation CSV files:
 * 1. report_summary.csv
 * 2. report_accounts.csv
 * 3. dpd_grid.csv
 * 4. score_history.csv
 * 5. rejections.csv
 */
public class CommandFileRunner {

    private static final Logger logger = LoggerFactory.getLogger(CommandFileRunner.class);

    private final TradelineIngester tradelineIngester = new TradelineIngester();
    private final EnquiryIngester enquiryIngester = new EnquiryIngester();
    private final AccountMergingEngine mergingEngine = new AccountMergingEngine();
    private final ScoringEngine scoringEngine = new ScoringEngine();

    // Dispute state
    public record DisputeState(
            String consumerId,
            String lenderId,
            String accountNo,
            LocalDate disputeDate,
            LocalDate resolutionDate,
            String outcome // VALID or REMOVED
    ) {
        public boolean isActiveAt(LocalDate asOfDate) {
            if (disputeDate.isAfter(asOfDate)) return false;
            if (resolutionDate == null) return true;
            return resolutionDate.isAfter(asOfDate);
        }

        public boolean isResolvedValidAt(LocalDate asOfDate) {
            return "VALID".equalsIgnoreCase(outcome) && resolutionDate != null && !resolutionDate.isAfter(asOfDate);
        }

        public boolean isResolvedRemovedAt(LocalDate asOfDate) {
            return "REMOVED".equalsIgnoreCase(outcome) && resolutionDate != null && !resolutionDate.isAfter(asOfDate);
        }
    }

    // Override state
    public record OverrideState(
            String consumerId,
            String lenderId,
            String accountNo,
            LocalDate eventDate,
            EventType eventType
    ) {}

    public void run(Path dataDir, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        // 1. Ingest tradelines and enquiries
        logger.info("Ingesting tradelines from: {}", dataDir);
        ValidationResult<Tradeline> tradelineResult = tradelineIngester.ingestFromDirectory(dataDir);
        List<Tradeline> allTradelines = tradelineResult.validRecords();

        logger.info("Ingesting enquiries from: {}", dataDir);
        ValidationResult<Enquiry> enquiryResult = enquiryIngester.ingestFromDirectory(dataDir);
        List<Enquiry> allEnquiries = enquiryResult.validRecords();

        // 2. Collect rejections and write rejections.csv
        List<Rejection> allRejections = new ArrayList<>();
        allRejections.addAll(tradelineResult.rejections());
        allRejections.addAll(enquiryResult.rejections());
        writeRejections(outputDir.resolve("rejections.csv"), allRejections);

        // 3. Prepare CSV writers for the other 4 output files
        Path summaryPath = outputDir.resolve("report_summary.csv");
        Path accountsPath = outputDir.resolve("report_accounts.csv");
        Path dpdGridPath = outputDir.resolve("dpd_grid.csv");
        Path historyPath = outputDir.resolve("score_history.csv");

        try (BufferedWriter summaryWriter = Files.newBufferedWriter(summaryPath);
             BufferedWriter accountsWriter = Files.newBufferedWriter(accountsPath);
             BufferedWriter dpdGridWriter = Files.newBufferedWriter(dpdGridPath);
             BufferedWriter historyWriter = Files.newBufferedWriter(historyPath)) {

            // Write headers
            summaryWriter.write("consumer_id,as_of_date,history_months,merged_account_count,payment_history_points,utilisation_pct,utilisation_points,credit_age_points,mix_points,enquiry_deduction,raw_score,override_cap,final_score,na_reason,rule_ids\n");
            accountsWriter.write("consumer_id,as_of_date,merged_account_id,lender_id,account_nos,account_type,open_date,sanctioned_amount,balance,status,worst_dpd_12m,included,exclusion_reason\n");
            dpdGridWriter.write("consumer_id,as_of_date,merged_account_id,month_index,report_month,dpd\n");
            historyWriter.write("consumer_id,score_date,final_score,rule_ids\n");

            // State accumulators
            Map<String, DisputeState> disputes = new HashMap<>(); // key: consumerId + "|" + lenderId + "|" + accountNo
            List<OverrideState> overrides = new ArrayList<>();

            Path commandFile = dataDir.resolve("commands.txt");
            if (!Files.exists(commandFile)) {
                logger.warn("commands.txt not found in: {}", dataDir);
                return;
            }

            try (BufferedReader reader = Files.newBufferedReader(commandFile)) {
                String line;
                int lineNum = 0;
                while ((line = reader.readLine()) != null) {
                    lineNum++;
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }

                    String[] parts = trimmed.split("\\|", -1);
                    String command = parts[0].trim().toUpperCase();

                    switch (command) {
                        case "OVERRIDE" -> {
                            // OVERRIDE|consumer_id|lender_id|account_no|event_date|event_type
                            String consumerId = parts[1].trim();
                            String lenderId = parts[2].trim();
                            String accountNo = parts[3].trim();
                            LocalDate eventDate = LocalDate.parse(parts[4].trim());
                            EventType eventType = EventType.valueOf(parts[5].trim().toUpperCase());
                            overrides.add(new OverrideState(consumerId, lenderId, accountNo, eventDate, eventType));
                        }
                        case "DISPUTE" -> {
                            // DISPUTE|consumer_id|lender_id|account_no|dispute_date
                            String consumerId = parts[1].trim();
                            String lenderId = parts[2].trim();
                            String accountNo = parts[3].trim();
                            LocalDate disputeDate = LocalDate.parse(parts[4].trim());
                            String key = consumerId.toUpperCase() + "|" + lenderId.toUpperCase() + "|" + accountNo.toUpperCase();
                            disputes.put(key, new DisputeState(consumerId, lenderId, accountNo, disputeDate, null, null));
                        }
                        case "RESOLVE" -> {
                            // RESOLVE|consumer_id|lender_id|account_no|resolution_date|outcome
                            String consumerId = parts[1].trim();
                            String lenderId = parts[2].trim();
                            String accountNo = parts[3].trim();
                            LocalDate resDate = LocalDate.parse(parts[4].trim());
                            String outcome = parts[5].trim().toUpperCase();
                            String key = consumerId.toUpperCase() + "|" + lenderId.toUpperCase() + "|" + accountNo.toUpperCase();
                            DisputeState existing = disputes.get(key);
                            LocalDate dispDate = existing != null ? existing.disputeDate() : resDate;
                            disputes.put(key, new DisputeState(consumerId, lenderId, accountNo, dispDate, resDate, outcome));
                        }
                        case "REPORT" -> {
                            // REPORT|consumer_id|as_of_date
                            String consumerId = parts[1].trim();
                            LocalDate asOfDate = LocalDate.parse(parts[2].trim());
                            handleReport(consumerId, asOfDate, allTradelines, allEnquiries, disputes, overrides,
                                    summaryWriter, accountsWriter, dpdGridWriter);
                        }
                        case "SCORE_HISTORY" -> {
                            // SCORE_HISTORY|consumer_id|from_date|to_date
                            String consumerId = parts[1].trim();
                            LocalDate fromDate = LocalDate.parse(parts[2].trim());
                            LocalDate toDate = LocalDate.parse(parts[3].trim());
                            handleScoreHistory(consumerId, fromDate, toDate, allTradelines, allEnquiries, disputes, overrides, historyWriter);
                        }
                        default -> logger.warn("Unknown command at line {}: {}", lineNum, command);
                    }
                }
            }
        }

        logger.info("Evaluation finished. Output files written to: {}", outputDir);
    }

    private void handleReport(
            String consumerId,
            LocalDate asOfDate,
            List<Tradeline> allTradelines,
            List<Enquiry> allEnquiries,
            Map<String, DisputeState> disputes,
            List<OverrideState> overrides,
            BufferedWriter summaryWriter,
            BufferedWriter accountsWriter,
            BufferedWriter dpdGridWriter
    ) throws IOException {
        // Point-in-time filtering: receivedDate <= asOfDate
        List<Tradeline> eligibleTradelines = allTradelines.stream()
                .filter(t -> t.consumerId().equalsIgnoreCase(consumerId))
                .filter(t -> !t.receivedDate().isAfter(asOfDate))
                .toList();

        // Enquiries: enquiryTs <= asOfDate 23:59:59
        LocalDateTime endOfAsOfDate = asOfDate.atTime(23, 59, 59);
        List<Enquiry> eligibleEnquiries = allEnquiries.stream()
                .filter(e -> e.consumerId().equalsIgnoreCase(consumerId))
                .filter(e -> !e.enquiryTs().isAfter(endOfAsOfDate))
                .toList();

        // Merge accounts for this consumer
        List<CreditAccount> mergedAccounts = mergingEngine.mergeAccounts(eligibleTradelines, List.of());

        // Attach disputes and overrides
        for (CreditAccount account : mergedAccounts) {
            // Check dispute status as of asOfDate
            for (String accNo : account.getMemberAccountNos()) {
                String key = consumerId.toUpperCase() + "|" + account.getLender().toUpperCase() + "|" + accNo.toUpperCase();
                DisputeState ds = disputes.get(key);
                if (ds != null && !ds.disputeDate().isAfter(asOfDate)) {
                    if (ds.resolutionDate() == null || ds.resolutionDate().isAfter(asOfDate)) {
                        // Under active dispute
                        account.addEvent(AccountEvent.disputeRaised(account.getMergedAccountId(), consumerId, ds.disputeDate()));
                    } else if ("REMOVED".equalsIgnoreCase(ds.outcome())) {
                        account.addEvent(AccountEvent.disputeResolvedRemoved(account.getMergedAccountId(), consumerId, ds.disputeDate(), ds.resolutionDate()));
                    } else if ("VALID".equalsIgnoreCase(ds.outcome())) {
                        account.addEvent(AccountEvent.disputeResolvedValid(account.getMergedAccountId(), consumerId, ds.disputeDate(), ds.resolutionDate()));
                    }
                }
            }

            // Check overrides as of asOfDate
            for (OverrideState os : overrides) {
                if (os.consumerId().equalsIgnoreCase(consumerId) &&
                    os.lenderId().equalsIgnoreCase(account.getLender()) &&
                    (account.getMemberAccountNos().contains(os.accountNo()) || os.accountNo().equalsIgnoreCase(account.getMergedAccountId()))) {
                    if (!os.eventDate().isAfter(asOfDate)) {
                        account.addEvent(AccountEvent.override(account.getMergedAccountId(), consumerId, os.eventType(), os.eventDate()));
                        account.setCurrentStatus(AccountStatus.valueOf(os.eventType().name()));
                    }
                }
            }
        }

        // Sort accounts by lender_id, then open_date
        mergedAccounts.sort(Comparator.comparing(CreditAccount::getLender, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(CreditAccount::getOpenDate)
                .thenComparing(CreditAccount::getMergedAccountId));

        // Distinct report months for history_months
        // Only count distinct report months from accounts that are eligible (not excluded/removed)
        long historyMonths = mergedAccounts.stream()
                .filter(a -> a.isIncluded(asOfDate))
                .flatMap(a -> a.getDpdHistory().stream())
                .map(com.creditbureau.simulator.domain.model.DPDRecord::reportingMonth)
                .distinct()
                .count();

        // Calculate score
        ScoreResult result = scoringEngine.calculateScore(
                consumerId,
                mergedAccounts,
                eligibleEnquiries,
                mergedAccounts.stream().flatMap(a -> a.getEvents().stream()).toList(),
                asOfDate
        );

        // Calculate utilisation percentage string
        String utilPctStr = "";
        BigDecimal totalBalance = BigDecimal.ZERO;
        BigDecimal totalLimit = BigDecimal.ZERO;
        boolean hasEligibleRevolving = false;

        for (CreditAccount acc : mergedAccounts) {
            if (!acc.isIncluded(asOfDate)) continue;
            if (acc.getCurrentStatus() == AccountStatus.CLOSED) continue;
            if (acc.getAccountType() == AccountType.CC) {
                if (acc.getCreditLimit().compareTo(BigDecimal.ZERO) > 0) {
                    hasEligibleRevolving = true;
                    totalLimit = totalLimit.add(acc.getCreditLimit());
                    totalBalance = totalBalance.add(acc.getOutstandingBalance());
                }
            } else if (acc.getAccountType() == AccountType.BNPL) {
                if (acc.getCreditLimit().compareTo(new BigDecimal("20000")) >= 0) {
                    hasEligibleRevolving = true;
                    totalLimit = totalLimit.add(acc.getCreditLimit());
                    totalBalance = totalBalance.add(acc.getOutstandingBalance());
                }
            }
        }

        if (hasEligibleRevolving && totalLimit.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pct = totalBalance.multiply(BigDecimal.valueOf(100)).divide(totalLimit, 2, RoundingMode.HALF_UP);
            utilPctStr = String.format("%.2f", pct.doubleValue());
        }

        // Enquiry deduction as positive magnitude
        int enqDeduction = Math.abs(result.enquiryContribution().awardedPoints());

        // Override cap string
        String overrideCapStr = "";
        for (com.creditbureau.simulator.domain.model.OverrideResult or : result.overrides()) {
            if (or.isTriggered()) {
                if (overrideCapStr.isEmpty() || or.capValue() < Integer.parseInt(overrideCapStr)) {
                    overrideCapStr = String.valueOf(or.capValue());
                }
            }
        }

        String finalScoreStr = result.isThinFile() ? "NA" : String.valueOf(result.finalScore());
        String naReasonStr = result.isThinFile() && result.thinFileReason() != null ? result.thinFileReason() : "";

        // Write row to report_summary.csv
        // consumer_id,as_of_date,history_months,merged_account_count,payment_history_points,utilisation_pct,utilisation_points,credit_age_points,mix_points,enquiry_deduction,raw_score,override_cap,final_score,na_reason,rule_ids
        summaryWriter.write(String.format("%s,%s,%d,%d,%d,%s,%d,%d,%d,%d,%d,%s,%s,%s,%s\n",
                consumerId,
                asOfDate,
                historyMonths,
                mergedAccounts.size(),
                result.paymentContribution().awardedPoints(),
                utilPctStr,
                result.utilisationContribution().awardedPoints(),
                result.creditAgeContribution().awardedPoints(),
                result.creditMixContribution().awardedPoints(),
                enqDeduction,
                result.rawScore(),
                overrideCapStr,
                finalScoreStr,
                naReasonStr,
                String.join(";", result.appliedRuleIds())
        ));

        // Write rows to report_accounts.csv and dpd_grid.csv
        for (CreditAccount acc : mergedAccounts) {
            // report_accounts.csv: consumer_id,as_of_date,merged_account_id,lender_id,account_nos,account_type,open_date,sanctioned_amount,balance,status,worst_dpd_12m,included,exclusion_reason
            accountsWriter.write(String.format("%s,%s,%s,%s,%s,%s,%s,%.2f,%.2f,%s,%d,%s,%s\n",
                    consumerId,
                    asOfDate,
                    acc.getMergedAccountId(),
                    acc.getLender(),
                    acc.getAccountNosFormatted(),
                    acc.getAccountType().name(),
                    acc.getOpenDate(),
                    acc.getCreditLimit().doubleValue(),
                    acc.getOutstandingBalance().doubleValue(),
                    acc.getCurrentStatus(),
                    acc.getWorstDpd12m(asOfDate),
                    acc.isIncluded(asOfDate),
                    acc.getExclusionReason(asOfDate)
            ));

            // dpd_grid.csv: 36 rows per merged account
            // consumer_id,as_of_date,merged_account_id,month_index,report_month,dpd
            YearMonth asOfMonth = YearMonth.from(asOfDate);
            for (int i = 1; i <= 36; i++) {
                YearMonth targetMonth = asOfMonth.minusMonths(i - 1);
                String dpdValueStr = "NR";
                for (com.creditbureau.simulator.domain.model.DPDRecord r : acc.getDpdHistory()) {
                    if (r.reportingMonth().equals(targetMonth)) {
                        dpdValueStr = String.valueOf(r.dpdValue());
                        break;
                    }
                }
                dpdGridWriter.write(String.format("%s,%s,%s,%d,%s,%s\n",
                        consumerId,
                        asOfDate,
                        acc.getMergedAccountId(),
                        i,
                        targetMonth,
                        dpdValueStr
                ));
            }
        }
    }

    private void handleScoreHistory(
            String consumerId,
            LocalDate fromDate,
            LocalDate toDate,
            List<Tradeline> allTradelines,
            List<Enquiry> allEnquiries,
            Map<String, DisputeState> disputes,
            List<OverrideState> overrides,
            BufferedWriter historyWriter
    ) throws IOException {
        // Enumerate all month ends from fromDate to toDate inclusive
        List<LocalDate> monthEnds = new ArrayList<>();
        YearMonth current = YearMonth.from(fromDate);
        YearMonth end = YearMonth.from(toDate);

        while (!current.isAfter(end)) {
            LocalDate monthEnd = current.atEndOfMonth();
            if (!monthEnd.isBefore(fromDate) && !monthEnd.isAfter(toDate)) {
                monthEnds.add(monthEnd);
            }
            current = current.plusMonths(1);
        }

        for (LocalDate scoreDate : monthEnds) {
            // Tradelines received on or before scoreDate
            List<Tradeline> eligibleTradelines = allTradelines.stream()
                    .filter(t -> t.consumerId().equalsIgnoreCase(consumerId))
                    .filter(t -> !t.receivedDate().isAfter(scoreDate))
                    .toList();

            LocalDateTime endOfScoreDate = scoreDate.atTime(23, 59, 59);
            List<Enquiry> eligibleEnquiries = allEnquiries.stream()
                    .filter(e -> e.consumerId().equalsIgnoreCase(consumerId))
                    .filter(e -> !e.enquiryTs().isAfter(endOfScoreDate))
                    .toList();

            List<CreditAccount> mergedAccounts = mergingEngine.mergeAccounts(eligibleTradelines, List.of());

            // Retroactive disputes and overrides known at time of SCORE_HISTORY execution
            for (CreditAccount account : mergedAccounts) {
                for (String accNo : account.getMemberAccountNos()) {
                    String key = consumerId.toUpperCase() + "|" + account.getLender().toUpperCase() + "|" + accNo.toUpperCase();
                    DisputeState ds = disputes.get(key);
                    if (ds != null && !ds.disputeDate().isAfter(scoreDate)) {
                        // Known resolution outcome applies retroactively
                        if ("REMOVED".equalsIgnoreCase(ds.outcome())) {
                            account.addEvent(AccountEvent.disputeResolvedRemoved(account.getMergedAccountId(), consumerId, ds.disputeDate(), ds.resolutionDate() != null ? ds.resolutionDate() : scoreDate));
                        } else if ("VALID".equalsIgnoreCase(ds.outcome())) {
                            account.addEvent(AccountEvent.disputeResolvedValid(account.getMergedAccountId(), consumerId, ds.disputeDate(), ds.resolutionDate() != null ? ds.resolutionDate() : scoreDate));
                        } else {
                            // Unresolved dispute
                            account.addEvent(AccountEvent.disputeRaised(account.getMergedAccountId(), consumerId, ds.disputeDate()));
                        }
                    }
                }

                // Overrides effective on or before scoreDate
                for (OverrideState os : overrides) {
                    if (os.consumerId().equalsIgnoreCase(consumerId) &&
                        os.lenderId().equalsIgnoreCase(account.getLender()) &&
                        (account.getMemberAccountNos().contains(os.accountNo()) || os.accountNo().equalsIgnoreCase(account.getMergedAccountId()))) {
                        if (!os.eventDate().isAfter(scoreDate)) {
                            account.addEvent(AccountEvent.override(account.getMergedAccountId(), consumerId, os.eventType(), os.eventDate()));
                            account.setCurrentStatus(AccountStatus.valueOf(os.eventType().name()));
                        }
                    }
                }
            }

            ScoreResult result = scoringEngine.calculateScore(
                    consumerId,
                    mergedAccounts,
                    eligibleEnquiries,
                    mergedAccounts.stream().flatMap(a -> a.getEvents().stream()).toList(),
                    scoreDate
            );

            String finalScoreStr = result.isThinFile() ? "NA" : String.valueOf(result.finalScore());

            // Write row to score_history.csv: consumer_id,score_date,final_score,rule_ids
            historyWriter.write(String.format("%s,%s,%s,%s\n",
                    consumerId,
                    scoreDate,
                    finalScoreStr,
                    String.join(";", result.appliedRuleIds())
            ));
        }
    }

    private void writeRejections(Path rejectionsPath, List<Rejection> rejections) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(rejectionsPath)) {
            writer.write("source_file,record_id,reason\n");
            for (Rejection r : rejections) {
                writer.write(String.format("%s,%s,%s\n", r.sourceFile(), r.recordId(), r.reason()));
            }
        }
    }
}
