package com.creditbureau.simulator.cli;

import com.creditbureau.simulator.domain.model.AccountEvent;
import com.creditbureau.simulator.domain.model.CreditAccount;
import com.creditbureau.simulator.domain.model.CreditProfile;
import com.creditbureau.simulator.domain.model.Enquiry;
import com.creditbureau.simulator.domain.model.ScoreHistoryEntry;
import com.creditbureau.simulator.domain.model.ScoreResult;
import com.creditbureau.simulator.domain.model.Tradeline;
import com.creditbureau.simulator.ingestion.EnquiryIngester;
import com.creditbureau.simulator.ingestion.EventIngester;
import com.creditbureau.simulator.ingestion.TradelineIngester;
import com.creditbureau.simulator.ingestion.ValidationResult;
import com.creditbureau.simulator.persistence.DataStore;
import com.creditbureau.simulator.processing.AccountMergingEngine;
import com.creditbureau.simulator.processing.AsOfDateFilter;
import com.creditbureau.simulator.processing.ProfileBuilder;
import com.creditbureau.simulator.reporting.CreditReportGenerator;
import com.creditbureau.simulator.reporting.ReportFormatter;
import com.creditbureau.simulator.scoring.ScoreHistoryEngine;
import com.creditbureau.simulator.scoring.ScoringEngine;
import com.creditbureau.simulator.web.WebServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Routes CLI commands to the appropriate service components (Phase 13).
 */
public class CommandRouter {

    private static final Logger logger = LoggerFactory.getLogger(CommandRouter.class);

    private final CommandValidator validator = new CommandValidator();
    private final DataStore dataStore = new DataStore();
    private final TradelineIngester tradelineIngester = new TradelineIngester();
    private final EnquiryIngester enquiryIngester = new EnquiryIngester();
    private final EventIngester eventIngester = new EventIngester();
    private final AsOfDateFilter asOfFilter = new AsOfDateFilter();
    private final AccountMergingEngine mergingEngine = new AccountMergingEngine();
    private final ProfileBuilder profileBuilder = new ProfileBuilder();
    private final ScoringEngine scoringEngine = new ScoringEngine();
    private final ScoreHistoryEngine scoreHistoryEngine = new ScoreHistoryEngine();
    private final CreditReportGenerator reportGenerator = new CreditReportGenerator();

    public int route(String[] args) {
        CommandArgs commandArgs = CommandArgs.parse(args);
        validator.validate(commandArgs);

        logger.info("Executing command: {} for consumer: {} as-of: {}",
                commandArgs.command(), commandArgs.consumerId(), commandArgs.asOfDate());

        return switch (commandArgs.command()) {
            case "ingest-tradelines" -> handleIngestTradelines(commandArgs);
            case "ingest-enquiries" -> handleIngestEnquiries(commandArgs);
            case "ingest-events" -> handleIngestEvents(commandArgs);
            case "build-profile" -> handleBuildProfile(commandArgs);
            case "calculate-score" -> handleCalculateScore(commandArgs);
            case "generate-report" -> handleGenerateReport(commandArgs);
            case "score-history" -> handleScoreHistory(commandArgs);
            case "run-all" -> handleRunAll(commandArgs);
            case "serve" -> handleServe(commandArgs);
            default -> {
                System.err.println("Unknown command: " + commandArgs.command());
                yield 1;
            }
        };
    }

    private void ingestAll(Path dataDir) {
        ValidationResult<Tradeline> tRes = tradelineIngester.ingestFromDirectory(dataDir);
        dataStore.storeTradelines(tRes.validRecords());
        if (tRes.hasErrors()) {
            System.err.println("Tradeline ingestion notices: " + tRes.errorCount());
        }

        ValidationResult<Enquiry> enqRes = enquiryIngester.ingestFromDirectory(dataDir);
        dataStore.storeEnquiries(enqRes.validRecords());

        ValidationResult<AccountEvent> evRes = eventIngester.ingestFromDirectory(dataDir);
        dataStore.storeEvents(evRes.validRecords());
    }

    private int handleIngestTradelines(CommandArgs args) {
        Path path = Paths.get(args.dataDir());
        ValidationResult<Tradeline> res = tradelineIngester.ingestFromDirectory(path);
        System.out.printf("Successfully ingested %d tradeline records from %s%n", res.validCount(), path);
        if (res.hasErrors()) {
            System.out.printf("Encountered %d validation errors:%n", res.errorCount());
            res.errors().forEach(e -> System.out.println("  " + e));
        }
        return 0;
    }

    private int handleIngestEnquiries(CommandArgs args) {
        Path path = Paths.get(args.dataDir());
        ValidationResult<Enquiry> res = enquiryIngester.ingestFromDirectory(path);
        System.out.printf("Successfully ingested %d enquiry records from %s%n", res.validCount(), path);
        return 0;
    }

    private int handleIngestEvents(CommandArgs args) {
        Path path = Paths.get(args.dataDir());
        ValidationResult<AccountEvent> res = eventIngester.ingestFromDirectory(path);
        System.out.printf("Successfully ingested %d event records from %s%n", res.validCount(), path);
        return 0;
    }

    private int handleBuildProfile(CommandArgs args) {
        ingestAll(Paths.get(args.dataDir()));
        LocalDate asOfDate = args.asOfDate() != null ? args.asOfDate() : LocalDate.now();

        List<Tradeline> tradelines = asOfFilter.filterTradelines(dataStore.getTradelinesForConsumer(args.consumerId()), asOfDate);
        List<Enquiry> enquiries = asOfFilter.filterEnquiries(dataStore.getEnquiriesForConsumer(args.consumerId()), asOfDate);
        List<AccountEvent> events = asOfFilter.filterEvents(dataStore.getEventsForConsumer(args.consumerId()), asOfDate);

        List<CreditAccount> accounts = mergingEngine.mergeAccounts(tradelines, events);
        CreditProfile profile = profileBuilder.buildProfile(args.consumerId(), accounts, enquiries, asOfDate);

        System.out.println(ReportFormatter.header("CONSUMER CREDIT PROFILE"));
        System.out.printf("Consumer ID:              %s%n", profile.getConsumerId());
        System.out.printf("As-Of Date:               %s%n", profile.getAsOfDate());
        System.out.printf("Merged Accounts:          %d%n", profile.getMergedAccountCount());
        System.out.printf("Active Accounts:          %d%n", profile.getActiveAccountCount());
        System.out.printf("Closed Accounts:          %d%n", profile.getClosedAccountCount());
        System.out.printf("Revolving Utilisation:    %.2f%%%n", profile.getRevolvingUtilisation().doubleValue());
        System.out.printf("Mixed Credit Portfolio:   %s%n", profile.isHasMixedCredit() ? "Yes" : "No");
        System.out.printf("Hard Enquiries:           %d%n", profile.getHardEnquiryCount());
        return 0;
    }

    private int handleCalculateScore(CommandArgs args) {
        ingestAll(Paths.get(args.dataDir()));
        ScoreResult res = scoreHistoryEngine.computeScoreAt(
                args.consumerId(),
                dataStore.getTradelinesForConsumer(args.consumerId()),
                dataStore.getEnquiriesForConsumer(args.consumerId()),
                dataStore.getEventsForConsumer(args.consumerId()),
                args.asOfDate()
        );

        if (res.isThinFile()) {
            System.out.printf("Consumer: %s | As-Of: %s | Score: NA (%s)%n",
                    args.consumerId(), args.asOfDate(), res.thinFileReason());
        } else {
            System.out.printf("Consumer: %s | As-Of: %s | Final Score: %d (Pre-Override: %d)%n",
                    args.consumerId(), args.asOfDate(), res.finalScore(), res.preOverrideScore());
        }
        return 0;
    }

    private int handleGenerateReport(CommandArgs args) {
        return executePipeline(args);
    }

    private int handleRunAll(CommandArgs args) {
        return executePipeline(args);
    }

    private int executePipeline(CommandArgs args) {
        ingestAll(Paths.get(args.dataDir()));

        List<Tradeline> rawTradelines = dataStore.getTradelinesForConsumer(args.consumerId());
        List<Enquiry> rawEnquiries = dataStore.getEnquiriesForConsumer(args.consumerId());
        List<AccountEvent> rawEvents = dataStore.getEventsForConsumer(args.consumerId());

        LocalDate asOfDate = args.asOfDate() != null ? args.asOfDate() : LocalDate.now();

        List<Tradeline> filteredTradelines = asOfFilter.filterTradelines(rawTradelines, asOfDate);
        List<Enquiry> filteredEnquiries = asOfFilter.filterEnquiries(rawEnquiries, asOfDate);
        List<AccountEvent> filteredEvents = asOfFilter.filterEvents(rawEvents, asOfDate);

        List<CreditAccount> accounts = mergingEngine.mergeAccounts(filteredTradelines, filteredEvents);
        CreditProfile profile = profileBuilder.buildProfile(args.consumerId(), accounts, filteredEnquiries, asOfDate);
        ScoreResult scoreResult = scoringEngine.calculateScore(args.consumerId(), accounts, filteredEnquiries, filteredEvents, asOfDate);

        String report = reportGenerator.generateReport(
                args.consumerId(), asOfDate, profile, accounts, filteredEnquiries, filteredEvents, scoreResult
        );

        System.out.println(report);
        return 0;
    }

    private int handleScoreHistory(CommandArgs args) {
        ingestAll(Paths.get(args.dataDir()));
        List<Tradeline> rawTradelines = dataStore.getTradelinesForConsumer(args.consumerId());
        List<Enquiry> rawEnquiries = dataStore.getEnquiriesForConsumer(args.consumerId());
        List<AccountEvent> rawEvents = dataStore.getEventsForConsumer(args.consumerId());

        List<LocalDate> dates = new ArrayList<>();
        for (Tradeline t : rawTradelines) {
            dates.add(t.submissionDate());
        }
        if (args.asOfDate() != null) {
            dates.add(args.asOfDate());
        }

        List<ScoreHistoryEntry> history = scoreHistoryEngine.computeHistory(
                args.consumerId(), rawTradelines, rawEnquiries, rawEvents, dates
        );

        System.out.println(ReportFormatter.header("SCORE HISTORY: " + args.consumerId()));
        System.out.printf("%-15s %-10s %-15s%n", "As-Of Date", "Score", "Thin File");
        System.out.println(ReportFormatter.divider('-', 45));
        for (ScoreHistoryEntry entry : history) {
            System.out.printf("%-15s %-10s %-15s%n",
                    entry.asOfDate(),
                    entry.isThinFile() ? "NA" : String.valueOf(entry.score()),
                    entry.isThinFile() ? "Yes" : "No");
        }
        return 0;
    }

    private int handleServe(CommandArgs args) {
        int port = 8080;
        try {
            WebServer server = new WebServer(port, Paths.get(args.dataDir()));
            server.start();
            // Keep main thread alive
            Thread.currentThread().join();
        } catch (IOException | InterruptedException e) {
            logger.error("Web server error: {}", e.getMessage(), e);
            System.err.println("Web server failed to start: " + e.getMessage());
            return 1;
        }
        return 0;
    }
}
