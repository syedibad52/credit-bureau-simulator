# System Architecture: Credit Bureau Score Simulation Engine (PS-022)

## 1. Architectural Philosophy

The Credit Bureau Score Simulation Engine follows a **modular, layered architecture** adhering to SOLID principles, separation of concerns, and deterministic execution. The design models real-world credit bureau architecture:
- **Correctness First**: Strict validation, exact decimal calculations, and deterministic sorting.
- **Maintainability Second**: Clean abstractions, independent factor calculators, and explicit business rule traceability.
- **Performance Third**: Lightweight in-memory data structures, streaming filters, and minimal memory footprint.

---

## 2. Layered Architecture

```
+-------------------------------------------------------------------+
|                           CLI Layer                               |
|        CreditBureauApp, CommandRouter, CommandArgs, Validator     |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                        Ingestion Layer                            |
|    CsvParser, FieldValidator, TradelineIngester, EnquiryIngester  |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                       Processing Layer                            |
|    AsOfDateFilter, AccountMergingEngine, DPDHistoryEngine,        |
|    ProfileBuilder                                                 |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                         Scoring Layer                             |
|    ScoringEngine, ThinFileChecker, PaymentHistoryCalculator,      |
|    UtilisationCalculator, CreditAgeCalculator, CreditMixCalc,    |
|    EnquiryCalculator, OverrideEngine, DisputeEngine               |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                        Reporting Layer                            |
|       CreditReportGenerator, DPDGridFormatter, ReportFormatter    |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                      Domain & Persistence                         |
|     Consumer, CreditAccount, Tradeline, DPDRecord, Enquiry,       |
|     AccountEvent, ScoreResult, DataStore, DataStoreInterface      |
+-------------------------------------------------------------------+
```

---

## 3. Core Modules & Responsibilities

### Ingestion Layer (`com.creditbureau.simulator.ingestion`)
- **`CsvParser`**: Robust RFC 4180-compliant CSV parsing, handling whitespace trimming, header validation, empty/comment lines, and deterministic multi-file ordering.
- **`FieldValidator`**: Type validation, range checks, and enum translation with descriptive error reporting.
- **`TradelineIngester`**, **`EnquiryIngester`**, **`EventIngester`**: Typed dataset loaders populating immutable domain records.

### Processing Layer (`com.creditbureau.simulator.processing`)
- **`AsOfDateFilter`**: Point-in-time boundary filter ensuring no record with timestamp > as-of date enters the pipeline (**BR-46**).
- **`AccountMergingEngine`**: Identifies duplicate tradelines across lenders using 30-day open date window and 5% sanctioned limit threshold (**BR-01**). Resolves attributes favoring the latest eligible submission (**BR-02**) and retains full historical DPD records (**BR-03**).
- **`DPDHistoryEngine`**: Constructs 36-month repayment grids backwards from the as-of date, categorizing delinquencies (`STD`, `SMA-0`, `SMA-1`, `SMA-2`, `NPA`, `XXX`).
- **`ProfileBuilder`**: Synthesizes merged consumer profiles, aggregating revolving credit limits, utilisation, portfolio mix, and enquiry frequencies.

### Scoring Layer (`com.creditbureau.simulator.scoring`)
- **`ScoringEngine`**: Main orchestrator. Evaluates thin file criteria, executes factor calculators, caps pre-override score at 900, evaluates risk overrides, and logs applied rule IDs.
- **`ThinFileChecker`**: Validates minimum 6-month history and eligible account existence (**BR-04, BR-05, BR-06**).
- **Factor Calculators**:
  - `PaymentHistoryCalculator`: Recency-weighted deductions (M1-12 full, M13-36 half-weight, BNPL 50%).
  - `UtilisationCalculator`: Exact `BigDecimal` revolving ratio evaluation across tiered bands.
  - `CreditAgeCalculator`: Calendar-exact age evaluation from oldest active account.
  - `CreditMixCalculator`: Portfolio diversification analysis.
  - `EnquiryCalculator`: Hard enquiry deductions with 14-day same-lender clustering.
- **`OverrideEngine`**: Enforces strict risk-based score capping (650 for recent 90+ DPD, 600 for write-offs/settlements, 550 for suits filed).
- **`DisputeEngine`**: Tracks active disputes and enables retroactive re-scoring on resolution.
- **`ScoreHistoryEngine`**: Point-in-time historical score computation across any sequence of dates.

### Reporting Layer (`com.creditbureau.simulator.reporting`)
- **`CreditReportGenerator`**: Formats comprehensive CLI credit reports with clear factor contributions, 36-month grids, applied overrides, and rule IDs.
- **`DPDGridFormatter`**: Renders 36-month DPD timelines in readable 12-month row layouts.

---

## 4. Key Design Decisions

1. **Exact Precision (`BigDecimal`)**:
   Financial values (`creditLimit`, `outstandingBalance`) and utilisation calculations use `BigDecimal` with `RoundingMode.HALF_UP` to prevent floating-point inaccuracies.

2. **Java 17 Features**:
   - `record` for immutable input events and results (`Tradeline`, `Enquiry`, `AccountEvent`, `DPDRecord`, `ScoreResult`).
   - Enhanced `switch` expressions and pattern matching for command routing and enum dispatch.
   - Standard `java.time` APIs (`LocalDate`, `YearMonth`, `Period`, `ChronoUnit`) for accurate calendar arithmetic, eliminating leap-year inaccuracies.

3. **Pluggable Persistence (`DataStoreInterface`)**:
   The domain and scoring engines depend solely on `DataStoreInterface`, permitting immediate in-memory simulation and seamless future migration to relational databases (e.g. PostgreSQL, H2).

