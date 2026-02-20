import cv2
import numpy as np
from dataclasses import dataclass

@dataclass
class RatSummary:
    rat_idx: int
    swim_s: float
    immobile_s: float
    escape_s: float

def _default_rois(w: int, h: int):
    hw, hh = w // 2, h // 2
    return [
        (0, 0, hw, hh),
        (hw, 0, w - hw, hh),
        (0, hh, hw, h - hh),
        (hw, hh, w - hw, h - hh),
    ]

def run_analysis(video_path: str, seconds_per_window: float = 1.0, fps_cap: int = 15) -> list[RatSummary]:
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        raise RuntimeError(f"Cannot open video: {video_path}")

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    step = max(int(round(fps / fps_cap)), 1) if fps_cap else 1

    ok, frame = cap.read()
    if not ok:
        raise RuntimeError("Empty video")

    h, w = frame.shape[:2]
    rois = _default_rois(w, h)
    prev = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

    win_target = max(int(round(seconds_per_window * fps / step)), 1)
    motion_acc = [0.0] * 4
    frames_in_win = 0
    totals = [{"swim": 0.0, "imm": 0.0, "esc": 0.0} for _ in range(4)]

    immobile_thr = 1.2
    escape_thr = 6.0

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

        for i, (x, y, rw, rh) in enumerate(rois):
            d = diff[y:y+rh, x:x+rw]
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

    cap.release()
    return [RatSummary(i, totals[i]["swim"], totals[i]["imm"], totals[i]["esc"]) for i in range(4)]
