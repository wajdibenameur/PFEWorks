from __future__ import annotations

import logging
from collections import defaultdict
from typing import Dict, List, Sequence, Tuple

import numpy as np


LOGGER = logging.getLogger(__name__)


def _severity_to_bucket(raw_severity: object, severity_buckets: Dict[int, int]) -> int:
    try:
        severity = int(raw_severity)
    except (TypeError, ValueError):
        return 1
    return severity_buckets.get(severity, 3 if severity >= 4 else 1)


def _normalize_dump(raw_data: Dict, config: Dict) -> Dict:
    severity_buckets = {int(key): int(value) for key, value in config["data"]["severity_buckets"].items()}

    hosts: Dict[str, str] = {}
    for row in raw_data["monitoring_host"]:
        hostid = str(row.get("host_id") or row.get("hostid") or "").strip()
        host_name = row.get("name") or row.get("host_name") or "unknown"
        if hostid:
            hosts[hostid] = str(host_name)

    metrics_by_host: Dict[str, List[Tuple[int, float]]] = defaultdict(list)
    for row in raw_data["zabbix_metric"]:
        hostid = str(row.get("host_id") or row.get("hostid") or "").strip()
        timestamp = row.get("timestamp") or row.get("clock")
        value = row.get("value")
        if not hostid or timestamp in (None, "") or value in (None, ""):
            continue
        try:
            metrics_by_host[hostid].append((int(timestamp), float(value)))
        except (TypeError, ValueError):
            continue

    problems_by_host: Dict[str, List[Tuple[int, int]]] = defaultdict(list)
    skipped = 0
    for row in raw_data["zabbix_problem"]:
        hostid = str(row.get("host_id") or row.get("hostid") or "").strip()
        problem_time = row.get("started_at") or row.get("clock")
        if not hostid or problem_time in (None, ""):
            skipped += 1
            continue
        severity_bucket = _severity_to_bucket(row.get("severity"), severity_buckets)
        try:
            problems_by_host[hostid].append((int(problem_time), severity_bucket))
        except (TypeError, ValueError):
            skipped += 1

    for hostid in metrics_by_host:
        metrics_by_host[hostid].sort(key=lambda item: item[0])
    for hostid in problems_by_host:
        problems_by_host[hostid].sort(key=lambda item: item[0])

    LOGGER.info("Normalized dump | hosts=%s | skipped_problems_without_timestamp=%s", len(hosts), skipped)
    return {
        "hosts": hosts,
        "metrics_by_host": metrics_by_host,
        "problems_by_host": problems_by_host,
    }


def _window_values(values: Sequence[Tuple[int, float]], end_ts: int, window_seconds: int) -> List[float]:
    start_ts = end_ts - window_seconds
    return [value for ts, value in values if start_ts <= ts < end_ts]


def _window_values_between(
    values: Sequence[Tuple[int, float]],
    start_ts: int,
    end_ts: int,
) -> List[float]:
    return [value for ts, value in values if start_ts <= ts < end_ts]


def _window_problem_count(values: Sequence[Tuple[int, int]], end_ts: int, window_seconds: int) -> int:
    start_ts = end_ts - window_seconds
    return sum(1 for ts, _ in values if start_ts <= ts < end_ts)


def build_event_dataset(raw_data: Dict, config: Dict) -> Dict[str, np.ndarray]:
    normalized = _normalize_dump(raw_data, config)
    feature_order = config["features"]["feature_order"]
    metric_window_1h = int(config["features"]["metric_window_1h_seconds"])
    metric_window_24h = int(config["features"]["metric_window_24h_seconds"])
    problem_window_1h = int(config["features"]["problem_window_1h_seconds"])
    problem_window_24h = int(config["features"]["problem_window_24h_seconds"])

    rows: List[List[float]] = []
    labels: List[int] = []
    timestamps: List[int] = []
    hostids: List[str] = []

    for hostid, problems in normalized["problems_by_host"].items():
        metric_values = normalized["metrics_by_host"].get(hostid, [])
        prior_problems: List[Tuple[int, int]] = []
        for problem_ts, severity_bucket in problems:
            metric_last_1h = _window_values(metric_values, problem_ts, metric_window_1h)
            metric_last_24h = _window_values(metric_values, problem_ts, metric_window_24h)
            metric_prev_23h = _window_values_between(metric_values, problem_ts - metric_window_24h, problem_ts - metric_window_1h)
            problem_count_last_1h = _window_problem_count(prior_problems, problem_ts, problem_window_1h)
            problem_count_last_24h = _window_problem_count(prior_problems, problem_ts, problem_window_24h)
            time_since_last_problem = float(problem_ts - prior_problems[-1][0]) if prior_problems else float(problem_window_24h * 7)

            avg_metric_last_1h = float(np.mean(metric_last_1h)) if metric_last_1h else 0.0
            max_metric_last_1h = float(np.max(metric_last_1h)) if metric_last_1h else 0.0
            avg_metric_last_24h = float(np.mean(metric_last_24h)) if metric_last_24h else 0.0
            avg_metric_prev_23h = float(np.mean(metric_prev_23h)) if metric_prev_23h else avg_metric_last_24h
            trend_metric = avg_metric_last_1h - avg_metric_prev_23h

            feature_map = {
                "problem_count_last_1h": float(problem_count_last_1h),
                "problem_count_last_24h": float(problem_count_last_24h),
                "avg_metric_last_1h": avg_metric_last_1h,
                "max_metric_last_1h": max_metric_last_1h,
                "trend_metric": float(trend_metric),
                "time_since_last_problem": time_since_last_problem,
            }

            rows.append([feature_map[name] for name in feature_order])
            labels.append(int(severity_bucket) - 1)
            timestamps.append(problem_ts)
            hostids.append(hostid)
            prior_problems.append((problem_ts, severity_bucket))

    event_count = len(rows)
    min_events = int(config["data"]["min_timestamped_events"])
    if event_count < min_events:
        raise ValueError(
            f"Only {event_count} timestamped problem events are available. "
            f"At least {min_events} are required for training."
        )

    LOGGER.info("Built event dataset with %s labeled rows", event_count)
    return {
        "X": np.asarray(rows, dtype=np.float32),
        "y": np.asarray(labels, dtype=np.int64),
        "timestamps": np.asarray(timestamps, dtype=np.int64),
        "hostids": np.asarray(hostids),
        "feature_order": np.asarray(feature_order),
    }
