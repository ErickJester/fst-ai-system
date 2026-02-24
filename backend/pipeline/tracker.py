"""
FST Rat Tracker v2.0 — YOLO-based detection + ROI identity assignment

Approach
--------
- YOLO (single class "rat") detects bounding boxes on each frame.
- Identity (Rata 1-4) is assigned by which fixed ROI the bbox center falls in.
  ROI1 → Rata 1, ROI2 → Rata 2, etc.  The model learns "what a rat looks
  like"; the ROI handles "which rat is it".
- "Always bbox" policy:
    * If a ROI has no detection, freeze the last known bbox with gradually
      decaying confidence for up to max_freeze_frames frames.
    * After that, the source is marked "lost" but bbox is still returned so
      the rest of the pipeline never receives a gap.

Dependencies: ultralytics, torch, opencv-python
"""

import cv2
import numpy as np
import json
import time
from pathlib import Path
from dataclasses import dataclass, asdict, field
from typing import Optional, Tuple, List, Dict

RAT_COLORS = [(0, 255, 0), (255, 100, 0), (0, 180, 255), (255, 0, 180)]
RAT_LABELS = ["Rata 1", "Rata 2", "Rata 3", "Rata 4"]

# Freeze policy defaults
MAX_FREEZE_FRAMES = 20    # frames without detection before marking "lost"
FREEZE_CONF_DECAY = 0.90  # multiply confidence by this each frozen frame


# ──────────────────────────────────────────────────────────────────────
# Data model
# ──────────────────────────────────────────────────────────────────────

@dataclass
class Detection:
    rat_idx: int
    frame_num: int
    x: int
    y: int
    w: int
    h: int
    cx: float
    cy: float
    area: int
    confidence: float
    source: str          # "yolo" | "freeze" | "lost"


# ──────────────────────────────────────────────────────────────────────
# Utilities
# ──────────────────────────────────────────────────────────────────────

def _clamp(v: float, lo: float, hi: float) -> float:
    return lo if v < lo else hi if v > hi else v


# ──────────────────────────────────────────────────────────────────────
# ROI computation  (kept from v1 — not part of detection)
# ──────────────────────────────────────────────────────────────────────

def find_cylinder_separators(gray_frame: np.ndarray) -> List[int]:
    h, w = gray_frame.shape[:2]
    band = gray_frame[int(h * 0.3):int(h * 0.7), :]
    cp = np.mean(band, axis=0)
    sm = np.convolve(cp, np.ones(15) / 15, mode="same")
    md = w // 6
    cands = []
    for x in range(md, w - md):
        left  = sm[max(0, x - md // 2):x]
        right = sm[x + 1:min(w, x + md // 2 + 1)]
        if len(left) > 0 and len(right) > 0 and sm[x] < np.min(left) and sm[x] < np.min(right):
            cands.append((x, min(np.mean(left) - sm[x], np.mean(right) - sm[x])))
    cands.sort(key=lambda c: c[1], reverse=True)
    seps = sorted([c[0] for c in cands[:3]])
    return seps if len(seps) >= 3 else [w // 4, w // 2, 3 * w // 4]


def compute_rois(
    frame_w: int,
    frame_h: int,
    layout: str = "1x4",
    gray_frame: Optional[np.ndarray] = None,
) -> List[Tuple[int, int, int, int]]:
    if layout == "1x4" and gray_frame is not None:
        s = find_cylinder_separators(gray_frame)
        return [
            (0,    0, s[0],          frame_h),
            (s[0], 0, s[1] - s[0],  frame_h),
            (s[1], 0, s[2] - s[1],  frame_h),
            (s[2], 0, frame_w - s[2], frame_h),
        ]
    if layout == "1x4":
        q = frame_w // 4
        return [
            (0,      0, q,              frame_h),
            (q,      0, q,              frame_h),
            (2 * q,  0, q,              frame_h),
            (3 * q,  0, frame_w - 3*q, frame_h),
        ]
    if layout == "2x2":
        hw, hh = frame_w // 2, frame_h // 2
        return [
            (0,  0,  hw,          hh),
            (hw, 0,  frame_w-hw,  hh),
            (0,  hh, hw,          frame_h-hh),
            (hw, hh, frame_w-hw,  frame_h-hh),
        ]
    raise ValueError(f"Layout desconocido: {layout}")


def detect_layout(fw: int, fh: int) -> str:
    return "1x4" if fw / fh > 1.2 else "2x2"


# ──────────────────────────────────────────────────────────────────────
# YOLO detector wrapper
# ──────────────────────────────────────────────────────────────────────

class YOLODetector:
    """
    Thin wrapper around ultralytics YOLO.
    Expects a model trained with a single class: rat (class index 0).
    """

    def __init__(self, model_path: str, conf_threshold: float = 0.25, device: str = ""):
        try:
            from ultralytics import YOLO  # noqa: PLC0415
        except ImportError:
            raise ImportError(
                "ultralytics no está instalado.\n"
                "Ejecuta:  pip install ultralytics"
            )

        mp = Path(model_path)
        if not mp.exists():
            raise FileNotFoundError(
                f"Modelo YOLO no encontrado: {model_path}\n\n"
                "Para entrenar desde cero:\n"
                "  yolo train data=dataset.yaml model=yolov8n.pt epochs=100 imgsz=640\n\n"
                "Coloca el archivo .pt resultante en la ruta indicada con --model."
            )

        self.conf_threshold = float(conf_threshold)
        self.device = device if device else self._auto_device()
        self.model = YOLO(str(mp))

    @staticmethod
    def _auto_device() -> str:
        try:
            import torch
            return "cuda" if torch.cuda.is_available() else "cpu"
        except ImportError:
            return "cpu"

    def detect(self, frame_bgr: np.ndarray) -> List[Tuple[int, int, int, int, float]]:
        """
        Run inference on one BGR frame.
        Returns list of (x, y, w, h, conf) for every "rat" detection
        (class 0) above conf_threshold, in pixel coords of the full frame.
        """
        results = self.model(
            frame_bgr,
            conf=self.conf_threshold,
            verbose=False,
            device=self.device,
        )
        boxes: List[Tuple[int, int, int, int, float]] = []
        for r in results:
            if r.boxes is None:
                continue
            for box in r.boxes:
                if int(box.cls[0]) != 0:   # only class "rat"
                    continue
                conf = float(box.conf[0])
                x1, y1, x2, y2 = (int(v) for v in box.xyxy[0])
                w, h = x2 - x1, y2 - y1
                if w > 0 and h > 0:
                    boxes.append((x1, y1, w, h, conf))
        return boxes


# ──────────────────────────────────────────────────────────────────────
# ROI assignment
# ──────────────────────────────────────────────────────────────────────

def assign_detections_to_rois(
    detections: List[Tuple[int, int, int, int, float]],
    rois: List[Tuple[int, int, int, int]],
) -> Dict[int, Optional[Tuple[int, int, int, int, float]]]:
    """
    For each ROI, keep the detection with the highest confidence whose
    bounding-box center (cx, cy) falls inside that ROI.

    A detection is assigned to at most one ROI (the first match found in
    ROI order).  Returns {roi_idx: (x, y, w, h, conf) or None}.
    """
    result: Dict[int, Optional[Tuple[int, int, int, int, float]]] = {
        i: None for i in range(len(rois))
    }
    for x, y, w, h, conf in detections:
        cx = x + w / 2.0
        cy = y + h / 2.0
        for i, (rx, ry, rw, rh) in enumerate(rois):
            if rx <= cx < rx + rw and ry <= cy < ry + rh:
                current = result[i]
                if current is None or conf > current[4]:
                    result[i] = (x, y, w, h, conf)
                break   # one ROI per detection
    return result


# ──────────────────────────────────────────────────────────────────────
# Per-rat state  (freeze policy)
# ──────────────────────────────────────────────────────────────────────

class RatState:
    def __init__(self):
        self.last_det: Optional[Detection] = None
        self.freeze_count: int = 0
        self.lost: bool = False


# ──────────────────────────────────────────────────────────────────────
# Drawing
# ──────────────────────────────────────────────────────────────────────

def draw_detections(
    frame: np.ndarray,
    detections: List[Optional[Detection]],
    rois: List[Tuple[int, int, int, int]],
) -> np.ndarray:
    out = frame.copy()

    # ROI borders (grey)
    for rx, ry, rw, rh in rois:
        cv2.rectangle(out, (rx, ry), (rx + rw, ry + rh), (80, 80, 80), 1)

    for det in detections:
        if det is None:
            continue

        base_color = RAT_COLORS[det.rat_idx % 4]

        if det.source == "yolo":
            color = base_color
            thick = 2
        elif det.source == "freeze":
            color = tuple(int(c * 0.55) for c in base_color)
            thick = 1
        else:  # "lost"
            color = (60, 60, 60)
            thick = 1

        cv2.rectangle(out, (det.x, det.y), (det.x + det.w, det.y + det.h), color, thick)

        src_tag = "" if det.source == "yolo" else f" [{det.source}]"
        text = f"{RAT_LABELS[det.rat_idx]} {det.confidence:.2f}{src_tag}"
        font, sc = cv2.FONT_HERSHEY_SIMPLEX, 0.45
        (tw, th), _ = cv2.getTextSize(text, font, sc, 1)
        ly = max(det.y - 5, th + 5)
        cv2.rectangle(out, (det.x, ly - th - 4), (det.x + tw + 4, ly + 2), color, -1)
        cv2.putText(out, text, (det.x + 2, ly - 2), font, sc, (255, 255, 255), 1, cv2.LINE_AA)
        cv2.circle(out, (int(det.cx), int(det.cy)), 3, color, -1)

    return out


# ──────────────────────────────────────────────────────────────────────
# Main pipeline
# ──────────────────────────────────────────────────────────────────────

def track_video(
    input_path,
    output_video=None,
    output_json=None,
    layout: str = "auto",
    fps_cap: int = 0,
    show_progress: bool = True,
    model_path: str = "weights/rat.pt",
    conf_threshold: float = 0.25,
    max_freeze_frames: int = MAX_FREEZE_FRAMES,
    device: str = "",
):
    cap = cv2.VideoCapture(input_path)
    if not cap.isOpened():
        raise RuntimeError(f"No se puede abrir: {input_path}")

    fps   = cap.get(cv2.CAP_PROP_FPS) or 30.0
    total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    fw    = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    fh    = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    if show_progress:
        print("FST Rat Tracker v2.0 — YOLO")
        print(f"Video: {fw}x{fh} @ {fps:.1f} FPS, {total} frames ({total/fps:.1f}s)")
        print(f"Modelo: {model_path}  conf≥{conf_threshold}")

    if layout == "auto":
        layout = detect_layout(fw, fh)
        if show_progress:
            print(f"Layout: {layout}")

    # Read first frame to compute ROIs
    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
    ok, first = cap.read()
    if not ok:
        raise RuntimeError("Video vacío")
    first_gray = cv2.cvtColor(first, cv2.COLOR_BGR2GRAY)
    rois = compute_rois(fw, fh, layout, gray_frame=first_gray)

    if show_progress:
        print(f"ROIs (x_start, x_end): {[(r[0], r[0]+r[2]) for r in rois]}")

    # Load YOLO model
    detector = YOLODetector(model_path, conf_threshold=conf_threshold, device=device)
    if show_progress:
        print(f"Dispositivo YOLO: {detector.device}")

    writer = None
    if output_video:
        out_fps = fps if fps_cap <= 0 else min(fps, fps_cap)
        writer = cv2.VideoWriter(
            output_video, cv2.VideoWriter_fourcc(*"mp4v"), out_fps, (fw, fh)
        )

    step   = max(1, int(fps / fps_cap)) if fps_cap > 0 else 1
    states = [RatState() for _ in range(4)]
    all_dets: list = []
    stats = {
        "total_frames": 0,
        "yolo":   [0] * 4,
        "freeze": [0] * 4,
        "lost":   [0] * 4,
    }

    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
    frame_num = 0
    t0 = time.time()

    while True:
        ok, frame = cap.read()
        if not ok:
            break
        frame_num += 1
        if frame_num % step != 0:
            continue
        stats["total_frames"] += 1

        # ── YOLO inference on the full frame ──────────────────────────
        raw_boxes = detector.detect(frame)

        # ── Assign detections to ROIs ─────────────────────────────────
        roi_assignments = assign_detections_to_rois(raw_boxes, rois)

        dets_out: List[Optional[Detection]] = []

        for i in range(4):
            st = states[i]
            assigned = roi_assignments.get(i)

            if assigned is not None:
                # ── Real detection ────────────────────────────────────
                x, y, w, h, conf = assigned
                det = Detection(
                    rat_idx=i, frame_num=frame_num,
                    x=int(x), y=int(y), w=int(w), h=int(h),
                    cx=float(x + w / 2.0), cy=float(y + h / 2.0),
                    area=int(w * h),
                    confidence=round(float(_clamp(conf, 0.0, 1.0)), 3),
                    source="yolo",
                )
                st.last_det    = det
                st.freeze_count = 0
                st.lost         = False
                dets_out.append(det)
                stats["yolo"][i] += 1
                all_dets.append(asdict(det))

            elif st.last_det is not None:
                # ── Freeze: no detection, but we have a prior bbox ────
                st.freeze_count += 1
                # Exponential decay: each frozen frame multiplies conf by FREEZE_CONF_DECAY
                frozen_conf = round(
                    float(_clamp(st.last_det.confidence * FREEZE_CONF_DECAY, 0.01, 0.99)),
                    3,
                )
                if st.freeze_count > max_freeze_frames:
                    st.lost = True
                source = "lost" if st.lost else "freeze"

                det = Detection(
                    rat_idx=i, frame_num=frame_num,
                    x=st.last_det.x, y=st.last_det.y,
                    w=st.last_det.w, h=st.last_det.h,
                    cx=st.last_det.cx, cy=st.last_det.cy,
                    area=st.last_det.area,
                    confidence=frozen_conf,
                    source=source,
                )
                # Update so next freeze decays from already-decayed value
                st.last_det = det
                dets_out.append(det)
                if st.lost:
                    stats["lost"][i] += 1
                else:
                    stats["freeze"][i] += 1
                all_dets.append(asdict(det))

            else:
                # ── Never detected ────────────────────────────────────
                dets_out.append(None)
                stats["lost"][i] += 1

        if writer:
            writer.write(draw_detections(frame, dets_out, rois))

        if show_progress and frame_num % max(1, int(fps) * 5) == 0:
            pct = frame_num / total * 100 if total else 0
            print(f"  Frame {frame_num}/{total} ({pct:.0f}%) — {time.time()-t0:.1f}s")

    cap.release()
    if writer:
        writer.release()
        if show_progress:
            print(f"Video anotado: {output_video}")

    if output_json:
        payload = {
            "video":                  str(input_path),
            "fps":                    float(fps),
            "frame_size":             [fw, fh],
            "layout":                 layout,
            "model":                  str(model_path),
            "conf_threshold":         conf_threshold,
            "rois": [
                {"rat_idx": i, "x": r[0], "y": r[1], "w": r[2], "h": r[3]}
                for i, r in enumerate(rois)
            ],
            "total_frames_processed": stats["total_frames"],
            "detections":             all_dets,
        }
        with open(output_json, "w") as f:
            json.dump(payload, f, indent=2)
        if show_progress:
            print(f"Tracking JSON: {output_json}")

    if show_progress:
        elapsed = time.time() - t0
        n = stats["total_frames"]
        print(f"\nResumen ({elapsed:.1f}s — {n/(elapsed or 1):.1f} fps procesados):")
        for i in range(4):
            yn = stats["yolo"][i]
            fn = stats["freeze"][i]
            ln = stats["lost"][i]
            t  = yn + fn + ln
            det_rate = yn / t * 100 if t else 0.0
            print(
                f"  {RAT_LABELS[i]}: "
                f"yolo={yn} ({det_rate:.0f}%)  "
                f"freeze={fn}  sin_det={ln}"
            )

    return stats


# ──────────────────────────────────────────────────────────────────────
# CLI
# ──────────────────────────────────────────────────────────────────────

def main():
    import argparse
    p = argparse.ArgumentParser(description="FST Rat Tracker v2.0 — YOLO")
    p.add_argument("--input",      "-i", required=True,
                   help="Video de entrada")
    p.add_argument("--output",     "-o", default=None,
                   help="Video de salida con anotaciones")
    p.add_argument("--data",       "-d", default=None,
                   help="Archivo JSON de salida")
    p.add_argument("--layout",     "-l", default="auto",
                   choices=["auto", "1x4", "2x2"])
    p.add_argument("--fps-cap",    type=int,   default=0,
                   help="Límite de FPS a procesar (0 = sin límite)")
    p.add_argument("--model",      "-m", default="weights/rat.pt",
                   help="Ruta al archivo de pesos YOLO (.pt)")
    p.add_argument("--conf",       type=float, default=0.25,
                   help="Umbral de confianza YOLO (default: 0.25)")
    p.add_argument("--max-freeze", type=int,   default=MAX_FREEZE_FRAMES,
                   help="Cuadros máximos congelados antes de marcar 'perdido'")
    p.add_argument("--device",     default="",
                   help="Dispositivo de inferencia: 'cpu', 'cuda', '0', etc.")
    args = p.parse_args()

    inp = Path(args.input)
    track_video(
        args.input,
        args.output     or str(inp.parent / f"{inp.stem}_tracked{inp.suffix}"),
        args.data       or str(inp.parent / f"{inp.stem}_tracking.json"),
        args.layout,
        args.fps_cap,
        model_path=args.model,
        conf_threshold=args.conf,
        max_freeze_frames=args.max_freeze,
        device=args.device,
    )


if __name__ == "__main__":
    main()
