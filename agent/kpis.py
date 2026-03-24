"""KPI computation functions for API metrics analysis."""

from collections import defaultdict
from typing import Any

import numpy as np


def compute_traffic_analysis(docs: list[dict[str, Any]]) -> dict[str, Any]:
    """Compute traffic analysis KPIs.

    Returns:
        - total_api_calls
        - daily_traffic_trend: {date: count}
        - peak_traffic_day: {date, count}
        - top_5_busiest_apis: [{api, count}, ...]
    """
    total_api_calls = sum(d.get("request_count", 0) for d in docs)

    daily_traffic: dict[str, int] = defaultdict(int)
    api_traffic: dict[str, int] = defaultdict(int)

    for doc in docs:
        date = doc.get("date", "unknown")
        daily_traffic[date] += doc.get("request_count", 0)

        api_key = f"{doc.get('service', 'unknown')} {doc.get('endpoint', 'unknown')}"
        api_traffic[api_key] += doc.get("request_count", 0)

    daily_trend = dict(sorted(daily_traffic.items()))

    peak_day = max(daily_traffic.items(), key=lambda x: x[1]) if daily_traffic else ("N/A", 0)

    top_5 = sorted(api_traffic.items(), key=lambda x: x[1], reverse=True)[:5]
    top_5_apis = [{"api": api, "call_count": count} for api, count in top_5]

    return {
        "total_api_calls": total_api_calls,
        "daily_traffic_trend": daily_trend,
        "peak_traffic_day": {"date": peak_day[0], "count": peak_day[1]},
        "top_5_busiest_apis": top_5_apis,
    }


def compute_success_failure_metrics(docs: list[dict[str, Any]]) -> dict[str, Any]:
    """Compute success and failure KPIs.

    Returns:
        - total_success_count
        - total_failure_count
        - success_ratio_percent
        - failure_rate_percent
        - top_apis_by_failure_rate: [{api, failure_rate, total_calls}, ...]
    """
    total_success = sum(d.get("success_count", 0) for d in docs)
    total_failure = sum(d.get("failure_count", 0) for d in docs)
    total = total_success + total_failure

    success_ratio = round((total_success / total) * 100, 2) if total > 0 else 0.0
    failure_rate = round((total_failure / total) * 100, 2) if total > 0 else 0.0

    api_stats: dict[str, dict[str, int]] = defaultdict(lambda: {"success": 0, "failure": 0, "total": 0})
    for doc in docs:
        api_key = f"{doc.get('service', 'unknown')} {doc.get('endpoint', 'unknown')}"
        api_stats[api_key]["success"] += doc.get("success_count", 0)
        api_stats[api_key]["failure"] += doc.get("failure_count", 0)
        api_stats[api_key]["total"] += doc.get("request_count", 0)

    api_failure_rates = []
    for api, stats in api_stats.items():
        t = stats["success"] + stats["failure"]
        if t > 0:
            fr = round((stats["failure"] / t) * 100, 2)
            api_failure_rates.append({
                "api": api,
                "failure_rate_percent": fr,
                "total_calls": stats["total"],
                "failure_count": stats["failure"],
            })

    api_failure_rates.sort(key=lambda x: x["failure_rate_percent"], reverse=True)

    return {
        "total_success_count": total_success,
        "total_failure_count": total_failure,
        "success_ratio_percent": success_ratio,
        "failure_rate_percent": failure_rate,
        "top_apis_by_failure_rate": api_failure_rates[:10],
    }


def compute_performance_metrics(docs: list[dict[str, Any]]) -> dict[str, Any]:
    """Compute performance KPIs.

    Returns:
        - avg_memory_usage_mb
        - peak_memory_usage_mb
        - traffic_memory_correlation
    """
    memory_values = [d.get("memory_usage_mb", 0) for d in docs if d.get("memory_usage_mb") is not None]
    request_counts = [d.get("request_count", 0) for d in docs if d.get("memory_usage_mb") is not None]

    avg_memory = round(float(np.mean(memory_values)), 2) if memory_values else 0.0
    peak_memory = max(memory_values) if memory_values else 0

    correlation = 0.0
    correlation_interpretation = "insufficient data"
    if len(memory_values) >= 2 and np.std(memory_values) > 0 and np.std(request_counts) > 0:
        corr_matrix = np.corrcoef(request_counts, memory_values)
        correlation = round(float(corr_matrix[0, 1]), 4)
        if abs(correlation) >= 0.7:
            correlation_interpretation = "strong"
        elif abs(correlation) >= 0.4:
            correlation_interpretation = "moderate"
        else:
            correlation_interpretation = "weak"

    return {
        "avg_memory_usage_mb": avg_memory,
        "peak_memory_usage_mb": peak_memory,
        "traffic_memory_correlation": {
            "coefficient": correlation,
            "interpretation": correlation_interpretation,
        },
    }


def compute_time_based_insights(docs: list[dict[str, Any]]) -> dict[str, Any]:
    """Compute time-based KPIs with anomaly detection.

    Returns:
        - daily_breakdown: [{date, traffic, success, failure}, ...]
        - anomalies: [{date, metric, value, mean, std_dev, threshold}, ...]
    """
    daily: dict[str, dict[str, int]] = defaultdict(lambda: {"traffic": 0, "success": 0, "failure": 0})

    for doc in docs:
        date = doc.get("date", "unknown")
        daily[date]["traffic"] += doc.get("request_count", 0)
        daily[date]["success"] += doc.get("success_count", 0)
        daily[date]["failure"] += doc.get("failure_count", 0)

    daily_breakdown = [
        {"date": date, **stats}
        for date, stats in sorted(daily.items())
    ]

    # Anomaly detection: >2σ from mean
    anomalies = []
    for metric in ["traffic", "success", "failure"]:
        values = [d[metric] for d in daily_breakdown]
        if len(values) >= 2:
            mean = float(np.mean(values))
            std = float(np.std(values))
            threshold = mean + 2 * std
            for entry in daily_breakdown:
                if entry[metric] > threshold:
                    anomalies.append({
                        "date": entry["date"],
                        "metric": metric,
                        "value": entry[metric],
                        "mean": round(mean, 2),
                        "std_dev": round(std, 2),
                        "threshold_2sigma": round(threshold, 2),
                    })

    return {
        "daily_breakdown": daily_breakdown,
        "anomalies": anomalies,
        "anomaly_detection_method": ">2 standard deviations from mean",
    }


def compute_reliability_insights(docs: list[dict[str, Any]]) -> dict[str, Any]:
    """Compute reliability KPIs.

    Returns:
        - high_failure_days: [{date, failure_count, threshold}, ...]
        - unstable_apis: [{api, success_rate_mean, success_rate_std, data_points}, ...]
    """
    # Days with unusually high failure counts (>2σ)
    daily_failure: dict[str, int] = defaultdict(int)
    for doc in docs:
        date = doc.get("date", "unknown")
        daily_failure[date] += doc.get("failure_count", 0)

    failure_values = list(daily_failure.values())
    high_failure_days = []
    if len(failure_values) >= 2:
        mean_f = float(np.mean(failure_values))
        std_f = float(np.std(failure_values))
        threshold_f = mean_f + 2 * std_f
        for date, count in sorted(daily_failure.items()):
            if count > threshold_f:
                high_failure_days.append({
                    "date": date,
                    "failure_count": count,
                    "threshold_2sigma": round(threshold_f, 2),
                })

    # APIs with unstable performance (high variance in success rate)
    api_daily_rates: dict[str, list[float]] = defaultdict(list)
    for doc in docs:
        api_key = f"{doc.get('service', 'unknown')} {doc.get('endpoint', 'unknown')}"
        total = doc.get("success_count", 0) + doc.get("failure_count", 0)
        if total > 0:
            rate = doc.get("success_count", 0) / total * 100
            api_daily_rates[api_key].append(rate)

    unstable_apis = []
    for api, rates in api_daily_rates.items():
        if len(rates) >= 5:
            std_rate = float(np.std(rates))
            mean_rate = float(np.mean(rates))
            if std_rate > 3.0:  # >3% std in success rate signals instability
                unstable_apis.append({
                    "api": api,
                    "success_rate_mean_percent": round(mean_rate, 2),
                    "success_rate_std_percent": round(std_rate, 2),
                    "data_points": len(rates),
                })

    unstable_apis.sort(key=lambda x: x["success_rate_std_percent"], reverse=True)

    return {
        "high_failure_days": high_failure_days,
        "unstable_apis": unstable_apis,
    }


def compute_all_kpis(docs: list[dict[str, Any]]) -> dict[str, Any]:
    """Compute all KPIs from the metrics documents."""
    return {
        "traffic_analysis": compute_traffic_analysis(docs),
        "success_failure_metrics": compute_success_failure_metrics(docs),
        "performance_metrics": compute_performance_metrics(docs),
        "time_based_insights": compute_time_based_insights(docs),
        "reliability_insights": compute_reliability_insights(docs),
    }
