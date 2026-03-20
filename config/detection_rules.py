"""
Detection Rules Configuration

Centralised thresholds and rule parameters for anomaly detection.
Adjust these values to tune sensitivity.
"""

# ---------------------------------------------------------------------------
# Risk Scoring Thresholds
# ---------------------------------------------------------------------------
HIGH_AMOUNT_THRESHOLD = 10000       # +25 points when amount exceeds this
VERY_HIGH_AMOUNT_THRESHOLD = 100000 # +15 additional points
HIGH_RISK_TYPES = ("CASH_OUT", "TRANSFER")  # +20 points for these types
UNIQUE_DEST_BONUS = 15              # Points when destination seen only once
MULTI_TXN_SAME_STEP_BONUS = 15     # Points for >1 txn by same origin in step
ZERO_BALANCE_BONUS = 10            # Points when balance drained to zero
MAX_RISK_SCORE = 100

# ---------------------------------------------------------------------------
# Sequence Detection
# ---------------------------------------------------------------------------
# Suspicious transaction type sequences to look for
SUSPICIOUS_SEQUENCES = [
    ("TRANSFER", "TRANSFER", "CASH_OUT"),
    ("TRANSFER", "CASH_OUT"),
]

# ---------------------------------------------------------------------------
# Anomaly Detection Thresholds
# ---------------------------------------------------------------------------
# Repeated high-value: flag if N+ consecutive txns above this amount
REPEATED_HIGH_VALUE_THRESHOLD = 50000
REPEATED_HIGH_VALUE_MIN_COUNT = 2

# Sudden spike: flag if a txn amount is this many times larger than the
# customer's average transaction amount
SUDDEN_SPIKE_MULTIPLIER = 5.0

# Time window: transactions within this many steps are considered "short window"
SHORT_TIME_WINDOW_STEPS = 1

# Minimum anomaly score to flag a sequence as suspicious
MIN_ANOMALY_SCORE = 1

# ---------------------------------------------------------------------------
# Cross-Customer (Fund Flow) Detection
# ---------------------------------------------------------------------------
# Flag destination accounts that receive from multiple high-risk sources
MIN_INBOUND_TRANSFERS_FOR_FUND_FLOW = 2

# Balance anomaly: flag when newbalanceOrig == 0 but amount < oldbalanceOrg
BALANCE_DRAIN_CHECK = True
