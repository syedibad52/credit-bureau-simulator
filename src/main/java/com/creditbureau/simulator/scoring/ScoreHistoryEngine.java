package com.creditbureau.simulator.scoring;

import com.creditbureau.simulator.domain.model.AccountEvent;
import com.creditbureau.simulator.domain.model.CreditAccount;
import com.creditbureau.simulator.domain.model.Enquiry;
import com.creditbureau.simulator.domain.model.ScoreHistoryEntry;
import com.creditbureau.simulator.domain.model.ScoreResult;
import com.creditbureau.simulator.domain.model.Tradeline;
import com.creditbureau.simulator.processing.AccountMergingEngine;
import com.creditbureau.simulator.processing.AsOfDateFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Manages score history calculations across multiple historical as-of dates (Phase 11, BR-48).
 * Supports deterministic on-demand historical calculation and retroactive dispute resolution (BR-43).
 */
public class ScoreHistoryEngine {

    private static final Logger logger = LoggerFactory.getLogger(ScoreHistoryEngine.class);

    private final AsOfDateFilter asOfFilter = new AsOfDateFilter();
    private final AccountMergingEngine mergingEngine = new AccountMergingEngine();
    private final ScoringEngine scoringEngine = new ScoringEngine();

    /**
     * Compute score for a specific as-of date using raw records.
     * Ensures strict point-in-time calculation (BR-46).
     */
    public ScoreResult computeScoreAt(
            String consumerId,
            List<Tradeline> rawTradelines,
            List<Enquiry> rawEnquiries,
            List<AccountEvent> rawEvents,
            LocalDate asOfDate
    ) {
        // Strict as-of date filtering
        List<Tradeline> filteredTradelines = asOfFilter.filterTradelines(rawTradelines, asOfDate);
        List<Enquiry> filteredEnquiries = asOfFilter.filterEnquiries(rawEnquiries, asOfDate);
        List<AccountEvent> filteredEvents = asOfFilter.filterEvents(rawEvents, asOfDate);

        // Merge accounts from point-in-time tradelines
        List<CreditAccount> accounts = mergingEngine.mergeAccounts(filteredTradelines, filteredEvents);

        // Compute score
        return scoringEngine.calculateScore(consumerId, accounts, filteredEnquiries, filteredEvents, asOfDate);
    }

    /**
     * Compute score history across multiple specified as-of dates.
     */
    public List<ScoreHistoryEntry> computeHistory(
            String consumerId,
            List<Tradeline> rawTradelines,
            List<Enquiry> rawEnquiries,
            List<AccountEvent> rawEvents,
            List<LocalDate> asOfDates
    ) {
        List<ScoreHistoryEntry> history = new ArrayList<>();

        // Sort dates chronologically
        List<LocalDate> sortedDates = asOfDates.stream().distinct().sorted().toList();

        for (LocalDate date : sortedDates) {
            ScoreResult res = computeScoreAt(consumerId, rawTradelines, rawEnquiries, rawEvents, date);
            history.add(new ScoreHistoryEntry(
                    consumerId,
                    date,
                    res.finalScore(),
                    res.isThinFile(),
                    LocalDateTime.now()
            ));
        }

        return history;
    }
}

