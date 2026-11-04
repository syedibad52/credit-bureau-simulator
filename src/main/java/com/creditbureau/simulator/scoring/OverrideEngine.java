package com.creditbureau.simulator.scoring;

import com.creditbureau.simulator.domain.enums.AccountStatus;
import com.creditbureau.simulator.domain.enums.EventType;
import com.creditbureau.simulator.domain.model.AccountEvent;
import com.creditbureau.simulator.domain.model.CreditAccount;
import com.creditbureau.simulator.domain.model.OverrideResult;
import com.creditbureau.simulator.processing.DPDHistoryEngine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates score overrides (Phase 9, BR-36 through BR-39).
 * Overrides CAP the score rather than deducting points.
 * If multiple overrides apply, the strictest (lowest cap) wins.
 */
public class OverrideEngine {

    private final DPDHistoryEngine dpdEngine = new DPDHistoryEngine();

    public record Evaluation(
            List<OverrideResult> allResults,
            List<OverrideResult> appliedOverrides,
            Integer strictestCap
    ) {}

    /**
     * Evaluate all override rules at asOfDate.
     *
     * @param accounts credit accounts
     * @param events   account events
     * @param asOfDate evaluation date
     * @return Evaluation containing all evaluated rules and any active caps
     */
    public Evaluation evaluate(List<CreditAccount> accounts, List<AccountEvent> events, LocalDate asOfDate) {
        List<OverrideResult> allResults = new ArrayList<>();
        List<OverrideResult> applied = new ArrayList<>();

        // 1. BR-36: Any 90+ DPD within the last 6 months (Months 1 to 6) -> Cap = 650
        boolean hasRecent90DPD = false;
        String dpdReason = null;
        for (CreditAccount acc : accounts) {
            if (acc.isExcludedFromScoring(asOfDate)) {
                continue;
            }
            int worst6 = dpdEngine.getWorstDPD(acc, asOfDate, 1, 6);
            if (worst6 >= 90) {
                hasRecent90DPD = true;
                dpdReason = String.format("Account %s has DPD of %d within last 6 months", acc.getAccountId(), worst6);
                break;
            }
        }
        OverrideResult r1 = new OverrideResult(
                "BR-36",
                "Recent Severe Delinquency (90+ DPD in M1-6)",
                650,
                hasRecent90DPD,
                hasRecent90DPD ? dpdReason : "No 90+ DPD in last 6 months"
        );
        allResults.add(r1);
        if (hasRecent90DPD) applied.add(r1);

        // 2. BR-37: WRITTEN_OFF or SETTLED account status within the last 24 months -> Cap = 600
        LocalDate twentyFourMonthsAgo = asOfDate.minusMonths(24);
        boolean hasWrittenOffOrSettled = false;
        String writeOffReason = null;

        // Check account events
        for (AccountEvent ev : events) {
            if (!ev.eventDate().isAfter(asOfDate) && !ev.eventDate().isBefore(twentyFourMonthsAgo)) {
                if (ev.eventType() == EventType.WRITTEN_OFF || ev.eventType() == EventType.SETTLED) {
                    hasWrittenOffOrSettled = true;
                    writeOffReason = String.format("%s on %s for account %s", ev.eventType(), ev.eventDate(), ev.accountId());
                    break;
                }
            }
        }

        // Also check account status if set directly
        if (!hasWrittenOffOrSettled) {
            for (CreditAccount acc : accounts) {
                if (acc.getCurrentStatus() == AccountStatus.WRITTEN_OFF || acc.getCurrentStatus() == AccountStatus.SETTLED) {
                    // Check if open or closed/submitted within 24 months
                    LocalDate statusDate = acc.getCloseDate() != null ? acc.getCloseDate() : acc.getOpenDate();
                    if (!statusDate.isAfter(asOfDate) && !statusDate.isBefore(twentyFourMonthsAgo)) {
                        hasWrittenOffOrSettled = true;
                        writeOffReason = String.format("Account %s status is %s", acc.getAccountId(), acc.getCurrentStatus());
                        break;
                    }
                }
            }
        }

        OverrideResult r2 = new OverrideResult(
                "BR-37",
                "Written Off / Settled Account (Last 24 Months)",
                600,
                hasWrittenOffOrSettled,
                hasWrittenOffOrSettled ? writeOffReason : "No written off / settled accounts in last 24 months"
        );
        allResults.add(r2);
        if (hasWrittenOffOrSettled) applied.add(r2);

        // 3. BR-38: SUIT_FILED status -> Cap = 550 (A-10: Lifetime)
        boolean hasSuitFiled = false;
        String suitReason = null;

        for (AccountEvent ev : events) {
            if (!ev.eventDate().isAfter(asOfDate) && ev.eventType() == EventType.SUIT_FILED) {
                hasSuitFiled = true;
                suitReason = String.format("Suit filed on %s for account %s", ev.eventDate(), ev.accountId());
                break;
            }
        }

        if (!hasSuitFiled) {
            for (CreditAccount acc : accounts) {
                if (acc.getCurrentStatus() == AccountStatus.SUIT_FILED) {
                    hasSuitFiled = true;
                    suitReason = String.format("Account %s status is SUIT_FILED", acc.getAccountId());
                    break;
                }
            }
        }

        OverrideResult r3 = new OverrideResult(
                "BR-38",
                "Suit Filed",
                550,
                hasSuitFiled,
                hasSuitFiled ? suitReason : "No suit filed against any account"
        );
        allResults.add(r3);
        if (hasSuitFiled) applied.add(r3);

        // BR-39: Strictest cap wins
        Integer strictestCap = null;
        for (OverrideResult res : applied) {
            if (strictestCap == null || res.capValue() < strictestCap) {
                strictestCap = res.capValue();
            }
        }

        return new Evaluation(allResults, applied, strictestCap);
    }
}

