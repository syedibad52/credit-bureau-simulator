package com.creditbureau.simulator.integration;

import com.creditbureau.simulator.cli.CommandRouter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class FullWorkflowTest {

    private final CommandRouter router = new CommandRouter();
    private final String sampleDataDir = Paths.get("test-data").toAbsolutePath().toString();

    @Test
    @DisplayName("Run All: CUST-1001 Prime consumer report generation")
    void testRunAllPrimeConsumer() {
        String[] args = {
                "run-all",
                "--data", sampleDataDir,
                "--consumer", "CUST-1001",
                "--as-of", "2026-10-31"
        };

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            int exitCode = router.route(args);
            assertEquals(0, exitCode);
            String output = outContent.toString();

            assertTrue(output.contains("CREDIT BUREAU SIMULATION REPORT"));
            assertTrue(output.contains("Consumer ID:    CUST-1001"));
            assertTrue(output.contains("Total Merged Accounts:    3"));
            assertTrue(output.contains("FINAL CREDIT SCORE:"));
            assertTrue(output.contains("Payment History:"));
            assertTrue(output.contains("Credit Utilisation:"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Run All: CUST-1002 Delinquent consumer override 650 cap")
    void testRunAllDelinquentConsumer() {
        String[] args = {
                "run-all",
                "--data", sampleDataDir,
                "--consumer", "CUST-1002",
                "--as-of", "2026-10-31"
        };

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            int exitCode = router.route(args);
            assertEquals(0, exitCode);
            String output = outContent.toString();

            assertTrue(output.contains("FINAL CREDIT SCORE:     650"), "Score should be capped at 650 by BR-36 override");
            assertTrue(output.contains("BR-36"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Run All: CUST-1003 Thin file consumer")
    void testRunAllThinFileConsumer() {
        String[] args = {
                "run-all",
                "--data", sampleDataDir,
                "--consumer", "CUST-1003",
                "--as-of", "2026-10-31"
        };

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            int exitCode = router.route(args);
            assertEquals(0, exitCode);
            String output = outContent.toString();

            assertTrue(output.contains("THIN FILE (NA)"));
            assertTrue(output.contains("BR-04"));
        } finally {
            System.setOut(originalOut);
        }
    }
}

