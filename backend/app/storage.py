from pathlib import Path
from .config import DATA_DIR

def ensure_dirs():
    Path(DATA_DIR).mkdir(parents=True, exist_ok=True)
    (Path(DATA_DIR) / "videos").mkdir(parents=True, exist_ok=True)

def video_path(experiment_id: int, video_id: int, filename: str) -> str:
    safe = filename.replace("/", "_").replace("\\", "_")
    p = Path(DATA_DIR) / "videos" / f"experiment_{experiment_id}"
    p.mkdir(parents=True, exist_ok=True)
    return str(p / f"video_{video_id}_{safe}")


def report_path(experiment_id: int, report_id: int, ext: str) -> str:
    p = Path(DATA_DIR) / "reports" / f"experiment_{experiment_id}"
    p.mkdir(parents=True, exist_ok=True)
    return str(p / f"report_{report_id}.{ext}")
