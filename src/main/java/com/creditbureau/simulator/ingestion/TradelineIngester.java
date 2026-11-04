package com.creditbureau.simulator.ingestion;

import com.creditbureau.simulator.domain.enums.AccountStatus;
import com.creditbureau.simulator.domain.enums.AccountType;
import com.creditbureau.simulator.domain.model.Tradeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Ingests tradeline CSV files, validates each record, and produces
 * a list of valid Tradeline domain objects.
 */
public class TradelineIngester {

    private static final Logger logger = LoggerFactory.getLogger(TradelineIngester.class);
    private static final int EXPECTED_COLUMNS = 9;

    private final CsvParser csvParser = new CsvParser();

    public ValidationResult<Tradeline> ingestFromDirectory(Path dataDirectory) {
        List<Path> files = csvParser.findFiles(dataDirectory, "tradelines");
        if (files.isEmpty()) {
            logger.warn("No tradeline files found in: {}", dataDirectory);
            return new ValidationResult<>(List.of(), List.of("No tradeline files found in: " + dataDirectory));
        }

        List<Tradeline> allTradelines = new ArrayList<>();
        List<String> allErrors = new ArrayList<>();
        int globalFileOrderIndex = 0;

        for (Path file : files) {
            ValidationResult<Tradeline> result = ingestFile(file, globalFileOrderIndex);
            allTradelines.addAll(result.validRecords());
            allErrors.addAll(result.errors());
            globalFileOrderIndex += result.validCount() + result.errorCount();
        }

        logger.info("Total tradelines ingested: {} valid, {} errors",
                allTradelines.size(), allErrors.size());
        return new ValidationResult<>(allTradelines, allErrors);
    }

    public ValidationResult<Tradeline> ingestFile(Path filePath, int startingFileOrderIndex) {
        ValidationResult<String[]> csvResult = csvParser.parse(filePath, EXPECTED_COLUMNS);

        List<Tradeline> tradelines = new ArrayList<>();
        List<String> errors = new ArrayList<>(csvResult.errors());
        int fileOrderIndex = startingFileOrderIndex;

        for (String[] fields : csvResult.validRecords()) {
            fileOrderIndex++;
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
        }

        return new ValidationResult<>(tradelines, errors);
    }
}

