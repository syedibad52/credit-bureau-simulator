package com.creditbureau.simulator.domain.model;

import com.creditbureau.simulator.domain.enums.AccountType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a computed consumer credit profile at a specific as-of date.
 * Built by the ProfileBuilder from merged account data.
 */
public class CreditProfile {
    private String consumerId;
    private LocalDate asOfDate;
    private int mergedAccountCount;
    private int activeAccountCount;
    private int closedAccountCount;
    private Set<AccountType> accountTypes = new HashSet<>();
    private BigDecimal totalRevolvingLimit = BigDecimal.ZERO;
    private BigDecimal totalRevolvingBalance = BigDecimal.ZERO;
    private BigDecimal revolvingUtilisation = BigDecimal.ZERO;
    private Period oldestAccountAge = Period.ZERO;
    private LocalDate oldestAccountOpenDate;
    private boolean hasMixedCredit;
    private int hardEnquiryCount;
    private int softEnquiryCount;

    // All getters and setters
    public String getConsumerId() { return consumerId; }
    public void setConsumerId(String consumerId) { this.consumerId = consumerId; }
    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }
    public int getMergedAccountCount() { return mergedAccountCount; }
    public void setMergedAccountCount(int count) { this.mergedAccountCount = count; }
    public int getActiveAccountCount() { return activeAccountCount; }
    public void setActiveAccountCount(int count) { this.activeAccountCount = count; }
    public int getClosedAccountCount() { return closedAccountCount; }
    public void setClosedAccountCount(int count) { this.closedAccountCount = count; }
    public Set<AccountType> getAccountTypes() { return accountTypes; }
    public void setAccountTypes(Set<AccountType> types) { this.accountTypes = types; }
    public BigDecimal getTotalRevolvingLimit() { return totalRevolvingLimit; }
    public void setTotalRevolvingLimit(BigDecimal limit) { this.totalRevolvingLimit = limit; }
    public BigDecimal getTotalRevolvingBalance() { return totalRevolvingBalance; }
    public void setTotalRevolvingBalance(BigDecimal balance) { this.totalRevolvingBalance = balance; }
    public BigDecimal getRevolvingUtilisation() { return revolvingUtilisation; }
    public void setRevolvingUtilisation(BigDecimal util) { this.revolvingUtilisation = util; }
    public Period getOldestAccountAge() { return oldestAccountAge; }
    public void setOldestAccountAge(Period age) { this.oldestAccountAge = age; }
    public LocalDate getOldestAccountOpenDate() { return oldestAccountOpenDate; }
    public void setOldestAccountOpenDate(LocalDate date) { this.oldestAccountOpenDate = date; }
    public boolean isHasMixedCredit() { return hasMixedCredit; }
    public void setHasMixedCredit(boolean mixed) { this.hasMixedCredit = mixed; }
    public int getHardEnquiryCount() { return hardEnquiryCount; }
    public void setHardEnquiryCount(int count) { this.hardEnquiryCount = count; }
    public int getSoftEnquiryCount() { return softEnquiryCount; }
    public void setSoftEnquiryCount(int count) { this.softEnquiryCount = count; }

    @Override
    public String toString() {
        return "CreditProfile{" + consumerId + ", asOf=" + asOfDate + 
               ", accounts=" + mergedAccountCount + ", util=" + revolvingUtilisation + "%}";
    }
}
