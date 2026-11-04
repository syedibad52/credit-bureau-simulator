# Technical Viva & Architectural Defense Guide (PS-022)

This guide provides targeted explanations, answers to challenging viva questions, and domain justification for college evaluations and technical interviews.

---

## 1. High-Level System Pitch (30-Second Elevator Pitch)

> "We built a deterministic, production-quality Credit Bureau Score Simulation Engine in Java 17 that simulates the core processing workflow of bureaus like CIBIL, Experian, and Equifax. The engine ingests multi-lender monthly tradelines, enquiries, and account events, merges duplicate accounts using strict similarity rules, maintains a 36-month Days Past Due (DPD) repayment grid, computes an un-gamed factor-weighted credit score between 300 and 900, and enforces non-negotiable risk overrides and retroactive dispute resolution. The design prioritizes correctness first, maintainability second, and performance third."

---

## 2. Frequently Asked Technical Questions & Answers

### Q1: Why use `BigDecimal` instead of `double` or `float`?
**Answer**: In financial systems, binary floating-point representations (`double`, `float`) introduce rounding errors due to base-2 IEEE 754 representations (e.g. `0.1 + 0.2 != 0.3`). For credit limits, outstanding balances, and exact utilisation percentages (such as the 30.0% boundary in **BR-16**), `BigDecimal` ensures exact decimal arithmetic and predictable `HALF_UP` rounding.

---

### Q2: How does the engine identify duplicate accounts across different lender submissions?
**Answer**: As mandated by **BR-01**, two tradeline submissions are identified as duplicate representations of the same underlying credit account if and only if:
1. The **lender** name is identical (case-insensitive).
2. The **account type** is identical.
3. The **open dates** are within **30 calendar days** of each other (inclusive).
4. The **credit limits** differ by no more than **5%** relative to the larger amount (`|a - b| * 100 <= max(a, b) * 5`).
When matched, the later eligible submission wins for current balance, DPD, and status (**BR-02**), while all historical monthly DPD records are preserved (**BR-03**).

---

### Q3: How is the 36-Month DPD Grid structured, and how are missing months handled?
**Answer**: The repayment history is a backward-looking 36-month timeline starting at Month 1 (the calendar month of the evaluation as-of date) through Month 36 (35 months prior).
- If a lender submitted data for that calendar month, the DPD value is categorized:
  - `0`: Standard / STD
  - `1–29`: SMA-0 (Special Mention Account - 0)
  - `30–59`: SMA-1
  - `60–89`: SMA-2
  - `90+`: Non-Performing Asset / Major Default (NPA)
- If no submission was reported for a month, it is recorded as `XXX` (missing data), which is distinct from `STD` (0 DPD). Missing months do not incur DPD penalties.

---

### Q4: Explain the difference between Factor Deductions and Score Overrides.
**Answer**:
- **Factor Deductions** (e.g. Payment History, Enquiries) subtract points from the base potential score during formula evaluation.
- **Score Overrides** (**BR-36 to BR-39**) do **NOT** subtract points; they enforce an absolute risk ceiling (CAP) on the final score regardless of how high the factor calculations reached. For example, a consumer with an otherwise flawless profile and a raw score of 850 who experienced a 90+ DPD within the last 6 months has their score hard-capped at 650. If multiple overrides apply, the strictest (lowest cap) wins.

---

### Q5: How is dispute resolution retroactive without silently rewriting historical archives?
**Answer**: The engine implements on-demand point-in-time calculation. When computing a score for date $T$:
1. If an account had an active unresolved dispute on date $T$, it is excluded from scoring (**BR-40**).
2. If the dispute was resolved as `VALID` on date $T_{res} \le T$, the account is treated as eligible and included (**BR-41**).
3. If an evaluation is requested today for past date $T$ after the resolution occurred, the historical score is recomputed on-demand using the validated timeline, restoring the consumer's score retroactively without mutating a static database table.

---

### Q6: How does the engine guarantee determinism?
**Answer**:
- Input collections are sorted using explicit multi-key comparators before processing:
  - Tradelines: `submissionDate` → `lender` → `accountType` → `fileOrderIndex`.
  - Enquiries: `enquiryDate` → `lender` → `fileOrderIndex`.
- `fileOrderIndex` provides a guaranteed, stable tie-breaker.
- Verified by automated test `DeterminismTest.testDeterminismAcrossShuffledInputs`, where reversed and shuffled input files generate bit-for-bit identical credit reports and scores.

---

## 3. Score Breakdown Summary Table for Examiners

| Factor | Maximum Contribution | Core Logic |
|---|---|---|
| **Base Score** | 300 | Starting baseline for every scoring file |
| **Payment History** | 300 | Deductions based on worst DPD: M1-12 full, M13-36 half weight, BNPL 50% discount |
| **Credit Utilisation** | 200 | Revolving balance/limit ratio (≤30%: 200, 31-50%: 150, 51-75%: 90, 76-100%: 40, >100%: 0) |
| **Credit Age** | 100 | Age of oldest active account (<1 yr: 20, 1-3 yrs: 50, 3-7 yrs: 80, 7+ yrs: 100) |
| **Credit Mix** | 100 | Portfolio balance (Secured + Unsecured: 100, Single category: 60, Only BNPL: 30) |
| **Enquiries** | -100 (deduction) | Hard enquiries (M1-6: -15 each, M7-12: -5 each; 14-day same-lender deduplication) |
| **Score Ceiling** | 900 Cap | Total pre-override score is capped at 900 |
| **Risk Overrides** | Cap at 650 / 600 / 550 | 90+ DPD (650), Write-off/Settled (600), Suit Filed (550). Strictest cap wins |

