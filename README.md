# Credit Bureau Score Simulation Engine (PS-022)

A deterministic, production-quality Credit Bureau Score Simulation Engine built in Java 17. The engine simulates how a modern credit bureau ingests monthly tradeline data, hard/soft enquiries, and account events from multiple lenders, detects and merges duplicate accounts, builds 36-month repayment grids, and computes credit scores between 300 and 900 based on standard BFSI factor-weighted methodologies and risk overrides.

---

## 🚀 Key Features

- **Strict Date-Based Ingestion**: Ingests monthly tradeline submissions, enquiries, and account events (write-offs, settlements, suits filed, disputes). Ensures no future information contaminates historical evaluation dates (**BR-46**).
- **Intelligent Account Merging**: Merges multi-lender account duplicates when lender, account type match, open dates are within 30 days, and credit limits differ by ≤ 5% (**BR-01, BR-02, BR-03**).
- **36-Month Repayment Grid**: Tracks monthly Days Past Due (DPD) for 36 months, distinguishing between zero DPD (`STD`), delays (`SMA-0`, `SMA-1`, `SMA-2`), major defaults (`NPA` / `90+`), and missing months (`XXX`).
- **Comprehensive Factor-Based Scoring Model**:
  - **Base Score**: 300 points (**BR-44**)
  - **Payment History**: Up to 300 points with recency weighting (M1-12 full, M13-36 half-weight, BNPL 50% discount) (**BR-07 to BR-14**)
  - **Credit Utilisation**: Up to 200 points based on revolving balances/limits (**BR-15 to BR-21**)
  - **Credit Age**: Up to 100 points based on oldest open account calendar age (**BR-22 to BR-24**)
  - **Credit Mix**: Up to 100 points for balanced secured/unsecured credit portfolios (**BR-25 to BR-29**)
  - **Enquiries**: Up to -100 point deduction, with same-lender 14-day deduplication (**BR-30 to BR-35**)
  - **Score Ceiling**: Capped at 900 points (**BR-45**)
- **Risk Overrides**: Strict caps that override factor scores:
  - 90+ DPD within last 6 months → Cap at 650 (**BR-36**)
  - Written-off / Settled within last 24 months → Cap at 600 (**BR-37**)
  - Suit filed → Cap at 550 (**BR-38**)
  - Strictest cap wins (**BR-39**)
- **Retroactive Dispute Resolution**: Excludes accounts under dispute; recomputes on demand when resolved as valid (**BR-40 to BR-43**).
- **Historical Score Calculation**: Recomputes point-in-time score history for any as-of date (**BR-48**).

---

## 🛠️ Technology Stack

- **Language**: Java 17 (Temurin / OpenJDK)
- **Build Tool**: Apache Maven 3.9+
- **Architecture**: Modular Layered Architecture (SOLID principles)
- **Testing**: JUnit 5 (39 automated unit, integration, boundary, and regression tests)
- **Precision**: `BigDecimal` for currency and utilisation arithmetic
- **Dates**: Java `java.time` (`LocalDate`, `YearMonth`, `Period`, `ChronoUnit`)
- **Logging**: SLF4J with Logback

---

## 📦 Build & Execution

### Prerequisites
- JDK 17 or higher
- Maven 3.8+ (or included Maven binary)

### 1. Compile and Run Tests
```bash
mvn clean test
```

### 2. Build Fat Executable JAR
```bash
mvn clean package -DskipTests
```
The resulting executable JAR will be located at:
`target/credit-bureau-simulator-1.0.0.jar`

---

## 💻 CLI Commands

### 1. Run Complete Pipeline (Ingest, Score & Generate Full Report)
```bash
java -jar target/credit-bureau-simulator-1.0.0.jar run-all \
  --data ./sample-data \
  --consumer CUST-1001 \
  --as-of 2026-10-31
```

### 2. Generate Credit Report Only
```bash
java -jar target/credit-bureau-simulator-1.0.0.jar generate-report \
  --data ./sample-data \
  --consumer CUST-1001 \
  --as-of 2026-10-31
```

### 3. Calculate Score Only
```bash
java -jar target/credit-bureau-simulator-1.0.0.jar calculate-score \
  --data ./sample-data \
  --consumer CUST-1001 \
  --as-of 2026-10-31
```

### 4. Build Consumer Profile
```bash
java -jar target/credit-bureau-simulator-1.0.0.jar build-profile \
  --data ./sample-data \
  --consumer CUST-1001 \
  --as-of 2026-10-31
```

### 5. View Score History Across Dates
```bash
java -jar target/credit-bureau-simulator-1.0.0.jar score-history \
  --data ./sample-data \
  --consumer CUST-1002
```

---

## 📁 Project Structure

```
credit-bureau-simulator/
├── pom.xml
├── README.md
├── sample-data/
│   ├── tradelines.csv
│   ├── enquiries.csv
│   └── events.csv
├── docs/
│   ├── Architecture.md
│   ├── BusinessRules.md
│   ├── DataDictionary.md
│   ├── TestCases.md
│   └── VivaPreparation.md
└── src/
    ├── main/
    │   ├── java/com/creditbureau/simulator/
    │   │   ├── CreditBureauApp.java
    │   │   ├── cli/
    │   │   ├── domain/model/
    │   │   ├── domain/enums/
    │   │   ├── ingestion/
    │   │   ├── processing/
    │   │   ├── scoring/
    │   │   ├── reporting/
    │   │   └── persistence/
    │   └── resources/logback.xml
    └── test/
        └── java/com/creditbureau/simulator/
            ├── integration/
            ├── processing/
            └── scoring/
```

---

## 📚 Documentation Links
- [Business Rules Register](docs/BusinessRules.md)
- [System Architecture](docs/Architecture.md)
- [Data Dictionary](docs/DataDictionary.md)
- [Test Cases & Traceability Matrix](docs/TestCases.md)
- [Viva Preparation Guide](docs/VivaPreparation.md)

