# Recruiter presentation guide

## A 90-second explanation

“This project is a Java 17 credit-score simulator. It accepts raw account, enquiry, and event data from CSV files. For any consumer and any as-of date, it filters out future information, merges duplicate lender submissions, calculates five score factors, applies risk caps, and returns both the score and the reason behind it. I built a browser dashboard so the complete calculation is easy to inspect, not just a number in the terminal.”

## Demo order

1. Start the server and open `http://localhost:8080`.
2. Choose `CON-004` and the 30% utilisation scenario.
3. Explain the final score and the five factor cards.
4. Point out the 36-month repayment grid to show historical data handling.
5. Point out the applied business-rule IDs to show traceability.
6. Open `ScoringEngine` and one factor calculator to show modular backend code.
7. Open the matching test to show the rule is verified.

## Strong points to mention

- The same inputs always produce the same result.
- All calculations are date-aware, so a historical score does not use future data.
- Financial values use `BigDecimal` instead of floating-point arithmetic.
- The score is explainable through factor descriptions and rule IDs.
- The project has both a command-line interface and a browser interface.

## Useful files during a presentation

| Topic | File |
| --- | --- |
| Web API and dashboard server | `src/main/java/com/creditbureau/simulator/web/WebServer.java` |
| Overall scoring flow | `src/main/java/com/creditbureau/simulator/scoring/ScoringEngine.java` |
| Account merging | `src/main/java/com/creditbureau/simulator/processing/AccountMergingEngine.java` |
| Business-rule mapping | `docs/BusinessRules.md` |
| End-to-end validation | `src/test/java/com/creditbureau/simulator/scoring/ScoringEngineIntegrationTest.java` |
