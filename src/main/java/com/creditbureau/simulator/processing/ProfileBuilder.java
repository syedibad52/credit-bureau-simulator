package com.creditbureau.simulator.processing;

import com.creditbureau.simulator.domain.enums.AccountCategory;
import com.creditbureau.simulator.domain.enums.AccountStatus;
import com.creditbureau.simulator.domain.enums.AccountType;
import com.creditbureau.simulator.domain.enums.EnquiryType;
import com.creditbureau.simulator.domain.model.CreditAccount;
import com.creditbureau.simulator.domain.model.CreditProfile;
import com.creditbureau.simulator.domain.model.Enquiry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates a comprehensive Consumer Credit Profile at a specific as-of date (Phase 7).
 */
public class ProfileBuilder {

    private static final BigDecimal BNPL_UTILISATION_THRESHOLD = new BigDecimal("20000");

    /**
     * Build the consumer credit profile from merged accounts and enquiries at asOfDate.
     */
    public CreditProfile buildProfile(String consumerId, List<CreditAccount> accounts, List<Enquiry> enquiries, LocalDate asOfDate) {
        CreditProfile profile = new CreditProfile();
        profile.setConsumerId(consumerId);
        profile.setAsOfDate(asOfDate);
        profile.setMergedAccountCount(accounts.size());

        int activeCount = 0;
        int closedCount = 0;
        Set<AccountType> types = new HashSet<>();
        BigDecimal revolvingLimit = BigDecimal.ZERO;
        BigDecimal revolvingBalance = BigDecimal.ZERO;

        LocalDate oldestOpenDate = null;
        boolean hasSecured = false;
        boolean hasUnsecured = false;

        for (CreditAccount acc : accounts) {
            types.add(acc.getAccountType());

            boolean isDisputed = acc.isExcludedFromScoring(asOfDate);
            boolean isClosed = acc.getCurrentStatus() == AccountStatus.CLOSED ||
                    (acc.getCloseDate() != null && !acc.getCloseDate().isAfter(asOfDate));

            if (isClosed) {
                closedCount++;
            } else {
                activeCount++;
            }

            // Calculations for open, non-disputed accounts
            if (!isClosed && !isDisputed) {
                // Oldest open account age (BR-22)
                if (oldestOpenDate == null || acc.getOpenDate().isBefore(oldestOpenDate)) {
                    oldestOpenDate = acc.getOpenDate();
                }

                // Credit Mix (BR-25 to BR-29)
                if (acc.getCategory() == AccountCategory.SECURED) {
                    hasSecured = true;
                } else {
                    hasUnsecured = true;
                }

                // Revolving Utilisation (BR-15 to BR-21)
                // Credit Card: limit > 0
                if (acc.getAccountType() == AccountType.CC) {
                    if (acc.getCreditLimit().compareTo(BigDecimal.ZERO) > 0) {
                        revolvingLimit = revolvingLimit.add(acc.getCreditLimit());
                        revolvingBalance = revolvingBalance.add(acc.getOutstandingBalance());
                    }
                } else if (acc.getAccountType() == AccountType.BNPL) {
                    // BNPL counts towards revolving utilisation only if limit >= 20,000 (BR-19)
                    if (acc.getCreditLimit().compareTo(BNPL_UTILISATION_THRESHOLD) >= 0) {
                        revolvingLimit = revolvingLimit.add(acc.getCreditLimit());
                        revolvingBalance = revolvingBalance.add(acc.getOutstandingBalance());
                    }
                }
            }
        }

        profile.setActiveAccountCount(activeCount);
        profile.setClosedAccountCount(closedCount);
        profile.setAccountTypes(types);
        profile.setTotalRevolvingLimit(revolvingLimit);
        profile.setTotalRevolvingBalance(revolvingBalance);

        if (revolvingLimit.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal util = revolvingBalance.multiply(BigDecimal.valueOf(100))
                    .divide(revolvingLimit, 2, RoundingMode.HALF_UP);
            profile.setRevolvingUtilisation(util);
        } else {
            profile.setRevolvingUtilisation(BigDecimal.ZERO);
        }

        if (oldestOpenDate != null) {
            profile.setOldestAccountOpenDate(oldestOpenDate);
            profile.setOldestAccountAge(Period.between(oldestOpenDate, asOfDate));
        } else {
            profile.setOldestAccountAge(Period.ZERO);
        }

        profile.setHasMixedCredit(hasSecured && hasUnsecured);

        // Enquiries in last 12 months up to asOfDate
        int hardCount = 0;
        int softCount = 0;
        LocalDate twelveMonthsAgo = asOfDate.minusMonths(12);

        for (Enquiry enq : enquiries) {
            if (!enq.enquiryDate().isAfter(asOfDate) && !enq.enquiryDate().isBefore(twelveMonthsAgo)) {
                if (enq.type() == EnquiryType.HARD) {
                    hardCount++;
                } else {
                    softCount++;
                }
            }
        }

        profile.setHardEnquiryCount(hardCount);
        profile.setSoftEnquiryCount(softCount);

        return profile;
    }
}

