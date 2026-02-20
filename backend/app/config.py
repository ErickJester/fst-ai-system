import os

def env(name: str, default: str | None = None) -> str | None:
    return os.getenv(name, default)

DATABASE_URL = env("DATABASE_URL", "postgresql+psycopg2://fst:fst@localhost:5432/fst")
DATA_DIR = env("DATA_DIR", os.path.abspath("./data"))
UPLOAD_MAX_MB = int(env("UPLOAD_MAX_MB", "2048") or "2048")
