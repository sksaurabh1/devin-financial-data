"""
Data Loader Utility

Loads transaction data from TSV files and provides structured access
to the transaction records.
"""

import csv
import os


def get_data_path(filename="Example1.csv"):
    """Return the absolute path to a file in the data/ directory."""
    repo_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    return os.path.join(repo_root, "data", filename)


def load_transactions(filepath=None):
    """
    Load transactions from a tab-delimited CSV file.

    Args:
        filepath: Path to the CSV file. Defaults to data/Example1.csv.

    Returns:
        A list of dicts, one per transaction row, with numeric fields
        converted to float/int where appropriate.
    """
    if filepath is None:
        filepath = get_data_path()

    transactions = []
    with open(filepath, newline="") as f:
        reader = csv.DictReader(f, delimiter="\t")
        for row in reader:
            transactions.append({
                "step": int(row["step"]),
                "type": row["type"],
                "amount": float(row["amount"]),
                "nameOrig": row["nameOrig"],
                "oldbalanceOrg": float(row["oldbalanceOrg"]),
                "newbalanceOrig": float(row["newbalanceOrig"]),
                "nameDest": row["nameDest"],
                "oldbalanceDest": float(row["oldbalanceDest"]),
                "newbalanceDest": float(row["newbalanceDest"]),
                "isFraud": int(row["isFraud"]),
                "isFlaggedFraud": int(row["isFlaggedFraud"]),
            })

    return transactions
