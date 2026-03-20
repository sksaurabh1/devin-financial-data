"""
Anomalous Transaction Sequence Detector

Detects suspicious patterns per customer based on the following rules:
1. TRANSFER -> TRANSFER -> CASH_OUT sequence
2. TRANSFER -> CASH_OUT (transfer followed by immediate cash-out)
3. Repeated high-value transactions in sequence
4. Sudden spike in transaction amount compared to customer history
5. Rapid change in transaction type
6. Transactions within a short time window analysed together

Each detected anomaly is reported with the customer, the triggering
transactions, and the rule(s) that fired.
"""

import sys
import os
from collections import defaultdict

# Allow running as a standalone script from the repo root
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from src.utils.data_loader import load_transactions
from config.detection_rules import (
    SUSPICIOUS_SEQUENCES,
    REPEATED_HIGH_VALUE_THRESHOLD,
    REPEATED_HIGH_VALUE_MIN_COUNT,
    SUDDEN_SPIKE_MULTIPLIER,
    SHORT_TIME_WINDOW_STEPS,
    MIN_ANOMALY_SCORE,
    MIN_INBOUND_TRANSFERS_FOR_FUND_FLOW,
    BALANCE_DRAIN_CHECK,
)


# ---- Helpers ---------------------------------------------------------------

def _group_by_customer(transactions):
    """Group transactions by originating customer, sorted by step."""
    groups = defaultdict(list)
    for txn in transactions:
        groups[txn["nameOrig"]].append(txn)
    # Sort each customer's transactions by step (time)
    for name in groups:
        groups[name].sort(key=lambda t: t["step"])
    return groups


def _group_by_destination(transactions):
    """Group transactions by destination account, sorted by step."""
    groups = defaultdict(list)
    for txn in transactions:
        groups[txn["nameDest"]].append(txn)
    for name in groups:
        groups[name].sort(key=lambda t: t["step"])
    return groups


def _txn_summary(txn):
    """Return a concise one-line summary of a transaction."""
    return (
        f"step={txn['step']} type={txn['type']} amount={txn['amount']:.2f} "
        f"orig={txn['nameOrig']} dest={txn['nameDest']}"
    )


# ---- Detection Rules -------------------------------------------------------

def detect_suspicious_sequences(customer_txns):
    """
    Rule 1 & 2: Detect suspicious type sequences.
    Looks for TRANSFER->TRANSFER->CASH_OUT and TRANSFER->CASH_OUT patterns.

    Returns list of anomaly dicts.
    """
    anomalies = []
    types = [t["type"] for t in customer_txns]

    for seq in SUSPICIOUS_SEQUENCES:
        seq_len = len(seq)
        for i in range(len(types) - seq_len + 1):
            window = tuple(types[i : i + seq_len])
            if window == seq:
                involved = customer_txns[i : i + seq_len]
                anomalies.append({
                    "rule": f"Suspicious sequence: {' -> '.join(seq)}",
                    "transactions": involved,
                    "severity": "HIGH" if seq_len >= 3 else "MEDIUM",
                })
    return anomalies


def detect_repeated_high_value(customer_txns):
    """
    Rule 3: Repeated high-value transactions in sequence.
    Flags runs of consecutive transactions above the threshold.

    Returns list of anomaly dicts.
    """
    anomalies = []
    run = []

    for txn in customer_txns:
        if txn["amount"] >= REPEATED_HIGH_VALUE_THRESHOLD:
            run.append(txn)
        else:
            if len(run) >= REPEATED_HIGH_VALUE_MIN_COUNT:
                anomalies.append({
                    "rule": (
                        f"Repeated high-value transactions "
                        f"({len(run)} consecutive >= {REPEATED_HIGH_VALUE_THRESHOLD})"
                    ),
                    "transactions": list(run),
                    "severity": "HIGH",
                })
            run = []

    # Check the trailing run
    if len(run) >= REPEATED_HIGH_VALUE_MIN_COUNT:
        anomalies.append({
            "rule": (
                f"Repeated high-value transactions "
                f"({len(run)} consecutive >= {REPEATED_HIGH_VALUE_THRESHOLD})"
            ),
            "transactions": list(run),
            "severity": "HIGH",
        })

    return anomalies


def detect_sudden_spike(customer_txns):
    """
    Rule 4: Sudden spike in transaction amount.
    A transaction is flagged if its amount is SUDDEN_SPIKE_MULTIPLIER times
    larger than the customer's running average up to that point.

    Returns list of anomaly dicts.
    """
    anomalies = []
    if len(customer_txns) < 2:
        return anomalies

    running_sum = customer_txns[0]["amount"]
    running_count = 1

    for txn in customer_txns[1:]:
        avg = running_sum / running_count
        if avg > 0 and txn["amount"] >= avg * SUDDEN_SPIKE_MULTIPLIER:
            anomalies.append({
                "rule": (
                    f"Sudden spike in amount "
                    f"(txn={txn['amount']:.2f}, running_avg={avg:.2f}, "
                    f"ratio={txn['amount'] / avg:.1f}x)"
                ),
                "transactions": [txn],
                "severity": "MEDIUM",
            })
        running_sum += txn["amount"]
        running_count += 1

    return anomalies


def detect_rapid_type_change(customer_txns):
    """
    Rule 5: Sudden change in transaction type.
    Flags when consecutive transactions have different types AND occur
    within the short time window.

    Returns list of anomaly dicts.
    """
    anomalies = []
    for i in range(1, len(customer_txns)):
        prev = customer_txns[i - 1]
        curr = customer_txns[i]
        step_diff = abs(curr["step"] - prev["step"])

        if prev["type"] != curr["type"] and step_diff <= SHORT_TIME_WINDOW_STEPS:
            anomalies.append({
                "rule": (
                    f"Rapid type change: {prev['type']} -> {curr['type']} "
                    f"within {step_diff} step(s)"
                ),
                "transactions": [prev, curr],
                "severity": "MEDIUM",
            })

    return anomalies


def detect_short_window_cluster(customer_txns):
    """
    Rule 6: Multiple transactions in a short time window.
    Groups transactions that fall within SHORT_TIME_WINDOW_STEPS of each
    other and flags clusters of 3+ transactions.

    Returns list of anomaly dicts.
    """
    anomalies = []
    if len(customer_txns) < 3:
        return anomalies

    cluster = [customer_txns[0]]
    for txn in customer_txns[1:]:
        if txn["step"] - cluster[0]["step"] <= SHORT_TIME_WINDOW_STEPS:
            cluster.append(txn)
        else:
            if len(cluster) >= 3:
                anomalies.append({
                    "rule": (
                        f"Short time-window cluster "
                        f"({len(cluster)} transactions within "
                        f"{SHORT_TIME_WINDOW_STEPS} step(s))"
                    ),
                    "transactions": list(cluster),
                    "severity": "HIGH",
                })
            cluster = [txn]

    if len(cluster) >= 3:
        anomalies.append({
            "rule": (
                f"Short time-window cluster "
                f"({len(cluster)} transactions within "
                f"{SHORT_TIME_WINDOW_STEPS} step(s))"
            ),
            "transactions": list(cluster),
            "severity": "HIGH",
        })

    return anomalies


def detect_balance_anomaly(customer_txns):
    """
    Rule 7: Balance inconsistency.
    Flags transactions where the account is drained to zero but the
    amount is less than the old balance (balance mismatch).

    Returns list of anomaly dicts.
    """
    if not BALANCE_DRAIN_CHECK:
        return []

    anomalies = []
    for txn in customer_txns:
        old_bal = txn["oldbalanceOrg"]
        new_bal = txn["newbalanceOrig"]
        amount = txn["amount"]

        # Account drained to zero when it shouldn't be
        if new_bal == 0 and old_bal > 0 and abs(old_bal - amount) > 0.01:
            anomalies.append({
                "rule": (
                    f"Balance anomaly: balance drained to 0 but "
                    f"amount ({amount:.2f}) != oldBalance ({old_bal:.2f})"
                ),
                "transactions": [txn],
                "severity": "HIGH",
            })

        # Transfer drains entire balance (exact match) — fraud pattern
        if (new_bal == 0 and old_bal > 0
                and abs(old_bal - amount) < 0.01
                and txn["type"] in ("TRANSFER", "CASH_OUT")):
            anomalies.append({
                "rule": (
                    f"Full balance drain via {txn['type']}: "
                    f"amount={amount:.2f} equals oldBalance={old_bal:.2f}"
                ),
                "transactions": [txn],
                "severity": "HIGH",
            })

    return anomalies


# ---- Cross-customer detection -----------------------------------------------

def detect_fund_flow_convergence(transactions):
    """
    Cross-customer rule: Fund flow convergence.
    Flags destination accounts that receive TRANSFER or CASH_OUT from
    multiple different originators within a short time window.
    This is a hallmark of money-laundering / layering.

    Returns list of anomaly dicts keyed by destination account.
    """
    dest_groups = _group_by_destination(transactions)
    anomalies = []

    for dest, txns in dest_groups.items():
        # Only look at high-risk inbound types
        inbound = [t for t in txns if t["type"] in ("TRANSFER", "CASH_OUT")]
        if len(inbound) < MIN_INBOUND_TRANSFERS_FOR_FUND_FLOW:
            continue

        # Check if they come from different originators
        origins = set(t["nameOrig"] for t in inbound)
        if len(origins) >= MIN_INBOUND_TRANSFERS_FOR_FUND_FLOW:
            total_amount = sum(t["amount"] for t in inbound)
            anomalies.append({
                "rule": (
                    f"Fund flow convergence: {len(origins)} different "
                    f"originators sent {len(inbound)} transfers "
                    f"(total={total_amount:.2f}) to {dest}"
                ),
                "transactions": inbound,
                "severity": "HIGH",
                "destination": dest,
            })

    return anomalies


def detect_transfer_then_cashout_cross_customer(transactions):
    """
    Cross-customer rule: TRANSFER -> CASH_OUT across accounts.
    Detects when funds are transferred to an account and then
    immediately cashed out by a different customer from the same
    destination within the time window.

    Returns list of anomaly dicts.
    """
    dest_groups = _group_by_destination(transactions)
    anomalies = []

    for dest, txns in dest_groups.items():
        transfers_in = [t for t in txns if t["type"] == "TRANSFER"]
        cashouts = [
            t for t in transactions
            if t["type"] == "CASH_OUT" and t["nameOrig"] == dest
        ]

        for transfer in transfers_in:
            for cashout in cashouts:
                step_diff = abs(cashout["step"] - transfer["step"])
                if step_diff <= SHORT_TIME_WINDOW_STEPS:
                    anomalies.append({
                        "rule": (
                            f"Cross-customer TRANSFER->CASH_OUT: "
                            f"{transfer['nameOrig']} transferred {transfer['amount']:.2f} "
                            f"to {dest}, then {dest} cashed out {cashout['amount']:.2f} "
                            f"within {step_diff} step(s)"
                        ),
                        "transactions": [transfer, cashout],
                        "severity": "HIGH",
                        "destination": dest,
                    })

    return anomalies


# ---- Main orchestration ----------------------------------------------------

ALL_CUSTOMER_RULES = [
    detect_suspicious_sequences,
    detect_repeated_high_value,
    detect_sudden_spike,
    detect_rapid_type_change,
    detect_short_window_cluster,
    detect_balance_anomaly,
]


def analyse_customer(customer_name, customer_txns):
    """
    Run all per-customer detection rules against a single customer's
    transactions.

    Returns a dict with customer name, total anomaly count, and details.
    """
    all_anomalies = []
    for rule_fn in ALL_CUSTOMER_RULES:
        all_anomalies.extend(rule_fn(customer_txns))

    return {
        "customer": customer_name,
        "transaction_count": len(customer_txns),
        "anomaly_count": len(all_anomalies),
        "anomalies": all_anomalies,
    }


def run_detection(transactions=None):
    """
    Run anomaly detection across all customers and cross-customer.

    Args:
        transactions: List of transaction dicts. Loads default data if None.

    Returns:
        Tuple of (per_customer_results, cross_customer_anomalies).
    """
    if transactions is None:
        transactions = load_transactions()

    # Per-customer analysis
    customer_groups = _group_by_customer(transactions)
    customer_results = []

    for customer, txns in customer_groups.items():
        result = analyse_customer(customer, txns)
        if result["anomaly_count"] >= MIN_ANOMALY_SCORE:
            customer_results.append(result)

    customer_results.sort(key=lambda r: r["anomaly_count"], reverse=True)

    # Cross-customer analysis
    cross_anomalies = []
    cross_anomalies.extend(detect_fund_flow_convergence(transactions))
    cross_anomalies.extend(detect_transfer_then_cashout_cross_customer(transactions))

    return customer_results, cross_anomalies


# ---- CLI output ------------------------------------------------------------

def print_report(customer_results, cross_anomalies):
    """Print a human-readable anomaly report."""
    total_customer_anomalies = sum(r["anomaly_count"] for r in customer_results)

    print("=" * 90)
    print("ANOMALOUS TRANSACTION SEQUENCE REPORT")
    print("=" * 90)

    # Section A: Per-customer anomalies
    print(f"\n--- Per-Customer Anomalies ---")
    print(f"Customers flagged : {len(customer_results)}")
    print(f"Total anomalies   : {total_customer_anomalies}")

    for r in customer_results:
        print(f"\nCustomer: {r['customer']}  "
              f"(transactions: {r['transaction_count']}, "
              f"anomalies: {r['anomaly_count']})")
        print("-" * 80)
        for a in r["anomalies"]:
            print(f"  [{a['severity']}] {a['rule']}")
            for txn in a["transactions"]:
                print(f"         - {_txn_summary(txn)}")

    # Section B: Cross-customer anomalies
    print(f"\n--- Cross-Customer Anomalies ---")
    print(f"Total cross-customer anomalies: {len(cross_anomalies)}")

    for a in cross_anomalies:
        print(f"\n  [{a['severity']}] {a['rule']}")
        for txn in a["transactions"]:
            print(f"         - {_txn_summary(txn)}")

    print("\n" + "=" * 90)


def main():
    customer_results, cross_anomalies = run_detection()
    print_report(customer_results, cross_anomalies)


if __name__ == "__main__":
    main()
