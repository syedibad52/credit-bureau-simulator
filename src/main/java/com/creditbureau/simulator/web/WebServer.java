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
 * Embedded lightweight HTTP server serving the Credit Bureau dashboard and API.
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
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        server.createContext("/", this::handleRoot);
        server.createContext("/health", this::handleHealth);
        server.createContext("/api/consumers", this::handleConsumers);
        server.createContext("/api/report", this::handleReport);
        server.createContext("/api/history", this::handleHistory);

        server.setExecutor(null); // Default executor
        server.start();

        System.out.println("========================================================================");
        System.out.printf("  CREDIT BUREAU DASHBOARD RUNNING AT: http://localhost:%d%n", port);
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

    private void handleHealth(HttpExchange exchange) throws IOException {
        sendJsonResponse(exchange, "{\"status\":\"ok\"}");
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
        String cleanExplanation = f.explanation() != null
                ? f.explanation().replace("\u20B9", "&#8377;").replace("₹", "&#8377;").replace("?", "&#8377;")
                : "";
        return String.format("{\"name\":\"%s\",\"max\":%d,\"awarded\":%d,\"explanation\":\"%s\"}",
                f.factorName(), f.maxPoints(), f.awardedPoints(), escapeJson(cleanExplanation));
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
        try {
            java.nio.file.Path p = java.nio.file.Path.of("index.html");
            if (java.nio.file.Files.exists(p)) {
                return java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {}
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="description" content="Deterministic credit bureau score simulation engine and interactive audit platform for consumer credit profiles.">
    <title>Credit Bureau Score Simulation Engine: BFSI Evaluation Platform</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500;700&display=swap" rel="stylesheet">
    <style>
/* Hallmark · shape: app screen · genre: modern-minimal · theme: Vantage (deal 3/3, seed credit-bureau-score-simulation-846d·1e9b8a52·2026-11-13·r0)
 * nav: edge-aligned · signature: score-arc-instrument · states: all-eight · vantage: terrain/steel
 * axes: dark/grotesk-sans/chromatic-lime · posture: restrained · contrast: pass · v2.0.0 */
/* Hallmark · pre-emit critique: P4 H4 E4 S4 R4 V4 · weakest: R4, strictly snapped to 4px spacing grid */

:root {
    color-scheme: dark;
    --font-display: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, sans-serif;
    --font-body: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, sans-serif;
    --font-mono: 'JetBrains Mono', monospace;

    --color-paper: oklch(14% 0.012 235);
    --color-paper-2: oklch(18% 0.014 235);
    --color-paper-3: oklch(22% 0.016 235);
    --color-paper-hover: oklch(24% 0.018 235);

    --color-ink: oklch(96% 0.006 230);
    --color-ink-2: oklch(84% 0.012 230);
    --color-muted: oklch(68% 0.016 230);
    --color-dim: oklch(52% 0.018 230);

    --color-rule: oklch(26% 0.014 235);
    --color-rule-2: oklch(34% 0.016 235);
    --color-rule-focus: oklch(88% 0.19 126);

    --color-accent: oklch(88% 0.19 126);
    --color-accent-hi: oklch(93% 0.17 126);
    --color-accent-ink: oklch(16% 0.03 126);
    --color-focus: oklch(88% 0.19 126);

    --signal-lime: oklch(88% 0.19 126);
    --signal-lime-bg: oklch(22% 0.05 126);
    --signal-cyan: oklch(82% 0.14 210);
    --signal-cyan-bg: oklch(22% 0.04 210);
    --signal-blue: oklch(76% 0.13 245);
    --signal-blue-bg: oklch(22% 0.04 245);
    --signal-amber: oklch(80% 0.16 75);
    --signal-amber-bg: oklch(22% 0.05 75);
    --signal-rose: oklch(68% 0.20 25);
    --signal-rose-bg: oklch(22% 0.06 25);
    --signal-slate: oklch(50% 0.02 230);
    --signal-slate-bg: oklch(20% 0.01 230);

    --space-2xs: 4px;
    --space-xs: 8px;
    --space-sm: 12px;
    --space-md: 16px;
    --space-lg: 20px;
    --space-xl: 24px;
    --space-2xl: 32px;

    --radius-xs: 4px;
    --radius-sm: 8px;
    --radius-md: 12px;
    --radius-lg: 16px;
    --radius-pill: 999px;

    --tracking-display: -0.025em;
    --container: 1440px;
}

*, *::before, *::after {
    box-sizing: border-box;
    margin: 0;
    padding: 0;
}

html, body {
    overflow-x: clip;
}

body {
    background-color: var(--color-paper);
    color: var(--color-ink);
    font-family: var(--font-body);
    font-size: 14px;
    line-height: 1.5;
    -webkit-font-smoothing: antialiased;
    padding: var(--space-xl) var(--space-2xl);
    min-height: 100vh;
    caret-color: var(--color-accent);
}

::selection {
    background-color: var(--color-accent);
    color: var(--color-accent-ink);
}

:focus-visible {
    outline: 2px solid var(--color-focus);
    outline-offset: 2px;
}

h1, h2, h3 {
    font-family: var(--font-display);
    font-weight: 700;
    letter-spacing: var(--tracking-display);
    line-height: 1.15;
    text-wrap: balance;
    overflow-wrap: break-word;
    min-width: 0;
}

h1 { font-size: 20px; color: var(--color-ink); }
h2 { font-size: 15px; color: var(--color-ink); }
h3 { font-size: 13px; color: var(--color-muted); }

.layout-wrap {
    max-width: var(--container);
    margin-inline: auto;
}

/* App Header */
.app-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: var(--space-md);
    flex-wrap: wrap;
    margin-bottom: var(--space-lg);
    padding-bottom: var(--space-md);
    border-bottom: 1px solid var(--color-rule);
}

.brand-block {
    display: flex;
    align-items: center;
    gap: var(--space-sm);
}

.brand-icon {
    width: 36px;
    height: 36px;
    background-color: var(--color-paper-3);
    border: 1px solid var(--color-rule-2);
    border-radius: var(--radius-sm);
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--color-accent);
}

.brand-title-group h1 {
    font-size: 18px;
    margin-bottom: var(--space-2xs);
}

.brand-subtitle {
    font-size: 12px;
    color: var(--color-muted);
    display: flex;
    align-items: center;
    gap: var(--space-xs);
}

.status-badge {
    background-color: var(--signal-lime-bg);
    color: var(--signal-lime);
    border: 1px solid var(--color-rule);
    font-size: 11px;
    font-weight: 600;
    padding: var(--space-2xs) var(--space-xs);
    border-radius: var(--radius-pill);
}

/* Command Controls */
.controls-panel {
    display: flex;
    align-items: center;
    gap: var(--space-sm);
    flex-wrap: wrap;
}

.field-group {
    display: flex;
    align-items: center;
    gap: var(--space-xs);
    font-size: 12px;
    color: var(--color-muted);
}

select, input[type="date"] {
    background-color: var(--color-paper-2);
    border: 1px solid var(--color-rule);
    color: var(--color-ink);
    padding: var(--space-xs) var(--space-sm);
    border-radius: var(--radius-sm);
    font-family: inherit;
    font-size: 13px;
    outline: none;
    cursor: pointer;
    transition: border-color 0.15s ease, background-color 0.15s ease;
}

select:hover, input[type="date"]:hover {
    border-color: var(--color-rule-2);
}

select:disabled, input[type="date"]:disabled, button:disabled {
    opacity: 0.45;
    cursor: not-allowed;
}

.btn-primary {
    background-color: var(--color-accent);
    color: var(--color-accent-ink);
    font-weight: 700;
    font-size: 13px;
    padding: var(--space-xs) var(--space-md);
    border-radius: var(--radius-sm);
    border: 1px solid var(--color-accent);
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    gap: var(--space-xs);
    transition: background-color 0.15s ease, border-color 0.15s ease;
}

.btn-primary:hover {
    background-color: var(--color-accent-hi);
}

.btn-primary:active {
    background-color: var(--color-accent);
    transform: translateY(1px);
}

/* Scenario Tray */
.scenarios-tray {
    display: flex;
    align-items: center;
    gap: var(--space-xs);
    overflow-x: auto;
    padding-bottom: var(--space-md);
    margin-bottom: var(--space-lg);
    scrollbar-width: thin;
    scrollbar-color: var(--color-rule) transparent;
}

.tray-title {
    font-size: 11px;
    font-weight: 700;
    color: var(--color-dim);
    margin-right: var(--space-2xs);
    white-space: nowrap;
}

.chip-btn {
    background-color: var(--color-paper-2);
    border: 1px solid var(--color-rule);
    color: var(--color-muted);
    padding: var(--space-2xs) var(--space-sm);
    border-radius: var(--radius-pill);
    font-size: 12px;
    font-weight: 500;
    white-space: nowrap;
    cursor: pointer;
    transition: color 0.15s ease, background-color 0.15s ease, border-color 0.15s ease;
}

.chip-btn:hover {
    background-color: var(--color-paper-3);
    color: var(--color-ink);
    border-color: var(--color-rule-2);
}

.chip-btn:active {
    transform: translateY(1px);
}

.chip-btn.active {
    background-color: var(--color-paper-3);
    color: var(--color-accent);
    border-color: var(--color-accent);
}

/* Main Grid */
.dashboard-grid {
    display: grid;
    grid-template-columns: 360px minmax(0, 1fr);
    gap: var(--space-xl);
    align-items: start;
}

@media (max-width: 1040px) {
    .dashboard-grid {
        grid-template-columns: 1fr;
    }
}

/* Panels */
.panel {
    background-color: var(--color-paper-2);
    color: var(--color-ink);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-md);
    padding: var(--space-lg);
}

.panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: var(--space-md);
    padding-bottom: var(--space-xs);
    border-bottom: 1px solid var(--color-rule);
}

/* Left Hero Score Instrument */
.hero-instrument {
    text-align: center;
}

.instrument-title {
    font-size: 11px;
    font-weight: 600;
    color: var(--color-muted);
    margin-bottom: var(--space-xs);
}

.gauge-wrap {
    position: relative;
    width: 240px;
    height: 140px;
    margin: 0 auto;
}

.gauge-svg {
    width: 240px;
    height: 140px;
    display: block;
}

.gauge-track {
    fill: none;
    stroke: var(--color-rule);
    stroke-width: 12;
    stroke-linecap: round;
}

.gauge-progress {
    fill: none;
    stroke: var(--signal-cyan);
    stroke-width: 12;
    stroke-linecap: round;
    transition: stroke-dashoffset 0.8s var(--ease-out), stroke 0.3s ease;
}

.gauge-center-content {
    position: absolute;
    top: 40px;
    left: 0;
    right: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    pointer-events: none;
}

.score-display {
    font-size: 48px;
    font-weight: 800;
    line-height: 1;
    letter-spacing: -0.03em;
    font-family: var(--font-display);
    font-variant-numeric: tabular-nums;
}

.score-tier-tag {
    font-size: 11px;
    font-weight: 600;
    padding: var(--space-2xs) var(--space-sm);
    border-radius: var(--radius-pill);
    margin-top: var(--space-xs);
    border: 1px solid var(--color-rule);
    background-color: var(--color-paper-3);
    color: var(--color-ink-2);
}

.score-scale-hint {
    font-size: 10px;
    color: var(--color-dim);
    margin-top: var(--space-2xs);
    font-family: var(--font-mono);
}

/* Override Notice */
.override-alert {
    background-color: var(--signal-rose-bg);
    border: 1px solid var(--signal-rose);
    color: var(--color-ink);
    padding: var(--space-sm) var(--space-md);
    border-radius: var(--radius-sm);
    margin-top: var(--space-md);
    font-size: 12px;
    text-align: left;
    line-height: 1.4;
}

/* Formula Block (Flat Ruled, No Card Inside Card) */
.formula-section {
    margin-top: var(--space-md);
    padding-top: var(--space-sm);
    border-top: 1px solid var(--color-rule);
    text-align: left;
}

.formula-heading {
    font-size: 10px;
    font-weight: 600;
    color: var(--color-dim);
    margin-bottom: var(--space-xs);
}

.formula-flow {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: var(--space-2xs);
    font-family: var(--font-mono);
    font-size: 11px;
}

.node {
    background-color: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    padding: var(--space-2xs) var(--space-xs);
    border-radius: var(--radius-xs);
    color: var(--color-ink-2);
}

.node.accent {
    border-color: var(--signal-cyan);
    color: var(--signal-cyan);
}

.node.highlight {
    border-color: var(--color-accent);
    color: var(--color-accent);
    font-weight: 700;
}

/* Profile Grid */
.profile-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: var(--space-xs);
}

.stat-cell {
    background-color: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-xs);
    padding: var(--space-xs);
}

.stat-cell.span-full {
    grid-column: span 2;
}

.cell-label {
    font-size: 10px;
    font-weight: 500;
    color: var(--color-muted);
    margin-bottom: var(--space-2xs);
    display: block;
}

.cell-value {
    font-size: 12px;
    font-weight: 700;
    font-family: var(--font-mono);
    color: var(--color-ink);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

/* Factor Cards */
.factors-row {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
    gap: var(--space-sm);
    margin-bottom: var(--space-lg);
}

.factor-tile {
    background-color: var(--color-paper-2);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-md);
    padding: var(--space-md);
    display: flex;
    flex-direction: column;
    justify-content: space-between;
}

.factor-header {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    margin-bottom: var(--space-xs);
}

.factor-name {
    font-size: 12px;
    font-weight: 600;
    color: var(--color-ink-2);
}

.factor-cap {
    font-size: 10px;
    font-family: var(--font-mono);
    color: var(--color-dim);
}

.factor-score-num {
    font-size: 24px;
    font-weight: 800;
    font-family: var(--font-display);
    font-variant-numeric: tabular-nums;
    margin: var(--space-2xs) 0;
    color: var(--color-ink);
}

.meter-track {
    height: 4px;
    background-color: var(--color-rule);
    border-radius: var(--radius-pill);
    overflow: hidden;
    margin-bottom: var(--space-xs);
}

.meter-fill {
    height: 100%;
    border-radius: var(--radius-pill);
    transform-origin: left;
    transform: scaleX(0);
    transition: transform 0.6s var(--ease-out);
}

.bar-lime  { background-color: var(--signal-lime); }
.bar-cyan  { background-color: var(--signal-cyan); }
.bar-blue  { background-color: var(--signal-blue); }
.bar-amber { background-color: var(--signal-amber); }
.bar-rose  { background-color: var(--signal-rose); }
.bar-slate { background-color: var(--signal-slate); }

.factor-detail {
    font-size: 11px;
    color: var(--color-muted);
    line-height: 1.35;
}

/* Repayment Matrix */
.matrix-legend {
    display: flex;
    gap: var(--space-sm);
    font-size: 11px;
    color: var(--color-muted);
    align-items: center;
    flex-wrap: wrap;
}

.account-card {
    background-color: var(--color-paper-3);
    color: var(--color-ink);
    border: 1px solid var(--color-rule);
    border-radius: var(--radius-sm);
    padding: var(--space-md);
    margin-bottom: var(--space-sm);
}

.account-card:last-child {
    margin-bottom: 0;
}

.account-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: var(--space-xs);
    flex-wrap: wrap;
    gap: var(--space-xs);
}

.acc-title {
    font-family: var(--font-mono);
    font-size: 13px;
    font-weight: 700;
    color: var(--signal-cyan);
}

.acc-meta {
    font-size: 11px;
    color: var(--color-muted);
    margin-left: var(--space-xs);
}

.timeline-grid {
    display: grid;
    grid-template-columns: repeat(36, minmax(0, 1fr));
    gap: var(--space-2xs);
    background-color: var(--color-paper);
    padding: var(--space-xs);
    border-radius: var(--radius-xs);
    border: 1px solid var(--color-rule);
}

@media (max-width: 900px) {
    .timeline-grid {
        grid-template-columns: repeat(18, minmax(0, 1fr));
    }
}

.dpd-badge {
    padding: var(--space-2xs) 1px;
    border-radius: var(--radius-xs);
    text-align: center;
    font-size: 9px;
    font-weight: 600;
    font-family: var(--font-body);
    cursor: pointer;
    transition: transform 0.15s ease;
}

.dpd-badge:hover {
    transform: translateY(-2px);
}

.cat-std  { background-color: var(--signal-lime-bg); color: var(--signal-lime); border: 1px solid var(--signal-lime); }
.cat-sma0 { background-color: var(--signal-blue-bg); color: var(--signal-blue); border: 1px solid var(--signal-blue); }
.cat-sma1 { background-color: var(--signal-amber-bg); color: var(--signal-amber); border: 1px solid var(--signal-amber); }
.cat-sma2 { background-color: var(--signal-rose-bg); color: var(--signal-rose); border: 1px solid var(--signal-rose); }
.cat-npa  { background-color: var(--signal-rose-bg); color: var(--signal-rose); border: 1px solid var(--signal-rose); }
.cat-nr   { background-color: var(--signal-slate-bg); color: var(--color-ink-2); border: 1px solid var(--color-rule); }

/* Rules Section */
.rules-container {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-2xs);
}

.rule-chip {
    background-color: var(--color-paper-3);
    border: 1px solid var(--color-rule);
    color: var(--signal-cyan);
    font-size: 11px;
    font-family: var(--font-body);
    font-weight: 600;
    padding: var(--space-2xs) var(--space-xs);
    border-radius: var(--radius-xs);
}

@media (prefers-reduced-motion: reduce) {
    *, *::before, *::after {
        animation-duration: 0.01ms !important;
        animation-iteration-count: 1 !important;
        transition-duration: 0.01ms !important;
        scroll-behavior: auto !important;
    }
}
    </style>
</head>
<body>
    <div class="layout-wrap">
        <!-- App Header -->
        <header class="app-header">
            <div class="brand-block">
                <div class="brand-icon">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                        <path d="M12 2L2 7l10 5 10-5-10-5zm0 9l2.5-1.25L12 8.5l-2.5 1.25L12 11zm0 2.5l-5-2.5-5 2.5 10 5 10-5-5-2.5-5 2.5z"/>
                    </svg>
                </div>
                <div class="brand-title-group">
                    <h1>Credit Bureau Simulation Engine</h1>
                    <div class="brand-subtitle">
                        <span>PS-022 BFSI Evaluation Platform</span>
                        <span class="status-badge">Engine Active</span>
                    </div>
                </div>
            </div>

            <div class="controls-panel">
                <div class="field-group">
                    <label for="consumerSelect">Consumer:</label>
                    <select id="consumerSelect" onchange="loadReport()">
                        <option value="CON-001">CON-001 (1-Yr Age Boundary)</option>
                        <option value="CON-002">CON-002 (Dispute and Settlement)</option>
                        <option value="CON-003">CON-003 (Delinquency Month 6: 650 Cap)</option>
                        <option value="CON-004">CON-004 (Utilisation 30.0%)</option>
                        <option value="CON-005">CON-005 (Balance Correction and BNPL)</option>
                        <option value="CON-006">CON-006 (Delinquency Month 7: No Cap)</option>
                        <option value="CON-007">CON-007 (Settled Account and Limit 0 CC)</option>
                        <option value="CON-008">CON-008 (Written-Off Override 24m)</option>
                        <option value="CON-009">CON-009 (Dispute Removed Retroactive)</option>
                        <option value="CON-010">CON-010 (Thin File: 5 Months)</option>
                        <option value="CON-011">CON-011 (Raw 1000 Capped at 900)</option>
                        <option value="CON-012">CON-012 (Enquiries 14 Days Apart)</option>
                        <option value="CON-013">CON-013 (Thin File: 0 Tradelines)</option>
                        <option value="CON-014">CON-014 (Settled and Dispute Removed)</option>
                        <option value="CON-015">CON-015 (Point-in-Time Cutoff and Dispute)</option>
                        <option value="CON-016">CON-016 (Exactly 6 Months: Eligible)</option>
                        <option value="CON-017">CON-017 (Utilisation 100.0%)</option>
                        <option value="CON-018">CON-018 (Account Merging Boundary 30d/5%)</option>
                        <option value="CON-019">CON-019 (Suit Filed Override 550 Cap)</option>
                        <option value="CON-020">CON-020 (Dispute Valid Retroactive)</option>
                    </select>
                </div>

                <div class="field-group">
                    <label for="asOfDate">As-Of:</label>
                    <input type="date" id="asOfDate" value="2026-10-31">
                </div>

                <button class="btn-primary" onclick="loadReport()">
                    <svg width="14" height="14" fill="currentColor" viewBox="0 0 16 16" aria-hidden="true">
                        <path d="M11.534 7h3.932a.25.25 0 0 1 .192.41l-1.966 2.36a.25.25 0 0 1-.384 0l-1.966-2.36a.25.25 0 0 1 .192-.41zm-11 2h3.932a.25.25 0 0 0 .192-.41L2.692 6.23a.25.25 0 0 0-.384 0L.342 8.59A.25.25 0 0 0 .534 9z"/>
                        <path fill-rule="evenodd" d="M8 3c-1.552 0-2.94.707-3.857 1.818a.5.5 0 1 1-.771-.636A6.002 6.002 0 0 1 13.917 7H12.9A5.002 5.002 0 0 0 8 3zM3.1 9a5.002 5.002 0 0 0 8.757 2.182.5.5 0 1 1 .771.636A6.002 6.002 0 0 1 2.083 9H3.1z"/>
                    </svg>
                    <span>Evaluate Score</span>
                </button>
            </div>
        </header>

        <!-- Scenario Quick Presets -->
        <nav class="scenarios-tray" aria-label="Evaluation scenarios">
            <span class="tray-title">Scenarios:</span>
            <button class="chip-btn" onclick="selectScenario('CON-004', '2026-10-31')">[EC-01] Util 30% (CON-004)</button>
            <button class="chip-btn" onclick="selectScenario('CON-017', '2026-10-31')">[EC-01] Util 100% (CON-017)</button>
            <button class="chip-btn" onclick="selectScenario('CON-001', '2026-10-31')">[EC-02] 1-Yr Age (CON-001)</button>
            <button class="chip-btn" onclick="selectScenario('CON-003', '2026-10-31')">[EC-04] M6 90 DPD Cap 650 (CON-003)</button>
            <button class="chip-btn" onclick="selectScenario('CON-006', '2026-10-31')">[EC-04] M7 90 DPD No Cap (CON-006)</button>
            <button class="chip-btn" onclick="selectScenario('CON-005', '2026-09-15')">[EC-05] Prelim Balance (CON-005)</button>
            <button class="chip-btn" onclick="selectScenario('CON-005', '2026-09-30')">[EC-05] Corrected Bal (CON-005)</button>
            <button class="chip-btn" onclick="selectScenario('CON-020', '2026-10-31')">[EC-06] Dispute Valid (CON-020)</button>
            <button class="chip-btn" onclick="selectScenario('CON-009', '2026-10-31')">[EC-06] Dispute Removed (CON-009)</button>
            <button class="chip-btn" onclick="selectScenario('CON-016', '2026-10-31')">[EC-07] 6-Mo Eligible (CON-016)</button>
            <button class="chip-btn" onclick="selectScenario('CON-010', '2026-10-31')">[EC-07] 5-Mo Thin (CON-010)</button>
            <button class="chip-btn" onclick="selectScenario('CON-013', '2026-10-31')">[EC-07] 0 Tradelines (CON-013)</button>
            <button class="chip-btn" onclick="selectScenario('CON-011', '2026-10-31')">[EC-08] Cap at 900 (CON-011)</button>
            <button class="chip-btn" onclick="selectScenario('CON-019', '2026-10-31')">[Trap] Suit Filed 550 (CON-019)</button>
        </nav>

        <!-- Main Dashboard Grid -->
        <main class="dashboard-grid">
            <!-- Left Column: Score Hero and Profile -->
            <div>
                <!-- Score Hero Panel -->
                <section class="panel hero-instrument">
                    <div class="instrument-title">Credit Score Output</div>

                    <div class="gauge-wrap">
                        <svg class="gauge-svg" viewBox="0 0 240 145" aria-hidden="true">
                            <path class="gauge-track" d="M 30 125 A 90 90 0 0 1 210 125" />
                            <path id="gaugeFill" class="gauge-progress" d="M 30 125 A 90 90 0 0 1 210 125" />
                        </svg>
                        <div class="gauge-center-content">
                            <div id="scoreValue" class="score-display">---</div>
                            <div id="scoreBand" class="score-tier-tag">Evaluating</div>
                            <div class="score-scale-hint">Range: 300 to 900</div>
                        </div>
                    </div>

                    <div id="overrideBox" class="override-alert" style="display:none;"></div>

                    <!-- Deterministic Formula Flow (Flat, not nested card) -->
                    <div class="formula-section">
                        <div class="formula-heading">Deterministic Scoring Formula</div>
                        <div class="formula-flow">
                            <span class="node">Base: 300</span>
                            <span>+</span>
                            <span id="formulaFactors" class="node accent">+0 Ftr</span>
                            <span>-</span>
                            <span id="formulaEnq" class="node">-0 Enq</span>
                            <span>=</span>
                            <span id="formulaRaw" class="node">Raw: ---</span>
                            <span>&rarr;</span>
                            <span id="formulaFinal" class="node highlight">Final: ---</span>
                        </div>
                    </div>
                </section>

                <!-- Profile Summary Panel -->
                <section class="panel" style="margin-top: var(--space-md);">
                    <div class="panel-header">
                        <h2>Consumer Profile</h2>
                        <span id="consumerIdBadge" class="status-badge">CON-001</span>
                    </div>

                    <div class="profile-grid">
                        <div class="stat-cell">
                            <span class="cell-label">Merged Tradelines</span>
                            <div id="profMerged" class="cell-value">---</div>
                        </div>
                        <div class="stat-cell">
                            <span class="cell-label">Active / Closed</span>
                            <div id="profActiveClosed" class="cell-value">--- / ---</div>
                        </div>
                        <div class="stat-cell">
                            <span class="cell-label">Revolving Util</span>
                            <div id="profUtil" class="cell-value">---%</div>
                        </div>
                        <div class="stat-cell">
                            <span class="cell-label">Hard / Soft Enquiries</span>
                            <div id="profEnq" class="cell-value">--- / ---</div>
                        </div>
                        <div class="stat-cell span-full">
                            <span class="cell-label">Revolving Balance / Limit</span>
                            <div id="profBalLimit" class="cell-value">&#8377;0 / &#8377;0</div>
                        </div>
                        <div class="stat-cell span-full">
                            <span class="cell-label">Oldest Account Age</span>
                            <div id="profAge" class="cell-value">---</div>
                        </div>
                        <div class="stat-cell span-full">
                            <span class="cell-label">Credit Portfolio Mix</span>
                            <div id="profMix" class="cell-value">---</div>
                        </div>
                    </div>
                </section>
            </div>

            <!-- Right Column: 5 Factors and Repayment Matrix -->
            <div>
                <!-- Factor Summary Cards -->
                <div class="factors-row">
                    <!-- F1: Payment History -->
                    <div class="factor-tile">
                        <div class="factor-header">
                            <span class="factor-name">Payment History</span>
                            <span class="factor-cap">Max 300</span>
                        </div>
                        <div id="f1Num" class="factor-score-num">+300</div>
                        <div class="meter-track">
                            <div id="f1Bar" class="meter-fill bar-lime"></div>
                        </div>
                        <div id="f1Desc" class="factor-detail">Clean repayment record across all accounts.</div>
                    </div>

                    <!-- F2: Utilisation -->
                    <div class="factor-tile">
                        <div class="factor-header">
                            <span class="factor-name">Utilisation</span>
                            <span class="factor-cap">Max 200</span>
                        </div>
                        <div id="f2Num" class="factor-score-num">+200</div>
                        <div class="meter-track">
                            <div id="f2Bar" class="meter-fill bar-cyan"></div>
                        </div>
                        <div id="f2Desc" class="factor-detail">Revolving balance to credit limit ratio.</div>
                    </div>

                    <!-- F3: Credit Age -->
                    <div class="factor-tile">
                        <div class="factor-header">
                            <span class="factor-name">Credit Age</span>
                            <span class="factor-cap">Max 100</span>
                        </div>
                        <div id="f3Num" class="factor-score-num">+100</div>
                        <div class="meter-track">
                            <div id="f3Bar" class="meter-fill bar-blue"></div>
                        </div>
                        <div id="f3Desc" class="factor-detail">Calendar age of oldest open credit account.</div>
                    </div>

                    <!-- F4: Credit Mix -->
                    <div class="factor-tile">
                        <div class="factor-header">
                            <span class="factor-name">Credit Mix</span>
                            <span class="factor-cap">Max 100</span>
                        </div>
                        <div id="f4Num" class="factor-score-num">+100</div>
                        <div class="meter-track">
                            <div id="f4Bar" class="meter-fill bar-amber"></div>
                        </div>
                        <div id="f4Desc" class="factor-detail">Portfolio distribution between secured and unsecured debt.</div>
                    </div>

                    <!-- F5: Enquiries -->
                    <div class="factor-tile">
                        <div class="factor-header">
                            <span class="factor-name">Enquiries</span>
                            <span class="factor-cap">Max -100</span>
                        </div>
                        <div id="f5Num" class="factor-score-num">0</div>
                        <div class="meter-track">
                            <div id="f5Bar" class="meter-fill bar-rose"></div>
                        </div>
                        <div id="f5Desc" class="factor-detail">Hard credit inquiries in preceding 12 months.</div>
                    </div>
                </div>

                <!-- Merged Tradelines and 36-Month DPD Matrix -->
                <section class="panel">
                    <div class="panel-header">
                        <h2>Merged Accounts and 36-Month Repayment Grid</h2>
                        <div class="matrix-legend">
                            <span><span class="dpd-badge cat-std" style="padding: 2px 4px;">STD</span> 0 DPD</span>
                            <span><span class="dpd-badge cat-sma0" style="padding: 2px 4px;">1-29</span> SMA-0</span>
                            <span><span class="dpd-badge cat-sma1" style="padding: 2px 4px;">30-59</span> SMA-1</span>
                            <span><span class="dpd-badge cat-sma2" style="padding: 2px 4px;">60-89</span> SMA-2</span>
                            <span><span class="dpd-badge cat-npa" style="padding: 2px 4px;">90+</span> NPA</span>
                            <span><span class="dpd-badge cat-nr" style="padding: 2px 4px;">NR</span> No Data</span>
                        </div>
                    </div>

                    <div id="accountsContainer">Loading accounts…</div>
                </section>

                <!-- Business Rules Traceability -->
                <section class="panel" style="margin-top: var(--space-md);">
                    <div class="panel-header">
                        <h2>Applied Business Rules Register</h2>
                    </div>
                    <div id="rulesList" class="rules-container">Loading rules…</div>
                </section>
            </div>
        </main>
    </div>

    <script>
        function selectScenario(consumerId, asOfDate) {
            document.getElementById('consumerSelect').value = consumerId;
            document.getElementById('asOfDate').value = asOfDate;
            document.querySelectorAll('.chip-btn').forEach(function(c) { c.classList.remove('active'); });
            if (window.event && window.event.target) {
                window.event.target.classList.add('active');
            }
            loadReport();
        }

        async function loadReport() {
            const consumer = document.getElementById('consumerSelect').value;
            const asOf = document.getElementById('asOfDate').value;

            try {
                const res = await fetch('/api/report?consumer=' + encodeURIComponent(consumer) + '&asOf=' + encodeURIComponent(asOf));
                if (!res.ok) throw new Error('HTTP ' + res.status);
                const data = await res.json();
                renderReport(data);
            } catch (err) {
                console.error('Failed to load report', err);
            }
        }

        function renderReport(data) {
            document.getElementById('consumerIdBadge').textContent = data.consumerId;

            const gaugeFill = document.getElementById('gaugeFill');
            const scoreVal = document.getElementById('scoreValue');
            const scoreBand = document.getElementById('scoreBand');
            const overrideBox = document.getElementById('overrideBox');

            const totalLength = gaugeFill.getTotalLength() || 283;
            gaugeFill.style.strokeDasharray = totalLength;

            if (data.isThinFile) {
                gaugeFill.style.strokeDashoffset = totalLength;
                gaugeFill.style.stroke = 'var(--signal-slate)';
                scoreVal.textContent = 'NA';
                scoreBand.textContent = 'Thin File';
                scoreBand.style.borderColor = 'var(--signal-rose)';
                scoreBand.style.color = 'var(--signal-rose)';

                overrideBox.style.display = 'block';
                overrideBox.innerHTML = '<strong>Thin File Exclusion:</strong> ' + (data.thinFileReason || 'Fewer than 6 months history or 0 tradelines');
            } else {
                const ratio = Math.max(0, Math.min(1, (data.finalScore - 300) / 600));
                const targetOffset = totalLength - (ratio * totalLength);
                gaugeFill.style.strokeDashoffset = targetOffset;

                animateCounter(scoreVal, data.finalScore);

                if (data.finalScore >= 800) {
                    gaugeFill.style.stroke = 'var(--signal-lime)';
                    scoreBand.textContent = 'Super-Prime';
                    scoreBand.style.borderColor = 'var(--signal-lime)';
                    scoreBand.style.color = 'var(--signal-lime)';
                } else if (data.finalScore >= 720) {
                    gaugeFill.style.stroke = 'var(--signal-cyan)';
                    scoreBand.textContent = 'Prime';
                    scoreBand.style.borderColor = 'var(--signal-cyan)';
                    scoreBand.style.color = 'var(--signal-cyan)';
                } else if (data.finalScore >= 650) {
                    gaugeFill.style.stroke = 'var(--signal-blue)';
                    scoreBand.textContent = 'Good / Near Prime';
                    scoreBand.style.borderColor = 'var(--signal-blue)';
                    scoreBand.style.color = 'var(--signal-blue)';
                } else if (data.finalScore >= 550) {
                    gaugeFill.style.stroke = 'var(--signal-amber)';
                    scoreBand.textContent = 'Moderate Risk';
                    scoreBand.style.borderColor = 'var(--signal-amber)';
                    scoreBand.style.color = 'var(--signal-amber)';
                } else {
                    gaugeFill.style.stroke = 'var(--signal-rose)';
                    scoreBand.textContent = 'High Risk';
                    scoreBand.style.borderColor = 'var(--signal-rose)';
                    scoreBand.style.color = 'var(--signal-rose)';
                }

                const activeOverrides = data.overrides.filter(function(o) { return o.applied; });
                if (activeOverrides.length > 0) {
                    overrideBox.style.display = 'block';
                    overrideBox.innerHTML = activeOverrides.map(function(o) {
                        return '<strong>[' + o.ruleId + '] Hard Override Cap: ' + o.cap + '</strong><br>' + o.name + ' (' + o.reason + ')';
                    }).join('<hr style="border:0;border-top:1px solid var(--signal-rose);margin:8px 0;">');
                } else {
                    overrideBox.style.display = 'none';
                }
            }

            // Formula Flow Bar
            let totalPositiveFactors = 0;
            let enquiryDeduction = 0;
            data.factors.forEach(function(f) {
                if (f.name === 'Enquiries') enquiryDeduction = Math.abs(f.awarded);
                else totalPositiveFactors += f.awarded;
            });

            document.getElementById('formulaFactors').textContent = '+' + totalPositiveFactors + ' Ftr';
            document.getElementById('formulaEnq').textContent = '-' + enquiryDeduction + ' Enq';
            document.getElementById('formulaRaw').textContent = data.isThinFile ? 'Raw: NA' : ('Raw: ' + data.preOverrideScore);
            document.getElementById('formulaFinal').textContent = data.isThinFile ? 'Final: NA' : ('Final: ' + data.finalScore);

            // Profile Stats
            document.getElementById('profMerged').textContent = data.profile.mergedAccounts;
            document.getElementById('profActiveClosed').textContent = data.profile.activeAccounts + ' Act / ' + data.profile.closedAccounts + ' Cls';
            document.getElementById('profUtil').textContent = data.profile.revolvingUtilisation.toFixed(2) + '%';
            document.getElementById('profBalLimit').textContent = '\u20B9' + data.profile.revolvingBalance.toLocaleString() + ' / \u20B9' + data.profile.revolvingLimit.toLocaleString();
            document.getElementById('profAge').textContent = data.profile.oldestOpenDate ? (data.profile.oldestAgeYears + 'y ' + data.profile.oldestAgeMonths + 'm (' + data.profile.oldestOpenDate + ')') : 'None';
            document.getElementById('profMix').textContent = data.profile.hasMixedCredit ? 'Secured + Unsecured' : 'Single Category';
            document.getElementById('profEnq').textContent = data.profile.hardEnquiries + ' Hard / ' + data.profile.softEnquiries + ' Soft';

            // Factors Cards
            const f1 = data.factors.find(function(f) { return f.name.indexOf('Payment') !== -1; }) || { awarded: 0, max: 300, explanation: '' };
            const f2 = data.factors.find(function(f) { return f.name.indexOf('Util') !== -1; }) || { awarded: 0, max: 200, explanation: '' };
            const f3 = data.factors.find(function(f) { return f.name.indexOf('Age') !== -1; }) || { awarded: 0, max: 100, explanation: '' };
            const f4 = data.factors.find(function(f) { return f.name.indexOf('Mix') !== -1; }) || { awarded: 0, max: 100, explanation: '' };
            const f5 = data.factors.find(function(f) { return f.name.indexOf('Enquir') !== -1; }) || { awarded: 0, max: 0, explanation: '' };

            updateFactorCard('f1', f1, 300);
            updateFactorCard('f2', f2, 200);
            updateFactorCard('f3', f3, 100);
            updateFactorCard('f4', f4, 100);
            updateFactorCard('f5', f5, 100, true);

            // Accounts Container
            const container = document.getElementById('accountsContainer');
            if (!data.accounts || data.accounts.length === 0) {
                container.innerHTML = '<div style="color:var(--color-dim);padding:16px;text-align:center;">No accounts available for this consumer as-of the selected date.</div>';
            } else {
                container.innerHTML = data.accounts.map(function(acc) {
                    const statusClass = acc.status === 'ACTIVE' ? 'cat-std' : (acc.status === 'CLOSED' ? 'cat-nr' : 'cat-npa');
                    return '<div class="account-card">' +
                        '<div class="account-top">' +
                            '<div>' +
                                '<span class="acc-title">' + acc.accountId + '</span>' +
                                '<span class="acc-meta">' + acc.lender + ' &bull; ' + acc.type + ' &bull; Opened ' + acc.openDate + '</span>' +
                                (!acc.included ? ('<span class="status-badge" style="background-color:var(--signal-rose-bg);color:var(--signal-rose);border-color:var(--signal-rose);margin-left:8px;">Excluded: ' + acc.exclusionReason + '</span>') : '') +
                            '</div>' +
                            '<div style="display:flex;align-items:center;gap:8px;">' +
                                '<span class="dpd-badge ' + statusClass + '" style="padding:2px 8px;font-size:11px;">' + acc.status + '</span>' +
                                '<span style="font-family:var(--font-mono);font-size:12px;color:var(--color-muted);">' +
                                    'Limit: <strong style="color:var(--color-ink);">&#8377;' + acc.limit.toLocaleString() + '</strong> | ' +
                                    'Bal: <strong style="color:var(--color-ink);">&#8377;' + acc.balance.toLocaleString() + '</strong>' +
                                '</span>' +
                            '</div>' +
                        '</div>' +
                        '<div class="timeline-grid">' +
                            acc.dpdGrid.map(function(g) {
                                const cat = (!g.category || g.category === 'XXX') ? 'NR' : g.category;
                                let badge = 'cat-nr';
                                if (cat === 'STD') badge = 'cat-std';
                                else if (cat === 'SMA-0') badge = 'cat-sma0';
                                else if (cat === 'SMA-1') badge = 'cat-sma1';
                                else if (cat === 'SMA-2') badge = 'cat-sma2';
                                else if (cat === 'NPA') badge = 'cat-npa';
                                const tip = g.dpd !== null ? (g.yearMonth + ' (Month ' + g.month + '): ' + g.dpd + ' DPD [' + cat + ']') : (g.yearMonth + ' (Month ' + g.month + '): Not Reported / No data (NR)');
                                return '<div class="dpd-badge ' + badge + '" title="' + tip + '">' + cat + '</div>';
                            }).join('') +
                        '</div>' +
                    '</div>';
                }).join('');
            }

            // Rules List
            const rulesEl = document.getElementById('rulesList');
            rulesEl.innerHTML = data.rules.map(function(r) {
                return '<span class="rule-chip" title="Business Rule ' + r + '">[' + r + ']</span>';
            }).join('');
        }

        function updateFactorCard(prefix, factor, max, isDeduction) {
            const numEl = document.getElementById(prefix + 'Num');
            const barEl = document.getElementById(prefix + 'Bar');
            const descEl = document.getElementById(prefix + 'Desc');

            if (isDeduction) {
                const ded = Math.abs(factor.awarded);
                numEl.textContent = ded > 0 ? ('-' + ded) : '0';
                const ratio = Math.min(1, ded / max);
                barEl.style.transform = 'scaleX(' + ratio + ')';
            } else {
                numEl.textContent = '+' + factor.awarded;
                const ratio = Math.min(1, Math.max(0, factor.awarded / max));
                barEl.style.transform = 'scaleX(' + ratio + ')';
            }
            descEl.innerHTML = factor.explanation || '';
        }

        function animateCounter(element, target) {
            let start = 300;
            const duration = 600;
            const startTime = performance.now();

            function update(time) {
                const elapsed = time - startTime;
                const progress = Math.min(elapsed / duration, 1);
                const current = Math.floor(start + (target - start) * (1 - Math.pow(2, -10 * progress)));
                element.textContent = current;
                if (progress < 1) requestAnimationFrame(update);
                else element.textContent = target;
            }
            requestAnimationFrame(update);
        }

        window.onload = loadReport;
    </script>
</body>
</html>

""";
    }
}
