from __future__ import annotations

import logging
from collections import defaultdict
from typing import Dict, List, Sequence, Tuple

import numpy as np


LOGGER = logging.getLogger(__name__)


def _parse_raw_severity(raw_severity: object) -> int:
    try:
        return int(raw_severity)
    except (TypeError, ValueError):
        return 1


def _severity_to_bucket(raw_severity: object, severity_buckets: Dict[int, int]) -> int:
    severity = _parse_raw_severity(raw_severity)
    return severity_buckets.get(severity, 3 if severity >= 4 else 1)


def _normalize_dump(raw_data: Dict, config: Dict) -> Dict:
    severity_buckets = {int(key): int(value) for key, value in config["data"]["severity_buckets"].items()}

    hosts: Dict[str, str] = {}
    host_ips_by_hostid: Dict[str, set[str]] = defaultdict(set)
    for row in raw_data["monitoring_host"]:
        hostid = str(row.get("host_id") or row.get("hostid") or "").strip()
        host_name = row.get("name") or row.get("host_name") or "unknown"
        host_ip = str(row.get("ip") or row.get("ip_address") or "").strip()
        if hostid:
            hosts[hostid] = str(host_name)
            if host_ip:
                host_ips_by_hostid[hostid].add(host_ip)

    metrics_by_host: Dict[str, List[Dict[str, float | int | str]]] = defaultdict(list)
    for row in raw_data["zabbix_metric"]:
        hostid = str(row.get("host_id") or row.get("hostid") or "").strip()
        timestamp = row.get("timestamp") or row.get("clock")
        value = row.get("value")
        metric_key = str(row.get("metric_key") or row.get("item_key") or "").strip()
        metric_ip = str(row.get("ip") or row.get("ip_address") or "").strip()
        if not hostid or timestamp in (None, "") or value in (None, ""):
            continue
        try:
            metrics_by_host[hostid].append(
                {
                    "timestamp": int(timestamp),
                    "value": float(value),
                    "metric_key": metric_key,
                }
            )
            if metric_ip:
                host_ips_by_hostid[hostid].add(metric_ip)
        except (TypeError, ValueError):
            continue

    problems_by_host: Dict[str, List[Dict[str, int]]] = defaultdict(list)
    skipped = 0
    for row in raw_data["zabbix_problem"]:
        hostid = str(row.get("host_id") or row.get("hostid") or "").strip()
        problem_time = row.get("started_at") or row.get("clock")
        problem_ip = str(row.get("ip") or row.get("ip_address") or "").strip()
        if not hostid or problem_time in (None, ""):
            skipped += 1
            continue
        raw_severity = _parse_raw_severity(row.get("severity"))
        severity_bucket = _severity_to_bucket(raw_severity, severity_buckets)
        try:
            problems_by_host[hostid].append(
                {
                    "timestamp": int(problem_time),
                    "severity_bucket": severity_bucket,
                    "raw_severity": raw_severity,
                }
            )
            if problem_ip:
                host_ips_by_hostid[hostid].add(problem_ip)
        except (TypeError, ValueError):
            skipped += 1

    observium_rows = raw_data.get("observium_interface", [])
    observium_by_ip: Dict[str, List[Dict[str, float | int | str]]] = defaultdict(list)
    for row in observium_rows:
        obs_ip = str(row.get("ip_address") or row.get("host_id") or "").strip()
        timestamp = row.get("last_poll_epoch_sec")
        if not obs_ip or timestamp in (None, ""):
            continue
        try:
            observium_by_ip[obs_ip].append(
                {
                    "timestamp": int(timestamp),
                    "in_bandwidth_mbps": float(row.get("in_bandwidth_mbps") or 0.0),
                    "out_bandwidth_mbps": float(row.get("out_bandwidth_mbps") or 0.0),
                    "in_errors": float(row.get("in_errors") or 0.0),
                    "out_errors": float(row.get("out_errors") or 0.0),
                    "oper_status": str(row.get("oper_status") or "").strip().upper(),
                    "utilization_percent": float(row.get("utilization_percent") or 0.0),
                }
            )
        except (TypeError, ValueError):
            continue

    observium_by_host: Dict[str, List[Dict[str, float | int | str]]] = defaultdict(list)
    for hostid, ip_candidates in host_ips_by_hostid.items():
        for ip_candidate in ip_candidates:
            observium_by_host[hostid].extend(observium_by_ip.get(ip_candidate, []))

    for hostid in metrics_by_host:
        metrics_by_host[hostid].sort(key=lambda item: int(item["timestamp"]))
    for hostid in problems_by_host:
        problems_by_host[hostid].sort(key=lambda item: int(item["timestamp"]))
    for hostid in observium_by_host:
        observium_by_host[hostid].sort(key=lambda item: int(item["timestamp"]))

    observium_covered_hosts = sum(1 for hostid in observium_by_host if observium_by_host[hostid])
    LOGGER.info(
        "Normalized dump | hosts=%s | skipped_problems_without_timestamp=%s | observium_covered_hosts=%s",
        len(hosts),
        skipped,
        observium_covered_hosts,
    )
    return {
        "hosts": hosts,
        "metrics_by_host": metrics_by_host,
        "problems_by_host": problems_by_host,
        "observium_by_host": observium_by_host,
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


def _window_problem_count(values: Sequence[Dict[str, int]], end_ts: int, window_seconds: int) -> int:
    start_ts = end_ts - window_seconds
    return sum(1 for row in values if start_ts <= int(row["timestamp"]) < end_ts)


def _metric_matches(metric_key: str, prefixes: Sequence[str]) -> bool:
    normalized = metric_key.lower()
    return any(normalized.startswith(prefix.lower()) for prefix in prefixes)


def _latest_metric_value(
    values: Sequence[Dict[str, float | int | str]],
    end_ts: int,
    prefixes: Sequence[str],
) -> float:
    candidates = [
        row for row in values
        if int(row["timestamp"]) < end_ts and _metric_matches(str(row.get("metric_key") or ""), prefixes)
    ]
    if not candidates:
        return 0.0
    latest = max(candidates, key=lambda row: int(row["timestamp"]))
    return float(latest["value"])


def _window_metric_values(
    values: Sequence[Dict[str, float | int | str]],
    start_ts: int,
    end_ts: int,
    prefixes: Sequence[str],
) -> List[float]:
    return [
        float(row["value"])
        for row in values
        if start_ts <= int(row["timestamp"]) < end_ts
        and _metric_matches(str(row.get("metric_key") or ""), prefixes)
    ]


def _safe_percent(value: float) -> float:
    if not np.isfinite(value):
        return 0.0
    return float(np.clip(value, 0.0, 100.0))


def _safe_non_negative(value: float) -> float:
    if not np.isfinite(value):
        return 0.0
    return float(max(0.0, value))


def _safe_temperature(value: float) -> float:
    if not np.isfinite(value):
        return 0.0
    return float(np.clip(value, -50.0, 150.0))


def _observium_rows_for_timestamp(
    values: Sequence[Dict[str, float | int | str]],
    end_ts: int,
) -> List[Dict[str, float | int | str]]:
    if not values:
        return []
    prior = [row for row in values if int(row["timestamp"]) <= end_ts]
    if not prior:
        latest_ts = max(int(row["timestamp"]) for row in values)
        return [row for row in values if int(row["timestamp"]) == latest_ts]
    latest_ts = max(int(row["timestamp"]) for row in prior)
    return [row for row in prior if int(row["timestamp"]) == latest_ts]


def _aggregate_observium_interface(
    values: Sequence[Dict[str, float | int | str]],
    end_ts: int,
) -> Dict[str, float]:
    latest_rows = _observium_rows_for_timestamp(values, end_ts)
    if not latest_rows:
        return {
            "traffic_in_bps": 0.0,
            "traffic_out_bps": 0.0,
            "interface_errors_in": 0.0,
            "interface_errors_out": 0.0,
            "availability_status": 0.0,
            "interface_utilization_max": 0.0,
        }

    traffic_in_bps = sum(float(row.get("in_bandwidth_mbps") or 0.0) for row in latest_rows) * 1_000_000.0
    traffic_out_bps = sum(float(row.get("out_bandwidth_mbps") or 0.0) for row in latest_rows) * 1_000_000.0
    interface_errors_in = sum(float(row.get("in_errors") or 0.0) for row in latest_rows)
    interface_errors_out = sum(float(row.get("out_errors") or 0.0) for row in latest_rows)
    availability_status = 1.0 if any(str(row.get("oper_status") or "").upper() == "UP" for row in latest_rows) else 0.0
    interface_utilization_max = max(float(row.get("utilization_percent") or 0.0) for row in latest_rows)
    return {
        "traffic_in_bps": _safe_non_negative(traffic_in_bps),
        "traffic_out_bps": _safe_non_negative(traffic_out_bps),
        "interface_errors_in": _safe_non_negative(interface_errors_in),
        "interface_errors_out": _safe_non_negative(interface_errors_out),
        "availability_status": availability_status,
        "interface_utilization_max": _safe_percent(interface_utilization_max),
    }


def _window_problem_count_by_severity(
    values: Sequence[Dict[str, int]],
    end_ts: int,
    window_seconds: int,
    min_severity: int,
) -> int:
    start_ts = end_ts - window_seconds
    return sum(
        1
        for row in values
        if start_ts <= int(row["timestamp"]) < end_ts and int(row["raw_severity"]) >= min_severity
    )


def _last_problem_severity(values: Sequence[Dict[str, int]], end_ts: int) -> int:
    prior = [row for row in values if int(row["timestamp"]) < end_ts]
    if not prior:
        return 0
    return int(max(prior, key=lambda item: int(item["timestamp"]))["raw_severity"])


def _time_since_last_critical_problem_minutes(values: Sequence[Dict[str, int]], end_ts: int, min_severity: int) -> float:
    critical = [
        int(row["timestamp"])
        for row in values
        if int(row["timestamp"]) < end_ts and int(row["raw_severity"]) >= min_severity
    ]
    if not critical:
        return float((86400 * 7) / 60)
    return float(max(0, end_ts - max(critical)) / 60.0)


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
        observium_values = normalized["observium_by_host"].get(hostid, [])
        prior_problems: List[Dict[str, int]] = []
        for problem in problems:
            problem_ts = int(problem["timestamp"])
            severity_bucket = int(problem["severity_bucket"])
            problem_count_last_1h = _window_problem_count(prior_problems, problem_ts, problem_window_1h)
            problem_count_last_24h = _window_problem_count(prior_problems, problem_ts, problem_window_24h)
            critical_problem_count_last_1h = _window_problem_count_by_severity(prior_problems, problem_ts, problem_window_1h, 4)
            critical_problem_count_last_24h = _window_problem_count_by_severity(prior_problems, problem_ts, problem_window_24h, 4)
            high_problem_count_last_1h = _window_problem_count_by_severity(prior_problems, problem_ts, problem_window_1h, 4)
            disaster_problem_count_last_1h = _window_problem_count_by_severity(prior_problems, problem_ts, problem_window_1h, 5)

            cpu_usage_percent = _safe_percent(
                max(
                    _latest_metric_value(metric_values, problem_ts, ("system.cpu.util", "cpu.util")),
                    _latest_metric_value(metric_values, problem_ts, ("system.cpu.load",)),
                )
            )

            ram_usage_percent = _safe_percent(
                _latest_metric_value(metric_values, problem_ts, ("vm.memory.util", "memory.util", "vm.memory.size[pused]"))
            )
            if ram_usage_percent == 0.0:
                used_memory = _latest_metric_value(metric_values, problem_ts, ("vm.memory.size[used]",))
                total_memory = _latest_metric_value(metric_values, problem_ts, ("vm.memory.size[total]",))
                available_memory = _latest_metric_value(metric_values, problem_ts, ("vm.memory.size[available]",))
                if used_memory > 0 and total_memory > 0:
                    ram_usage_percent = _safe_percent((used_memory / total_memory) * 100.0)
                elif used_memory > 0 and available_memory > 0:
                    ram_usage_percent = _safe_percent((used_memory / (used_memory + available_memory)) * 100.0)

            latency_seconds = max(
                _latest_metric_value(metric_values, problem_ts, ("icmppingsec",)),
                _latest_metric_value(metric_values, problem_ts, ("net.tcp.service.perf",)),
            )
            latency_ms = _safe_non_negative(latency_seconds * 1000.0)
            observium_aggregate = _aggregate_observium_interface(observium_values, problem_ts)
            traffic_in_bps = observium_aggregate["traffic_in_bps"]
            traffic_out_bps = observium_aggregate["traffic_out_bps"]
            interface_errors_in = observium_aggregate["interface_errors_in"]
            interface_errors_out = observium_aggregate["interface_errors_out"]
            packet_loss_percent = _safe_percent(
                _latest_metric_value(metric_values, problem_ts, ("icmppingloss", "packet.loss"))
            )
            availability_status = observium_aggregate["availability_status"]
            if availability_status == 0.0 and _latest_metric_value(
                metric_values,
                problem_ts,
                ("icmpping", "agent.ping", "zabbix[host,available]"),
            ) > 0:
                availability_status = 1.0
            temperature_celsius = _safe_temperature(
                _latest_metric_value(metric_values, problem_ts, ("sensor.temp", "temperature", "system.hw.temperature"))
            )
            last_problem_severity = float(_last_problem_severity(prior_problems, problem_ts))
            active_critical_problems = float(
                _window_problem_count_by_severity(prior_problems, problem_ts, problem_window_24h, 4)
            )
            time_since_last_critical_problem_minutes = _time_since_last_critical_problem_minutes(prior_problems, problem_ts, 4)

            feature_map = {
                "cpu_usage_percent": cpu_usage_percent,
                "ram_usage_percent": ram_usage_percent,
                "latency_ms": latency_ms,
                "traffic_in_bps": traffic_in_bps,
                "traffic_out_bps": traffic_out_bps,
                "interface_errors_in": interface_errors_in,
                "interface_errors_out": interface_errors_out,
                "packet_loss_percent": packet_loss_percent,
                "availability_status": availability_status,
                "temperature_celsius": temperature_celsius,
                "problem_count_last_1h": float(problem_count_last_1h),
                "problem_count_last_24h": float(problem_count_last_24h),
                "critical_problem_count_last_1h": float(critical_problem_count_last_1h),
                "critical_problem_count_last_24h": float(critical_problem_count_last_24h),
                "high_problem_count_last_1h": float(high_problem_count_last_1h),
                "disaster_problem_count_last_1h": float(disaster_problem_count_last_1h),
                "last_problem_severity": last_problem_severity,
                "active_critical_problems": active_critical_problems,
                "time_since_last_critical_problem_minutes": time_since_last_critical_problem_minutes,
            }

            rows.append([feature_map[name] for name in feature_order])
            labels.append(int(severity_bucket) - 1)
            timestamps.append(problem_ts)
            hostids.append(hostid)
            prior_problems.append(problem)

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
