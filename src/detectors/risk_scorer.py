"""
Fraud Risk Scorer

Computes an additive risk score for each transaction using a two-pass
approach. Refactored from the original scripts/fraud_risk_scorer.py.
"""

from collections import Counter

from src.utils.data_loader import load_transactions
from config.detection_rules import (
    HIGH_AMOUNT_THRESHOLD,
    HIGH_AMOUNT_POINTS,
    VERY_HIGH_AMOUNT_THRESHOLD,
    VERY_HIGH_AMOUNT_POINTS,
    HIGH_RISK_TYPES,
    HIGH_RISK_TYPE_POINTS,
    UNIQUE_DEST_BONUS,
    MULTI_TXN_SAME_STEP_BONUS,
    ZERO_BALANCE_BONUS,
    MAX_RISK_SCORE,
)


def compute_risk_scores(transactions=None):
    """
    Score each transaction with an additive risk score (0-100).

    Pass 1: Pre-scan to build frequency counts.
    Pass 2: Score each transaction based on rules.

    Args:
        transactions: List of transaction dicts. If None, loads from default file.

    Returns:
        List of dicts with transaction info and risk_score.
    """
    if transactions is None:
        transactions = load_transactions()

    # Pass 1: Pre-scan
    name_dest_count = Counter()
    orig_step_count = Counter()

    for txn in transactions:
        name_dest_count[txn["nameDest"]] += 1
        orig_step_count[(txn["nameOrig"], txn["step"])] += 1

    # Pass 2: Score each transaction
    results = []

    for txn in transactions:
        score = 0
        amount = txn["amount"]
        txn_type = txn["type"]
        name_dest = txn["nameDest"]
        name_orig = txn["nameOrig"]
        step = txn["step"]
        new_balance_orig = txn["newbalanceOrig"]
        old_balance_org = txn["oldbalanceOrg"]

        if amount > HIGH_AMOUNT_THRESHOLD:
            score += HIGH_AMOUNT_POINTS

        if amount > VERY_HIGH_AMOUNT_THRESHOLD:
            score += VERY_HIGH_AMOUNT_POINTS

        if txn_type in HIGH_RISK_TYPES:
            score += HIGH_RISK_TYPE_POINTS

        if name_dest_count[name_dest] == 1:
            score += UNIQUE_DEST_BONUS

        if orig_step_count[(name_orig, step)] > 1:
            score += MULTI_TXN_SAME_STEP_BONUS

        if new_balance_orig == 0 and old_balance_org > 0:
            score += ZERO_BALANCE_BONUS

        score = min(score, MAX_RISK_SCORE)

        results.append({
            "step": step,
            "type": txn_type,
            "amount": amount,
            "nameOrig": name_orig,
            "nameDest": name_dest,
            "risk_score": score,
        })

    return results


def print_risk_scores(results):
    """Print risk scores in a formatted table."""
    print(f"{'step':<6} {'type':<12} {'amount':>14} {'nameOrig':<16} {'nameDest':<16} {'risk_score':>10}")
    print("-" * 80)
    for r in results:
        print(
            f"{r['step']:<6} {r['type']:<12} {r['amount']:>14.2f} "
            f"{r['nameOrig']:<16} {r['nameDest']:<16} {r['risk_score']:>10}"
        )

    print(f"\nTotal transactions scored: {len(results)}")

    high_risk = [r for r in results if r["risk_score"] >= 50]
    print(f"High-risk transactions (score >= 50): {len(high_risk)}")


def main():
    results = compute_risk_scores()
    print_risk_scores(results)


if __name__ == "__main__":
    main()
