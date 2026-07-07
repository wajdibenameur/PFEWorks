from __future__ import annotations

import ast
import logging
from pathlib import Path
from typing import Dict, Iterable, List, Tuple


LOGGER = logging.getLogger(__name__)
TARGET_TABLES = {"zabbix_metric", "zabbix_problem", "monitored_host", "monitoring_host", "observium_interface"}


def _split_rows(values_block: str) -> List[str]:
    rows: List[str] = []
    current: List[str] = []
    depth = 0
    in_quote = False
    quote_char = ""
    i = 0

    while i < len(values_block):
        char = values_block[i]
        current.append(char)

        if in_quote:
            if char == "\\" and i + 1 < len(values_block):
                i += 1
                current.append(values_block[i])
            elif char == quote_char:
                in_quote = False
        else:
            if char in {"'", '"'}:
                in_quote = True
                quote_char = char
            elif char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
                if depth == 0:
                    rows.append("".join(current).strip().rstrip(","))
                    current = []
                    while i + 1 < len(values_block) and values_block[i + 1] in {",", "\n", "\r", " "}:
                        i += 1
        i += 1
    return rows


def _sql_row_to_python(row_text: str) -> Tuple:
    normalized = row_text.strip()
    normalized = normalized.replace("NULL", "None")
    normalized = normalized.replace("b'1'", "1").replace("b'0'", "0")
    return ast.literal_eval(normalized)


def _parse_insert_statement(statement: str) -> Tuple[str, List[str], List[Tuple]]:
    header, values_part = statement.split("VALUES", 1)
    table_name = header.split("`")[1]
    columns = [part.strip(" `") for part in header.split("(", 1)[1].rsplit(")", 1)[0].split(",")]
    rows = [_sql_row_to_python(row) for row in _split_rows(values_part.strip().rstrip(";"))]
    return table_name, columns, rows


def _iter_insert_statements(sql_text: str) -> Iterable[str]:
    current: List[str] = []
    collecting = False
    for line in sql_text.splitlines():
        if line.startswith("INSERT INTO `"):
            table_name = line.split("`")[1]
            if table_name in TARGET_TABLES:
                collecting = True
                current = [line]
            else:
                collecting = False
                current = []
            continue

        if collecting:
            current.append(line)
            if line.rstrip().endswith(";"):
                yield "\n".join(current)
                current = []
                collecting = False


def _load_sql_file(path: Path) -> Dict[str, List[Dict]]:
    sql_text = path.read_text(encoding="utf-8", errors="ignore")
    data = {
        "zabbix_metric": [],
        "zabbix_problem": [],
        "monitoring_host": [],
        "observium_interface": [],
    }

    for statement in _iter_insert_statements(sql_text):
        table_name, columns, rows = _parse_insert_statement(statement)
        normalized_name = "monitoring_host" if table_name in {"monitored_host", "monitoring_host"} else table_name
        data[normalized_name].extend(dict(zip(columns, row)) for row in rows)

    return data


def load_sql_dump(config: Dict) -> Dict[str, List[Dict]]:
    base_dir = Path(config["__base_dir__"])
    dump_path = (base_dir / config["paths"]["sql_dump_path"]).resolve()
    LOGGER.info("Loading SQL dump from %s", dump_path)
    data = _load_sql_file(dump_path)

    observium_dump_path = config["paths"].get("observium_sql_dump_path")
    if observium_dump_path:
        resolved_observium_path = Path(observium_dump_path).expanduser()
        if not resolved_observium_path.is_absolute():
            resolved_observium_path = (base_dir / resolved_observium_path).resolve()
        if resolved_observium_path.exists():
            LOGGER.info("Loading Observium SQL dump from %s", resolved_observium_path)
            observium_data = _load_sql_file(resolved_observium_path)
            if observium_data["observium_interface"]:
                data["observium_interface"] = observium_data["observium_interface"]
            if observium_data["monitoring_host"]:
                data["monitoring_host"] = observium_data["monitoring_host"]
        else:
            LOGGER.warning("Configured Observium SQL dump does not exist: %s", resolved_observium_path)

    LOGGER.info(
        "Parsed dump rows | metrics=%s | problems=%s | hosts=%s | observium_interfaces=%s",
        len(data["zabbix_metric"]),
        len(data["zabbix_problem"]),
        len(data["monitoring_host"]),
        len(data["observium_interface"]),
    )
    return data
