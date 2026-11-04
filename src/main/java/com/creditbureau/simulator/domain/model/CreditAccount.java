package com.creditbureau.simulator.domain.model;

import com.creditbureau.simulator.domain.enums.AccountCategory;
import com.creditbureau.simulator.domain.enums.AccountStatus;
import com.creditbureau.simulator.domain.enums.AccountType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Represents a merged logical credit account, combining one or more tradeline
 * submissions that represent the same underlying account.
 * 
 * Mutable during the merging phase; effectively immutable once processing is complete.
 */
public class CreditAccount {
    private final String accountId;
    private final String consumerId;
    private final String lender;
    private final AccountType accountType;
    private final LocalDate openDate;
    private LocalDate closeDate;
    private BigDecimal creditLimit;
    private BigDecimal outstandingBalance;
    private AccountStatus currentStatus;
    private final List<Tradeline> mergedTradelines;
    private final List<DPDRecord> dpdHistory;
    private final List<AccountEvent> events;

    public CreditAccount(String accountId, String consumerId, String lender,
                          AccountType accountType, LocalDate openDate,
                          BigDecimal creditLimit, BigDecimal outstandingBalance,
                          AccountStatus currentStatus) {
        this.accountId = accountId;
        this.consumerId = consumerId;
        this.lender = lender;
        this.accountType = accountType;
        this.openDate = openDate;
        this.creditLimit = creditLimit;
        this.outstandingBalance = outstandingBalance;
        this.currentStatus = currentStatus;
        this.mergedTradelines = new ArrayList<>();
        this.dpdHistory = new ArrayList<>();
        this.events = new ArrayList<>();
    }

    /** Generate a deterministic account ID from lender, type, and open date. */
    public static String generateAccountId(String lender, AccountType type, LocalDate openDate) {
        return lender.toUpperCase() + "-" + type.name() + "-" + openDate.toString().replace("-", "");
    }

    /** Merge another tradeline into this account (BR-02, BR-03). */
    public void mergeTradeline(Tradeline tradeline) {
        mergedTradelines.add(tradeline);
        // Latest submission wins for current attributes (BR-02)
        // Determined by submission date, then file order index for tie-breaking
        Tradeline latest = mergedTradelines.stream()
                .max(Comparator.comparing(Tradeline::submissionDate)
                        .thenComparingInt(Tradeline::fileOrderIndex))
                .orElseThrow();
        this.creditLimit = latest.creditLimit();
        this.outstandingBalance = latest.outstandingBalance();
        this.currentStatus = latest.status();
    }

    /** Add a DPD record, avoiding duplicates for the same reporting month (BR-14). */
    public void addDPDRecord(DPDRecord record) {
        // If we already have a record for this month, keep the one from the latest submission
        Optional<DPDRecord> existing = dpdHistory.stream()
                .filter(r -> r.reportingMonth().equals(record.reportingMonth()))
                .findFirst();
        if (existing.isPresent()) {
            DPDRecord old = existing.get();
            if (record.submissionDate().isAfter(old.submissionDate()) ||
                (record.submissionDate().equals(old.submissionDate()) && true)) {
                // Replace with newer record
                dpdHistory.remove(old);
                dpdHistory.add(record);
            }
            // If old is newer, keep old (do nothing)
        } else {
            dpdHistory.add(record);
        }
    }

    public void addEvent(AccountEvent event) {
        events.add(event);
    }

    public void setCloseDate(LocalDate closeDate) {
        this.closeDate = closeDate;
    }

    // --- Query methods ---

    /** Check if account is under active dispute at the given date (BR-40). */
    public boolean isUnderDisputeAt(LocalDate asOfDate) {
        return events.stream().anyMatch(e -> e.isActiveDisputeAt(asOfDate));
    }

    /** Check if dispute was resolved as VALID by as-of date (BR-41, BR-43). */
    public boolean isDisputeResolvedValidAt(LocalDate asOfDate) {
        return events.stream().anyMatch(e -> e.isDisputeResolvedValidAt(asOfDate));
    }

    /** Check if dispute was resolved as REMOVED by as-of date (BR-42). */
    public boolean isDisputeResolvedRemovedAt(LocalDate asOfDate) {
        return events.stream().anyMatch(e -> e.isDisputeResolvedRemovedAt(asOfDate));
    }

    /** Check if this account should be excluded from scoring at the as-of date. */
    public boolean isExcludedFromScoring(LocalDate asOfDate) {
        // Excluded if under active dispute (not yet resolved)
        if (isUnderDisputeAt(asOfDate)) return true;
        // Excluded if dispute resolved as REMOVED
        if (isDisputeResolvedRemovedAt(asOfDate)) return true;
        return false;
    }

    /** Check if account is active (not closed) at the as-of date. */
    public boolean isActiveAt(LocalDate asOfDate) {
        if (currentStatus == AccountStatus.CLOSED && closeDate != null) {
            return closeDate.isAfter(asOfDate);
        }
        return currentStatus == AccountStatus.ACTIVE || 
               currentStatus == AccountStatus.WRITTEN_OFF ||
               currentStatus == AccountStatus.SETTLED ||
               currentStatus == AccountStatus.SUIT_FILED ||
               currentStatus == AccountStatus.DISPUTE;
    }

    /** Check if closed account should still contribute to payment history (BR-11). */
    public boolean contributesToPaymentHistory(LocalDate asOfDate) {
        if (currentStatus != AccountStatus.CLOSED) return true;
        if (closeDate == null) return true;
        // Closed accounts contribute for 36 months after closure
        return !asOfDate.isAfter(closeDate.plusMonths(36));
    }

    /** Get DPD records within the specified month window from as-of date. */
    public List<DPDRecord> getDPDRecordsInWindow(LocalDate asOfDate, int startMonth, int endMonth) {
        List<DPDRecord> result = new ArrayList<>();
        for (int i = startMonth; i <= endMonth; i++) {
            YearMonth targetMonth = YearMonth.from(asOfDate).minusMonths(i);
            dpdHistory.stream()
                    .filter(r -> r.reportingMonth().equals(targetMonth))
                    .findFirst()
                    .ifPresent(result::add);
        }
        return result;
    }

    /** Get the worst (maximum) DPD value in a month window. Returns -1 if no records. */
    public int getWorstDPDInWindow(LocalDate asOfDate, int startMonth, int endMonth) {
        return getDPDRecordsInWindow(asOfDate, startMonth, endMonth).stream()
                .mapToInt(DPDRecord::dpdValue)
                .max()
                .orElse(-1);
    }

    public AccountCategory getCategory() {
        return accountType.getCategory();
    }

    // --- Standard getters ---

    public String getAccountId() { return accountId; }
    public String getConsumerId() { return consumerId; }
    public String getLender() { return lender; }
    public AccountType getAccountType() { return accountType; }
    public LocalDate getOpenDate() { return openDate; }
    public LocalDate getCloseDate() { return closeDate; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public BigDecimal getOutstandingBalance() { return outstandingBalance; }
    public AccountStatus getCurrentStatus() { return currentStatus; }
    public List<Tradeline> getMergedTradelines() { return Collections.unmodifiableList(mergedTradelines); }
    public List<DPDRecord> getDpdHistory() { return Collections.unmodifiableList(dpdHistory); }
    public List<AccountEvent> getEvents() { return Collections.unmodifiableList(events); }

    @Override
    public String toString() {
        return "CreditAccount{" + accountId + ", " + lender + ", " + accountType + 
               ", opened=" + openDate + ", status=" + currentStatus + "}";
    }
}
