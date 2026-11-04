package com.creditbureau.simulator.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate root representing a consumer in the credit bureau system.
 * Holds all accounts, enquiries, events, and score history for one individual.
 */
public class Consumer {
    private final String consumerId;
    private final List<CreditAccount> accounts;
    private final List<Enquiry> enquiries;
    private final List<AccountEvent> events;
    private final List<ScoreHistoryEntry> scoreHistory;

    public Consumer(String consumerId) {
        if (consumerId == null || consumerId.isBlank()) {
            throw new IllegalArgumentException("consumerId must not be blank");
        }
        this.consumerId = consumerId;
        this.accounts = new ArrayList<>();
        this.enquiries = new ArrayList<>();
        this.events = new ArrayList<>();
        this.scoreHistory = new ArrayList<>();
    }

    public void addAccount(CreditAccount account) { accounts.add(account); }
    public void addEnquiry(Enquiry enquiry) { enquiries.add(enquiry); }
    public void addEvent(AccountEvent event) { events.add(event); }
    public void addScoreHistoryEntry(ScoreHistoryEntry entry) { scoreHistory.add(entry); }

    public String getConsumerId() { return consumerId; }
    public List<CreditAccount> getAccounts() { return Collections.unmodifiableList(accounts); }
    public List<Enquiry> getEnquiries() { return Collections.unmodifiableList(enquiries); }
    public List<AccountEvent> getEvents() { return Collections.unmodifiableList(events); }
    public List<ScoreHistoryEntry> getScoreHistory() { return Collections.unmodifiableList(scoreHistory); }

    @Override
    public String toString() {
        return "Consumer{" + consumerId + ", accounts=" + accounts.size() + "}";
    }
}
