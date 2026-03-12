# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FST Rat Tracker is an AI-powered system for analyzing rat behavior in the Forced Swim Test (FST). It detects and tracks up to 4 rats in cylindrical compartments using YOLOv8, classifies their behavior (swimming, immobility, escape attempts), and stores results via a Flask + PostgreSQL backend with a React frontend.

## Development Commands

### Docker (full stack)
```bash
docker-compose up --build        # Start all services (db, api, worker, frontend)
docker-compose up -d             # Detached mode
docker-compose logs -f api       # Follow API logs
```

Services: API at `localhost:8000`, Frontend at `localhost:5173`, PostgreSQL at `localhost:5432`.

### Local Python setup
```bash
conda create -n fst-yolo python=3.11
conda activate fst-yolo
pip install -r backend/requirements.txt
```

### Standalone tracker (CLI)
```bash
python run_tracker.py "video.mp4" \
  --model weights/rat.pt \
  --layout 1x4 \
  --conf 0.25 \
  --skip-frames 30 \
  --warmup-frames 50
```
`--layout` accepts `1x4` (horizontal) or `2x2` (grid). `weights/rat.pt` is the custom-trained model; falls back to `yolov8n.pt` (COCO) if missing.

### Flask API (dev)
```bash
export DATABASE_URL="postgresql+psycopg2://fst:fst@localhost:5432/fst"
python -c "from backend.app.main import app; app.run(host='0.0.0.0', port=8000, debug=True)"
```

### Background worker
```bash
python -m backend.worker.worker
```

### Frontend
```bash
cd frontend && npm install && npm run dev
```

### YOLO training
```bash
yolo train data=dataset/dataset.yaml model=yolov8n.pt epochs=100 imgsz=640 batch=16
```
Training outputs go to `runs/detect/`. Dataset split: 90% train / 10% val under `dataset/images/` and `dataset/labels/`.

## Architecture

### Pipeline flow
```
Video → track_video() → YOLO detections → assign_to_rois() → PhysicalGate → Freeze/Lost policy → JSON output
                                                                                    ↓
                                                                         run_analysis.py → behavior classification → ResultSummary (DB)
```

### Key pipeline components (`backend/pipeline/tracker.py`)

- **`YOLOEngine`** — Wraps ultralytics YOLO with ByteTrack/BoT-SORT. Falls back to CPU if CUDA unavailable. Calls `.track()` per frame.
- **ROI detection** — Auto-detects cylinder layout via contour detection on the frame's middle horizontal band. Each ROI gets exactly one bounding box.
- **`StableWaterline`** — Bottom-up pixel search for water surface per ROI. Uses EMA (α=0.12) with ±6 px/frame clamping.
- **`PhysicalGate`** — Rejects detections with center outside ROI, above waterline (8% head margin), abnormal size (< 0.4% or > 35% of ROI area), or velocity jumps (> 25% diagonal/frame).
- **`ROIState`** — Freeze/lost policy: freezes last bbox up to 20 frames with 0.90 confidence decay; transitions to "lost" after max freeze.
- **Frame stabilization** — Optional ECC alignment, ORB fallback.

### Behavior classification (`backend/pipeline/run_analysis.py`)
Frame-to-frame pixel difference per rat, 1-second windows:
- Immobile: motion < 1.2
- Swimming: 1.2 ≤ motion ≤ 6.0
- Escape: motion > 6.0

### API endpoints (`backend/app/main.py`)
```
POST /api/sessions              Create session
GET  /api/sessions              List sessions
POST /api/videos/upload         Upload video (multipart)
GET  /api/sessions/{id}/videos  List session videos
POST /api/jobs                  Queue analysis job
GET  /api/jobs/{id}             Job status (QUEUED|RUNNING|DONE|FAILED)
GET  /api/jobs/{id}/summary     Results per rat (swim_s, immobile_s, escape_s)
GET  /health                    Health check
```

### Database schema (`backend/app/models.py`)
`sessions` → `videos` (day: DAY1|DAY2) → `jobs` (status) → `result_summary` (rat_idx 0-3, swim_s, immobile_s, escape_s)

### Job processing (`backend/worker/worker.py`)
Long-polling daemon that picks up `QUEUED` jobs, runs `run_analysis.py`, writes `ResultSummary` rows, and updates job status.

## Environment Configuration

Copy `.env.example` to `.env`. Key variable: `UPLOAD_MAX_MB=2048`.

`DATABASE_URL` defaults to `postgresql+psycopg2://fst:fst@db:5432/fst` in Docker (see `backend/app/config.py`).

## Model Weights

- `weights/rat.pt` — Custom-trained YOLOv8 rat detector (not in repo, must be trained or provided separately).
- `yolov8n.pt` — Fallback COCO model (committed to repo, used when `rat.pt` is absent).
