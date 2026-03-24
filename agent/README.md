# API Metrics Report Agent

A **LangGraph**-based Python agent that queries MongoDB and generates comprehensive API Metrics Reports in both **JSON** and **Markdown** formats.

## Architecture

```
┌──────────────┐     ┌───────────────┐     ┌────────────────┐
│  HumanMessage│────►│  Agent (LLM)  │────►│  Tool Nodes    │
│  (prompt)    │     │  gpt-4o-mini  │◄────│  (MongoDB +    │
└──────────────┘     │               │     │   KPI compute) │
                     └───────┬───────┘     └────────────────┘
                             │
                     ┌───────▼───────┐
                     │  Generate Node│
                     │  (final KPIs  │
                     │  + reports)   │
                     └───────┬───────┘
                             │
                     ┌───────▼───────┐
                     │   Output:     │
                     │  JSON + MD    │
                     └───────────────┘
```

The agent uses a **state graph** with three node types:
- **Agent node** – LLM (gpt-4o-mini) decides which analysis tool to call next
- **Tool node** – Executes MongoDB queries and KPI computations
- **Generate node** – Produces final JSON and Markdown reports

## Report KPIs

| Section | Metrics |
|---------|---------|
| Traffic Analysis | Total calls, daily trend, peak day, top 5 APIs |
| Success & Failure | Success/failure counts, ratios, top failing APIs |
| Performance | Avg/peak memory, traffic↔memory correlation |
| Time-Based Insights | Day-wise breakdown, anomaly detection (>2σ) |
| Reliability | High-failure days, unstable APIs (variance analysis) |

## Setup

```bash
# 1. Install dependencies
pip install -r agent/requirements.txt

# 2. Create .env file (see .env.example)
cp agent/.env.example agent/.env
# Edit agent/.env with your credentials

# 3. Run the agent
python -m agent.main
```

## Environment Variables

| Variable | Description |
|----------|-------------|
| `MONGO_URI` | MongoDB connection string for `api-metrics-db` |
| `OPENAI_API_KEY` | OpenAI API key (for gpt-4o-mini) |

## Output

Reports are saved to `agent/output/`:
- `api_metrics_report.json` – Structured JSON with all KPIs
- `api_metrics_report.md` – Human-readable Markdown with tables and insights
