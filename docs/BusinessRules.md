# Business Rules Register (PS-022)

This document details all 48 business rules implemented in the Credit Bureau Score Simulation Engine, mapped directly to their implementation classes and automated test cases.

---

## 1. Account Merging (BR-01 to BR-03)

| Rule ID | Rule Name | Specification | Implementation | Test Case |
|---|---|---|---|---|
| **BR-01** | Account Duplicate Identification | Two submissions belong to the same account if: identical lender (case-insensitive), identical account type, open dates within 30 calendar days (inclusive), and credit limit / sanctioned amount differs by ≤ 5% relative to the larger amount. | `AccountMergingEngine.isSameAccount()` | `AccountMergingEngineTest.testMergeExactly30Days`, `testMergeExactly5PercentDifference` |
| **BR-02** | Latest Submission Priority | Later eligible submission wins for current balance, current DPD, credit limit, and account status. Ties broken deterministically by file order index. | `AccountMergingEngine.mergeAccounts()`, `CreditAccount.mergeTradeline()` | `AccountMergingEngineTest.testMergeSameMonthDifferentBalance` |
| **BR-03** | Repayment History Preservation | Merging preserves all historical monthly DPD observations from constituent submissions. | `CreditAccount.addDPDRecord()` | `AccountMergingEngineTest.testMergeSameMonthDifferentBalance` |

---

## 2. Thin File Handling (BR-04 to BR-06)

| Rule ID | Rule Name | Specification | Implementation | Test Case |
|---|---|---|---|---|
| **BR-04** | Insufficient Credit Duration | Return score NA if credit history duration is < 6 calendar months from oldest open account to as-of date. | `ThinFileChecker.check()` | `ThinFileCheckerTest.testUnderSixMonthsHistoryIsThinFile` |
| **BR-05** | No Eligible Tradeline | Return score NA if consumer has fewer than 1 eligible tradeline (non-disputed/non-removed). | `ThinFileChecker.check()` | `ThinFileCheckerTest.testZeroEligibleAccountsIsThinFile` |
| **BR-06** | Exactly 6 Months Duration | Exactly 6 calendar months of history is NOT classified as thin file. | `ThinFileChecker.check()` | `ThinFileCheckerTest.testExactlySixMonthsHistoryNotThinFile` |

---

## 3. Payment History (BR-07 to BR-14) - Max 300 Points

| Rule ID | Rule Name | Specification | Implementation | Test Case |
|---|---|---|---|---|
| **BR-07** | Payment History Base | Starts with 300 points. Clean accounts receive 300. | `PaymentHistoryCalculator.calculate()` | `PaymentHistoryCalculatorTest.testCleanPaymentHistory` |
| **BR-08** | Months 1–12 DPD Deductions | Per-account worst DPD in M1-12: 1-29 DPD: -20; 30-59 DPD: -60; 60-89 DPD: -100; 90+ DPD: -150. | `PaymentHistoryCalculator.calculate()` | `PaymentHistoryCalculatorTest` |
| **BR-09** | Months 13–36 Half-Weight | Same DPD tiers apply at 50% deduction weight for months 13 to 36. | `PaymentHistoryCalculator.calculate()` | `PaymentHistoryCalculatorTest.testMonths13To36HalfWeight` |
| **BR-10** | Payment History Floor | Total payment history contribution cannot be negative (floor = 0). | `PaymentHistoryCalculator.calculate()` | `PaymentHistoryCalculatorTest.testFloorAtZero` |
| **BR-11** | Closed Account DPD Inclusion | Closed accounts contribute DPD deductions for 36 months following closure date. | `CreditAccount.contributesToPaymentHistory()` | `PaymentHistoryCalculatorTest.testClosedAccountContributingToPaymentHistory` |
| **BR-12** | BNPL Half-Weight DPD | DPD deductions on Buy-Now-Pay-Later (BNPL) accounts receive a 50% discount. Stacks multiplicatively with M13-36. | `PaymentHistoryCalculator.calculate()` | `PaymentHistoryCalculatorTest.testBnplHalfWeightDeduction` |
| **BR-13** | Disputed Account Exclusion | Accounts under active dispute are excluded from payment history deductions. | `PaymentHistoryCalculator.calculate()` | `DisputeEngineTest.testActiveDisputeExcludesAccount` |
| **BR-14** | Duplicate Month Deduplication | Only one DPD entry per account per calendar month is recorded. | `CreditAccount.addDPDRecord()` | `AccountMergingEngineTest.testMergeSameMonthDifferentBalance` |

---

## 4. Credit Utilisation (BR-15 to BR-21) - Max 200 Points

| Rule ID | Rule Name | Specification | Implementation | Test Case |
|---|---|---|---|---|
| **BR-15** | Utilisation Formula | Revolving Utilisation = Total Revolving Balances / Total Revolving Limits. | `UtilisationCalculator.calculate()` | `UtilisationCalculatorTest` |
| **BR-16** | Utilisation Scoring Bands | ≤30%: 200 pts; >30% & ≤50%: 150 pts; >50% & ≤75%: 90 pts; >75% & ≤100%: 40 pts; >100%: 0 pts. | `UtilisationCalculator.calculate()` | `UtilisationCalculatorTest.testUtilisationExactlyThirtyPercent`, `testUtilisationExactlyHundredPercent` |
| **BR-17** | No CC Accounts Default | Consumers with no eligible revolving accounts receive a fixed 120 points. | `UtilisationCalculator.calculate()` | `UtilisationCalculatorTest.testBnplBelow20kExcluded` |
| **BR-18** | Zero Limit CC Exclusion | Credit card accounts with limit = 0 are excluded from utilisation. | `UtilisationCalculator.calculate()` | `UtilisationCalculatorTest` |
| **BR-19** | BNPL Limit Threshold | BNPL accounts contribute to utilisation only if sanctioned limit ≥ ₹20,000. | `UtilisationCalculator.calculate()` | `UtilisationCalculatorTest.testBnplBelow20kExcluded`, `testBnplExactly20kIncluded` |
| **BR-20** | Closed Account Exclusion | Closed accounts do not contribute to utilisation. | `UtilisationCalculator.calculate()` | `UtilisationCalculatorTest.testClosedAccountExcludedFromUtilisation` |
| **BR-21** | Disputed Account Exclusion | Disputed accounts are excluded from revolving balance/limit aggregation. | `UtilisationCalculator.calculate()` | `DisputeEngineTest.testActiveDisputeExcludesAccount` |

---

## 5. Credit Age (BR-22 to BR-24) - Max 100 Points

| Rule ID | Rule Name | Specification | Implementation | Test Case |
|---|---|---|---|---|
| **BR-22** | Oldest Open Account Age | Measured from open date of oldest eligible open account to as-of date using calendar dates. | `CreditAgeCalculator.calculate()` | `CreditAgeCalculatorTest` |
| **BR-23** | Age Scoring Bands | < 1 year: 20 pts; 1–3 years: 50 pts; 3–7 years: 80 pts; 7+ years: 100 pts. | `CreditAgeCalculator.calculate()` | `CreditAgeCalculatorTest.testAccountSevenPlusYears` |
| **BR-24** | Exactly 1 Year Boundary | Exactly 1 calendar year receives 50 points. | `CreditAgeCalculator.calculate()` | `CreditAgeCalculatorTest.testOldestAccountExactlyOneYear` |

---

## 6. Credit Mix (BR-25 to BR-29) - Max 100 Points

| Rule ID | Rule Name | Specification | Implementation | Test Case |
|---|---|---|---|---|
| **BR-25** | Balanced Portfolio | Both secured (HL, AL, GOLD) and unsecured (CC, PL, BNPL) active accounts: 100 points. | `CreditMixCalculator.calculate()` | `CreditMixCalculatorTest.testSecuredAndUnsecuredMix` |
| **BR-26** | Single Category | Only secured or only unsecured (non-BNPL): 60 points. | `CreditMixCalculator.calculate()` | `CreditMixCalculatorTest.testOnlyUnsecuredCategory` |
| **BR-27** | Only BNPL Active | Only BNPL accounts active: 30 points. | `CreditMixCalculator.calculate()` | `CreditMixCalculatorTest.testOnlyBnplAccounts` |
| **BR-28** | Closed Account Exclusion | Closed accounts do not contribute to active mix. | `CreditMixCalculator.calculate()` | `CreditMixCalculatorTest` |
| **BR-29** | Disputed Account Exclusion | Disputed accounts excluded from mix. | `CreditMixCalculator.calculate()` | `DisputeEngineTest` |

---

## 7. Enquiries (BR-30 to BR-35) - Max -100 Points Deduction

| Rule ID | Rule Name | Specification | Implementation | Test Case |
|---|---|---|---|---|
| **BR-30** | Recent Hard Enquiries | Hard enquiry in last 6 months: -15 points each. | `EnquiryCalculator.calculate()` | `EnquiryCalculatorTest` |
| **BR-31** | Older Hard Enquiries | Hard enquiry in months 7–12: -5 points each. | `EnquiryCalculator.calculate()` | `EnquiryCalculatorTest` |
| **BR-32** | Soft Enquiries | Soft enquiries cause 0 deduction. | `EnquiryCalculator.calculate()` | `EnquiryCalculatorTest.testSoftEnquiryZeroDeduction` |
| **BR-33** | Same-Lender Deduplication | Multiple hard enquiries from same lender within 14 calendar days count as one. | `EnquiryCalculator.deduplicate()` | `EnquiryCalculatorTest.testEnquiriesExactly14DaysApart` |
| **BR-34** | Deduction Ceiling | Total enquiry deduction capped at 100 points. | `EnquiryCalculator.calculate()` | `EnquiryCalculatorTest` |
| **BR-35** | Future Enquiry Exclusion | Enquiries after as-of date excluded. | `EnquiryCalculator.calculate()` | `EnquiryCalculatorTest.testFutureEnquiriesExcluded` |

---

## 8. Risk Overrides (BR-36 to BR-39)

| Rule ID | Rule Name | Specification | Implementation | Test Case |
|---|---|---|---|---|
| **BR-36** | Recent Delinquency Cap | Any 90+ DPD within last 6 months caps score at 650. | `OverrideEngine.evaluate()` | `OverrideEngineTest.testDPD90InMonth6TriggersCap`, `testDPD90InMonth7DoesNotTriggerCap` |
| **BR-37** | Write-Off / Settled Cap | Written-off or settled account within last 24 months caps score at 600. | `OverrideEngine.evaluate()` | `OverrideEngineTest.testMultipleSimultaneousOverrides` |
| **BR-38** | Suit Filed Cap | Suit filed against any account caps score at 550. | `OverrideEngine.evaluate()` | `OverrideEngineTest.testMultipleSimultaneousOverrides` |
| **BR-39** | Strictest Cap Priority | Multiple overrides resolve to the lowest (strictest) cap. | `OverrideEngine.evaluate()` | `OverrideEngineTest.testMultipleSimultaneousOverrides` |

---

## 9. Dispute Handling & History (BR-40 to BR-48)

| Rule ID | Rule Name | Specification | Implementation | Test Case |
|---|---|---|---|---|
| **BR-40** | Total Dispute Exclusion | Account under active dispute excluded across all scoring dimensions. | `DisputeEngine.isAccountExcluded()` | `DisputeEngineTest.testActiveDisputeExcludesAccount` |
| **BR-41** | Dispute Valid Resolution | Dispute resolved as VALID re-includes account from resolution date forward. | `DisputeEngine.isResolvedValid()` | `DisputeEngineTest.testDisputeResolvedAsValid` |
| **BR-42** | Dispute Removed Resolution | Dispute resolved as REMOVED permanently excludes account. | `DisputeEngine.isResolvedRemoved()` | `DisputeEngineTest.testDisputeResolvedAsRemoved` |
| **BR-43** | Retroactive Recalculation | Dispute resolved valid enables retroactive recalculation for affected dates. | `ScoreHistoryEngine.computeScoreAt()` | `HistoricalScoreTest.testDisputeRetroactiveRecalculation` |
| **BR-44** | Base Score Constant | System base score is 300 points. | `ScoringEngine.BASE_SCORE` | `ScoringEngineIntegrationTest` |
| **BR-45** | Maximum Score Cap | Pre-override score capped at 900 points. | `ScoringEngine.MAX_SCORE` | `ScoringEngineIntegrationTest.testScoreCappedAt900` |
| **BR-46** | As-Of Date Point-in-Time | Strictly filter all records submitted after as-of date. | `AsOfDateFilter` | `HistoricalScoreTest.testFutureRecordsDoNotAffectPastScore` |
| **BR-47** | Deterministic Results | Order of inputs does not alter final output. | `AccountMergingEngine`, `AsOfDateFilter` | `DeterminismTest.testDeterminismAcrossShuffledInputs` |
| **BR-48** | Score History Storage | Maintain historical score records per consumer per date. | `ScoreHistoryEngine` | `HistoricalScoreTest` |

