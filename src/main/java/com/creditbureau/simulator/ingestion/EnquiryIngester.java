package com.creditbureau.simulator.ingestion;

import com.creditbureau.simulator.domain.enums.EnquiryType;
import com.creditbureau.simulator.domain.model.Enquiry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Ingests enquiry CSV files and produces validated Enquiry domain objects.
 */
public class EnquiryIngester {

    private static final Logger logger = LoggerFactory.getLogger(EnquiryIngester.class);
    private static final int EXPECTED_COLUMNS = 5;

    private final CsvParser csvParser = new CsvParser();

    public ValidationResult<Enquiry> ingestFromDirectory(Path dataDirectory) {
        List<Path> files = csvParser.findFiles(dataDirectory, "enquiries");
        if (files.isEmpty()) {
            logger.warn("No enquiry files found in: {}", dataDirectory);
            return new ValidationResult<>(List.of(), List.of("No enquiry files found in: " + dataDirectory));
        }

        List<Enquiry> allEnquiries = new ArrayList<>();
        List<String> allErrors = new ArrayList<>();
        int globalFileOrderIndex = 0;

        for (Path file : files) {
            ValidationResult<Enquiry> result = ingestFile(file, globalFileOrderIndex);
            allEnquiries.addAll(result.validRecords());
            allErrors.addAll(result.errors());
            globalFileOrderIndex += result.validCount() + result.errorCount();
        }

        logger.info("Total enquiries ingested: {} valid, {} errors",
                allEnquiries.size(), allErrors.size());
        return new ValidationResult<>(allEnquiries, allErrors);
    }

    public ValidationResult<Enquiry> ingestFile(Path filePath, int startingFileOrderIndex) {
        ValidationResult<String[]> csvResult = csvParser.parse(filePath, EXPECTED_COLUMNS);

        List<Enquiry> enquiries = new ArrayList<>();
        List<String> errors = new ArrayList<>(csvResult.errors());
        int fileOrderIndex = startingFileOrderIndex;

        for (String[] fields : csvResult.validRecords()) {
            fileOrderIndex++;
            FieldValidator v = new FieldValidator(fileOrderIndex);

            String consumerId = v.requireString(fields[0], "consumer_id");
            String lender = v.requireString(fields[1], "lender");
            String purpose = v.requireString(fields[2], "purpose");
            EnquiryType type = v.requireEnum(fields[3], "enquiry_type", EnquiryType.class);
            LocalDate enquiryDate = v.requireDate(fields[4], "enquiry_date");

            if (v.hasErrors()) {
                errors.addAll(v.getErrors());
                continue;
            }

            enquiries.add(new Enquiry(consumerId, lender, purpose, type, enquiryDate, fileOrderIndex));
        }

        return new ValidationResult<>(enquiries, errors);
    }
}

