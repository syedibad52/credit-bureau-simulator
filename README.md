# Credit Bureau Score Simulator

A Java 17 project that turns raw credit-bureau-style CSV data into an explainable credit score and an interactive report.

It is designed as a portfolio project: a recruiter can see how data enters the system, how the score is calculated, which rule affected it, and how the result reaches the browser.

## What this project does

The simulator reads consumer accounts, enquiries, and account events from CSV files. For a selected consumer and date, it:

1. ignores information that was not known on that date;
2. merges duplicate account submissions from different lenders;
3. builds a 36-month repayment history;
4. calculates five score factors;
5. applies risk caps such as a recent serious delinquency; and
6. shows the final result, explanations, accounts, and applied rules in the web dashboard.

The score range is 300 to 900. This is a deterministic simulator, not a real credit-bureau product or production credit decisioning system.

## Quick start

### Prerequisites

- Java 17 or later
- Maven 3.8 or later

### Run the web dashboard

```bash
cd credit-bureau-simulator
mvn clean package
java -jar target/credit-bureau-simulator-1.0.0.jar serve --data ./sample-data
```

Open [http://localhost:8080](http://localhost:8080). Select a consumer, choose an as-of date, and press **Evaluate Score**.

### Run the tests

```bash
mvn test
```

### Generate a report from the command line

```bash
java -jar target/credit-bureau-simulator-1.0.0.jar generate-report \
  --data ./sample-data \
  --consumer CON-004 \
  --as-of 2026-10-31
```

## How it works

```text
CSV files
  │
  ├── validation and ingestion
  ├── as-of-date filtering and account merging
  ├── score factors and risk overrides
  ├── report JSON or CLI report
  └── interactive browser dashboard
```

The scoring engine exposes the calculation instead of returning only a number. The dashboard displays the score, factor contributions, repayment grid, account profile, overrides, and business-rule IDs.

## Score components

| Component | Maximum contribution | In plain English |
| --- | ---: | --- |
| Base score | 300 | Every eligible profile starts here. |
| Payment history | 300 | Late repayments reduce the score. Recent issues count more. |
| Credit utilisation | 200 | Using a smaller part of revolving credit is better. |
| Credit age | 100 | Older eligible accounts improve the score. |
| Credit mix | 100 | A balanced secured and unsecured portfolio scores higher. |
| Enquiries | -100 | Recent hard enquiries reduce the score. |
| Risk overrides | caps the result | Serious events can cap the final score at 650, 600, or 550. |

See [BusinessRules.md](docs/BusinessRules.md) for the complete rule register.

## Repository guide

```text
credit-bureau-simulator/
├── index.html                  Frontend dashboard
├── api/                         Vercel functions that proxy API calls
├── lib/                         Shared code for Vercel functions
├── Dockerfile                   Build instructions for the Java API container
├── render.yaml                  Render service definition
├── vercel.json                  Vercel function configuration
├── sample-data/                Demo input CSV files
├── test-data/                  Focused data used by tests
├── output/                     Generated evaluation reports
├── src/
│   ├── main/java/.../
│   │   ├── web/                HTTP server and JSON API
│   │   ├── cli/                Command-line entry points
│   │   ├── ingestion/          CSV parsing and validation
│   │   ├── processing/         Filtering, merging, profile and DPD grid
│   │   ├── scoring/            Score calculators and overrides
│   │   ├── reporting/          CLI report formatters
│   │   ├── domain/             Data records and enums
│   │   └── persistence/        In-memory data store abstraction
│   └── test/java/.../          Unit and integration tests
└── docs/                       Architecture, rules, data, tests, and demos
```

## Frontend and backend

| Part | Location | Responsibility |
| --- | --- | --- |
| Frontend | `index.html` | Lets a user select a scenario and displays the report returned by the API. |
| Web API | `src/main/java/.../web/WebServer.java` | Serves the dashboard and `/api/report`, `/api/consumers`, and `/api/history`. |
| Backend logic | `src/main/java/.../ingestion`, `processing`, `scoring` | Converts CSV records into an explainable score. |
| Data | `sample-data/` | Supplies demo consumers, accounts, enquiries, and events. |
| Tests | `src/test/java/` | Verifies calculations, boundaries, determinism, and end-to-end workflow. |

For a deeper explanation, read [Frontend guide](docs/Frontend.md) and [Backend guide](docs/Backend.md).

## Recruiter walkthrough

1. Start with the browser dashboard and select `CON-004` to show a clean 30% utilisation scenario.
2. Open the factor strip to explain where the score came from.
3. Show the repayment grid and applied-rule register to demonstrate traceability.
4. Point to `ScoringEngine` as the orchestrator and to individual calculators for isolated business logic.
5. Show the tests to demonstrate boundary coverage and deterministic results.

Use [PresentationGuide.md](docs/PresentationGuide.md) for a concise interview script.

## Deploy it live

The project is prepared for a split deployment:

- **Render** runs the Java scoring API and reads the sample CSV data.
- **Vercel** hosts the dashboard and proxies browser API requests to Render.

Follow [Deployment.md](docs/Deployment.md) for the exact steps. The only value you need to supply is the Render service URL as Vercel's `BACKEND_URL` environment variable.

## Documentation

- [Documentation index](docs/README.md)
- [Frontend guide](docs/Frontend.md)
- [Backend guide](docs/Backend.md)
- [Architecture](docs/Architecture.md)
- [Business rules](docs/BusinessRules.md)
- [Data dictionary](docs/DataDictionary.md)
- [Test guide](docs/TestCases.md)
- [Presentation guide](docs/PresentationGuide.md)
- [Deployment guide](docs/Deployment.md)
- [Viva preparation](docs/VivaPreparation.md)

## Technology

- Java 17
- Maven
- JUnit 5
- JDK built-in `HttpServer`
- HTML, CSS, and vanilla JavaScript
- SLF4J and Logback
