from __future__ import annotations

import json
import sys
import time
from pathlib import Path
from typing import Any, Dict

import numpy as np
import torch
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, f1_score
from sklearn.neighbors import KNeighborsClassifier
from xgboost import XGBClassifier

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from ml.src.config.logging_utils import configure_logging
from ml.src.config.settings import load_config
from ml.src.data.loaders import load_sql_dump
from ml.src.features.feature_engineering import build_event_dataset
from ml.src.models.model import TabularSeverityNet
from ml.src.training.train import _chronological_split, _compute_normalization, _set_seed


def _class_distribution(values: np.ndarray, num_classes: int = 3) -> list[int]:
    return np.bincount(values, minlength=num_classes).astype(int).tolist()


def _top_feature_importances(
    importances: np.ndarray,
    feature_order: list[str],
    limit: int = 10,
) -> list[dict[str, float | str]]:
    ranked = sorted(
        zip(feature_order, importances.tolist()),
        key=lambda item: item[1],
        reverse=True,
    )
    return [
        {"feature": feature_name, "importance": float(importance)}
        for feature_name, importance in ranked[:limit]
    ]


def _evaluate_predictions(
    name: str,
    y_true: np.ndarray,
    y_pred: np.ndarray,
    train_seconds: float,
) -> Dict[str, Any]:
    if isinstance(y_pred, np.ndarray) and y_pred.ndim > 1:
        y_pred = np.argmax(y_pred, axis=1)
    y_pred = np.asarray(y_pred).astype(int)
    report = classification_report(y_true, y_pred, output_dict=True, zero_division=0)
    return {
        "model": name,
        "accuracy": float(accuracy_score(y_true, y_pred)),
        "f1_macro": float(f1_score(y_true, y_pred, average="macro", zero_division=0)),
        "precision_macro": float(report["macro avg"]["precision"]),
        "recall_macro": float(report["macro avg"]["recall"]),
        "confusion_matrix": confusion_matrix(y_true, y_pred, labels=[0, 1, 2]).astype(int).tolist(),
        "class_metrics": {
            label: {
                "precision": float(metrics["precision"]),
                "recall": float(metrics["recall"]),
                "f1_score": float(metrics["f1-score"]),
                "support": int(metrics["support"]),
            }
            for label, metrics in report.items()
            if label in {"0", "1", "2"}
        },
        "train_seconds": round(train_seconds, 4),
    }


def _train_torch_model(
    config: Dict[str, Any],
    X_train_norm: np.ndarray,
    y_train: np.ndarray,
    X_test_norm: np.ndarray,
) -> np.ndarray:
    class_counts = np.bincount(y_train, minlength=3).astype(np.float32)
    class_weights = np.where(class_counts > 0, class_counts.sum() / np.maximum(class_counts, 1.0), 0.0)
    class_weights = class_weights / np.maximum(class_weights.sum(), 1.0)

    model = TabularSeverityNet(
        input_dim=X_train_norm.shape[1],
        hidden_sizes=[int(value) for value in config["training"]["hidden_sizes"]],
        dropout=float(config["training"]["dropout"]),
    )
    criterion = torch.nn.CrossEntropyLoss(weight=torch.tensor(class_weights, dtype=torch.float32))
    optimizer = torch.optim.Adam(
        model.parameters(),
        lr=float(config["training"]["learning_rate"]),
        weight_decay=float(config["training"]["weight_decay"]),
    )

    features = torch.tensor(X_train_norm, dtype=torch.float32)
    labels = torch.tensor(y_train, dtype=torch.long)
    batch_size = int(config["training"]["batch_size"])

    model.train()
    for _ in range(int(config["training"]["epochs"])):
        permutation = torch.randperm(features.size(0))
        for start in range(0, features.size(0), batch_size):
            batch_idx = permutation[start : start + batch_size]
            logits = model(features[batch_idx])
            loss = criterion(logits, labels[batch_idx])
            optimizer.zero_grad()
            loss.backward()
            optimizer.step()

    model.eval()
    with torch.no_grad():
        logits = model(torch.tensor(X_test_norm, dtype=torch.float32))
        return torch.argmax(logits, dim=1).cpu().numpy()


def compare_models(config_path: str | None = None) -> Dict[str, Any]:
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

    X_train, X_test, y_train, y_test, train_timestamps, test_timestamps = _chronological_split(
        X,
        y,
        timestamps,
        float(config["training"]["test_ratio"]),
    )

    means, stds = _compute_normalization(X_train)
    X_train_norm = (X_train - means) / stds
    X_test_norm = (X_test - means) / stds

    results: list[Dict[str, Any]] = []

    start = time.perf_counter()
    torch_pred = _train_torch_model(config, X_train_norm, y_train, X_test_norm)
    results.append(_evaluate_predictions("PyTorchTabularNet", y_test, torch_pred, time.perf_counter() - start))

    start = time.perf_counter()
    knn = KNeighborsClassifier(n_neighbors=max(1, min(5, len(X_train_norm))), weights="distance")
    knn.fit(X_train_norm, y_train)
    results.append(_evaluate_predictions("KNN", y_test, knn.predict(X_test_norm), time.perf_counter() - start))

    start = time.perf_counter()
    rf = RandomForestClassifier(
        n_estimators=300,
        random_state=int(config["project"]["random_seed"]),
        class_weight="balanced",
        min_samples_leaf=2,
        n_jobs=1,
    )
    rf.fit(X_train, y_train)
    rf_result = _evaluate_predictions("RandomForest", y_test, rf.predict(X_test), time.perf_counter() - start)
    rf_result["top_features"] = _top_feature_importances(rf.feature_importances_, feature_order)
    results.append(rf_result)

    start = time.perf_counter()
    xgb = XGBClassifier(
        objective="multi:softprob",
        num_class=3,
        n_estimators=250,
        max_depth=5,
        learning_rate=0.05,
        subsample=0.9,
        colsample_bytree=0.9,
        reg_lambda=1.0,
        random_state=int(config["project"]["random_seed"]),
        eval_metric="mlogloss",
        n_jobs=1,
    )
    xgb.fit(X_train, y_train)
    xgb_result = _evaluate_predictions("XGBoost", y_test, xgb.predict(X_test), time.perf_counter() - start)
    xgb_result["top_features"] = _top_feature_importances(xgb.feature_importances_, feature_order)
    results.append(xgb_result)

    ranking = sorted(results, key=lambda item: (item["f1_macro"], item["accuracy"]), reverse=True)
    summary: Dict[str, Any] = {
        "dataset": {
            "total_rows": int(len(X)),
            "num_features": int(X.shape[1]),
            "feature_order": feature_order,
            "train_size": int(len(X_train)),
            "test_size": int(len(X_test)),
            "train_class_distribution": _class_distribution(y_train),
            "test_class_distribution": _class_distribution(y_test),
            "train_timestamp_range": [int(train_timestamps.min()), int(train_timestamps.max())],
            "test_timestamp_range": [int(test_timestamps.min()), int(test_timestamps.max())],
        },
        "models": results,
        "best_model_by_f1_macro": ranking[0]["model"],
    }

    metrics_dir = base_dir / config["paths"]["artifacts_dir"] / "metrics"
    metrics_dir.mkdir(parents=True, exist_ok=True)
    output_path = metrics_dir / "model_comparison.json"
    output_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    return summary


if __name__ == "__main__":
    result = compare_models()
    print(json.dumps(result, indent=2))
