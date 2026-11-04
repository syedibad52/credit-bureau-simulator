package com.creditbureau.simulator.cli;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * Parsed and validated command-line arguments.
 * 
 * @param command    The command to execute (e.g., "run-all", "generate-report")
 * @param dataDir    Path to the directory containing input CSV files
 * @param consumerId Target consumer ID
 * @param asOfDate   The as-of date for score calculation
 * @param outputDir  Output directory for reports
 */
public record CommandArgs(
    String command,
    String dataDir,
    String consumerId,
    LocalDate asOfDate,
    String outputDir
) {
    /**
     * Parse raw command-line arguments into a structured CommandArgs record.
     *
     * @param args raw CLI arguments
     * @return parsed CommandArgs
     * @throws IllegalArgumentException if arguments are invalid
     */
    public static CommandArgs parse(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("No command specified");
        }

        String command = args[0].toLowerCase();
        Map<String, String> options = new HashMap<>();

        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--") && i + 1 < args.length) {
                String key = args[i].substring(2).toLowerCase();
                String value = args[++i];
                options.put(key, value);
            } else if (args[i].equals("--help")) {
                options.put("help", "true");
            } else {
                throw new IllegalArgumentException("Unexpected argument: " + args[i]);
            }
        }

        String dataDir = options.getOrDefault("data", "./sample-data");
        String consumerId = options.get("consumer");
        String outputDir = options.getOrDefault("output", "./output");

        LocalDate asOfDate = null;
        String asOfStr = options.get("as-of");
        if (asOfStr != null) {
            try {
                asOfDate = LocalDate.parse(asOfStr);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(
                    "Invalid as-of date '" + asOfStr + "'. Expected format: YYYY-MM-DD");
            }
        }

        return new CommandArgs(command, dataDir, consumerId, asOfDate, outputDir);
    }
}

