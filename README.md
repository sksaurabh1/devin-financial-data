# Financial Fraud Detection

Anomalous transaction sequence detection and risk scoring for financial transaction data.

## Project Structure

```
devin-financial-data/
├── config/                          # Configuration & thresholds
│   └── detection_rules.py           # Tunable rule parameters
├── data/                            # Raw datasets
│   └── Example1.csv                 # Labelled transaction data (TSV)
├── docs/                            # Documentation
│   └── detection_rules.md           # Detection rules reference
├── scripts/                         # Standalone legacy scripts
│   └── fraud_risk_scorer.py         # Original risk scorer (kept for reference)
├── src/                             # Main source package
│   ├── detectors/                   # Detection modules
│   │   ├── anomaly_sequence_detector.py  # Sequence & behavioural anomaly detection
│   │   └── risk_scorer.py           # Additive risk scoring (refactored)
│   ├── utils/                       # Shared utilities
│   │   └── data_loader.py           # CSV/TSV data loading
│   └── main.py                      # Combined entry point
└── README.md
```

## Detection Rules

### Risk Scoring
Each transaction receives an additive risk score (0-100) based on:
- High transaction amount (>10k, >100k)
- Transaction type (CASH_OUT, TRANSFER)
- Unique destination account
- Multiple transactions by same origin in the same time step
- Account balance drained to zero

### Anomaly Sequence Detection
Per-customer behavioural analysis detecting:

1. **Suspicious sequences** — TRANSFER -> TRANSFER -> CASH_OUT, TRANSFER -> CASH_OUT
2. **Repeated high-value transactions** — consecutive large transactions above threshold
3. **Sudden amount spike** — transaction amount far exceeding the customer's running average
4. **Rapid type change** — transaction type switches within a short time window
5. **Short time-window clusters** — 3+ transactions occurring in rapid succession

See [docs/detection_rules.md](docs/detection_rules.md) for full details.

## Usage

Run the combined analysis from the repository root:

```bash
python -m src.main
```

Run individual detectors:

```bash
# Risk scorer only
python -m src.detectors.risk_scorer

# Anomaly sequence detector only
python -m src.detectors.anomaly_sequence_detector
```

## Configuration

All detection thresholds are centralised in `config/detection_rules.py` for easy tuning.
