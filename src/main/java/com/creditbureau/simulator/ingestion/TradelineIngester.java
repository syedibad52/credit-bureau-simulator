package com.creditbureau.simulator.ingestion;

import com.creditbureau.simulator.domain.enums.AccountStatus;
import com.creditbureau.simulator.domain.enums.AccountType;
import com.creditbureau.simulator.domain.model.Rejection;
import com.creditbureau.simulator.domain.model.Tradeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Ingests tradeline CSV files, validates each record, and produces
 * a list of valid Tradeline domain objects and rejections.
 */
public class TradelineIngester {

    private static final Logger logger = LoggerFactory.getLogger(TradelineIngester.class);
    private final CsvParser csvParser = new CsvParser();

    public ValidationResult<Tradeline> ingestFromDirectory(Path dataDirectory) {
        List<Path> files = csvParser.findFiles(dataDirectory, "tradeline");
        if (files.isEmpty()) {
            logger.warn("No tradeline files found in: {}", dataDirectory);
            return new ValidationResult<>(List.of(), List.of("No tradeline files found in: " + dataDirectory));
        }

        List<Tradeline> allTradelines = new ArrayList<>();
        List<String> allErrors = new ArrayList<>();
        List<Rejection> allRejections = new ArrayList<>();
        int globalFileOrderIndex = 0;

        for (Path file : files) {
            ValidationResult<Tradeline> result = ingestFile(file, globalFileOrderIndex);
            allTradelines.addAll(result.validRecords());
            allErrors.addAll(result.errors());
            allRejections.addAll(result.rejections());
            globalFileOrderIndex += result.validCount() + result.errorCount();
        }

        logger.info("Total tradelines ingested: {} valid, {} errors, {} rejections",
                allTradelines.size(), allErrors.size(), allRejections.size());
        return new ValidationResult<>(allTradelines, allErrors, allRejections);
    }

    public ValidationResult<Tradeline> ingestFile(Path filePath, int startingFileOrderIndex) {
        ValidationResult<String[]> csvResult = csvParser.parse(filePath, 0);

        List<Tradeline> tradelines = new ArrayList<>();
        List<String> errors = new ArrayList<>(csvResult.errors());
        List<Rejection> rejections = new ArrayList<>();
        int fileOrderIndex = startingFileOrderIndex;

        String sourceFileName = filePath.getFileName().toString();

        for (String[] fields : csvResult.validRecords()) {
            fileOrderIndex++;

            if (fields.length == 14) {
                // Official PS-022 format
                // submission_id,file_id,received_date,report_month,lender_id,consumer_id,account_no,account_type,open_date,sanctioned_amount,balance,dpd,status,closed_date
                String submissionId = fields[0];
                String fileId = fields[1];
                String receivedDateStr = fields[2];
                String reportMonthStr = fields[3];
                String lenderId = fields[4];
                String consumerId = fields[5];
                String accountNo = fields[6];
                String rawAccountType = fields[7];
                String openDateStr = fields[8];
                String sanctionedStr = fields[9];
                String balanceStr = fields[10];
                String dpdStr = fields[11];
                String statusStr = fields[12];
                String closedDateStr = fields[13];

                // Validate Account Type
                AccountType accountType = null;
                try {
                    accountType = AccountType.valueOf(rawAccountType.trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    rejections.add(new Rejection(sourceFileName, submissionId, "Invalid account type: " + rawAccountType));
                    errors.add(String.format("Row %s: Invalid account type '%s'", submissionId, rawAccountType));
                    continue;
                }

                FieldValidator v = new FieldValidator(fileOrderIndex);
                LocalDate receivedDate = v.requireDate(receivedDateStr, "received_date");
                LocalDate openDate = v.requireDate(openDateStr, "open_date");
                BigDecimal sanctionedAmount = v.requireBigDecimal(sanctionedStr, "sanctioned_amount");
                BigDecimal balance = v.requireBigDecimal(balanceStr, "balance");
                int dpd = v.requireNonNegativeInt(dpdStr, "dpd");
                AccountStatus status = v.requireEnum(statusStr, "status", AccountStatus.class);

                YearMonth reportMonth = null;
                try {
                    reportMonth = YearMonth.parse(reportMonthStr.trim());
                } catch (Exception e) {
                    v.addCustomError("Invalid report_month: " + reportMonthStr);
                }

                LocalDate closedDate = null;
                if (closedDateStr != null && !closedDateStr.isBlank()) {
                    try {
                        closedDate = LocalDate.parse(closedDateStr.trim());
                    } catch (Exception e) {
                        v.addCustomError("Invalid closed_date: " + closedDateStr);
                    }
                }

                if (v.hasErrors()) {
                    rejections.add(new Rejection(sourceFileName, submissionId, String.join("; ", v.getErrors())));
                    errors.addAll(v.getErrors());
                    continue;
                }

                tradelines.add(new Tradeline(
                        submissionId, fileId, receivedDate, reportMonth,
                        lenderId, consumerId, accountNo, accountType,
                        openDate, sanctionedAmount, balance, dpd,
                        status, closedDate, fileOrderIndex
                ));

            } else if (fields.length == 9) {
                // Legacy 9-field format
                FieldValidator v = new FieldValidator(fileOrderIndex);
                String consumerId = v.requireString(fields[0], "consumer_id");
                String lender = v.requireString(fields[1], "lender");
                AccountType accountType = v.requireEnum(fields[2], "account_type", AccountType.class);
                LocalDate openDate = v.requireDate(fields[3], "open_date");
                BigDecimal creditLimit = v.requireBigDecimal(fields[4], "credit_limit");
                BigDecimal outstandingBalance = v.requireBigDecimal(fields[5], "outstanding_balance");
                int currentDPD = v.requireNonNegativeInt(fields[6], "current_dpd");
                AccountStatus status = v.requireEnum(fields[7], "account_status", AccountStatus.class);
                LocalDate submissionDate = v.requireDate(fields[8], "submission_date");

                if (v.hasErrors()) {
                    errors.addAll(v.getErrors());
                    continue;
                }

                tradelines.add(new Tradeline(
                        consumerId, lender, accountType, openDate,
                        creditLimit, outstandingBalance, currentDPD,
                        status, submissionDate, fileOrderIndex
                ));
            } else {
                errors.add(String.format("Row %d: Unexpected column count %d in file %s", fileOrderIndex, fields.length, sourceFileName));
            }
        }

        return new ValidationResult<>(tradelines, errors, rejections);
    }
}
