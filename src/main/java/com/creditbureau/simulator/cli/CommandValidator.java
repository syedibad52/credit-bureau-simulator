package com.creditbureau.simulator.cli;

import java.util.Set;

/**
 * Validates parsed command-line arguments for completeness and correctness.
 */
public class CommandValidator {

    private static final Set<String> VALID_COMMANDS = Set.of(
        "ingest-tradelines",
        "ingest-enquiries",
        "ingest-events",
        "build-profile",
        "calculate-score",
        "generate-report",
        "score-history",
        "run-all",
        "serve",
        "evaluate",
        "run-commands"
    );

    /**
     * Validate the given command arguments.
     *
     * @param args parsed arguments
     * @throws IllegalArgumentException if validation fails
     */
    public void validate(CommandArgs args) {
        if (!VALID_COMMANDS.contains(args.command())) {
            throw new IllegalArgumentException(
                "Unknown command: '" + args.command() + "'. Valid commands: " + VALID_COMMANDS);
        }

        // Commands that require a consumer ID
        Set<String> needsConsumer = Set.of(
            "build-profile", "calculate-score", "generate-report", "score-history"
        );
        if (needsConsumer.contains(args.command()) && args.consumerId() == null) {
            throw new IllegalArgumentException(
                "Command '" + args.command() + "' requires --consumer <id>");
        }

        // Commands that require an as-of date
        Set<String> needsAsOf = Set.of(
            "calculate-score", "generate-report"
        );
        if (needsAsOf.contains(args.command()) && args.asOfDate() == null) {
            throw new IllegalArgumentException(
                "Command '" + args.command() + "' requires --as-of <YYYY-MM-DD>");
        }
    }
}
