from pathlib import Path
from .config import DATA_DIR

def ensure_dirs():
    Path(DATA_DIR).mkdir(parents=True, exist_ok=True)
    (Path(DATA_DIR) / "videos").mkdir(parents=True, exist_ok=True)

def video_path(session_id: int, video_id: int, filename: str) -> str:
    safe = filename.replace("/", "_").replace("\\", "_")
    p = Path(DATA_DIR) / "videos" / f"session_{session_id}"
    p.mkdir(parents=True, exist_ok=True)
    return str(p / f"video_{video_id}_{safe}")
