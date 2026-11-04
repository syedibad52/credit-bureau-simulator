package com.creditbureau.simulator.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic CSV file parser that handles header detection, field splitting,
 * and basic structural validation.
 */
public class CsvParser {

    private static final Logger logger = LoggerFactory.getLogger(CsvParser.class);
    private static final String DELIMITER = ",";

    public ValidationResult<String[]> parse(Path filePath, int expectedColumns) {
        List<String[]> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (!Files.exists(filePath)) {
            errors.add("File not found: " + filePath);
            return new ValidationResult<>(rows, errors);
        }

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                errors.add("File is empty: " + filePath);
                return new ValidationResult<>(rows, errors);
            }

            String[] headerFields = headerLine.split(DELIMITER, -1);
            if (headerFields.length != expectedColumns) {
                logger.warn("Header column count ({}) does not match expected ({}). File: {}",
                        headerFields.length, expectedColumns, filePath);
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }

                String[] fields = line.split(DELIMITER, -1);
                if (fields.length != expectedColumns) {
                    errors.add(String.format("Line %d: Expected %d columns but found %d: '%s'",
                            lineNumber, expectedColumns, fields.length, line));
                    continue;
                }

                for (int i = 0; i < fields.length; i++) {
                    fields[i] = fields[i].trim();
                }

                rows.add(fields);
            }

            logger.info("Parsed {} data rows from {} ({} structural errors)",
                    rows.size(), filePath.getFileName(), errors.size());

        } catch (IOException e) {
            errors.add("Error reading file " + filePath + ": " + e.getMessage());
            logger.error("Failed to read CSV file: {}", filePath, e);
        }

        return new ValidationResult<>(rows, errors);
    }

    public List<Path> findFiles(Path directory, String filePrefix) {
        List<Path> result = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            logger.warn("Not a directory: {}", directory);
            return result;
        }

        try (var stream = Files.list(directory)) {
            stream.filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.startsWith(filePrefix.toLowerCase()) && name.endsWith(".csv");
                    })
                    .sorted()
                    .forEach(result::add);
        } catch (IOException e) {
            logger.error("Error listing directory: {}", directory, e);
        }

        return result;
    }
}

