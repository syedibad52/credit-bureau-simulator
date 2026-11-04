# Automated Test Suite & Traceability Matrix (PS-022)

The Credit Bureau Score Simulation Engine features 39 automated JUnit 5 tests covering all 48 business rules, boundary conditions, edge cases, integration workflows, and determinism requirements.

---

## 1. Specification Edge Case Coverage Matrix (22 Mandatory Cases)

| Test ID | Edge Case Description | Affected Rules | Test Method | Class | Result |
|---|---|---|---|---|---|
| **T-01** | Utilisation exactly 30.0% | BR-16 | `testUtilisationExactlyThirtyPercent` | `UtilisationCalculatorTest` | PASS |
| **T-02** | Utilisation exactly 100.0% | BR-16 | `testUtilisationExactlyHundredPercent` | `UtilisationCalculatorTest` | PASS |
| **T-03** | Oldest account exactly 1 year old | BR-24 | `testOldestAccountExactlyOneYear` | `CreditAgeCalculatorTest` | PASS |
| **T-04** | Enquiries exactly 14 days apart from same lender | BR-33 | `testEnquiriesExactly14DaysApart` | `EnquiryCalculatorTest` | PASS |
| **T-05** | DPD 90 in Month 6 before as-of date | BR-36 | `testDPD90InMonth6TriggersCap` | `OverrideEngineTest` | PASS |
| **T-06** | DPD 90 in Month 7 before as-of date | BR-36 | `testDPD90InMonth7DoesNotTriggerCap` | `OverrideEngineTest` | PASS |
| **T-07** | Two submissions in same month with different balances | BR-02, BR-14 | `testMergeSameMonthDifferentBalance` | `AccountMergingEngineTest` | PASS |
| **T-08** | Dispute resolved as VALID | BR-41, BR-43 | `testDisputeResolvedAsValid` | `DisputeEngineTest` | PASS |
| **T-09** | Dispute resolved as REMOVED | BR-42 | `testDisputeResolvedAsRemoved` | `DisputeEngineTest` | PASS |
| **T-10** | Thin file with exactly 6 months history | BR-06 | `testExactlySixMonthsHistoryNotThinFile` | `ThinFileCheckerTest` | PASS |
| **T-11** | Score exceeding 900 before final cap | BR-45 | `testScoreCappedAt900` | `ScoringEngineIntegrationTest` | PASS |
| **T-12** | Account merging with exactly 30 days between open dates | BR-01 | `testMergeExactly30Days` | `AccountMergingEngineTest` | PASS |
| **T-13** | Account merging at exactly 5% sanctioned limit diff | BR-01 | `testMergeExactly5PercentDifference` | `AccountMergingEngineTest` | PASS |
| **T-14** | Closed account contributing to payment history | BR-11 | `testClosedAccountContributingToPaymentHistory` | `PaymentHistoryCalculatorTest` | PASS |
| **T-15** | Closed account excluded from utilisation | BR-20 | `testClosedAccountExcludedFromUtilisation` | `UtilisationCalculatorTest` | PASS |
| **T-16** | BNPL account with limit below ₹20,000 | BR-19 | `testBnplBelow20kExcluded` | `UtilisationCalculatorTest` | PASS |
| **T-17** | BNPL account with limit exactly ₹20,000 | BR-19 | `testBnplExactly20kIncluded` | `UtilisationCalculatorTest` | PASS |
| **T-18** | Multiple simultaneous overrides (strictest wins) | BR-39 | `testMultipleSimultaneousOverrides` | `OverrideEngineTest` | PASS |
| **T-19** | Soft enquiries producing zero deductions | BR-32 | `testSoftEnquiryZeroDeduction` | `EnquiryCalculatorTest` | PASS |
| **T-20** | Enquiries after as-of date excluded | BR-35 | `testFutureEnquiriesExcluded` | `EnquiryCalculatorTest` | PASS |
| **T-21** | Records received after as-of date excluded | BR-46 | `testFutureRecordsDoNotAffectPastScore` | `HistoricalScoreTest` | PASS |
| **T-22** | Disputed account exclusion across all factors | BR-40 | `testActiveDisputeExcludesAccount` | `DisputeEngineTest` | PASS |

---

## 2. Additional Automated Unit & Integration Tests

### Payment History
- `testCleanPaymentHistory`: 300/300 points awarded for clean accounts (**BR-07**)
- `testBnplHalfWeightDeduction`: 50% deduction weight on BNPL delinquency (**BR-12**)
- `testMonths13To36HalfWeight`: 50% deduction weight on older delinquency (**BR-09**)
- `testFloorAtZero`: Payment history deductions floor at 0, cannot be negative (**BR-10**)

### Credit Utilisation
- `testUtilisationOverHundredPercent`: Over-utilisation (>100%) receives 0 points (**BR-16**)

### Credit Age
- `testAccountUnderOneYear`: Account age < 1 year receives 20 points (**BR-23**)
- `testAccountSevenPlusYears`: Account age >= 7 years receives 100 points (**BR-23**)

### Credit Mix
- `testSecuredAndUnsecuredMix`: Balanced secured + unsecured portfolio receives 100 points (**BR-25**)
- `testOnlyUnsecuredCategory`: Single category portfolio receives 60 points (**BR-26**)
- `testOnlyBnplAccounts`: Only BNPL accounts portfolio receives 30 points (**BR-27**)

### Thin File
- `testUnderSixMonthsHistoryIsThinFile`: Credit history < 6 months returns score NA (**BR-04**)
- `testZeroEligibleAccountsIsThinFile`: Zero eligible tradelines returns score NA (**BR-05**)

### Determinism & Integration
- `testDeterminismAcrossShuffledInputs`: Processing shuffled tradeline and enquiry order produces bit-for-bit identical scores, deductions, and factors (**BR-47**)
- `testRunAllPrimeConsumer`: End-to-end CLI workflow for prime profile (**CUST-1001**)
- `testRunAllDelinquentConsumer`: End-to-end CLI workflow for delinquency override (**CUST-1002**)
- `testRunAllThinFileConsumer`: End-to-end CLI workflow for thin-file profile (**CUST-1003**)
- `testDisputeRetroactiveRecalculation`: End-to-end historical recalculation on valid dispute (**BR-43**)

