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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a merged logical credit account, combining one or more tradeline
 * submissions that represent the same underlying account.
 */
public class CreditAccount {
    private String mergedAccountId;
    private final String consumerId;
    private final String lender;
    private final AccountType accountType;
    private LocalDate openDate;
    private LocalDate closeDate;
    private BigDecimal creditLimit;
    private BigDecimal outstandingBalance;
    private AccountStatus currentStatus;
    private final Set<String> memberAccountNos;
    private final List<Tradeline> mergedTradelines;
    private final List<DPDRecord> dpdHistory;
    private final List<AccountEvent> events;

    public CreditAccount(String mergedAccountId, String consumerId, String lender,
                          AccountType accountType, LocalDate openDate,
                          BigDecimal creditLimit, BigDecimal outstandingBalance,
                          AccountStatus currentStatus) {
        this.mergedAccountId = mergedAccountId;
        this.consumerId = consumerId;
        this.lender = lender;
        this.accountType = accountType;
        this.openDate = openDate;
        this.creditLimit = creditLimit;
        this.outstandingBalance = outstandingBalance;
        this.currentStatus = currentStatus;
        this.memberAccountNos = new LinkedHashSet<>();
        this.mergedTradelines = new ArrayList<>();
        this.dpdHistory = new ArrayList<>();
        this.events = new ArrayList<>();
    }

    /** Generate a deterministic account ID from lender, type, and open date. */
    public static String generateAccountId(String lender, AccountType type, LocalDate openDate) {
        return lender.toUpperCase() + "-" + type.name() + "-" + openDate.toString().replace("-", "");
    }

    public void addMemberAccountNo(String accountNo) {
        if (accountNo != null && !accountNo.isBlank()) {
            memberAccountNos.add(accountNo);
        }
    }

    public void setMergedAccountId(String mergedAccountId) {
        this.mergedAccountId = mergedAccountId;
    }

    public void setOpenDate(LocalDate openDate) {
        this.openDate = openDate;
    }

    /** Merge another tradeline into this account (BR-02, BR-03). */
    public void mergeTradeline(Tradeline tradeline) {
        mergedTradelines.add(tradeline);
        addMemberAccountNo(tradeline.accountNo());

        // Update open date and mergedAccountId if this tradeline is older or breaks tie
        if (tradeline.openDate().isBefore(this.openDate) ||
                (tradeline.openDate().isEqual(this.openDate) && (this.lender.toUpperCase() + "-" + tradeline.accountNo()).compareTo(this.mergedAccountId) < 0)) {
            this.openDate = tradeline.openDate();
            this.mergedAccountId = this.lender.toUpperCase() + "-" + tradeline.accountNo();
        }

        // Latest submission wins for current attributes (BR-02)
        // Determined by receivedDate, then submissionId / fileOrderIndex
        Tradeline latest = mergedTradelines.stream()
                .max(Comparator.comparing(Tradeline::receivedDate)
                        .thenComparing(Tradeline::submissionId)
                        .thenComparingInt(Tradeline::fileOrderIndex))
                .orElseThrow();
        this.creditLimit = latest.sanctionedAmount();
        this.outstandingBalance = latest.balance();
        this.currentStatus = latest.status();
        if (latest.closedDate() != null) {
            this.closeDate = latest.closedDate();
        }
    }

    /** Add a DPD record, avoiding duplicates for the same reporting month (BR-14). */
    public void addDPDRecord(DPDRecord record) {
        Optional<DPDRecord> existing = dpdHistory.stream()
                .filter(r -> r.reportingMonth().equals(record.reportingMonth()))
                .findFirst();
        if (existing.isPresent()) {
            DPDRecord old = existing.get();
            if (record.submissionDate().isAfter(old.submissionDate()) ||
                record.submissionDate().isEqual(old.submissionDate())) {
                dpdHistory.remove(old);
                dpdHistory.add(record);
            }
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

    public void setCurrentStatus(AccountStatus status) {
        this.currentStatus = status;
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
        if (isUnderDisputeAt(asOfDate)) return true;
        if (isDisputeResolvedRemovedAt(asOfDate)) return true;
        return false;
    }

    public boolean isIncluded(LocalDate asOfDate) {
        return !isExcludedFromScoring(asOfDate);
    }

    public String getExclusionReason(LocalDate asOfDate) {
        if (isUnderDisputeAt(asOfDate)) return "DISPUTE";
        if (isDisputeResolvedRemovedAt(asOfDate)) return "REMOVED";
        return "";
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
        return !asOfDate.isAfter(closeDate.plusMonths(36));
    }

    /** Get DPD records within the specified 1-based month window from as-of date. */
    public List<DPDRecord> getDPDRecordsInWindow(LocalDate asOfDate, int startMonth, int endMonth) {
        List<DPDRecord> result = new ArrayList<>();
        YearMonth asOfMonth = YearMonth.from(asOfDate);
        for (int i = startMonth; i <= endMonth; i++) {
            YearMonth targetMonth = asOfMonth.minusMonths(i - 1);
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

    public int getWorstDpd12m(LocalDate asOfDate) {
        int worst = getWorstDPDInWindow(asOfDate, 1, 12);
        return Math.max(0, worst);
    }

    public AccountCategory getCategory() {
        return accountType.getCategory();
    }

    public String getAccountNosFormatted() {
        List<String> sorted = new ArrayList<>(memberAccountNos);
        Collections.sort(sorted);
        return String.join(";", sorted);
    }

    // --- Standard getters ---

    public String getAccountId() { return mergedAccountId; }
    public String getMergedAccountId() { return mergedAccountId; }
    public String getConsumerId() { return consumerId; }
    public String getLender() { return lender; }
    public AccountType getAccountType() { return accountType; }
    public LocalDate getOpenDate() { return openDate; }
    public LocalDate getCloseDate() { return closeDate; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public BigDecimal getOutstandingBalance() { return outstandingBalance; }
    public AccountStatus getCurrentStatus() { return currentStatus; }
    public Set<String> getMemberAccountNos() { return Collections.unmodifiableSet(memberAccountNos); }
    public List<Tradeline> getMergedTradelines() { return Collections.unmodifiableList(mergedTradelines); }
    public List<DPDRecord> getDpdHistory() { return Collections.unmodifiableList(dpdHistory); }
    public List<AccountEvent> getEvents() { return Collections.unmodifiableList(events); }

    @Override
    public String toString() {
        return "CreditAccount{" + mergedAccountId + ", " + lender + ", " + accountType + 
               ", opened=" + openDate + ", status=" + currentStatus + "}";
    }
}
