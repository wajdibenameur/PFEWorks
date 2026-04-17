from __future__ import annotations

import json
import logging
import random
from pathlib import Path
from typing import Dict, Tuple

import numpy as np
import torch
from torch import nn
from torch.utils.data import DataLoader, TensorDataset

from ml.src.config.logging_utils import configure_logging
from ml.src.config.settings import load_config
from ml.src.data.loaders import load_sql_dump
from ml.src.features.feature_engineering import build_event_dataset
from ml.src.models.model import TabularSeverityNet


LOGGER = logging.getLogger(__name__)


def _set_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)


def _chronological_split(X: np.ndarray, y: np.ndarray, timestamps: np.ndarray, test_ratio: float) -> Tuple:
    sorted_idx = np.argsort(timestamps)
    X_sorted = X[sorted_idx]
    y_sorted = y[sorted_idx]
    timestamps_sorted = timestamps[sorted_idx]

    split_idx = max(1, int(len(X_sorted) * (1 - test_ratio)))
    if split_idx >= len(X_sorted):
        split_idx = len(X_sorted) - 1

    return (
        X_sorted[:split_idx],
        X_sorted[split_idx:],
        y_sorted[:split_idx],
        y_sorted[split_idx:],
        timestamps_sorted[:split_idx],
        timestamps_sorted[split_idx:],
    )


def _compute_normalization(X_train: np.ndarray) -> Tuple[np.ndarray, np.ndarray]:
    means = X_train.mean(axis=0)
    stds = X_train.std(axis=0)
    stds = np.where(stds < 1e-8, 1.0, stds)
    return means.astype(np.float32), stds.astype(np.float32)


def _macro_f1(y_true: np.ndarray, y_pred: np.ndarray, num_classes: int = 3) -> float:
    scores = []
    for cls in range(num_classes):
        tp = int(np.sum((y_true == cls) & (y_pred == cls)))
        fp = int(np.sum((y_true != cls) & (y_pred == cls)))
        fn = int(np.sum((y_true == cls) & (y_pred != cls)))
        precision = tp / (tp + fp) if tp + fp else 0.0
        recall = tp / (tp + fn) if tp + fn else 0.0
        scores.append((2 * precision * recall / (precision + recall)) if precision + recall else 0.0)
    return float(sum(scores) / num_classes)


def _confusion_matrix(y_true: np.ndarray, y_pred: np.ndarray, num_classes: int = 3) -> list[list[int]]:
    matrix = [[0 for _ in range(num_classes)] for _ in range(num_classes)]
    for true_value, pred_value in zip(y_true.tolist(), y_pred.tolist()):
        matrix[int(true_value)][int(pred_value)] += 1
    return matrix


def train_model(config_path: str | None = None) -> Dict:
    config = load_config(config_path)
    base_dir = Path(config["__base_dir__"])
    configure_logging(base_dir / config["paths"]["logs_dir"])

    _set_seed(int(config["project"]["random_seed"]))
    raw_data = load_sql_dump(config)
    dataset = build_event_dataset(raw_data, config)

    X = dataset["X"]
    y = dataset["y"]
    timestamps = dataset["timestamps"]
    feature_order = dataset["feature_order"].tolist()

    X_train, X_test, y_train, y_test, _, test_timestamps = _chronological_split(
        X,
        y,
        timestamps,
        float(config["training"]["test_ratio"]),
    )

    means, stds = _compute_normalization(X_train)
    X_train_norm = (X_train - means) / stds
    X_test_norm = (X_test - means) / stds

    train_dataset = TensorDataset(
        torch.tensor(X_train_norm, dtype=torch.float32),
        torch.tensor(y_train, dtype=torch.long),
    )
    train_loader = DataLoader(
        train_dataset,
        batch_size=int(config["training"]["batch_size"]),
        shuffle=True,
    )

    class_counts = np.bincount(y_train, minlength=3).astype(np.float32)
    class_weights = np.where(class_counts > 0, class_counts.sum() / np.maximum(class_counts, 1.0), 0.0)
    class_weights = class_weights / np.maximum(class_weights.sum(), 1.0)

    model = TabularSeverityNet(
        input_dim=X_train.shape[1],
        hidden_sizes=[int(value) for value in config["training"]["hidden_sizes"]],
        dropout=float(config["training"]["dropout"]),
    )
    criterion = nn.CrossEntropyLoss(weight=torch.tensor(class_weights, dtype=torch.float32))
    optimizer = torch.optim.Adam(
        model.parameters(),
        lr=float(config["training"]["learning_rate"]),
        weight_decay=float(config["training"]["weight_decay"]),
    )

    model.train()
    for epoch in range(int(config["training"]["epochs"])):
        epoch_loss = 0.0
        for batch_features, batch_labels in train_loader:
            optimizer.zero_grad()
            logits = model(batch_features)
            loss = criterion(logits, batch_labels)
            loss.backward()
            optimizer.step()
            epoch_loss += float(loss.item())
        if epoch % 25 == 0 or epoch == int(config["training"]["epochs"]) - 1:
            LOGGER.info("Epoch %s | loss=%.6f", epoch + 1, epoch_loss / max(len(train_loader), 1))

    model.eval()
    with torch.no_grad():
        test_logits = model(torch.tensor(X_test_norm, dtype=torch.float32))
        test_probs = torch.softmax(test_logits, dim=1)
        test_pred = torch.argmax(test_probs, dim=1).cpu().numpy()

    accuracy = float(np.mean(test_pred == y_test)) if len(y_test) else 0.0
    f1_macro = _macro_f1(y_test, test_pred, num_classes=3) if len(y_test) else 0.0
    metrics = {
        "accuracy": accuracy,
        "f1_macro": f1_macro,
        "confusion_matrix": _confusion_matrix(y_test, test_pred, num_classes=3),
        "feature_order": feature_order,
        "train_size": int(len(X_train)),
        "test_size": int(len(X_test)),
        "test_timestamps": test_timestamps.astype(int).tolist(),
        "class_counts_train": class_counts.astype(int).tolist(),
    }

    artifacts_dir = base_dir / config["paths"]["artifacts_dir"]
    (artifacts_dir / "models").mkdir(parents=True, exist_ok=True)
    (artifacts_dir / "metrics").mkdir(parents=True, exist_ok=True)

    checkpoint_path = base_dir / config["paths"]["model_checkpoint"]
    metadata_path = base_dir / config["paths"]["feature_metadata"]
    metrics_path = base_dir / config["paths"]["training_metrics"]

    torch.save(
        {
            "state_dict": model.state_dict(),
            "input_dim": int(X_train.shape[1]),
            "hidden_sizes": [int(value) for value in config["training"]["hidden_sizes"]],
            "dropout": float(config["training"]["dropout"]),
            "feature_order": feature_order,
            "feature_means": means.tolist(),
            "feature_stds": stds.tolist(),
            "label_mapping": {"0": 1, "1": 2, "2": 3},
        },
        checkpoint_path,
    )
    metadata_path.write_text(
        json.dumps(
            {
                "feature_order": feature_order,
                "feature_means": means.tolist(),
                "feature_stds": stds.tolist(),
                "num_features": int(X_train.shape[1]),
                "label_mapping": {"0": 1, "1": 2, "2": 3},
            },
            indent=2,
        ),
        encoding="utf-8",
    )
    metrics_path.write_text(json.dumps(metrics, indent=2), encoding="utf-8")

    LOGGER.info("Saved checkpoint to %s", checkpoint_path)
    LOGGER.info("Saved feature metadata to %s", metadata_path)
    LOGGER.info("Saved metrics to %s", metrics_path)
    return metrics


if __name__ == "__main__":
    train_model()
