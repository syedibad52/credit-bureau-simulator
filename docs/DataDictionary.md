# Data Dictionary: Credit Bureau Score Simulation Engine (PS-022)

This document defines all input datasets, field specifications, data types, constraints, and domain enums used across the engine.

---

## 1. Input Datasets

### A. Tradeline Submissions (`tradelines.csv`)

| Field Name | Data Type | Mandatory | Description | Valid Values / Constraints |
|---|---|---|---|---|
| `consumer_id` | String | Yes | Unique identifier for the consumer | e.g. `CUST-1001` |
| `lender` | String | Yes | Name or code of the lending institution | e.g. `HDFC`, `SBI`, `ICICI` |
| `account_type` | Enum | Yes | Classification of the credit account | `CC`, `PL`, `HL`, `AL`, `GOLD`, `BNPL` |
| `open_date` | Date | Yes | Account sanction/origination date | `YYYY-MM-DD` |
| `credit_limit` | BigDecimal | Yes | Sanctioned credit limit or loan amount | Non-negative numeric |
| `outstanding_balance` | BigDecimal | Yes | Current outstanding balance as of submission | Non-negative numeric |
| `current_dpd` | Integer | Yes | Days Past Due for the submission month | Non-negative integer (0 = no delay) |
| `account_status` | Enum | Yes | Status of the account as of reporting date | `ACTIVE`, `CLOSED`, `WRITTEN_OFF`, `SETTLED`, `SUIT_FILED`, `DISPUTE` |
| `submission_date` | Date | Yes | Date on which lender reported this tradeline | `YYYY-MM-DD` |

---

### B. Credit Enquiries (`enquiries.csv`)

| Field Name | Data Type | Mandatory | Description | Valid Values / Constraints |
|---|---|---|---|---|
| `consumer_id` | String | Yes | Unique identifier for the consumer | e.g. `CUST-1001` |
| `lender` | String | Yes | Name of the institution requesting credit history | e.g. `HDFC`, `CRED` |
| `purpose` | String | Yes | Reason for the credit pull | e.g. `CREDIT_CARD`, `HOME_LOAN` |
| `enquiry_type` | Enum | Yes | Regulatory classification of enquiry | `HARD`, `SOFT` |
| `enquiry_date` | Date | Yes | Date enquiry was logged | `YYYY-MM-DD` |

---

### C. Account Events (`events.csv`)

| Field Name | Data Type | Mandatory | Description | Valid Values / Constraints |
|---|---|---|---|---|
| `consumer_id` | String | Yes | Unique identifier for the consumer | e.g. `CUST-1001` |
| `account_id` | String | Optional | Synthetic or lender-assigned account key | e.g. `HDFC-CC-20220115` |
| `event_type` | Enum | Yes | Type of status change or legal/dispute action | `WRITTEN_OFF`, `SETTLED`, `SUIT_FILED`, `DISPUTE`, `DISPUTE_RESOLVED` |
| `event_date` | Date | Yes | Date on which the event took place | `YYYY-MM-DD` |
| `resolution_date` | Date | Optional | Date of dispute resolution (if applicable) | `YYYY-MM-DD` |
| `dispute_outcome` | Enum | Optional | Verdict of dispute resolution | `VALID`, `REMOVED` |
| `details` | String | No | Supplementary explanatory notes | Free-text string |

---

## 2. Enumerated Domains

### `AccountType`
- `CC`: Credit Card (Revolving, Unsecured)
- `PL`: Personal Loan (Installment, Unsecured)
- `HL`: Home Loan (Installment, Secured)
- `AL`: Auto Loan (Installment, Secured)
- `GOLD`: Gold Loan (Installment, Secured)
- `BNPL`: Buy-Now-Pay-Later (Revolving/Installment, Unsecured; special utilisation & DPD rules apply)

### `AccountCategory`
- `SECURED`: Backed by collateral (`HL`, `AL`, `GOLD`)
- `UNSECURED`: Unbacked by collateral (`CC`, `PL`, `BNPL`)

### `AccountStatus`
- `ACTIVE`: Account open, operational, and active
- `CLOSED`: Account paid off, closed, or terminated
- `WRITTEN_OFF`: Lender wrote off defaulted balance
- `SETTLED`: Account settled with borrower for reduced amount
- `SUIT_FILED`: Legal suit filed by lender for recovery
- `DISPUTE`: Account under consumer investigation / dispute

### `EnquiryType`
- `HARD`: Application for new credit (impacts credit score)
- `SOFT`: Account review, pre-approved offer, or self-check (zero score deduction)

### `DisputeOutcome`
- `VALID`: Disputed record validated; re-included in scoring
- `REMOVED`: Disputed record invalidated; permanently excluded from scoring

