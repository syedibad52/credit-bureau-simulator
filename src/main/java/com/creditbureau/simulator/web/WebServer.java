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
 * Embedded lightweight HTTP server serving a rich, glassmorphism Credit Bureau Web Dashboard on localhost.
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
        List<String> distinct = dataStore.getAllConsumerIds().stream().sorted().toList();

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
            json.append("\"accountId\":\"").append(acc.getMergedAccountId()).append("\",");
            json.append("\"lender\":\"").append(acc.getLender()).append("\",");
            json.append("\"type\":\"").append(acc.getAccountType().name()).append("\",");
            json.append("\"openDate\":\"").append(acc.getOpenDate()).append("\",");
            json.append("\"limit\":").append(acc.getCreditLimit()).append(",");
            json.append("\"balance\":").append(acc.getOutstandingBalance()).append(",");
            json.append("\"status\":\"").append(acc.getCurrentStatus()).append("\",");
            json.append("\"included\":").append(acc.isIncluded(asOfDate)).append(",");
            json.append("\"exclusionReason\":\"").append(acc.getExclusionReason(asOfDate)).append("\",");

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
        dates.add(LocalDate.of(2026, 10, 31));
        List<LocalDate> distinctDates = dates.stream().distinct().sorted().toList();

        List<ScoreHistoryEntry> history = scoreHistoryEngine.computeHistory(
                consumerId, rawTradelines, rawEnquiries, rawEvents, distinctDates);

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

    private String formatFactorJson(com.creditbureau.simulator.domain.model.FactorContribution f) {
        return String.format("{\"name\":\"%s\",\"max\":%d,\"awarded\":%d,\"explanation\":\"%s\"}",
                f.factorName(), f.maxPoints(), f.awardedPoints(), escapeJson(f.explanation()));
    }

    private String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
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
    <title>Credit Bureau Score Simulation Engine — BFSI Platform</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-base: #060913;
            --glass-bg: rgba(15, 23, 42, 0.68);
            --glass-card: rgba(22, 33, 58, 0.58);
            --glass-border: rgba(255, 255, 255, 0.08);
            --glass-border-hover: rgba(56, 189, 248, 0.35);
            --glass-inner-glow: inset 0 1px 1px 0 rgba(255, 255, 255, 0.12);
            --text-main: #f8fafc;
            --text-muted: #94a3b8;
            --text-dim: #64748b;
            --emerald: #10b981;
            --emerald-glow: rgba(16, 185, 129, 0.35);
            --cyan: #06b6d4;
            --blue: #3b82f6;
            --amber: #f59e0b;
            --rose: #ef4444;
            --purple: #8b5cf6;
        }

        * { box-sizing: border-box; margin: 0; padding: 0; }

        body {
            font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, sans-serif;
            background-color: var(--bg-base);
            color: var(--text-main);
            min-height: 100vh;
            padding: 24px 32px;
            overflow-x: hidden;
            position: relative;
        }

        /* Ambient Glowing Mesh Background */
        body::before {
            content: '';
            position: fixed;
            top: -150px;
            left: -100px;
            width: 650px;
            height: 650px;
            background: radial-gradient(circle, rgba(56, 189, 248, 0.14) 0%, rgba(99, 102, 241, 0.08) 50%, transparent 75%);
            filter: blur(80px);
            z-index: -2;
            pointer-events: none;
            animation: pulseOrb1 16s ease-in-out infinite alternate;
        }

        body::after {
            content: '';
            position: fixed;
            bottom: -150px;
            right: -100px;
            width: 700px;
            height: 700px;
            background: radial-gradient(circle, rgba(139, 92, 246, 0.15) 0%, rgba(16, 185, 129, 0.08) 50%, transparent 75%);
            filter: blur(90px);
            z-index: -2;
            pointer-events: none;
            animation: pulseOrb2 20s ease-in-out infinite alternate;
        }

        @keyframes pulseOrb1 {
            0% { transform: translate(0, 0) scale(1); }
            100% { transform: translate(120px, 80px) scale(1.15); }
        }
        @keyframes pulseOrb2 {
            0% { transform: translate(0, 0) scale(1); }
            100% { transform: translate(-100px, -60px) scale(1.2); }
        }

        /* Frosted Glass Component */
        .glass {
            background: var(--glass-card);
            backdrop-filter: blur(24px) saturate(180%);
            -webkit-backdrop-filter: blur(24px) saturate(180%);
            border: 1px solid var(--glass-border);
            box-shadow: 0 12px 36px 0 rgba(0, 0, 0, 0.45), var(--glass-inner-glow);
            border-radius: 20px;
            transition: all 0.28s cubic-bezier(0.4, 0, 0.2, 1);
        }

        .glass:hover {
            border-color: var(--glass-border-hover);
            box-shadow: 0 16px 42px 0 rgba(0, 0, 0, 0.55), 0 0 20px 0 rgba(56, 189, 248, 0.12), var(--glass-inner-glow);
        }

        /* Header Navigation */
        .header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 20px;
            gap: 20px;
            flex-wrap: wrap;
        }

        .logo-group {
            display: flex;
            align-items: center;
            gap: 14px;
        }

        .logo-icon {
            width: 46px;
            height: 46px;
            background: linear-gradient(135deg, #06b6d4, #6366f1);
            border-radius: 14px;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 0 20px rgba(99, 102, 241, 0.45);
        }

        .logo-icon svg { width: 26px; height: 26px; fill: white; }

        .title-text h1 {
            font-size: 22px;
            font-weight: 800;
            letter-spacing: -0.5px;
            background: linear-gradient(135deg, #ffffff 40%, #94a3b8 100%);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }

        .title-text p {
            font-size: 13px;
            color: var(--text-muted);
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .badge-live {
            background: rgba(16, 185, 129, 0.18);
            color: #34d399;
            border: 1px solid rgba(16, 185, 129, 0.35);
            font-size: 11px;
            padding: 1px 8px;
            border-radius: 20px;
            font-weight: 600;
            display: inline-flex;
            align-items: center;
            gap: 5px;
        }
        .badge-live::before {
            content: '';
            width: 6px;
            height: 6px;
            background: #34d399;
            border-radius: 50%;
            box-shadow: 0 0 8px #34d399;
            animation: blink 1.8s infinite;
        }
        @keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.3; } }

        /* Top Controls Bar */
        .controls-glass {
            padding: 10px 18px;
            display: flex;
            align-items: center;
            gap: 14px;
            border-radius: 16px;
            background: rgba(15, 23, 42, 0.7);
        }

        .input-group {
            display: flex;
            align-items: center;
            gap: 8px;
            font-size: 13px;
            color: var(--text-muted);
        }

        select, input[type="date"] {
            background: rgba(30, 41, 59, 0.75);
            border: 1px solid rgba(255, 255, 255, 0.1);
            color: var(--text-main);
            padding: 9px 14px;
            border-radius: 10px;
            font-family: inherit;
            font-size: 13px;
            font-weight: 500;
            outline: none;
            transition: all 0.2s;
            cursor: pointer;
        }

        select:focus, input[type="date"]:focus {
            border-color: #38bdf8;
            box-shadow: 0 0 14px rgba(56, 189, 248, 0.25);
        }

        .btn-evaluate {
            background: linear-gradient(135deg, #2563eb, #4f46e5);
            color: white;
            font-weight: 700;
            font-size: 13px;
            padding: 10px 22px;
            border-radius: 10px;
            border: 1px solid rgba(255, 255, 255, 0.2);
            cursor: pointer;
            box-shadow: 0 4px 18px rgba(79, 70, 229, 0.45);
            transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .btn-evaluate:hover {
            transform: translateY(-1px);
            box-shadow: 0 6px 24px rgba(79, 70, 229, 0.65);
            background: linear-gradient(135deg, #1d4ed8, #4338ca);
        }

        .btn-evaluate:active { transform: translateY(1px); }

        /* Scenario Quick-Filter Chips */
        .scenarios-tray {
            display: flex;
            gap: 8px;
            overflow-x: auto;
            padding: 4px 0 16px 0;
            scrollbar-width: none;
        }
        .scenarios-tray::-webkit-scrollbar { display: none; }

        .chip {
            background: rgba(30, 41, 59, 0.55);
            border: 1px solid rgba(255, 255, 255, 0.08);
            color: var(--text-muted);
            padding: 6px 14px;
            border-radius: 20px;
            font-size: 12px;
            font-weight: 600;
            white-space: nowrap;
            cursor: pointer;
            transition: all 0.2s;
            display: flex;
            align-items: center;
            gap: 6px;
        }

        .chip:hover {
            background: rgba(56, 189, 248, 0.15);
            color: #38bdf8;
            border-color: rgba(56, 189, 248, 0.4);
            transform: translateY(-1px);
        }

        .chip.active {
            background: linear-gradient(135deg, rgba(37, 99, 235, 0.3), rgba(79, 70, 229, 0.3));
            color: #ffffff;
            border-color: rgba(99, 102, 241, 0.6);
            box-shadow: 0 0 12px rgba(99, 102, 241, 0.35);
        }

        /* Main Dashboard Grid */
        .dashboard-grid {
            display: grid;
            grid-template-columns: 380px 1fr;
            gap: 24px;
        }

        @media (max-width: 1080px) {
            .dashboard-grid { grid-template-columns: 1fr; }
        }

        /* Score Gauge Hero Card */
        .score-hero-card {
            padding: 28px 24px;
            text-align: center;
            position: relative;
            overflow: hidden;
        }

        .gauge-wrap {
            position: relative;
            width: 240px;
            height: 200px;
            margin: 0 auto 10px auto;
        }

        .gauge-svg {
            width: 240px;
            height: 200px;
            transform: rotate(-10deg);
        }

        .gauge-bg {
            fill: none;
            stroke: rgba(255, 255, 255, 0.07);
            stroke-width: 16;
            stroke-linecap: round;
        }

        .gauge-fill {
            fill: none;
            stroke-width: 16;
            stroke-linecap: round;
            stroke-dasharray: 440;
            stroke-dashoffset: 440;
            transition: stroke-dashoffset 1.4s cubic-bezier(0.16, 1, 0.3, 1), stroke 0.6s ease;
        }

        .gauge-center-content {
            position: absolute;
            top: 75px;
            left: 0;
            right: 0;
            display: flex;
            flex-direction: column;
            align-items: center;
        }

        .score-val {
            font-size: 58px;
            font-weight: 800;
            letter-spacing: -2px;
            line-height: 1;
            font-feature-settings: "tnum";
            font-variant-numeric: tabular-nums;
        }

        .score-band-pill {
            font-size: 13px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 1px;
            padding: 4px 12px;
            border-radius: 12px;
            margin-top: 8px;
            transition: all 0.4s;
        }

        .score-range-label {
            font-size: 11px;
            color: var(--text-dim);
            margin-top: 4px;
            font-family: 'JetBrains Mono', monospace;
        }

        /* Interactive Formula Flow */
        .formula-card {
            background: rgba(15, 23, 42, 0.5);
            border: 1px solid rgba(255, 255, 255, 0.06);
            border-radius: 14px;
            padding: 14px;
            margin-top: 20px;
            text-align: left;
        }

        .formula-title {
            font-size: 11px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.8px;
            color: var(--text-dim);
            margin-bottom: 8px;
        }

        .formula-flow {
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            gap: 6px;
            font-family: 'JetBrains Mono', monospace;
            font-size: 12px;
        }

        .formula-node {
            background: rgba(30, 41, 59, 0.8);
            border: 1px solid rgba(255, 255, 255, 0.08);
            padding: 3px 8px;
            border-radius: 6px;
            color: var(--text-main);
        }
        .formula-node.accent { border-color: rgba(56, 189, 248, 0.4); color: #38bdf8; }
        .formula-node.highlight { border-color: rgba(16, 185, 129, 0.5); color: #34d399; font-weight: 700; }

        /* Stat Row */
        .stat-row {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 11px 0;
            border-bottom: 1px solid rgba(255, 255, 255, 0.05);
            font-size: 13px;
        }
        .stat-row:last-child { border-bottom: none; }
        .stat-label { color: var(--text-muted); }
        .stat-val { font-weight: 700; font-family: 'JetBrains Mono', monospace; color: var(--text-main); }

        /* Overrides Alert */
        .override-banner {
            background: linear-gradient(135deg, rgba(239, 68, 68, 0.15), rgba(245, 158, 11, 0.12));
            border: 1px solid rgba(239, 68, 68, 0.35);
            padding: 14px 16px;
            border-radius: 12px;
            margin-top: 18px;
            font-size: 13px;
            line-height: 1.45;
            box-shadow: 0 0 20px rgba(239, 68, 68, 0.15);
            animation: fadeIn 0.4s ease;
        }

        @keyframes fadeIn { from { opacity: 0; transform: translateY(-4px); } to { opacity: 1; transform: translateY(0); } }

        /* Factor Cards Grid */
        .factors-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
            gap: 16px;
            margin-bottom: 24px;
        }

        .factor-card {
            padding: 18px;
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            position: relative;
            overflow: hidden;
        }

        .factor-card::before {
            content: '';
            position: absolute;
            top: 0; left: 0; right: 0;
            height: 3px;
            background: linear-gradient(90deg, #38bdf8, transparent);
            opacity: 0;
            transition: opacity 0.3s;
        }
        .factor-card:hover::before { opacity: 1; }

        .factor-head {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            margin-bottom: 8px;
        }

        .factor-title {
            font-size: 14px;
            font-weight: 700;
            color: var(--text-main);
        }

        .factor-max {
            font-size: 11px;
            font-family: 'JetBrains Mono', monospace;
            color: var(--text-dim);
            background: rgba(255, 255, 255, 0.05);
            padding: 2px 6px;
            border-radius: 6px;
        }

        .factor-num {
            font-size: 32px;
            font-weight: 800;
            letter-spacing: -1px;
            margin: 6px 0;
            font-feature-settings: "tnum";
            font-variant-numeric: tabular-nums;
        }

        .factor-bar-track {
            height: 6px;
            background: rgba(255, 255, 255, 0.08);
            border-radius: 6px;
            overflow: hidden;
            margin-bottom: 10px;
        }

        .factor-bar-fill {
            height: 100%;
            border-radius: 6px;
            width: 0%;
            transition: width 1.2s cubic-bezier(0.16, 1, 0.3, 1);
        }

        .factor-explanation {
            font-size: 11px;
            color: var(--text-muted);
            line-height: 1.4;
        }

        /* Accounts Section */
        .section-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 16px;
            padding-bottom: 12px;
            border-bottom: 1px solid rgba(255, 255, 255, 0.06);
        }

        .section-header h2 {
            font-size: 17px;
            font-weight: 700;
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .section-badge {
            background: rgba(56, 189, 248, 0.14);
            color: #38bdf8;
            font-size: 12px;
            padding: 2px 8px;
            border-radius: 8px;
            font-weight: 600;
        }

        .account-item {
            padding: 20px;
            margin-bottom: 16px;
            border-radius: 16px;
            background: rgba(18, 26, 46, 0.45);
        }

        .account-item:last-child { margin-bottom: 0; }

        .account-meta {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 14px;
            flex-wrap: wrap;
            gap: 12px;
        }

        .acc-id {
            font-family: 'JetBrains Mono', monospace;
            font-size: 15px;
            font-weight: 700;
            color: #38bdf8;
        }

        .acc-tag {
            font-size: 12px;
            color: var(--text-muted);
            margin-left: 8px;
        }

        /* 36-Month Timeline Matrix */
        .timeline-container {
            display: grid;
            grid-template-columns: repeat(36, minmax(0, 1fr));
            gap: 3px;
            background: rgba(10, 15, 28, 0.7);
            padding: 10px;
            border-radius: 12px;
            border: 1px solid rgba(255, 255, 255, 0.05);
        }

        @media (max-width: 900px) {
            .timeline-container { grid-template-columns: repeat(18, minmax(0, 1fr)); }
        }

        .dpd-chip {
            padding: 8px 1px;
            border-radius: 6px;
            text-align: center;
            font-size: 9px;
            font-weight: 700;
            font-family: 'JetBrains Mono', monospace;
            cursor: pointer;
            transition: all 0.2s;
            position: relative;
        }

        .dpd-chip:hover {
            transform: translateY(-2px) scale(1.15);
            z-index: 10;
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5);
        }

        /* DPD Category Badges */
        .cat-std  { background: rgba(16, 185, 129, 0.22); color: #34d399; border: 1px solid rgba(16, 185, 129, 0.4); }
        .cat-sma0 { background: rgba(59, 130, 246, 0.25); color: #60a5fa; border: 1px solid rgba(59, 130, 246, 0.45); }
        .cat-sma1 { background: rgba(245, 158, 11, 0.25); color: #fbbf24; border: 1px solid rgba(245, 158, 11, 0.45); }
        .cat-sma2 { background: rgba(249, 115, 22, 0.28); color: #fb923c; border: 1px solid rgba(249, 115, 22, 0.5); }
        .cat-npa  { background: rgba(239, 68, 68, 0.35);  color: #f87171; border: 1px solid rgba(239, 68, 68, 0.6); box-shadow: 0 0 10px rgba(239, 68, 68, 0.35); }
        .cat-nr   { background: rgba(255, 255, 255, 0.04); color: #64748b; border: 1px solid rgba(255, 255, 255, 0.06); }

        /* Business Rule Tag */
        .rule-pill {
            display: inline-block;
            background: rgba(56, 189, 248, 0.12);
            color: #38bdf8;
            border: 1px solid rgba(56, 189, 248, 0.25);
            font-size: 12px;
            font-family: 'JetBrains Mono', monospace;
            font-weight: 600;
            padding: 3px 10px;
            border-radius: 8px;
            margin: 3px 6px 3px 0;
            transition: all 0.2s;
        }

        .rule-pill:hover {
            background: rgba(56, 189, 248, 0.25);
            border-color: #38bdf8;
            transform: translateY(-1px);
        }
    </style>
</head>
<body>
    <!-- Top Bar -->
    <div class="header">
        <div class="logo-group">
            <div class="logo-icon">
                <svg viewBox="0 0 24 24"><path d="M12 2L2 7l10 5 10-5-10-5zm0 9l2.5-1.25L12 8.5l-2.5 1.25L12 11zm0 2.5l-5-2.5-5 2.5 10 5 10-5-5-2.5-5 2.5z"/></svg>
            </div>
            <div class="title-text">
                <h1>Credit Bureau Simulation Engine</h1>
                <p>PS-022 BFSI Evaluation Platform <span class="badge-live">Engine Active</span></p>
            </div>
        </div>

        <div class="controls-glass glass">
            <div class="input-group">
                <label for="consumerSelect">Consumer:</label>
                <select id="consumerSelect" onchange="loadReport()">
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
            </div>

            <div class="input-group">
                <label for="asOfDate">As-Of:</label>
                <input type="date" id="asOfDate" value="2026-10-31">
            </div>

            <button class="btn-evaluate" onclick="loadReport()">
                <svg width="16" height="16" fill="currentColor" viewBox="0 0 16 16"><path d="M11.534 7h3.932a.25.25 0 0 1 .192.41l-1.966 2.36a.25.25 0 0 1-.384 0l-1.966-2.36a.25.25 0 0 1 .192-.41zm-11 2h3.932a.25.25 0 0 0 .192-.41L2.692 6.23a.25.25 0 0 0-.384 0L.342 8.59A.25.25 0 0 0 .534 9z"/><path fill-rule="evenodd" d="M8 3c-1.552 0-2.94.707-3.857 1.818a.5.5 0 1 1-.771-.636A6.002 6.002 0 0 1 13.917 7H12.9A5.002 5.002 0 0 0 8 3zM3.1 9a5.002 5.002 0 0 0 8.757 2.182.5.5 0 1 1 .771.636A6.002 6.002 0 0 1 2.083 9H3.1z"/></svg>
                Evaluate Score
            </button>
        </div>
    </div>

    <!-- Quick Scenario Presets -->
    <div class="scenarios-tray">
        <span style="font-size:12px;color:var(--text-dim);font-weight:700;text-transform:uppercase;margin:auto 6px auto 0;">Scenarios:</span>
        <button class="chip" onclick="selectScenario('CON-004', '2026-10-31')">💳 EC-01: Util 30.0% (CON-004)</button>
        <button class="chip" onclick="selectScenario('CON-017', '2026-10-31')">⚡ EC-01: Util 100.0% (CON-017)</button>
        <button class="chip" onclick="selectScenario('CON-001', '2026-10-31')">⏳ EC-02: 1-Yr Age (CON-001)</button>
        <button class="chip" onclick="selectScenario('CON-003', '2026-10-31')">🚨 EC-04: M6 90 DPD Cap 650 (CON-003)</button>
        <button class="chip" onclick="selectScenario('CON-006', '2026-10-31')">🛡️ EC-04: M7 90 DPD No Cap (CON-006)</button>
        <button class="chip" onclick="selectScenario('CON-005', '2026-09-15')">💰 EC-05: Prelim Balance (CON-005)</button>
        <button class="chip" onclick="selectScenario('CON-005', '2026-09-30')">✨ EC-05: Corrected Balance (CON-005)</button>
        <button class="chip" onclick="selectScenario('CON-020', '2026-10-31')">⚖️ EC-06: Dispute Valid (CON-020)</button>
        <button class="chip" onclick="selectScenario('CON-009', '2026-10-31')">🗑️ EC-06: Dispute Removed (CON-009)</button>
        <button class="chip" onclick="selectScenario('CON-016', '2026-10-31')">📁 EC-07: 6-Mo Not Thin (CON-016)</button>
        <button class="chip" onclick="selectScenario('CON-010', '2026-10-31')">🚫 EC-07: 5-Mo Thin File (CON-010)</button>
        <button class="chip" onclick="selectScenario('CON-013', '2026-10-31')">❌ EC-07: 0 Tradelines (CON-013)</button>
        <button class="chip" onclick="selectScenario('CON-011', '2026-10-31')">🏆 EC-08: 1000 Capped at 900 (CON-011)</button>
        <button class="chip" onclick="selectScenario('CON-019', '2026-10-31')">⚖️ Trap: Suit Filed 550 (CON-019)</button>
    </div>

    <!-- Main Grid -->
    <div class="dashboard-grid">
        <!-- Left Column: Gauge & Profile -->
        <div>
            <!-- Hero Score Card -->
            <div class="glass score-hero-card">
                <h3 style="font-size:14px;font-weight:700;color:var(--text-muted);text-transform:uppercase;letter-spacing:1px;margin-bottom:14px;">Credit Score Output</h3>
                
                <div class="gauge-wrap">
                    <svg class="gauge-svg" viewBox="0 0 200 160">
                        <path class="gauge-bg" d="M 25 135 A 75 75 0 1 1 175 135" />
                        <path id="gaugeFill" class="gauge-fill" stroke="#38bdf8" d="M 25 135 A 75 75 0 1 1 175 135" />
                    </svg>
                    <div class="gauge-center-content">
                        <div id="scoreValue" class="score-val">---</div>
                        <div id="scoreBand" class="score-band-pill">Evaluating</div>
                        <div class="score-range-label">Range: 300 to 900</div>
                    </div>
                </div>

                <div id="overrideBox" style="display:none;" class="override-banner"></div>

                <!-- Step-by-Step Formula Flow -->
                <div class="formula-card">
                    <div class="formula-title">Deterministic Scoring Formula</div>
                    <div class="formula-flow">
                        <span class="formula-node">Base: 300</span>
                        <span>+</span>
                        <span id="formulaFactors" class="formula-node accent">+0 Ftr</span>
                        <span>-</span>
                        <span id="formulaEnq" class="formula-node">-0 Enq</span>
                        <span>=</span>
                        <span id="formulaRaw" class="formula-node">Raw: ---</span>
                        <span>&rarr;</span>
                        <span id="formulaFinal" class="formula-node highlight">Final: ---</span>
                    </div>
                </div>
            </div>

            <!-- Profile Summary Card -->
            <div class="glass" style="padding:24px;margin-top:20px;">
                <div class="section-header">
                    <h2>
                        <svg width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/></svg>
                        Consumer Profile
                    </h2>
                    <span id="consumerIdBadge" class="section-badge">CON-001</span>
                </div>

                <div class="stat-row">
                    <span class="stat-label">Merged Tradelines</span>
                    <span id="profMerged" class="stat-val">---</span>
                </div>
                <div class="stat-row">
                    <span class="stat-label">Active / Closed</span>
                    <span id="profActiveClosed" class="stat-val">--- / ---</span>
                </div>
                <div class="stat-row">
                    <span class="stat-label">Revolving Utilisation</span>
                    <span id="profUtil" class="stat-val" style="color:#38bdf8;">---%</span>
                </div>
                <div class="stat-row">
                    <span class="stat-label">Revolving Balance / Limit</span>
                    <span id="profBalLimit" class="stat-val">₹0 / ₹0</span>
                </div>
                <div class="stat-row">
                    <span class="stat-label">Oldest Account Age</span>
                    <span id="profAge" class="stat-val">---</span>
                </div>
                <div class="stat-row">
                    <span class="stat-label">Credit Portfolio Mix</span>
                    <span id="profMix" class="stat-val">---</span>
                </div>
                <div class="stat-row">
                    <span class="stat-label">Hard / Soft Enquiries</span>
                    <span id="profEnq" class="stat-val">--- / ---</span>
                </div>
            </div>
        </div>

        <!-- Right Column: 5 Factors & 36-Month Accounts -->
        <div>
            <!-- Factor Cards (5 Cards) -->
            <div class="factors-grid">
                <!-- Payment History -->
                <div class="glass factor-card">
                    <div class="factor-head">
                        <span class="factor-title">Payment History</span>
                        <span class="factor-max">Max 300</span>
                    </div>
                    <div id="f1Num" class="factor-num" style="color:#34d399;">+300</div>
                    <div class="factor-bar-track">
                        <div id="f1Bar" class="factor-bar-fill" style="background:#34d399;"></div>
                    </div>
                    <div id="f1Desc" class="factor-explanation">Clean repayment history across all accounts.</div>
                </div>

                <!-- Utilisation -->
                <div class="glass factor-card">
                    <div class="factor-head">
                        <span class="factor-title">Utilisation</span>
                        <span class="factor-max">Max 200</span>
                    </div>
                    <div id="f2Num" class="factor-num" style="color:#38bdf8;">+200</div>
                    <div class="factor-bar-track">
                        <div id="f2Bar" class="factor-bar-fill" style="background:#38bdf8;"></div>
                    </div>
                    <div id="f2Desc" class="factor-explanation">Balance to revolving limit ratio.</div>
                </div>

                <!-- Credit Age -->
                <div class="glass factor-card">
                    <div class="factor-head">
                        <span class="factor-title">Credit Age</span>
                        <span class="factor-max">Max 100</span>
                    </div>
                    <div id="f3Num" class="factor-num" style="color:#818cf8;">+100</div>
                    <div class="factor-bar-track">
                        <div id="f3Bar" class="factor-bar-fill" style="background:#818cf8;"></div>
                    </div>
                    <div id="f3Desc" class="factor-explanation">Calendar age of oldest open account.</div>
                </div>

                <!-- Credit Mix -->
                <div class="glass factor-card">
                    <div class="factor-head">
                        <span class="factor-title">Credit Mix</span>
                        <span class="factor-max">Max 100</span>
                    </div>
                    <div id="f4Num" class="factor-num" style="color:#a855f7;">+100</div>
                    <div class="factor-bar-track">
                        <div id="f4Bar" class="factor-bar-fill" style="background:#a855f7;"></div>
                    </div>
                    <div id="f4Desc" class="factor-explanation">Portfolio balance between secured and unsecured.</div>
                </div>

                <!-- Enquiries -->
                <div class="glass factor-card">
                    <div class="factor-head">
                        <span class="factor-title">Enquiries</span>
                        <span class="factor-max">Max -100</span>
                    </div>
                    <div id="f5Num" class="factor-num" style="color:#94a3b8;">0</div>
                    <div class="factor-bar-track">
                        <div id="f5Bar" class="factor-bar-fill" style="background:#f87171;"></div>
                    </div>
                    <div id="f5Desc" class="factor-explanation">Hard enquiries penalty in recent 12 months.</div>
                </div>
            </div>

            <!-- Merged Tradelines & 36-Month DPD Grid -->
            <div class="glass" style="padding:24px;">
                <div class="section-header">
                    <h2>
                        <svg width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"/></svg>
                        Merged Accounts & 36-Month Repayment Grid
                    </h2>
                    <div style="display:flex;gap:12px;font-size:11px;color:var(--text-muted);align-items:center;">
                        <span style="display:flex;align-items:center;gap:4px;"><span class="dpd-chip cat-std" style="padding:2px 6px;">STD</span> 0 DPD</span>
                        <span style="display:flex;align-items:center;gap:4px;"><span class="dpd-chip cat-sma0" style="padding:2px 6px;">1-29</span> SMA-0</span>
                        <span style="display:flex;align-items:center;gap:4px;"><span class="dpd-chip cat-sma1" style="padding:2px 6px;">30-59</span> SMA-1</span>
                        <span style="display:flex;align-items:center;gap:4px;"><span class="dpd-chip cat-sma2" style="padding:2px 6px;">60-89</span> SMA-2</span>
                        <span style="display:flex;align-items:center;gap:4px;"><span class="dpd-chip cat-npa" style="padding:2px 6px;">90+</span> NPA</span>
                        <span style="display:flex;align-items:center;gap:4px;"><span class="dpd-chip cat-nr" style="padding:2px 6px;">NR</span> No Data</span>
                    </div>
                </div>

                <div id="accountsContainer">Loading accounts...</div>
            </div>

            <!-- Traceable Business Rules Applied -->
            <div class="glass" style="padding:20px;margin-top:20px;">
                <div style="font-size:13px;font-weight:700;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.8px;margin-bottom:10px;">
                    Applied Business Rules Register (Traceable)
                </div>
                <div id="rulesList">Loading rules...</div>
            </div>
        </div>
    </div>

    <script>
        function selectScenario(consumerId, asOfDate) {
            document.getElementById('consumerSelect').value = consumerId;
            document.getElementById('asOfDate').value = asOfDate;
            document.querySelectorAll('.chip').forEach(c => c.classList.remove('active'));
            if (event && event.target) event.target.classList.add('active');
            loadReport();
        }

        async function loadReport() {
            const consumer = document.getElementById('consumerSelect').value;
            const asOf = document.getElementById('asOfDate').value;

            try {
                const res = await fetch(`/api/report?consumer=${consumer}&asOf=${asOf}`);
                if (!res.ok) throw new Error("HTTP " + res.status);
                const data = await res.json();
                renderReport(data);
            } catch (err) {
                console.error("Failed to load report", err);
            }
        }

        function renderReport(data) {
            document.getElementById('consumerIdBadge').textContent = data.consumerId;

            // Score Gauge Arc Math (Total arc circumference ~ 353px for radius 75 across 270 deg)
            const gaugeFill = document.getElementById('gaugeFill');
            const scoreVal = document.getElementById('scoreValue');
            const scoreBand = document.getElementById('scoreBand');
            const overrideBox = document.getElementById('overrideBox');

            const totalArcLength = 353;

            if (data.isThinFile) {
                gaugeFill.style.strokeDashoffset = totalArcLength;
                gaugeFill.style.stroke = "#64748b";
                scoreVal.textContent = "NA";
                scoreVal.style.color = "#94a3b8";
                scoreBand.textContent = "Thin File";
                scoreBand.style.background = "rgba(248, 113, 113, 0.18)";
                scoreBand.style.color = "#f87171";
                scoreBand.style.border = "1px solid rgba(248, 113, 113, 0.4)";

                overrideBox.style.display = "block";
                overrideBox.innerHTML = `<strong>⚠️ Thin File Exclusion</strong><br>${data.thinFileReason || "Fewer than 6 months history or 0 tradelines"}`;
            } else {
                // Score Range 300 to 900 -> 0 to 600 points delta
                const ratio = Math.max(0, Math.min(1, (data.finalScore - 300) / 600));
                const targetOffset = totalArcLength - (ratio * totalArcLength);
                gaugeFill.style.strokeDashoffset = targetOffset;

                animateCounter(scoreVal, data.finalScore);

                if (data.finalScore >= 800) {
                    gaugeFill.style.stroke = "var(--emerald)";
                    scoreVal.style.color = "var(--emerald)";
                    scoreBand.textContent = "Super-Prime";
                    scoreBand.style.background = "rgba(16, 185, 129, 0.2)";
                    scoreBand.style.color = "#34d399";
                    scoreBand.style.border = "1px solid rgba(16, 185, 129, 0.45)";
                } else if (data.finalScore >= 720) {
                    gaugeFill.style.stroke = "var(--cyan)";
                    scoreVal.style.color = "var(--cyan)";
                    scoreBand.textContent = "Prime";
                    scoreBand.style.background = "rgba(6, 182, 212, 0.2)";
                    scoreBand.style.color = "#38bdf8";
                    scoreBand.style.border = "1px solid rgba(6, 182, 212, 0.45)";
                } else if (data.finalScore >= 650) {
                    gaugeFill.style.stroke = "var(--blue)";
                    scoreVal.style.color = "var(--blue)";
                    scoreBand.textContent = "Good / Near Prime";
                    scoreBand.style.background = "rgba(59, 130, 246, 0.2)";
                    scoreBand.style.color = "#60a5fa";
                    scoreBand.style.border = "1px solid rgba(59, 130, 246, 0.45)";
                } else if (data.finalScore >= 550) {
                    gaugeFill.style.stroke = "var(--amber)";
                    scoreVal.style.color = "var(--amber)";
                    scoreBand.textContent = "Moderate Risk";
                    scoreBand.style.background = "rgba(245, 158, 11, 0.2)";
                    scoreBand.style.color = "#fbbf24";
                    scoreBand.style.border = "1px solid rgba(245, 158, 11, 0.45)";
                } else {
                    gaugeFill.style.stroke = "var(--rose)";
                    scoreVal.style.color = "var(--rose)";
                    scoreBand.textContent = "High Risk";
                    scoreBand.style.background = "rgba(239, 68, 68, 0.2)";
                    scoreBand.style.color = "#f87171";
                    scoreBand.style.border = "1px solid rgba(239, 68, 68, 0.45)";
                }

                // Check active overrides
                const activeOverrides = data.overrides.filter(o => o.applied);
                if (activeOverrides.length > 0) {
                    overrideBox.style.display = "block";
                    overrideBox.innerHTML = activeOverrides.map(o => `
                        <strong>🚨 [${o.ruleId}] Hard Override Cap: ${o.cap}</strong><br>
                        ${o.name} &bull; ${o.reason}
                    `).join('<hr style="border:0;border-top:1px solid rgba(239,68,68,0.25);margin:6px 0;">');
                } else {
                    overrideBox.style.display = "none";
                }
            }

            // Formula Flow Bar
            let totalPositiveFactors = 0;
            let enquiryDeduction = 0;
            data.factors.forEach(f => {
                if (f.name === "Enquiries") enquiryDeduction = Math.abs(f.awarded);
                else totalPositiveFactors += f.awarded;
            });

            document.getElementById('formulaFactors').textContent = `+${totalPositiveFactors} Ftr`;
            document.getElementById('formulaEnq').textContent = `-${enquiryDeduction} Enq`;
            document.getElementById('formulaRaw').textContent = data.isThinFile ? "Raw: NA" : `Raw: ${data.preOverrideScore}`;
            document.getElementById('formulaFinal').textContent = data.isThinFile ? "Final: NA" : `Final: ${data.finalScore}`;

            // Profile Stats
            document.getElementById('profMerged').textContent = data.profile.mergedAccounts;
            document.getElementById('profActiveClosed').textContent = `${data.profile.activeAccounts} Active / ${data.profile.closedAccounts} Closed`;
            document.getElementById('profUtil').textContent = data.profile.revolvingUtilisation.toFixed(2) + "%";
            document.getElementById('profBalLimit').textContent = `₹${data.profile.revolvingBalance.toLocaleString()} / ₹${data.profile.revolvingLimit.toLocaleString()}`;
            document.getElementById('profAge').textContent = data.profile.oldestOpenDate ? `${data.profile.oldestAgeYears}y ${data.profile.oldestAgeMonths}m (${data.profile.oldestOpenDate})` : "None";
            document.getElementById('profMix').textContent = data.profile.hasMixedCredit ? "Secured + Unsecured" : "Single Category";
            document.getElementById('profEnq').textContent = `${data.profile.hardEnquiries} Hard / ${data.profile.softEnquiries} Soft`;

            // Factors Cards
            const f1 = data.factors.find(f => f.name.includes("Payment")) || { awarded: 0, max: 300, explanation: "" };
            const f2 = data.factors.find(f => f.name.includes("Util")) || { awarded: 0, max: 200, explanation: "" };
            const f3 = data.factors.find(f => f.name.includes("Age")) || { awarded: 0, max: 100, explanation: "" };
            const f4 = data.factors.find(f => f.name.includes("Mix")) || { awarded: 0, max: 100, explanation: "" };
            const f5 = data.factors.find(f => f.name.includes("Enquir")) || { awarded: 0, max: 0, explanation: "" };

            updateFactorCard('f1', f1, 300, '#34d399');
            updateFactorCard('f2', f2, 200, '#38bdf8');
            updateFactorCard('f3', f3, 100, '#818cf8');
            updateFactorCard('f4', f4, 100, '#a855f7');
            updateFactorCard('f5', f5, 100, '#f87171', true);

            // Accounts Container
            const container = document.getElementById('accountsContainer');
            if (!data.accounts || data.accounts.length === 0) {
                container.innerHTML = '<div style="color:var(--text-dim);padding:20px;text-align:center;">No accounts available for this consumer as-of the selected date.</div>';
            } else {
                container.innerHTML = data.accounts.map(acc => {
                    const statusClass = acc.status === 'ACTIVE' ? 'cat-std' : (acc.status === 'CLOSED' ? 'cat-nr' : 'cat-npa');
                    return `
                        <div class="account-item">
                            <div class="account-meta">
                                <div>
                                    <span class="acc-id">${acc.accountId}</span>
                                    <span class="acc-tag">${acc.lender} &bull; ${acc.type} &bull; Opened ${acc.openDate}</span>
                                    ${!acc.included ? `<span class="badge-live" style="background:rgba(239,68,68,0.2);color:#f87171;border-color:rgba(239,68,68,0.4);margin-left:8px;">Excluded: ${acc.exclusionReason}</span>` : ''}
                                </div>
                                <div style="display:flex;align-items:center;gap:10px;">
                                    <span class="dpd-chip ${statusClass}" style="padding:3px 10px;font-size:11px;">${acc.status}</span>
                                    <span style="font-family:'JetBrains Mono';font-size:12px;color:var(--text-muted);">
                                        Limit: <strong style="color:var(--text-main);">₹${acc.limit.toLocaleString()}</strong> | 
                                        Bal: <strong style="color:var(--text-main);">₹${acc.balance.toLocaleString()}</strong>
                                    </span>
                                </div>
                            </div>

                            <div style="font-size:11px;color:var(--text-dim);margin-bottom:6px;display:flex;justify-content:space-between;">
                                <span>36-Month Repayment Grid (Month 1 = As-Of Date &rarr; Month 36 = Oldest)</span>
                                <span>Hover cell for DPD & month details</span>
                            </div>

                            <div class="timeline-container">
                                ${acc.dpdGrid.map(g => {
                                    let badge = 'cat-nr';
                                    if (g.category === 'STD') badge = 'cat-std';
                                    else if (g.category === 'SMA-0') badge = 'cat-sma0';
                                    else if (g.category === 'SMA-1') badge = 'cat-sma1';
                                    else if (g.category === 'SMA-2') badge = 'cat-sma2';
                                    else if (g.category === 'NPA') badge = 'cat-npa';
                                    return `<div class="dpd-chip ${badge}" title="${g.yearMonth} (M${g.month}): ${g.dpd !== null ? g.dpd + ' DPD (' + g.category + ')' : 'No submission (NR)'}">${g.category}</div>`;
                                }).join('')}
                            </div>
                        </div>
                    `;
                }).join('');
            }

            // Rules List
            const rulesEl = document.getElementById('rulesList');
            rulesEl.innerHTML = data.rules.map(r => `<span class="rule-pill" title="Business Rule ${r}">[${r}]</span>`).join('');
        }

        function updateFactorCard(prefix, factor, max, color, isDeduction = false) {
            const numEl = document.getElementById(prefix + 'Num');
            const barEl = document.getElementById(prefix + 'Bar');
            const descEl = document.getElementById(prefix + 'Desc');

            if (isDeduction) {
                const ded = Math.abs(factor.awarded);
                numEl.textContent = ded > 0 ? `-${ded}` : "0";
                numEl.style.color = ded > 0 ? color : "var(--text-muted)";
                barEl.style.width = Math.min(100, (ded / max) * 100) + "%";
            } else {
                numEl.textContent = `+${factor.awarded}`;
                numEl.style.color = color;
                barEl.style.width = Math.min(100, (factor.awarded / max) * 100) + "%";
            }
            descEl.textContent = factor.explanation || "";
        }

        function animateCounter(element, target) {
            let start = 300;
            const duration = 800;
            const startTime = performance.now();

            function update(time) {
                const elapsed = time - startTime;
                const progress = Math.min(elapsed / duration, 1);
                const current = Math.floor(start + (target - start) * easeOutExpo(progress));
                element.textContent = current;
                if (progress < 1) requestAnimationFrame(update);
                else element.textContent = target;
            }
            requestAnimationFrame(update);
        }

        function easeOutExpo(x) {
            return x === 1 ? 1 : 1 - Math.pow(2, -10 * x);
        }

        window.onload = loadReport;
    </script>
</body>
</html>
        """;
    }
}
