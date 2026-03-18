#!/usr/bin/env python3
"""
Fraud Risk Scoring Script

Reads transaction data from data/Example1.csv (tab-delimited TSV) and computes
an additive risk score for each transaction using a two-pass approach.

Scoring Rules (additive, capped at 100):
  - amount > 10,000           -> +25
  - amount > 100,000          -> +15 (additional)
  - type in (CASH_OUT, TRANSFER) -> +20
  - nameDest appears only once   -> +15
  - nameOrig has >1 txn in same step -> +15
  - newbalanceOrig == 0 and oldbalanceOrg > 0 -> +10

Risk Categories:
  - score < 40  -> LOW
  - 40 <= score <= 70 -> MEDIUM
  - score > 70  -> HIGH

Output: reports/transaction_risk_report.csv
"""

import csv
import os
from collections import Counter


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    input_path = os.path.join(project_root, "data", "Example1.csv")
    output_dir = os.path.join(project_root, "reports")
    output_path = os.path.join(output_dir, "transaction_risk_report.csv")

    os.makedirs(output_dir, exist_ok=True)

    # ---- Pass 1: Pre-scan ----
    name_dest_counter = Counter()
    orig_step_counter = Counter()

    with open(input_path, "r", newline="") as f:
        reader = csv.DictReader(f, delimiter="\t")
        for row in reader:
            name_dest_counter[row["nameDest"]] += 1
            orig_step_counter[(row["nameOrig"], row["step"])] += 1

    # ---- Pass 2: Score each transaction ----
    results = []

    with open(input_path, "r", newline="") as f:
        reader = csv.DictReader(f, delimiter="\t")
        for row in reader:
            score = 0
            factors = []

            amount = float(row["amount"])
            txn_type = row["type"]
            name_orig = row["nameOrig"]
            name_dest = row["nameDest"]
            step = row["step"]
            new_balance_orig = float(row["newbalanceOrig"])
            old_balance_org = float(row["oldbalanceOrg"])

            # Rule 1: amount > 10000
            if amount > 10000:
                score += 25
                factors.append("amount>10000")

            # Rule 2: amount > 100000 (additional)
            if amount > 100000:
                score += 15
                factors.append("amount>100000")

            # Rule 3: type in (CASH_OUT, TRANSFER)
            if txn_type in ("CASH_OUT", "TRANSFER"):
                score += 20
                factors.append("high_risk_type")

            # Rule 4: nameDest appears only once
            if name_dest_counter[name_dest] == 1:
                score += 15
                factors.append("unique_dest")

            # Rule 5: nameOrig has >1 txn in same step
            if orig_step_counter[(name_orig, step)] > 1:
                score += 15
                factors.append("multi_txn_same_step")

            # Rule 6: newbalanceOrig == 0 and oldbalanceOrg > 0
            if new_balance_orig == 0 and old_balance_org > 0:
                score += 10
                factors.append("balance_zeroed")

            # Cap at 100
            score = min(score, 100)

            # Categorize
            if score < 40:
                category = "LOW"
            elif score <= 70:
                category = "MEDIUM"
            else:
                category = "HIGH"

            results.append({
                "step": step,
                "type": txn_type,
                "amount": row["amount"],
                "nameOrig": name_orig,
                "nameDest": name_dest,
                "risk_score": score,
                "risk_category": category,
                "risk_factors": "; ".join(factors) if factors else "none",
            })

    # ---- Write output ----
    fieldnames = [
        "step", "type", "amount", "nameOrig", "nameDest",
        "risk_score", "risk_category", "risk_factors",
    ]

    with open(output_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(results)

    # ---- Print summary ----
    total = len(results)
    low = sum(1 for r in results if r["risk_category"] == "LOW")
    medium = sum(1 for r in results if r["risk_category"] == "MEDIUM")
    high = sum(1 for r in results if r["risk_category"] == "HIGH")

    print("=== Fraud Risk Scoring Summary ===")
    print(f"Total transactions scored: {total}")
    print(f"  LOW risk:    {low}")
    print(f"  MEDIUM risk: {medium}")
    print(f"  HIGH risk:   {high}")
    print(f"\nReport written to: {output_path}")


if __name__ == "__main__":
    main()
