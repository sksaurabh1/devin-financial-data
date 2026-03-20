"""
Main entry point for financial fraud detection analysis.

Runs both the risk scorer and the anomaly sequence detector,
then prints a combined summary.
"""

import sys
import os

# Allow running from repo root: python -m src.main
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from src.utils.data_loader import load_transactions
from src.detectors.risk_scorer import compute_risk_scores, print_risk_scores
from src.detectors.anomaly_sequence_detector import run_detection, print_report


def main():
    print("Loading transactions...")
    transactions = load_transactions()
    print(f"Loaded {len(transactions)} transactions.\n")

    # --- Risk Scoring ---
    print("=" * 90)
    print("SECTION 1: TRANSACTION RISK SCORES")
    print("=" * 90)
    scores = compute_risk_scores(transactions)
    print_risk_scores(scores)

    print("\n")

    # --- Anomaly Sequence Detection ---
    print("=" * 90)
    print("SECTION 2: ANOMALOUS TRANSACTION SEQUENCES")
    print("=" * 90)
    customer_results, cross_anomalies = run_detection(transactions)
    print_report(customer_results, cross_anomalies)


if __name__ == "__main__":
    main()
