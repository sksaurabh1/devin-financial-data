"""Report generation in JSON and Markdown formats."""

import json
from datetime import datetime, timezone
from typing import Any


def generate_json_report(kpis: dict[str, Any]) -> str:
    """Generate a structured JSON report from computed KPIs."""
    report = {
        "report_metadata": {
            "title": "API Metrics Report",
            "generated_at": datetime.now(timezone.utc).isoformat(),
            "data_source": "api-metrics-db.metrics",
        },
        **kpis,
    }
    return json.dumps(report, indent=2, default=str)


def generate_markdown_report(kpis: dict[str, Any]) -> str:
    """Generate a human-readable Markdown report from computed KPIs."""
    lines: list[str] = []

    lines.append("# API Metrics Report")
    lines.append("")
    lines.append(f"**Generated:** {datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M:%S UTC')}")
    lines.append(f"**Data Source:** `api-metrics-db.metrics`")
    lines.append("")

    # --- 1. Traffic Analysis ---
    ta = kpis.get("traffic_analysis", {})
    lines.append("---")
    lines.append("## 1. Traffic Analysis")
    lines.append("")
    lines.append(f"- **Total API Calls:** {ta.get('total_api_calls', 0):,}")
    peak = ta.get("peak_traffic_day", {})
    lines.append(f"- **Peak Traffic Day:** {peak.get('date', 'N/A')} ({peak.get('count', 0):,} calls)")
    lines.append("")

    lines.append("### Top 5 Busiest APIs")
    lines.append("")
    lines.append("| Rank | API | Call Count |")
    lines.append("|------|-----|------------|")
    for i, api in enumerate(ta.get("top_5_busiest_apis", []), 1):
        lines.append(f"| {i} | `{api['api']}` | {api['call_count']:,} |")
    lines.append("")

    lines.append("### Daily Traffic Trend")
    lines.append("")
    lines.append("| Date | Requests |")
    lines.append("|------|----------|")
    for date, count in ta.get("daily_traffic_trend", {}).items():
        lines.append(f"| {date} | {count:,} |")
    lines.append("")

    # --- 2. Success & Failure Metrics ---
    sf = kpis.get("success_failure_metrics", {})
    lines.append("---")
    lines.append("## 2. Success & Failure Metrics")
    lines.append("")
    lines.append(f"- **Total Success Count:** {sf.get('total_success_count', 0):,}")
    lines.append(f"- **Total Failure Count:** {sf.get('total_failure_count', 0):,}")
    lines.append(f"- **Success Ratio:** {sf.get('success_ratio_percent', 0)}%")
    lines.append(f"- **Failure Rate:** {sf.get('failure_rate_percent', 0)}%")
    lines.append("")

    lines.append("### Top APIs by Failure Rate")
    lines.append("")
    lines.append("| API | Failure Rate (%) | Failure Count | Total Calls |")
    lines.append("|-----|-----------------|---------------|-------------|")
    for api in sf.get("top_apis_by_failure_rate", []):
        lines.append(
            f"| `{api['api']}` | {api['failure_rate_percent']} | "
            f"{api['failure_count']:,} | {api['total_calls']:,} |"
        )
    lines.append("")

    # --- 3. Performance Metrics ---
    pm = kpis.get("performance_metrics", {})
    lines.append("---")
    lines.append("## 3. Performance Metrics")
    lines.append("")
    lines.append(f"- **Average Memory Usage:** {pm.get('avg_memory_usage_mb', 0)} MB")
    lines.append(f"- **Peak Memory Usage:** {pm.get('peak_memory_usage_mb', 0)} MB")
    corr = pm.get("traffic_memory_correlation", {})
    lines.append(
        f"- **Traffic ↔ Memory Correlation:** {corr.get('coefficient', 0)} "
        f"({corr.get('interpretation', 'N/A')})"
    )
    lines.append("")

    # --- 4. Time-Based Insights ---
    tb = kpis.get("time_based_insights", {})
    lines.append("---")
    lines.append("## 4. Time-Based Insights")
    lines.append("")
    lines.append("### Day-wise Breakdown")
    lines.append("")
    lines.append("| Date | Traffic | Success | Failure |")
    lines.append("|------|---------|---------|---------|")
    for entry in tb.get("daily_breakdown", []):
        lines.append(
            f"| {entry['date']} | {entry['traffic']:,} | "
            f"{entry['success']:,} | {entry['failure']:,} |"
        )
    lines.append("")

    anomalies = tb.get("anomalies", [])
    lines.append("### Anomaly Detection (>2σ from mean)")
    lines.append("")
    if anomalies:
        lines.append("| Date | Metric | Value | Mean | Std Dev | Threshold (2σ) |")
        lines.append("|------|--------|-------|------|---------|----------------|")
        for a in anomalies:
            lines.append(
                f"| {a['date']} | {a['metric']} | {a['value']:,} | "
                f"{a['mean']} | {a['std_dev']} | {a['threshold_2sigma']} |"
            )
        lines.append("")
        lines.append("**⚠ Actionable Insight:** The dates listed above show statistically significant ")
        lines.append("deviations from normal patterns. Investigate root causes such as traffic surges, ")
        lines.append("deployment changes, or upstream service issues on these dates.")
    else:
        lines.append("No anomalies detected — all daily values are within 2 standard deviations of the mean.")
    lines.append("")

    # --- 5. Reliability Insights ---
    ri = kpis.get("reliability_insights", {})
    lines.append("---")
    lines.append("## 5. Reliability Insights")
    lines.append("")

    hfd = ri.get("high_failure_days", [])
    lines.append("### Days with Unusually High Failure Counts")
    lines.append("")
    if hfd:
        lines.append("| Date | Failure Count | Threshold (2σ) |")
        lines.append("|------|---------------|----------------|")
        for d in hfd:
            lines.append(f"| {d['date']} | {d['failure_count']:,} | {d['threshold_2sigma']} |")
        lines.append("")
        lines.append("**⚠ Actionable Insight:** These dates experienced failure spikes. ")
        lines.append("Review error logs, check for infrastructure degradation, and correlate with deployment events.")
    else:
        lines.append("No days with unusually high failure counts detected.")
    lines.append("")

    ua = ri.get("unstable_apis", [])
    lines.append("### APIs with Unstable Performance (High Variance in Success Rate)")
    lines.append("")
    if ua:
        lines.append("| API | Avg Success Rate (%) | Std Dev (%) | Data Points |")
        lines.append("|-----|---------------------|-------------|-------------|")
        for api in ua:
            lines.append(
                f"| `{api['api']}` | {api['success_rate_mean_percent']} | "
                f"{api['success_rate_std_percent']} | {api['data_points']} |"
            )
        lines.append("")
        lines.append("**⚠ Actionable Insight:** These APIs show inconsistent reliability. ")
        lines.append("Consider investigating intermittent failures, timeout configurations, ")
        lines.append("or dependency stability for these endpoints.")
    else:
        lines.append("All APIs show consistent performance (success rate std dev ≤ 3%).")
    lines.append("")

    lines.append("---")
    lines.append("*Report generated by LangGraph API Metrics Agent using gpt-4o-mini.*")
    lines.append("")

    return "\n".join(lines)
