"""LangGraph-based agent for API Metrics Report generation."""

import json
from typing import Annotated, Any, TypedDict

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, ToolMessage
from langchain_core.tools import tool
from langchain_openai import ChatOpenAI
from langgraph.graph import END, StateGraph
from langgraph.graph.message import add_messages
from langgraph.prebuilt import ToolNode

from agent.db import fetch_all_metrics
from agent.kpis import (
    compute_all_kpis,
    compute_performance_metrics,
    compute_reliability_insights,
    compute_success_failure_metrics,
    compute_time_based_insights,
    compute_traffic_analysis,
)
from agent.report import generate_json_report, generate_markdown_report


# ---------------------------------------------------------------------------
# State
# ---------------------------------------------------------------------------

class AgentState(TypedDict):
    """State schema for the LangGraph agent."""

    messages: Annotated[list[BaseMessage], add_messages]
    metrics_data: list[dict[str, Any]]
    kpis: dict[str, Any]
    json_report: str
    markdown_report: str


# ---------------------------------------------------------------------------
# Tools – callable by the LLM
# ---------------------------------------------------------------------------

@tool
def fetch_metrics_from_db() -> str:
    """Fetch all API metrics data from the MongoDB collection api-metrics-db.metrics.

    Returns a summary of the fetched data including document count and date range.
    """
    docs = fetch_all_metrics()
    if not docs:
        return "No metrics data found in the database."

    dates = sorted(set(d.get("date", "") for d in docs if d.get("date")))
    services = sorted(set(d.get("service", "") for d in docs if d.get("service")))

    return json.dumps({
        "status": "success",
        "document_count": len(docs),
        "date_range": {"start": dates[0] if dates else "N/A", "end": dates[-1] if dates else "N/A"},
        "services": services,
        "sample_fields": list(docs[0].keys()) if docs else [],
    })


@tool
def analyze_traffic(data_json: str) -> str:
    """Analyze API traffic patterns including total calls, daily trends, peak days, and busiest APIs.

    Args:
        data_json: Not used directly — fetches from stored state. Pass 'run' to execute.
    """
    docs = fetch_all_metrics()
    result = compute_traffic_analysis(docs)
    # Trim daily trend for LLM context (just summary stats)
    trend = result["daily_traffic_trend"]
    result["daily_traffic_trend_summary"] = {
        "total_days": len(trend),
        "min_daily_traffic": min(trend.values()) if trend else 0,
        "max_daily_traffic": max(trend.values()) if trend else 0,
    }
    del result["daily_traffic_trend"]
    return json.dumps(result, default=str)


@tool
def analyze_success_failure(data_json: str) -> str:
    """Analyze success and failure metrics including ratios and top failing APIs.

    Args:
        data_json: Not used directly — fetches from stored state. Pass 'run' to execute.
    """
    docs = fetch_all_metrics()
    result = compute_success_failure_metrics(docs)
    return json.dumps(result, default=str)


@tool
def analyze_performance(data_json: str) -> str:
    """Analyze performance metrics including memory usage and traffic-memory correlation.

    Args:
        data_json: Not used directly — fetches from stored state. Pass 'run' to execute.
    """
    docs = fetch_all_metrics()
    result = compute_performance_metrics(docs)
    return json.dumps(result, default=str)


@tool
def analyze_time_insights(data_json: str) -> str:
    """Analyze time-based insights with anomaly detection (>2σ threshold).

    Args:
        data_json: Not used directly — fetches from stored state. Pass 'run' to execute.
    """
    docs = fetch_all_metrics()
    result = compute_time_based_insights(docs)
    # Summarize for LLM
    result["daily_breakdown_count"] = len(result["daily_breakdown"])
    del result["daily_breakdown"]
    return json.dumps(result, default=str)


@tool
def analyze_reliability(data_json: str) -> str:
    """Analyze reliability insights including high-failure days and unstable APIs.

    Args:
        data_json: Not used directly — fetches from stored state. Pass 'run' to execute.
    """
    docs = fetch_all_metrics()
    result = compute_reliability_insights(docs)
    return json.dumps(result, default=str)


@tool
def generate_reports(data_json: str) -> str:
    """Generate both JSON and Markdown reports from all computed KPIs.

    Args:
        data_json: Not used directly — computes all KPIs and generates reports. Pass 'run' to execute.

    Returns:
        Confirmation with report sizes.
    """
    docs = fetch_all_metrics()
    kpis = compute_all_kpis(docs)
    json_report = generate_json_report(kpis)
    md_report = generate_markdown_report(kpis)
    return json.dumps({
        "status": "reports_generated",
        "json_report_size": len(json_report),
        "markdown_report_size": len(md_report),
        "sections": list(kpis.keys()),
    })


TOOLS = [
    fetch_metrics_from_db,
    analyze_traffic,
    analyze_success_failure,
    analyze_performance,
    analyze_time_insights,
    analyze_reliability,
    generate_reports,
]


# ---------------------------------------------------------------------------
# Graph nodes
# ---------------------------------------------------------------------------

def create_llm():
    """Create the ChatOpenAI LLM instance."""
    return ChatOpenAI(model="gpt-4o-mini", temperature=0).bind_tools(TOOLS)


def agent_node(state: AgentState) -> dict[str, Any]:
    """LLM agent node — decides which tool to call or produces final answer."""
    llm = create_llm()
    response = llm.invoke(state["messages"])
    return {"messages": [response]}


def should_continue(state: AgentState) -> str:
    """Route: if the last message has tool calls go to 'tools', else 'generate'."""
    last = state["messages"][-1]
    if isinstance(last, AIMessage) and last.tool_calls:
        return "tools"
    return "generate"


def generate_node(state: AgentState) -> dict[str, Any]:
    """Final node — compute all KPIs and produce both reports."""
    docs = fetch_all_metrics()
    kpis = compute_all_kpis(docs)
    json_report = generate_json_report(kpis)
    md_report = generate_markdown_report(kpis)
    return {
        "metrics_data": docs,
        "kpis": kpis,
        "json_report": json_report,
        "markdown_report": md_report,
    }


# ---------------------------------------------------------------------------
# Build the graph
# ---------------------------------------------------------------------------

def build_graph() -> StateGraph:
    """Build and compile the LangGraph state graph."""
    graph = StateGraph(AgentState)

    # Nodes
    graph.add_node("agent", agent_node)
    graph.add_node("tools", ToolNode(TOOLS))
    graph.add_node("generate", generate_node)

    # Edges
    graph.set_entry_point("agent")
    graph.add_conditional_edges("agent", should_continue, {"tools": "tools", "generate": "generate"})
    graph.add_edge("tools", "agent")
    graph.add_edge("generate", END)

    return graph.compile()


def run_agent() -> dict[str, str]:
    """Run the agent end-to-end and return JSON + Markdown reports.

    Returns:
        Dict with keys 'json_report' and 'markdown_report'.
    """
    app = build_graph()

    system_prompt = (
        "You are an API Metrics Analyst Agent. Your job is to generate a comprehensive "
        "API Metrics Report by querying the MongoDB database and analyzing the data.\n\n"
        "Follow these steps:\n"
        "1. First, fetch the metrics data from MongoDB using fetch_metrics_from_db\n"
        "2. Then analyze each category:\n"
        "   - Traffic analysis (analyze_traffic)\n"
        "   - Success/failure metrics (analyze_success_failure)\n"
        "   - Performance metrics (analyze_performance)\n"
        "   - Time-based insights (analyze_time_insights)\n"
        "   - Reliability insights (analyze_reliability)\n"
        "3. Finally, call generate_reports to produce the final output\n"
        "4. After all tools have been called, summarize the key findings.\n\n"
        "Call each tool exactly once. Pass 'run' as the argument where required."
    )

    initial_state: AgentState = {
        "messages": [HumanMessage(content=system_prompt)],
        "metrics_data": [],
        "kpis": {},
        "json_report": "",
        "markdown_report": "",
    }

    final_state = app.invoke(initial_state, {"recursion_limit": 25})

    return {
        "json_report": final_state.get("json_report", ""),
        "markdown_report": final_state.get("markdown_report", ""),
    }
