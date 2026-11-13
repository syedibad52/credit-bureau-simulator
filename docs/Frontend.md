# Frontend guide

## What the frontend is

The frontend is a single-page dashboard in [`../index.html`](../index.html). It intentionally uses plain HTML, CSS, and JavaScript so the UI can be understood without a separate framework build step.

## What a user does

1. Select a consumer.
2. Choose an as-of date.
3. Press **Evaluate Score** or select a ready-made scenario.
4. Read the score, factors, consumer profile, accounts, repayment grid, and applied rules.

## How it receives data

The browser requests:

```text
GET /api/report?consumer=CON-004&asOf=2026-10-31
```

When the page is opened directly from the file system, it uses `http://localhost:8080/api/report` instead. This makes the dashboard work both as a local file preview and when it is served by the Java application.

## What the page renders

| UI area | API data used |
| --- | --- |
| Score gauge | `finalScore`, `preOverrideScore`, `isThinFile` |
| Formula trail | Base score, factors, enquiry deduction, final score |
| Consumer profile | Account count, utilisation, account age, mix, enquiries |
| Factor cards | Payment history, utilisation, age, mix, and enquiries |
| Repayment grid | Merged accounts and 36 months of DPD records |
| Rules register | Rule IDs applied during the calculation |

## Visual design

The dashboard uses a frosted-white glass effect: translucent panels, blurred backgrounds, soft colour washes, and small motion cues. Entrance and hover motion respects the operating system’s reduced-motion preference.
