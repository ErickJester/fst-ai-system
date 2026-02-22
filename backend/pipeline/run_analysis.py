"""
Pipeline de análisis FST.

Usa el tracker para localizar cada rata y luego clasifica la conducta
(nado, inmovilidad, escape) por ventana temporal.
"""

import cv2
import numpy as np
import json
from pathlib import Path
from dataclasses import dataclass, asdict
from typing import Optional

from .tracker import (
    compute_rois,
    detect_layout,
    build_background_model,
    detect_rat_in_roi,
    draw_detections,
    SmoothTracker,
    Detection,
)


@dataclass
class RatSummary:
    rat_idx: int
    swim_s: float
    immobile_s: float
    escape_s: float


def run_analysis(
    video_path: str,
    seconds_per_window: float = 1.0,
    fps_cap: int = 15,
    output_video: Optional[str] = None,
    output_json: Optional[str] = None,
) -> list[RatSummary]:
    """
    Analiza un video de FST: tracking + clasificación de conducta.

    Si output_video / output_json se proporcionan, genera el video
    anotado con bounding boxes y el JSON de coordenadas.
    """
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        raise RuntimeError(f"Cannot open video: {video_path}")

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    fw = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    fh = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    step = max(int(round(fps / fps_cap)), 1) if fps_cap else 1

    layout = detect_layout(fw, fh)
    rois = compute_rois(fw, fh, layout)

    # Modelo de fondo (mediana de frames)
    bg_models = build_background_model(cap, rois)

    # Primer frame
    ok, frame = cap.read()
    if not ok:
        raise RuntimeError("Empty video")
    prev = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

    # Video writer (opcional)
    writer = None
    if output_video:
        fourcc = cv2.VideoWriter_fourcc(*"mp4v")
        writer = cv2.VideoWriter(output_video, fourcc, fps, (fw, fh))

    # Tracker con suavizado EMA
    tracker = SmoothTracker(alpha=0.45, max_lost=int(fps * 0.5))

    # Ventana de clasificación de conducta
    win_target = max(int(round(seconds_per_window * fps / step)), 1)
    motion_acc = [0.0] * 4
    frames_in_win = 0
    totals = [{"swim": 0.0, "imm": 0.0, "esc": 0.0} for _ in range(4)]

    immobile_thr = 1.2
    escape_thr = 6.0

    all_dets: list[dict] = []

    idx = 0
    while True:
        ok, frame = cap.read()
        if not ok:
            break
        idx += 1
        if idx % step != 0:
            continue

        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        diff = cv2.absdiff(gray, prev)
        prev = gray

        # ── Tracking ──
        dets: list[Optional[Detection]] = []
        for i, (rx, ry, rw, rh) in enumerate(rois):
            raw = detect_rat_in_roi(
                gray[ry : ry + rh, rx : rx + rw],
                bg_models[i],
                (rx, ry),
                rat_idx=i,
                frame_num=idx,
            )
            smoothed = tracker.update(raw, i, idx)
            dets.append(smoothed)
            if smoothed is not None:
                all_dets.append(asdict(smoothed))

        # ── Clasificación de conducta ──
        for i, (rx, ry, rw, rh) in enumerate(rois):
            d = diff[ry : ry + rh, rx : rx + rw]
            motion = float(np.mean(d)) / 255.0 * 10.0
            motion_acc[i] += motion

        frames_in_win += 1
        if frames_in_win >= win_target:
            seconds = frames_in_win * step / fps
            for i in range(4):
                m = motion_acc[i] / frames_in_win
                if m < immobile_thr:
                    totals[i]["imm"] += seconds
                elif m > escape_thr:
                    totals[i]["esc"] += seconds
                else:
                    totals[i]["swim"] += seconds
            motion_acc = [0.0] * 4
            frames_in_win = 0

        # ── Video anotado ──
        if writer:
            writer.write(draw_detections(frame, dets, rois))

    cap.release()
    if writer:
        writer.release()

    # JSON de tracking
    if output_json:
        payload = {
            "video": str(video_path),
            "fps": fps,
            "frame_size": [fw, fh],
            "layout": layout,
            "rois": [
                {"rat_idx": i, "x": r[0], "y": r[1], "w": r[2], "h": r[3]}
                for i, r in enumerate(rois)
            ],
            "total_frames_processed": idx,
            "detections": all_dets,
        }
        with open(output_json, "w") as f:
            json.dump(payload, f, indent=2)

    return [
        RatSummary(i, totals[i]["swim"], totals[i]["imm"], totals[i]["esc"])
        for i in range(4)
    ]
