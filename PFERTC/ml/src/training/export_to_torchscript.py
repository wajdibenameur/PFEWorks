from __future__ import annotations

import logging
from pathlib import Path

import torch

from ml.src.config.logging_utils import configure_logging
from ml.src.config.settings import load_config
from ml.src.models.model import TabularSeverityNet


LOGGER = logging.getLogger(__name__)


def export_torchscript(config_path: str | None = None) -> Path:
    config = load_config(config_path)
    base_dir = Path(config["__base_dir__"])
    configure_logging(base_dir / config["paths"]["logs_dir"])

    checkpoint_path = base_dir / config["paths"]["model_checkpoint"]
    target_path = base_dir / config["paths"]["torchscript_model"]

    payload = torch.load(checkpoint_path, map_location="cpu")
    model = TabularSeverityNet(
        input_dim=int(payload["input_dim"]),
        hidden_sizes=[int(value) for value in payload["hidden_sizes"]],
        dropout=float(payload["dropout"]),
    )
    model.load_state_dict(payload["state_dict"])
    model.eval()

    example_input = torch.randn(1, int(payload["input_dim"]))
    traced_model = torch.jit.trace(model, example_input)
    target_path.parent.mkdir(parents=True, exist_ok=True)
    traced_model.save(str(target_path))

    LOGGER.info("Saved TorchScript model to %s", target_path)
    return target_path


if __name__ == "__main__":
    export_torchscript()
