from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from ml.src.training.export_to_torchscript import export_torchscript


if __name__ == "__main__":
    export_torchscript()
