# Detection Rules Reference

This document describes all anomaly detection rules implemented in the system.

## 1. Suspicious Transaction Sequences

Certain sequences of transaction types are inherently suspicious:

| Sequence | Severity | Rationale |
|---|---|---|
| TRANSFER -> TRANSFER -> CASH_OUT | HIGH | Funds moved through multiple accounts then withdrawn |
| TRANSFER -> CASH_OUT | MEDIUM | Immediate withdrawal after transfer — classic fraud pattern |

## 2. Repeated High-Value Transactions

Consecutive transactions above the configured threshold (default: 50,000) are flagged.

- **Minimum run length:** 2 consecutive high-value transactions
- **Severity:** HIGH

## 3. Sudden Spike in Transaction Amount

A transaction is flagged when its amount is significantly larger (default: 5x) than the customer's running average.

- **Severity:** MEDIUM
- The running average is computed incrementally so that the first unusual transaction triggers the alert.

## 4. Rapid Change in Transaction Type

When a customer's consecutive transactions have different types and occur within the short time window (default: 1 step), this is flagged.

- **Severity:** MEDIUM
- Example: A TRANSFER immediately followed by a CASH_OUT in the same step.

## 5. Short Time-Window Clusters

Groups of 3+ transactions occurring within the configured time window (default: 1 step) are flagged.

- **Severity:** HIGH
- Indicates potentially automated or coordinated activity.

## Configuration

All thresholds are configurable in `config/detection_rules.py`.
