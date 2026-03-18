# Financial Transaction Data & Fraud Risk Scoring

This repository contains a financial transaction dataset and a fraud risk scoring script for detecting potentially fraudulent transactions.

## Dataset

`data/Example1.csv` — A tab-delimited TSV file containing labeled financial transaction records with fields such as transaction type, amount, account balances, and fraud labels.

## Fraud Risk Scoring

### Usage

```bash
python scripts/fraud_risk_scorer.py
```

No external dependencies required — uses only Python standard library (`csv`, `collections`).

### Output

The script generates `reports/transaction_risk_report.csv` (comma-separated) with the following columns:

| Column | Description |
|---|---|
| `step` | Time step of the transaction |
| `type` | Transaction type (PAYMENT, TRANSFER, CASH_OUT, DEBIT) |
| `amount` | Transaction amount |
| `nameOrig` | Originating account |
| `nameDest` | Destination account |
| `risk_score` | Computed risk score (0–100) |
| `risk_category` | LOW, MEDIUM, or HIGH |
| `risk_factors` | Semicolon-separated list of triggered risk factors |

A summary of total transactions and counts per risk category is printed to stdout.

### Scoring Methodology

The script uses a two-pass approach over the transaction data:

**Pass 1 (Pre-scan):** Builds frequency counts for destination accounts (`nameDest`) and per-origin/step transaction counts.

**Pass 2 (Score):** Computes an additive risk score for each transaction (capped at 100):

| Condition | Points |
|---|---|
| `amount > 10,000` | +25 |
| `amount > 100,000` | +15 (additional) |
| `type` is CASH_OUT or TRANSFER | +20 |
| `nameDest` appears only once in dataset | +15 |
| `nameOrig` has more than one transaction in the same step | +15 |
| `newbalanceOrig == 0` and `oldbalanceOrg > 0` | +10 |

**Risk Categories:**

- **LOW:** score < 40
- **MEDIUM:** 40 <= score <= 70
- **HIGH:** score > 70
