#!/usr/bin/env python3
"""Main entry point for the API Metrics Report Agent.

Usage:
    python -m agent.main

Requires environment variables:
    MONGO_URI      – MongoDB connection string
    OPENAI_API_KEY – OpenAI API key for gpt-4o-mini

Reports are saved to agent/output/ as:
    - api_metrics_report.json
    - api_metrics_report.md
"""

import os
import sys
from pathlib import Path

from dotenv import load_dotenv


def main() -> None:
    """Run the LangGraph agent and write output reports."""
    # Load .env from the agent directory
    agent_dir = Path(__file__).resolve().parent
    env_path = agent_dir / ".env"
    if env_path.exists():
        load_dotenv(env_path)

    # Validate required env vars
    missing = []
    if not os.environ.get("MONGO_URI"):
        missing.append("MONGO_URI")
    if not os.environ.get("OPENAI_API_KEY"):
        missing.append("OPENAI_API_KEY")
    if missing:
        print(f"ERROR: Missing required environment variables: {', '.join(missing)}")
        print("Set them in agent/.env or export them before running.")
        sys.exit(1)

    print("=" * 60)
    print("  API Metrics Report Agent (LangGraph + gpt-4o-mini)")
    print("=" * 60)
    print()

    from agent.graph import run_agent

    print("[1/3] Starting LangGraph agent...")
    results = run_agent()
    print("[2/3] Agent completed. Generating reports...")

    # Write outputs
    output_dir = agent_dir / "output"
    output_dir.mkdir(exist_ok=True)

    json_path = output_dir / "api_metrics_report.json"
    md_path = output_dir / "api_metrics_report.md"

    json_path.write_text(results["json_report"], encoding="utf-8")
    md_path.write_text(results["markdown_report"], encoding="utf-8")

    print(f"[3/3] Reports saved:")
    print(f"  JSON:     {json_path}")
    print(f"  Markdown: {md_path}")
    print()
    print("Done!")


if __name__ == "__main__":
    main()
