"""
Fraud Risk Scorer

Reads data/Example1.csv (tab-delimited TSV) and computes an additive risk score
for each transaction using a two-pass approach. Uses only Python stdlib.
"""

import csv
import os
from collections import Counter


def main():
    # Resolve path relative to the script's parent directory (repo root)
    script_dir = os.path.dirname(os.path.abspath(__file__))
    repo_root = os.path.dirname(script_dir)
    data_path = os.path.join(repo_root, "data", "Example1.csv")

    # ---- Pass 1: Pre-scan ----
    # Build nameDest frequency count and per-(nameOrig, step) transaction count
    name_dest_count = Counter()
    orig_step_count = Counter()

    with open(data_path, newline="") as f:
        reader = csv.DictReader(f, delimiter="\t")
        for row in reader:
            name_dest_count[row["nameDest"]] += 1
            orig_step_count[(row["nameOrig"], row["step"])] += 1

    # ---- Pass 2: Score each transaction ----
    results = []

    with open(data_path, newline="") as f:
        reader = csv.DictReader(f, delimiter="\t")
        for row in reader:
            score = 0
            amount = float(row["amount"])
            txn_type = row["type"]
            name_dest = row["nameDest"]
            name_orig = row["nameOrig"]
            step = row["step"]
            new_balance_orig = float(row["newbalanceOrig"])
            old_balance_org = float(row["oldbalanceOrg"])

            # amount > 10000 → +25
            if amount > 10000:
                score += 25

            # amount > 100000 → +15 more
            if amount > 100000:
                score += 15

            # type in (CASH_OUT, TRANSFER) → +20
            if txn_type in ("CASH_OUT", "TRANSFER"):
                score += 20

            # nameDest count == 1 → +15
            if name_dest_count[name_dest] == 1:
                score += 15

            # nameOrig has >1 txn in same step → +15
            if orig_step_count[(name_orig, step)] > 1:
                score += 15

            # newbalanceOrig == 0 and oldbalanceOrg > 0 → +10
            if new_balance_orig == 0 and old_balance_org > 0:
                score += 10

            # Cap at 100
            score = min(score, 100)

            results.append({
                "step": step,
                "type": txn_type,
                "amount": amount,
                "nameOrig": name_orig,
                "nameDest": name_dest,
                "risk_score": score,
            })

    # Print results
    print(f"{'step':<6} {'type':<12} {'amount':>14} {'nameOrig':<16} {'nameDest':<16} {'risk_score':>10}")
    print("-" * 80)
    for r in results:
        print(
            f"{r['step']:<6} {r['type']:<12} {r['amount']:>14.2f} "
            f"{r['nameOrig']:<16} {r['nameDest']:<16} {r['risk_score']:>10}"
        )

    print(f"\nTotal transactions scored: {len(results)}")

    # Summary of high-risk transactions (score >= 50)
    high_risk = [r for r in results if r["risk_score"] >= 50]
    print(f"High-risk transactions (score >= 50): {len(high_risk)}")


if __name__ == "__main__":
    main()
