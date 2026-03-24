"""MongoDB connection and data fetching utilities."""

import os
import warnings
from typing import Any

from pymongo import MongoClient

warnings.filterwarnings("ignore", message=".*CosmosDB.*")


def get_mongo_client() -> MongoClient:
    """Create and return a MongoDB client."""
    uri = os.environ.get("MONGO_URI", "")
    if not uri:
        raise ValueError("MONGO_URI environment variable is not set")
    return MongoClient(uri)


def fetch_all_metrics() -> list[dict[str, Any]]:
    """Fetch all documents from the api-metrics-db.metrics collection.

    Excludes any test/malformed documents (those without a 'date' field).
    """
    client = get_mongo_client()
    try:
        db = client["api-metrics-db"]
        collection = db["metrics"]
        docs = list(collection.find({"date": {"$exists": True, "$ne": None}}))
        for doc in docs:
            doc["_id"] = str(doc["_id"])
        return docs
    finally:
        client.close()


def fetch_metrics_summary() -> dict[str, Any]:
    """Fetch aggregated summary statistics directly from MongoDB."""
    client = get_mongo_client()
    try:
        db = client["api-metrics-db"]
        collection = db["metrics"]

        pipeline = [
            {"$match": {"date": {"$exists": True, "$ne": None}}},
            {
                "$group": {
                    "_id": None,
                    "total_requests": {"$sum": "$request_count"},
                    "total_success": {"$sum": "$success_count"},
                    "total_failure": {"$sum": "$failure_count"},
                    "avg_memory_mb": {"$avg": "$memory_usage_mb"},
                    "max_memory_mb": {"$max": "$memory_usage_mb"},
                    "avg_response_ms": {"$avg": "$avg_response_ms"},
                    "doc_count": {"$sum": 1},
                }
            },
        ]
        result = list(collection.aggregate(pipeline))
        if result:
            summary = result[0]
            summary.pop("_id", None)
            return summary
        return {}
    finally:
        client.close()
