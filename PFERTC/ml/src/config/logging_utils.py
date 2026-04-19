from __future__ import annotations

import logging
from pathlib import Path


def configure_logging(log_dir: str | Path) -> None:
    log_path = Path(log_dir)
    log_path.mkdir(parents=True, exist_ok=True)
    logfile = log_path / "training.log"

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
        handlers=[
            logging.FileHandler(logfile, encoding="utf-8"),
            logging.StreamHandler(),
        ],
        force=True,
    )
