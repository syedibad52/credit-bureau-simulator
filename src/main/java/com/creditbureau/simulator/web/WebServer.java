package com.creditbureau.simulator.web;

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
import com.creditbureau.simulator.persistence.DataStore;
import com.creditbureau.simulator.processing.AccountMergingEngine;
import com.creditbureau.simulator.processing.AsOfDateFilter;
import com.creditbureau.simulator.processing.DPDHistoryEngine;
import com.creditbureau.simulator.processing.ProfileBuilder;
import com.creditbureau.simulator.scoring.ScoreHistoryEngine;
import com.creditbureau.simulator.scoring.ScoringEngine;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Embedded lightweight HTTP server serving a rich Credit Bureau Web Dashboard on localhost.
 */
public class WebServer {

    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);

    private final int port;
    private final Path dataDir;
    private final DataStore dataStore = new DataStore();
    private final TradelineIngester tradelineIngester = new TradelineIngester();
    private final EnquiryIngester enquiryIngester = new EnquiryIngester();
    private final EventIngester eventIngester = new EventIngester();
    private final AsOfDateFilter asOfFilter = new AsOfDateFilter();
    private final AccountMergingEngine mergingEngine = new AccountMergingEngine();
    private final ProfileBuilder profileBuilder = new ProfileBuilder();
    private final ScoringEngine scoringEngine = new ScoringEngine();
    private final ScoreHistoryEngine scoreHistoryEngine = new ScoreHistoryEngine();
    private final DPDHistoryEngine dpdEngine = new DPDHistoryEngine();

    public WebServer(int port, Path dataDir) {
        this.port = port;
        this.dataDir = dataDir;
        loadData();
    }

    private void loadData() {
        dataStore.clear();
        dataStore.storeTradelines(tradelineIngester.ingestFromDirectory(dataDir).validRecords());
        dataStore.storeEnquiries(enquiryIngester.ingestFromDirectory(dataDir).validRecords());
        dataStore.storeEvents(eventIngester.ingestFromDirectory(dataDir).validRecords());
        logger.info("Loaded {} tradelines, {} enquiries, {} events into WebServer",
                dataStore.getTradelineCount(), dataStore.getEnquiryCount(), dataStore.getEventCount());
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", this::handleRoot);
        server.createContext("/api/consumers", this::handleConsumers);
        server.createContext("/api/report", this::handleReport);
        server.createContext("/api/history", this::handleHistory);

        server.setExecutor(null); // Default executor
        server.start();

        System.out.println("========================================================================");
        System.out.printf("  🚀 CREDIT BUREAU DASHBOARD RUNNING AT: http://localhost:%d%n", port);
        System.out.println("========================================================================");
        System.out.printf("  - Open http://localhost:%d in your web browser%n", port);
        System.out.println("  - Interactive credit score calculations, factor breakdowns & DPD grids");
        System.out.println("  - Press Ctrl+C in terminal to stop the server\n");
    }

    private void handleRoot(HttpExchange exchange) throws IOException {
        String html = getDashboardHtml();
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleConsumers(HttpExchange exchange) throws IOException {
        loadData();
        List<String> consumerIds = dataStore.getTradelinesForConsumer("").isEmpty() ?
                // scan all
                dataStore.getAllConsumerIds() : List.of();

        // Get distinct consumer IDs from tradelines
        List<String> distinct = dataStore.getTradelinesForConsumer("").isEmpty() ?
                tradelineIngester.ingestFromDirectory(dataDir).validRecords().stream()
                        .map(Tradeline::consumerId)
                        .distinct()
                        .sorted()
                        .toList() : List.of();

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < distinct.size(); i++) {
            json.append("\"").append(distinct.get(i)).append("\"");
            if (i < distinct.size() - 1) json.append(",");
        }
        json.append("]");

        sendJsonResponse(exchange, json.toString());
    }

    private void handleReport(HttpExchange exchange) throws IOException {
        loadData();
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String consumerId = params.getOrDefault("consumer", "CON-001");
        String asOfStr = params.getOrDefault("asOf", "2026-10-31");
        LocalDate asOfDate = LocalDate.parse(asOfStr);

        List<Tradeline> rawTradelines = dataStore.getTradelinesForConsumer(consumerId);
        List<Enquiry> rawEnquiries = dataStore.getEnquiriesForConsumer(consumerId);
        List<AccountEvent> rawEvents = dataStore.getEventsForConsumer(consumerId);

        List<Tradeline> filteredTradelines = asOfFilter.filterTradelines(rawTradelines, asOfDate);
        List<Enquiry> filteredEnquiries = asOfFilter.filterEnquiries(rawEnquiries, asOfDate);
        List<AccountEvent> filteredEvents = asOfFilter.filterEvents(rawEvents, asOfDate);

        List<CreditAccount> accounts = mergingEngine.mergeAccounts(filteredTradelines, filteredEvents);
        CreditProfile profile = profileBuilder.buildProfile(consumerId, accounts, filteredEnquiries, asOfDate);
        ScoreResult res = scoringEngine.calculateScore(consumerId, accounts, filteredEnquiries, filteredEvents, asOfDate);

        StringBuilder json = new StringBuilder("{");
        json.append("\"consumerId\":\"").append(consumerId).append("\",");
        json.append("\"asOfDate\":\"").append(asOfDate).append("\",");
        json.append("\"isThinFile\":").append(res.isThinFile()).append(",");
        json.append("\"thinFileReason\":").append(res.thinFileReason() != null ? "\"" + escapeJson(res.thinFileReason()) + "\"" : "null").append(",");
        json.append("\"baseScore\":").append(res.baseScore()).append(",");
        json.append("\"finalScore\":").append(res.finalScore()).append(",");
        json.append("\"preOverrideScore\":").append(res.preOverrideScore()).append(",");

        // Profile summary
        json.append("\"profile\":{");
        json.append("\"mergedAccounts\":").append(profile.getMergedAccountCount()).append(",");
        json.append("\"activeAccounts\":").append(profile.getActiveAccountCount()).append(",");
        json.append("\"closedAccounts\":").append(profile.getClosedAccountCount()).append(",");
        json.append("\"revolvingLimit\":").append(profile.getTotalRevolvingLimit()).append(",");
        json.append("\"revolvingBalance\":").append(profile.getTotalRevolvingBalance()).append(",");
        json.append("\"revolvingUtilisation\":").append(profile.getRevolvingUtilisation()).append(",");
        json.append("\"oldestAgeYears\":").append(profile.getOldestAccountAge().getYears()).append(",");
        json.append("\"oldestAgeMonths\":").append(profile.getOldestAccountAge().getMonths()).append(",");
        json.append("\"oldestOpenDate\":").append(profile.getOldestAccountOpenDate() != null ? "\"" + profile.getOldestAccountOpenDate() + "\"" : "null").append(",");
        json.append("\"hasMixedCredit\":").append(profile.isHasMixedCredit()).append(",");
        json.append("\"hardEnquiries\":").append(profile.getHardEnquiryCount()).append(",");
        json.append("\"softEnquiries\":").append(profile.getSoftEnquiryCount());
        json.append("},");

        // Factors
        json.append("\"factors\":[");
        json.append(formatFactorJson(res.paymentHistory())).append(",");
        json.append(formatFactorJson(res.utilisation())).append(",");
        json.append(formatFactorJson(res.creditAge())).append(",");
        json.append(formatFactorJson(res.creditMix())).append(",");
        json.append(formatFactorJson(res.enquiries()));
        json.append("],");

        // Accounts list with DPD grid
        json.append("\"accounts\":[");
        for (int i = 0; i < accounts.size(); i++) {
            CreditAccount acc = accounts.get(i);
            json.append("{");
            json.append("\"accountId\":\"").append(acc.getAccountId()).append("\",");
            json.append("\"lender\":\"").append(acc.getLender()).append("\",");
            json.append("\"type\":\"").append(acc.getAccountType()).append("\",");
            json.append("\"openDate\":\"").append(acc.getOpenDate()).append("\",");
            json.append("\"limit\":").append(acc.getCreditLimit()).append(",");
            json.append("\"balance\":").append(acc.getOutstandingBalance()).append(",");
            json.append("\"status\":\"").append(acc.getCurrentStatus()).append("\",");

            // 36-month grid
            List<DPDHistoryEngine.MonthlyDPDEntry> grid = dpdEngine.build36MonthGrid(acc, asOfDate);
            json.append("\"dpdGrid\":[");
            for (int j = 0; j < grid.size(); j++) {
                DPDHistoryEngine.MonthlyDPDEntry e = grid.get(j);
                json.append("{");
                json.append("\"month\":").append(e.monthIndex()).append(",");
                json.append("\"yearMonth\":\"").append(e.yearMonth()).append("\",");
                json.append("\"dpd\":").append(e.dpdValue() != null ? e.dpdValue() : "null").append(",");
                json.append("\"category\":\"").append(e.category()).append("\"");
                json.append("}");
                if (j < grid.size() - 1) json.append(",");
            }
            json.append("]");

            json.append("}");
            if (i < accounts.size() - 1) json.append(",");
        }
        json.append("],");

        // Applied overrides
        json.append("\"overrides\":[");
        for (int i = 0; i < res.appliedOverrides().size(); i++) {
            var ov = res.appliedOverrides().get(i);
            json.append("{");
            json.append("\"ruleId\":\"").append(ov.ruleId()).append("\",");
            json.append("\"name\":\"").append(escapeJson(ov.overrideName())).append("\",");
            json.append("\"cap\":").append(ov.capValue()).append(",");
            json.append("\"applied\":").append(ov.applied()).append(",");
            json.append("\"reason\":\"").append(escapeJson(ov.reason())).append("\"");
            json.append("}");
            if (i < res.appliedOverrides().size() - 1) json.append(",");
        }
        json.append("],");

        // Applied rule IDs
        json.append("\"rules\":[");
        for (int i = 0; i < res.appliedRuleIds().size(); i++) {
            json.append("\"").append(res.appliedRuleIds().get(i)).append("\"");
            if (i < res.appliedRuleIds().size() - 1) json.append(",");
        }
        json.append("]");

        json.append("}");
        sendJsonResponse(exchange, json.toString());
    }

    private void handleHistory(HttpExchange exchange) throws IOException {
        loadData();
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String consumerId = params.getOrDefault("consumer", "CON-001");

        List<Tradeline> rawTradelines = dataStore.getTradelinesForConsumer(consumerId);
        List<Enquiry> rawEnquiries = dataStore.getEnquiriesForConsumer(consumerId);
        List<AccountEvent> rawEvents = dataStore.getEventsForConsumer(consumerId);

        List<LocalDate> dates = new ArrayList<>();
        for (Tradeline t : rawTradelines) {
            dates.add(t.submissionDate());
        }

        List<ScoreHistoryEntry> history = scoreHistoryEngine.computeHistory(
                consumerId, rawTradelines, rawEnquiries, rawEvents, dates
        );

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < history.size(); i++) {
            ScoreHistoryEntry e = history.get(i);
            json.append("{");
            json.append("\"date\":\"").append(e.asOfDate()).append("\",");
            json.append("\"score\":").append(e.score()).append(",");
            json.append("\"isThinFile\":").append(e.isThinFile());
            json.append("}");
            if (i < history.size() - 1) json.append(",");
        }
        json.append("]");

        sendJsonResponse(exchange, json.toString());
    }

    private String formatFactorJson(com.creditbureau.simulator.domain.model.FactorContribution fc) {
        return String.format("{\"name\":\"%s\",\"max\":%d,\"awarded\":%d,\"explanation\":\"%s\"}",
                fc.factorName(), fc.maxPoints(), fc.awardedPoints(), escapeJson(fc.explanation()));
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private void sendJsonResponse(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) return params;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                params.put(key, val);
            }
        }
        return params;
    }

    private String getDashboardHtml() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Credit Bureau Simulation Engine (PS-022)</title>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;600&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-primary: #0a0e17;
            --bg-secondary: #121826;
            --bg-card: #182234;
            --border-color: #26354d;
            --text-primary: #f1f5f9;
            --text-secondary: #94a3b8;
            --accent-blue: #38bdf8;
            --accent-green: #34d399;
            --accent-yellow: #fbbf24;
            --accent-red: #f87171;
            --accent-purple: #a855f7;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: 'Inter', sans-serif;
            background-color: var(--bg-primary);
            color: var(--text-primary);
            line-height: 1.5;
            padding: 24px;
        }
        .header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-bottom: 1px solid var(--border-color);
            padding-bottom: 20px;
            margin-bottom: 24px;
        }
        .title h1 {
            font-size: 24px;
            font-weight: 800;
            background: linear-gradient(135deg, #38bdf8, #818cf8);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .title p { color: var(--text-secondary); font-size: 14px; margin-top: 4px; }
        .controls {
            display: flex;
            gap: 12px;
            align-items: center;
            background: var(--bg-secondary);
            padding: 12px 16px;
            border-radius: 10px;
            border: 1px solid var(--border-color);
        }
        select, input[type="date"], button {
            background: var(--bg-card);
            border: 1px solid var(--border-color);
            color: var(--text-primary);
            padding: 8px 12px;
            border-radius: 6px;
            font-family: inherit;
            font-size: 14px;
            outline: none;
        }
        select:focus, input[type="date"]:focus { border-color: var(--accent-blue); }
        button {
            background: #2563eb;
            color: white;
            font-weight: 600;
            cursor: pointer;
            transition: background 0.2s;
            border: none;
        }
        button:hover { background: #1d4ed8; }
        .grid-layout {
            display: grid;
            grid-template-columns: 360px 1fr;
            gap: 24px;
        }
        .card {
            background: var(--bg-secondary);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            padding: 20px;
            margin-bottom: 20px;
        }
        .card h2 {
            font-size: 16px;
            font-weight: 700;
            margin-bottom: 16px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            color: var(--text-primary);
            border-bottom: 1px solid var(--border-color);
            padding-bottom: 10px;
        }
        /* Score Meter */
        .score-display {
            text-align: center;
            padding: 20px 0;
        }
        .score-number {
            font-size: 64px;
            font-weight: 800;
            letter-spacing: -2px;
            line-height: 1;
        }
        .score-rating {
            font-size: 16px;
            font-weight: 600;
            margin-top: 8px;
            text-transform: uppercase;
            letter-spacing: 1px;
        }
        .score-range {
            font-size: 12px;
            color: var(--text-secondary);
            margin-top: 4px;
        }
        .badge {
            display: inline-block;
            padding: 4px 8px;
            border-radius: 4px;
            font-size: 11px;
            font-weight: 700;
            font-family: 'JetBrains Mono', monospace;
        }
        .badge-std { background: rgba(52, 211, 153, 0.2); color: var(--accent-green); }
        .badge-sma0 { background: rgba(251, 191, 36, 0.2); color: var(--accent-yellow); }
        .badge-sma1 { background: rgba(251, 146, 60, 0.2); color: #fb923c; }
        .badge-sma2 { background: rgba(248, 113, 113, 0.2); color: var(--accent-red); }
        .badge-npa { background: rgba(239, 68, 68, 0.3); color: #ef4444; font-weight: 800; }
        .badge-xxx { background: rgba(148, 163, 184, 0.1); color: #64748b; }
        
        .stat-row {
            display: flex;
            justify-content: space-between;
            padding: 8px 0;
            border-bottom: 1px solid rgba(255,255,255,0.05);
            font-size: 13px;
        }
        .stat-row:last-child { border-bottom: none; }
        .stat-label { color: var(--text-secondary); }
        .stat-value { font-weight: 600; font-family: 'JetBrains Mono', monospace; }

        /* Factor Cards */
        .factors-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
            gap: 16px;
            margin-bottom: 24px;
        }
        .factor-card {
            background: var(--bg-card);
            border: 1px solid var(--border-color);
            border-radius: 8px;
            padding: 14px;
        }
        .factor-header {
            display: flex;
            justify-content: space-between;
            font-size: 13px;
            font-weight: 600;
            margin-bottom: 8px;
        }
        .factor-score { font-size: 18px; font-weight: 700; font-family: 'JetBrains Mono', monospace; }
        .factor-desc { font-size: 11px; color: var(--text-secondary); margin-top: 6px; }

        /* Tables */
        table {
            width: 100%;
            border-collapse: collapse;
            font-size: 13px;
            margin-top: 8px;
        }
        th, td {
            padding: 10px 12px;
            text-align: left;
            border-bottom: 1px solid var(--border-color);
        }
        th { color: var(--text-secondary); font-weight: 600; font-size: 12px; text-transform: uppercase; }
        tr:hover { background: rgba(255,255,255,0.02); }

        /* DPD Grid */
        .dpd-timeline {
            display: grid;
            grid-template-columns: repeat(12, 1fr);
            gap: 4px;
            margin: 8px 0 16px 0;
        }
        .dpd-cell {
            padding: 6px 2px;
            text-align: center;
            font-size: 10px;
            border-radius: 4px;
            font-family: 'JetBrains Mono', monospace;
        }
        .rule-tag {
            display: inline-block;
            background: rgba(56, 189, 248, 0.15);
            color: var(--accent-blue);
            padding: 2px 8px;
            border-radius: 4px;
            font-size: 12px;
            margin: 2px 4px 2px 0;
            font-family: 'JetBrains Mono', monospace;
        }
        .alert-box {
            padding: 12px 16px;
            border-radius: 8px;
            margin-bottom: 16px;
            font-size: 13px;
        }
        .alert-danger {
            background: rgba(239, 68, 68, 0.15);
            border: 1px solid rgba(239, 68, 68, 0.3);
            color: #fca5a5;
        }
    </style>
</head>
<body>
    <div class="header">
        <div class="title">
            <h1>Credit Bureau Score Simulation Engine</h1>
            <p>PS-022 Deterministic Evaluation Platform &bull; Localhost Dashboard</p>
        </div>
        <div class="controls">
            <label for="consumerSelect" style="font-size:13px;color:var(--text-secondary);">Consumer:</label>
            <select id="consumerSelect">
                <option value="CON-001">CON-001 (1-Yr Age Boundary - EC-02)</option>
                <option value="CON-002">CON-002 (Dispute & Settlement)</option>
                <option value="CON-003">CON-003 (Delinquency Month 6 - 650 Cap - EC-04)</option>
                <option value="CON-004">CON-004 (Utilisation 30.0% - EC-01)</option>
                <option value="CON-005">CON-005 (Balance Correction & BNPL - EC-05)</option>
                <option value="CON-006">CON-006 (Delinquency Month 7 - No Cap - EC-04)</option>
                <option value="CON-007">CON-007 (Settled Account & Limit 0 CC)</option>
                <option value="CON-008">CON-008 (Written-Off Override 24m)</option>
                <option value="CON-009">CON-009 (Dispute Removed Retroactive - EC-06)</option>
                <option value="CON-010">CON-010 (Thin File: 5 Months - EC-07)</option>
                <option value="CON-011">CON-011 (Raw 1000 Capped at 900 - EC-08)</option>
                <option value="CON-012">CON-012 (Enquiries 14 Days Apart - EC-03)</option>
                <option value="CON-013">CON-013 (Thin File: 0 Tradelines - EC-07)</option>
                <option value="CON-014">CON-014 (Settled & Dispute Removed)</option>
                <option value="CON-015">CON-015 (Point-in-Time Cutoff & Dispute)</option>
                <option value="CON-016">CON-016 (Exactly 6 Months - Not Thin - EC-07)</option>
                <option value="CON-017">CON-017 (Utilisation 100.0% - EC-01)</option>
                <option value="CON-018">CON-018 (Account Merging Boundary 30d/5%)</option>
                <option value="CON-019">CON-019 (Suit Filed Override 550 Cap)</option>
                <option value="CON-020">CON-020 (Dispute Valid Retroactive - EC-06)</option>
            </select>
            <label for="asOfDate" style="font-size:13px;color:var(--text-secondary);">As-Of:</label>
            <input type="date" id="asOfDate" value="2026-10-31">
            <button onclick="loadReport()">Evaluate Score</button>
        </div>
    </div>

    <div class="grid-layout">
        <!-- Left Sidebar: Score Meter & Consumer Overview -->
        <div>
            <div class="card">
                <h2>Credit Score Output</h2>
                <div class="score-display">
                    <div id="scoreNumber" class="score-number">---</div>
                    <div id="scoreRating" class="score-rating">Evaluating...</div>
                    <div class="score-range">Standard Range: 300 to 900</div>
                </div>
                <div id="thinFileAlert" style="display:none;" class="alert-box alert-danger"></div>
                
                <div class="stat-row">
                    <span class="stat-label">Base Score</span>
                    <span id="baseScore" class="stat-value">300</span>
                </div>
                <div class="stat-row">
                    <span class="stat-label">Pre-Override Score</span>
                    <span id="preOverrideScore" class="stat-value">---</span>
                </div>
                <div class="stat-row">
                    <span class="stat-label">Risk Overrides</span>
                    <span id="overridesCount" class="stat-value">None</span>
                </div>
            </div>

            <div class="card">
                <h2>Consumer Profile</h2>
                <div class="stat-row">
                    <span class="stat-label">Merged Tradelines</span>
                    <span id="mergedAccounts" class="stat-value">0</span>
                </div>
                <div class="stat-row">
                    <span class="stat-label">Active / Closed</span>
                    <span id="activeClosed" class="stat-value">0 / 0</span>
                </div>
                <div class="stat-row">
                    <span class="stat-label">Revolving Utilisation</span>
                    <span id="revolvingUtil" class="stat-value">0.00%</span>
                </div>
                <div class="stat-row">
                    <span class="stat-label">Revolving Balance / Limit</span>
                    <span id="balanceLimit" class="stat-value">₹0 / ₹0</span>
                </div>
                <div class="stat-row">
                    <span class="stat-label">Oldest Account Age</span>
                    <span id="oldestAge" class="stat-value">---</span>
                </div>
                <div class="stat-row">
                    <span class="stat-label">Credit Mix</span>
                    <span id="mixStatus" class="stat-value">---</span>
                </div>
                <div class="stat-row">
                    <span class="stat-label">Hard / Soft Enquiries</span>
                    <span id="enquiryCounts" class="stat-value">0 / 0</span>
                </div>
            </div>

            <div class="card">
                <h2>Score History Timeline</h2>
                <table>
                    <thead>
                        <tr><th>Date</th><th>Score</th><th>Status</th></tr>
                    </thead>
                    <tbody id="historyTableBody">
                        <tr><td colspan="3" style="text-align:center;">Loading...</td></tr>
                    </tbody>
                </table>
            </div>
        </div>

        <!-- Right Main Panel: Factors, DPD Grids, Accounts & Rules -->
        <div>
            <!-- Factor Cards -->
            <div class="card">
                <h2>Factor-Wise Score Contributions</h2>
                <div class="factors-grid" id="factorsContainer"></div>
            </div>

            <!-- Overrides Section -->
            <div id="overridesCard" class="card" style="display:none;">
                <h2>Active Risk Overrides</h2>
                <div id="overridesList"></div>
            </div>

            <!-- Merged Tradelines -->
            <div class="card">
                <h2>Merged Credit Accounts & Repayment Grids</h2>
                <div id="accountsContainer"></div>
            </div>

            <!-- Applied Rules -->
            <div class="card">
                <h2>Traceable Applied Business Rules</h2>
                <div id="rulesList"></div>
            </div>
        </div>
    </div>

    <script>
        async function loadReport() {
            const consumer = document.getElementById('consumerSelect').value;
            const asOf = document.getElementById('asOfDate').value;

            try {
                const res = await fetch(`/api/report?consumer=${consumer}&asOf=${asOf}`);
                const data = await res.json();
                renderReport(data);

                const histRes = await fetch(`/api/history?consumer=${consumer}`);
                const histData = await histRes.json();
                renderHistory(histData);
            } catch (err) {
                console.error("Error loading report:", err);
            }
        }

        function renderReport(data) {
            const scoreNum = document.getElementById('scoreNumber');
            const scoreRating = document.getElementById('scoreRating');
            const thinAlert = document.getElementById('thinFileAlert');

            if (data.isThinFile) {
                scoreNum.textContent = "NA";
                scoreNum.style.color = "#94a3b8";
                scoreRating.textContent = "Thin File";
                scoreRating.style.color = "#f87171";
                thinAlert.style.display = "block";
                thinAlert.textContent = "Thin File: " + data.thinFileReason;
            } else {
                thinAlert.style.display = "none";
                scoreNum.textContent = data.finalScore;
                if (data.finalScore >= 750) {
                    scoreNum.style.color = "var(--accent-green)";
                    scoreRating.textContent = "Excellent";
                    scoreRating.style.color = "var(--accent-green)";
                } else if (data.finalScore >= 650) {
                    scoreNum.style.color = "var(--accent-blue)";
                    scoreRating.textContent = "Good / Prime";
                    scoreRating.style.color = "var(--accent-blue)";
                } else if (data.finalScore >= 550) {
                    scoreNum.style.color = "var(--accent-yellow)";
                    scoreRating.textContent = "Fair / Moderate Risk";
                    scoreRating.style.color = "var(--accent-yellow)";
                } else {
                    scoreNum.style.color = "var(--accent-red)";
                    scoreRating.textContent = "High Risk / Default";
                    scoreRating.style.color = "var(--accent-red)";
                }
            }

            document.getElementById('baseScore').textContent = data.baseScore;
            document.getElementById('preOverrideScore').textContent = data.isThinFile ? "NA" : data.preOverrideScore;

            // Profile
            document.getElementById('mergedAccounts').textContent = data.profile.mergedAccounts;
            document.getElementById('activeClosed').textContent = `${data.profile.activeAccounts} / ${data.profile.closedAccounts}`;
            document.getElementById('revolvingUtil').textContent = data.profile.revolvingUtilisation.toFixed(2) + "%";
            document.getElementById('balanceLimit').textContent = `₹${data.profile.revolvingBalance.toLocaleString()} / ₹${data.profile.revolvingLimit.toLocaleString()}`;
            document.getElementById('oldestAge').textContent = data.profile.oldestOpenDate ? `${data.profile.oldestAgeYears}y ${data.profile.oldestAgeMonths}m (${data.profile.oldestOpenDate})` : "None";
            document.getElementById('mixStatus').textContent = data.profile.hasMixedCredit ? "Secured + Unsecured" : "Single Category";
            document.getElementById('enquiryCounts').textContent = `${data.profile.hardEnquiries} / ${data.profile.softEnquiries}`;

            // Factors
            const factorsContainer = document.getElementById('factorsContainer');
            factorsContainer.innerHTML = '';
            data.factors.forEach(f => {
                const card = document.createElement('div');
                card.className = 'factor-card';
                card.innerHTML = `
                    <div class="factor-header">
                        <span>${f.name}</span>
                        <span style="color:var(--text-secondary);font-size:11px;">Max ${f.max}</span>
                    </div>
                    <div class="factor-score" style="color:${f.awarded < 0 ? 'var(--accent-red)' : 'var(--text-primary)'}">${f.awarded > 0 ? '+' : ''}${f.awarded}</div>
                    <div class="factor-desc">${f.explanation}</div>
                `;
                factorsContainer.appendChild(card);
            });

            // Overrides
            const activeOverrides = data.overrides.filter(o => o.applied);
            const ovCard = document.getElementById('overridesCard');
            const ovCount = document.getElementById('overridesCount');
            if (activeOverrides.length > 0) {
                ovCard.style.display = 'block';
                ovCount.textContent = `${activeOverrides.length} active`;
                ovCount.style.color = "var(--accent-red)";
                const ovList = document.getElementById('overridesList');
                ovList.innerHTML = activeOverrides.map(o => `
                    <div class="alert-box alert-danger">
                        <strong>[${o.ruleId}] ${o.name}</strong>: Score hard-capped at <strong>${o.cap}</strong><br>
                        <span style="font-size:12px;opacity:0.9;">Reason: ${o.reason}</span>
                    </div>
                `).join('');
            } else {
                ovCard.style.display = 'none';
                ovCount.textContent = "None";
                ovCount.style.color = "var(--accent-green)";
            }

            // Accounts & 36-Month Grids
            const accContainer = document.getElementById('accountsContainer');
            accContainer.innerHTML = data.accounts.map(acc => `
                <div style="margin-bottom:20px;border-bottom:1px solid var(--border-color);padding-bottom:14px;">
                    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;">
                        <div>
                            <strong style="font-family:'JetBrains Mono';font-size:14px;">${acc.accountId}</strong>
                            <span style="font-size:12px;color:var(--text-secondary);margin-left:8px;">${acc.lender} &bull; ${acc.type}</span>
                        </div>
                        <div>
                            <span class="badge ${acc.status === 'ACTIVE' ? 'badge-std' : 'badge-sma2'}">${acc.status}</span>
                            <span style="font-family:'JetBrains Mono';font-size:12px;margin-left:8px;">Limit: ₹${acc.limit.toLocaleString()} | Bal: ₹${acc.balance.toLocaleString()}</span>
                        </div>
                    </div>
                    <div style="font-size:11px;color:var(--text-secondary);margin-bottom:4px;">36-Month Repayment Grid (Month 1 = As-Of Date &rarr; Month 36 = Oldest):</div>
                    <div class="dpd-timeline">
                        ${acc.dpdGrid.map(g => {
                            let badgeClass = 'badge-xxx';
                            if (g.category === 'STD') badgeClass = 'badge-std';
                            else if (g.category === 'SMA-0') badgeClass = 'badge-sma0';
                            else if (g.category === 'SMA-1') badgeClass = 'badge-sma1';
                            else if (g.category === 'SMA-2') badgeClass = 'badge-sma2';
                            else if (g.category === 'NPA') badgeClass = 'badge-npa';
                            return `<div class="dpd-cell ${badgeClass}" title="${g.yearMonth} (Month ${g.month}): ${g.dpd !== null ? g.dpd + ' DPD' : 'No Data'}">${g.category}</div>`;
                        }).join('')}
                    </div>
                </div>
            `).join('');

            // Rules
            const rulesList = document.getElementById('rulesList');
            rulesList.innerHTML = data.rules.map(r => `<span class="rule-tag">${r}</span>`).join('');
        }

        function renderHistory(history) {
            const tbody = document.getElementById('historyTableBody');
            if (!history || history.length === 0) {
                tbody.innerHTML = '<tr><td colspan="3" style="text-align:center;">No history</td></tr>';
                return;
            }
            tbody.innerHTML = history.map(h => `
                <tr>
                    <td>${h.date}</td>
                    <td style="font-weight:700;font-family:'JetBrains Mono';">${h.isThinFile ? 'NA' : h.score}</td>
                    <td><span class="badge ${h.isThinFile ? 'badge-sma2' : 'badge-std'}">${h.isThinFile ? 'Thin File' : 'Scored'}</span></td>
                </tr>
            `).join('');
        }

        // Initial load
        window.onload = loadReport;
    </script>
</body>
</html>
        """;
    }
}

