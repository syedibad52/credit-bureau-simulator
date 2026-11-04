package com.creditbureau.simulator.ingestion;

import com.creditbureau.simulator.domain.enums.DisputeOutcome;
import com.creditbureau.simulator.domain.enums.EventType;
import com.creditbureau.simulator.domain.model.AccountEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Ingests account event CSV files and produces validated AccountEvent domain objects.
 */
public class EventIngester {

    private static final Logger logger = LoggerFactory.getLogger(EventIngester.class);
    private static final int EXPECTED_COLUMNS = 7;

    private final CsvParser csvParser = new CsvParser();

    public ValidationResult<AccountEvent> ingestFromDirectory(Path dataDirectory) {
        List<Path> files = csvParser.findFiles(dataDirectory, "events");
        if (files.isEmpty()) {
            logger.warn("No event files found in: {}", dataDirectory);
            return new ValidationResult<>(List.of(), List.of());
        }

        List<AccountEvent> allEvents = new ArrayList<>();
        List<String> allErrors = new ArrayList<>();
        int globalFileOrderIndex = 0;

        for (Path file : files) {
            ValidationResult<AccountEvent> result = ingestFile(file, globalFileOrderIndex);
            allEvents.addAll(result.validRecords());
            allErrors.addAll(result.errors());
            globalFileOrderIndex += result.validCount() + result.errorCount();
        }

        logger.info("Total events ingested: {} valid, {} errors",
                allEvents.size(), allErrors.size());
        return new ValidationResult<>(allEvents, allErrors);
    }

    public ValidationResult<AccountEvent> ingestFile(Path filePath, int startingFileOrderIndex) {
        ValidationResult<String[]> csvResult = csvParser.parse(filePath, EXPECTED_COLUMNS);

        List<AccountEvent> events = new ArrayList<>();
        List<String> errors = new ArrayList<>(csvResult.errors());
        int fileOrderIndex = startingFileOrderIndex;

        for (String[] fields : csvResult.validRecords()) {
            fileOrderIndex++;
            FieldValidator v = new FieldValidator(fileOrderIndex);

            String consumerId = v.requireString(fields[0], "consumer_id");
            String accountId = fields[1] != null ? fields[1].trim() : null;
            EventType eventType = v.requireEnum(fields[2], "event_type", EventType.class);
            LocalDate eventDate = v.requireDate(fields[3], "event_date");
            LocalDate resolutionDate = v.optionalDate(fields[4], "resolution_date");
            DisputeOutcome disputeOutcome = v.optionalEnum(fields[5], "dispute_outcome", DisputeOutcome.class);
            String details = fields[6] != null ? fields[6].trim() : "";

            if (v.hasErrors()) {
                errors.addAll(v.getErrors());
                continue;
            }

            if (eventType == EventType.DISPUTE_RESOLVED && disputeOutcome == null) {
                errors.add(String.format("Line %d: DISPUTE_RESOLVED event must have a dispute_outcome", fileOrderIndex));
                continue;
            }

            events.add(new AccountEvent(consumerId, accountId, eventType, eventDate,
                    resolutionDate, disputeOutcome, details));
        }

        return new ValidationResult<>(events, errors);
    }
}

