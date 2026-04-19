# PFERTC TorchScript Pipeline

This module trains a small PyTorch model on tabular Zabbix data and exports it as TorchScript for direct Spring Boot + DJL inference.

Artifacts:

- `artifacts/models/final_model.pth`
- `artifacts/models/model.pt`
- `artifacts/models/feature_metadata.json`
- `artifacts/metrics/training_metrics.json`

Run:

```powershell
cd PFERTC
python scripts\train_model.py
python scripts\export_model.py
```

Spring Boot reads the exported files directly from:

- `PFERTC/artifacts/models/model.pt`
- `PFERTC/artifacts/models/feature_metadata.json`

Notes:

- The included SQL dump contains very few `zabbix_problem` rows with non-null timestamps.
- Training is therefore implemented honestly around timestamped problem events only.
- If your live database contains `clock` or populated `started_at`, the same pipeline can train on richer data without changing Java inference.
