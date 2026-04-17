from __future__ import annotations

from pathlib import Path
from typing import Any, Dict

import yaml


def load_config(config_path: str | Path | None = None) -> Dict[str, Any]:
    base_dir = Path(__file__).resolve().parents[3]
    resolved = Path(config_path) if config_path else base_dir / "config" / "config.yaml"
    with resolved.open("r", encoding="utf-8") as handle:
        config = yaml.safe_load(handle)

    config["__base_dir__"] = str(base_dir)
    return config
