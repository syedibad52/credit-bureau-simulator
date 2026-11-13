# Backend guide

## The backend in one sentence

The Java backend reads raw credit data, applies date-aware scoring rules, and returns an explainable result through a command-line interface or a small HTTP API.

## Request flow

```text
Browser or CLI request
        │
        ▼
CommandRouter or WebServer
        │
        ▼
CSV ingesters validate and load data
        │
        ▼
AsOfDateFilter removes future records
        │
        ▼
AccountMergingEngine creates logical accounts
        │
        ▼
ProfileBuilder and ScoringEngine calculate the result
        │
        ▼
WebServer returns JSON or reporting classes print a CLI report
```

## Main packages

| Package | Responsibility |
| --- | --- |
| `cli` | Parses commands such as `serve`, `generate-report`, and `calculate-score`. |
| `web` | Hosts the browser dashboard and JSON endpoints. |
| `ingestion` | Parses CSV files and rejects invalid rows. |
| `processing` | Filters data by date, merges accounts, builds profiles, and prepares DPD history. |
| `scoring` | Calculates score factors, thin-file status, disputes, and risk overrides. |
| `reporting` | Formats readable command-line reports. |
| `domain` | Defines the immutable business objects used everywhere else. |
| `persistence` | Provides the in-memory store behind the simulation. |

## API endpoints

| Endpoint | Purpose |
| --- | --- |
| `GET /` | Serves the dashboard. |
| `GET /api/consumers` | Lists known consumer IDs. |
| `GET /api/report?consumer=...&asOf=YYYY-MM-DD` | Returns the full score explanation. |
| `GET /api/history?consumer=...` | Returns score history for a consumer. |

## Why this design is easy to explain

- Each score factor has its own calculator, so a business rule does not hide inside a large method.
- `ScoringEngine` coordinates the process, making it a useful starting point for code review.
- The API returns factor explanations and applied rule IDs, making the result auditable.
- Tests cover individual calculators and end-to-end behaviour separately.
