package com.creditbureau.simulator.ingestion;

import com.creditbureau.simulator.domain.enums.EnquiryType;
import com.creditbureau.simulator.domain.model.Enquiry;
import com.creditbureau.simulator.domain.model.Rejection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ingests enquiry CSV files and produces validated Enquiry domain objects and rejections.
 */
public class EnquiryIngester {

    private static final Logger logger = LoggerFactory.getLogger(EnquiryIngester.class);
    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CsvParser csvParser = new CsvParser();

    public ValidationResult<Enquiry> ingestFromDirectory(Path dataDirectory) {
        List<Path> files = csvParser.findFiles(dataDirectory, "enquir");
        if (files.isEmpty()) {
            logger.warn("No enquiry files found in: {}", dataDirectory);
            return new ValidationResult<>(List.of(), List.of("No enquiry files found in: " + dataDirectory));
        }

        List<Enquiry> allEnquiries = new ArrayList<>();
        List<String> allErrors = new ArrayList<>();
        List<Rejection> allRejections = new ArrayList<>();
        int globalFileOrderIndex = 0;

        for (Path file : files) {
            ValidationResult<Enquiry> result = ingestFile(file, globalFileOrderIndex);
            allEnquiries.addAll(result.validRecords());
            allErrors.addAll(result.errors());
            allRejections.addAll(result.rejections());
            globalFileOrderIndex += result.validCount() + result.errorCount();
        }

        logger.info("Total enquiries ingested: {} valid, {} errors, {} rejections",
                allEnquiries.size(), allErrors.size(), allRejections.size());
        return new ValidationResult<>(allEnquiries, allErrors, allRejections);
    }

    public ValidationResult<Enquiry> ingestFile(Path filePath, int startingFileOrderIndex) {
        ValidationResult<String[]> csvResult = csvParser.parse(filePath, 0);

        List<Enquiry> enquiries = new ArrayList<>();
        List<String> errors = new ArrayList<>(csvResult.errors());
        List<Rejection> rejections = new ArrayList<>();
        Set<String> seenEnquiryIds = new HashSet<>();
        int fileOrderIndex = startingFileOrderIndex;

        String sourceFileName = filePath.getFileName().toString();

        for (String[] fields : csvResult.validRecords()) {
            fileOrderIndex++;

            if (fields.length == 6) {
                // Official PS-022 format: enquiry_id,consumer_id,lender_id,enquiry_ts,purpose,enquiry_type
                String enquiryId = fields[0];
                String consumerId = fields[1];
                String lenderId = fields[2];
                String enquiryTsStr = fields[3];
                String purpose = fields[4];
                String enquiryTypeStr = fields[5];

                // Duplicate enquiry delivery check
                if (seenEnquiryIds.contains(enquiryId)) {
                    rejections.add(new Rejection(sourceFileName, enquiryId, "Duplicate enquiry record"));
                    errors.add(String.format("Row %d: Duplicate enquiry delivery for ID '%s'", fileOrderIndex, enquiryId));
                    continue;
                }

                FieldValidator v = new FieldValidator(fileOrderIndex);
                v.requireString(consumerId, "consumer_id");
                v.requireString(lenderId, "lender_id");
                EnquiryType type = v.requireEnum(enquiryTypeStr, "enquiry_type", EnquiryType.class);

                LocalDateTime enquiryTs = null;
                try {
                    enquiryTs = LocalDateTime.parse(enquiryTsStr.trim(), TS_FORMATTER);
                } catch (Exception e) {
                    try {
                        enquiryTs = LocalDate.parse(enquiryTsStr.trim()).atStartOfDay();
                    } catch (Exception ex) {
                        v.addCustomError("Invalid enquiry_ts format: " + enquiryTsStr);
                    }
                }

                if (v.hasErrors()) {
                    rejections.add(new Rejection(sourceFileName, enquiryId, String.join("; ", v.getErrors())));
                    errors.addAll(v.getErrors());
                    continue;
                }

                seenEnquiryIds.add(enquiryId);
                enquiries.add(new Enquiry(enquiryId, consumerId, lenderId, enquiryTs, purpose, type, fileOrderIndex));

            } else if (fields.length == 5) {
                // Legacy 5-column format: consumer_id,lender,purpose,enquiry_type,enquiry_date
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
            } else {
                errors.add(String.format("Row %d: Unexpected column count %d in file %s", fileOrderIndex, fields.length, sourceFileName));
            }
        }

        return new ValidationResult<>(enquiries, errors, rejections);
    }
}
