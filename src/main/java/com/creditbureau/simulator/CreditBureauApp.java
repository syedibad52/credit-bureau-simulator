package com.creditbureau.simulator;

import com.creditbureau.simulator.cli.CommandRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the Credit Bureau Score Simulation Engine (PS-022).
 * 
 * <p>Usage examples:
 * <pre>
 *   java -jar credit-bureau-simulator.jar run-all --data ./sample-data --consumer CUST-1001 --as-of 2026-10-31
 *   java -jar credit-bureau-simulator.jar generate-report --consumer CUST-1001 --as-of 2026-10-31
 *   java -jar credit-bureau-simulator.jar score-history --data ./sample-data --consumer CUST-1001
 * </pre>
 */
public class CreditBureauApp {

    private static final Logger logger = LoggerFactory.getLogger(CreditBureauApp.class);

    public static void main(String[] args) {
        logger.info("Credit Bureau Score Simulation Engine v1.0.0 starting...");

        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        try {
            CommandRouter router = new CommandRouter();
            int exitCode = router.route(args);
            System.exit(exitCode);
        } catch (Exception e) {
            logger.error("Fatal error: {}", e.getMessage(), e);
            System.err.println("ERROR: " + e.getMessage());
            System.exit(2);
        }
    }

    private static void printUsage() {
        System.out.println("""
            ╔══════════════════════════════════════════════════════════════╗
            ║     Credit Bureau Score Simulation Engine (PS-022) v1.0     ║
            ╚══════════════════════════════════════════════════════════════╝
            
            Usage: java -jar credit-bureau-simulator.jar <command> [options]
            
            Commands:
              ingest-tradelines   Ingest tradeline CSV files
              ingest-enquiries    Ingest enquiry CSV files
              ingest-events       Ingest account event CSV files
              build-profile       Build consumer credit profile
              calculate-score     Calculate credit score for a consumer
              generate-report     Generate full credit report
              score-history       Show score history for a consumer
              run-all             Run complete pipeline (ingest + score + report)
            
            Common Options:
              --data <dir>        Directory containing input CSV files
              --consumer <id>     Consumer ID (e.g., CUST-1001)
              --as-of <date>      As-of date in YYYY-MM-DD format
              --output <dir>      Output directory for reports (default: ./output)
              --help              Show this help message
            
            Examples:
              java -jar credit-bureau-simulator.jar run-all --data ./sample-data --consumer CUST-1001 --as-of 2026-10-31
              java -jar credit-bureau-simulator.jar generate-report --consumer CUST-1001 --as-of 2026-10-31
              java -jar credit-bureau-simulator.jar score-history --data ./sample-data --consumer CUST-1001
            """);
    }
}

