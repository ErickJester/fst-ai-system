"""
FST Rat Tracker — Detección y seguimiento de ratas en la prueba de nado forzado.

Objetivo práctico:
- Dibujar un recuadro por rata SIN detectar nada arriba del agua.
- Para eso, detecta la "línea del agua" en cada cilindro y recorta la búsqueda debajo.

Cambio clave de esta versión:
- La línea del agua se busca de ABAJO hacia ARRIBA (bottom-up), para evitar que el fondo
  (estantes/reflejos arriba) gane por “borde más fuerte” cuando la cámara se mueve.
"""

import cv2
import numpy as np
import json
import time
from pathlib import Path
from dataclasses import dataclass, asdict
from typing import Optional


# Colores por rata (BGR)
RAT_COLORS = [
    (0, 255, 0),    # Rata 0: verde
    (255, 100, 0),  # Rata 1: azul
    (0, 180, 255),  # Rata 2: naranja
    (255, 0, 180),  # Rata 3: magenta
]

RAT_LABELS = ["Rata 1", "Rata 2", "Rata 3", "Rata 4"]


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


def compute_rois(frame_w: int, frame_h: int, layout: str = "1x4") -> list[tuple[int, int, int, int]]:
    if layout == "1x4":
        qw = frame_w // 4
        return [
            (0, 0, qw, frame_h),
            (qw, 0, qw, frame_h),
            (2 * qw, 0, qw, frame_h),
            (3 * qw, 0, frame_w - 3 * qw, frame_h),
        ]
    if layout == "2x2":
        hw, hh = frame_w // 2, frame_h // 2
        return [
            (0, 0, hw, hh),
            (hw, 0, frame_w - hw, hh),
            (0, hh, hw, frame_h - hh),
            (hw, hh, frame_w - hw, frame_h - hh),
        ]
    raise ValueError(f"Layout desconocido: {layout}")


def detect_layout(frame_w: int, frame_h: int) -> str:
    aspect = frame_w / frame_h if frame_h > 0 else 1
    return "1x4" if aspect > 1.2 else "2x2"


# -------------------- Línea del agua (bottom-up) --------------------

def _robust_threshold(values: np.ndarray, strength_k: float = 4.0) -> float:
    """Umbral robusto: mediana + k * MAD."""
    med = float(np.median(values))
    mad = float(np.median(np.abs(values - med))) + 1e-6
    return med + strength_k * mad


def estimate_waterline_y_bottomup(
    roi_gray: np.ndarray,
    search_min_pct: float = 0.22,
    search_max_pct: float = 0.92,
    smooth_k: int = 19,
    strength_k: float = 4.0,
    band_px: int = 12,
    darkness_delta: float = 6.0,
) -> Optional[int]:
    """
    Estima la línea del agua buscando de ABAJO hacia ARRIBA.

    Regla:
    - Calcula “energía de borde horizontal” por fila (cambio vertical).
    - Define un umbral robusto.
    - Escanea desde abajo y toma el primer y que:
        a) supera el umbral, y
        b) es consistente con agua abajo (más oscuro) vs arriba (más claro).

    Si no hay candidato confiable, retorna None.
    """
    h, w = roi_gray.shape[:2]
    if h < 60 or w < 60:
        return None

    y0 = int(max(2, min(h - 3, h * search_min_pct)))
    y1 = int(max(y0 + 10, min(h - 3, h * search_max_pct)))

    blur = cv2.GaussianBlur(roi_gray, (5, 5), 0)
    dy = cv2.Sobel(blur, cv2.CV_32F, 0, 1, ksize=3)
    energy = np.mean(np.abs(dy), axis=1).astype(np.float32)

    k = max(5, int(smooth_k) | 1)
    kernel = np.ones(k, dtype=np.float32) / float(k)
    smooth = np.convolve(energy, kernel, mode="same").astype(np.float32)

    seg = smooth[y0:y1]
    if seg.size < 10:
        return None

    thr = _robust_threshold(seg, strength_k=strength_k)

    # Escaneo bottom-up
    for y in range(y1 - 2, y0 + 2, -1):
        if smooth[y] < thr:
            continue

        # Consistencia “agua abajo más oscuro”
        a0 = max(0, y - band_px)
        a1 = max(0, y - 2)
        b0 = min(h - 1, y + 2)
        b1 = min(h, y + band_px)

        if a1 - a0 < 3 or b1 - b0 < 3:
            continue

        mean_above = float(np.mean(roi_gray[a0:a1, :]))
        mean_below = float(np.mean(roi_gray[b0:b1, :]))

        # Queremos que abajo sea más oscuro (menor brillo)
        if mean_below <= mean_above - darkness_delta:
            return y

    return None


def fallback_waterline_by_darkness(
    roi_gray: np.ndarray,
    search_min_pct: float = 0.10,
    search_max_pct: float = 0.95,
    dark_thr: float = 95.0,
    run_len: int = 14,
) -> Optional[int]:
    """Plan B: donde empieza una franja oscura estable."""
    h, w = roi_gray.shape[:2]
    if h < 60 or w < 60:
        return None
    y0 = int(max(2, min(h - 3, h * search_min_pct)))
    y1 = int(max(y0 + 10, min(h - 3, h * search_max_pct)))

    profile = roi_gray.mean(axis=1).astype(np.float32)
    for y in range(y0, y1 - run_len):
        if np.all(profile[y:y + run_len] < dark_thr):
            return y
    return None


def compute_waterlines_from_background(bg_models: list[Optional[np.ndarray]]) -> tuple[list[Optional[int]], Optional[int]]:
    wl: list[Optional[int]] = []
    for bg in bg_models:
        if bg is None:
            wl.append(None)
            continue
        y = estimate_waterline_y_bottomup(bg)
        if y is None:
            y = fallback_waterline_by_darkness(bg)
        wl.append(y)

    valid = [int(y) for y in wl if y is not None]
    if not valid:
        return wl, None

    med = int(np.median(valid))

    cleaned: list[Optional[int]] = []
    for y in wl:
        if y is None:
            cleaned.append(med)
            continue
        if abs(int(y) - med) > 90:
            cleaned.append(med)
        else:
            cleaned.append(int(y))
    return cleaned, med


# -------------------- Detección de rata en ROI (solo debajo del agua) --------------------

def detect_rat_in_roi(
    roi_gray: np.ndarray,
    bg_model: Optional[np.ndarray],
    roi_offset: tuple[int, int],
    rat_idx: int,
    frame_num: int,
    waterline_y: Optional[int],
    water_margin_px: int = 10,
    min_area_ratio: float = 0.005,
    max_area_ratio: float = 0.45,
    fallback_water_top_pct: float = 0.62,  # si falla waterline: recorte fuerte (casi siempre elimina arriba)
) -> Optional[Detection]:
    full_h, full_w = roi_gray.shape[:2]
    ox, oy = roi_offset

    if waterline_y is not None:
        crop_top = int(max(0, min(full_h - 2, waterline_y + water_margin_px)))
    else:
        crop_top = int(max(0, min(full_h - 2, full_h * fallback_water_top_pct)))

    roi_water = roi_gray[crop_top:, :]
    roi_h, roi_w = roi_water.shape[:2]
    if roi_h < 20 or roi_w < 20:
        return None

    total_area = roi_w * roi_h

    if bg_model is not None:
        bg_water = bg_model[crop_top:, :]
        diff = cv2.absdiff(roi_water, bg_water)
        diff = cv2.GaussianBlur(diff, (5, 5), 0)

        otsu_val, thresh_bg = cv2.threshold(diff, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
        if otsu_val < 15:
            _, thresh_bg = cv2.threshold(diff, 25, 255, cv2.THRESH_BINARY)

        blurred = cv2.GaussianBlur(roi_water, (7, 7), 0)
        _, thresh_bright = cv2.threshold(blurred, 100, 255, cv2.THRESH_BINARY)

        thresh = cv2.bitwise_or(thresh_bg, thresh_bright)
    else:
        blurred = cv2.GaussianBlur(roi_water, (7, 7), 0)
        _, thresh = cv2.threshold(blurred, 100, 255, cv2.THRESH_BINARY)

    k_close = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
    k_open = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (3, 3))
    thresh = cv2.morphologyEx(thresh, cv2.MORPH_CLOSE, k_close, iterations=2)
    thresh = cv2.morphologyEx(thresh, cv2.MORPH_OPEN, k_open, iterations=1)

    contours, _ = cv2.findContours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return None

    valid = []
    for c in contours:
        a = float(cv2.contourArea(c))
        if min_area_ratio * total_area <= a <= max_area_ratio * total_area:
            valid.append((c, a))

    if not valid:
        min_abs = total_area * min_area_ratio * 0.5
        valid = [(c, float(cv2.contourArea(c))) for c in contours if float(cv2.contourArea(c)) > min_abs]
        if not valid:
            return None

    best_c, best_area = max(valid, key=lambda x: x[1])
    x, y, w, h = cv2.boundingRect(best_c)

    px, py = int(w * 0.08), int(h * 0.08)
    x = max(0, x - px)
    y = max(0, y - py)
    w = min(roi_w - x, w + 2 * px)
    h = min(roi_h - y, h + 2 * py)

    x_global = int(x + ox)
    y_global = int(y + crop_top + oy)

    # Regla dura: la caja NO debe empezar arriba del agua
    if waterline_y is not None:
        waterline_global = int(oy + waterline_y + water_margin_px)
        if y_global < waterline_global:
            cut = waterline_global - y_global
            y_global = waterline_global
            h = h - cut
            if h < 10:
                return None

    confidence = min(1.0, (best_area / total_area) / 0.06)

    return Detection(
        rat_idx=rat_idx,
        frame_num=frame_num,
        x=x_global,
        y=y_global,
        w=int(w),
        h=int(h),
        cx=x_global + w / 2,
        cy=y_global + h / 2,
        area=int(best_area),
        confidence=round(float(confidence), 3),
    )


# -------------------- Fondo --------------------

def build_background_model(
    cap: cv2.VideoCapture,
    rois: list[tuple[int, int, int, int]],
    n_frames: int = 60,
    sample_step: int = 5,
) -> list[Optional[np.ndarray]]:
    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
    samples: list[list[np.ndarray]] = [[] for _ in rois]
    count, idx = 0, 0

    while count < n_frames:
        ok, frame = cap.read()
        if not ok:
            break
        idx += 1
        if idx % sample_step != 0:
            continue
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        for i, (rx, ry, rw, rh) in enumerate(rois):
            samples[i].append(gray[ry: ry + rh, rx: rx + rw].copy())
        count += 1

    bg_models: list[Optional[np.ndarray]] = []
    for s in samples:
        if s:
            bg_models.append(np.median(np.stack(s), axis=0).astype(np.uint8))
        else:
            bg_models.append(None)

    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
    return bg_models


# -------------------- Suavizado --------------------

class SmoothTracker:
    def __init__(self, alpha: float = 0.45, max_lost: int = 15):
        self.alpha = alpha
        self.max_lost = max_lost
        self._last: dict[int, Detection] = {}
        self._lost: dict[int, int] = {}

    def update(self, det: Optional[Detection], rat_idx: int, frame_num: int) -> Optional[Detection]:
        if det is not None:
            self._lost[rat_idx] = 0
            prev = self._last.get(rat_idx)
            if prev is not None:
                a = self.alpha
                sx = int(a * det.x + (1 - a) * prev.x)
                sy = int(a * det.y + (1 - a) * prev.y)
                sw = int(a * det.w + (1 - a) * prev.w)
                sh = int(a * det.h + (1 - a) * prev.h)
                smoothed = Detection(
                    rat_idx=rat_idx, frame_num=frame_num,
                    x=sx, y=sy, w=sw, h=sh,
                    cx=sx + sw / 2, cy=sy + sh / 2,
                    area=det.area, confidence=det.confidence,
                )
                self._last[rat_idx] = smoothed
                return smoothed
            self._last[rat_idx] = det
            return det

        self._lost[rat_idx] = self._lost.get(rat_idx, 0) + 1
        prev = self._last.get(rat_idx)
        if prev and self._lost[rat_idx] <= self.max_lost:
            fade = max(0.0, 1.0 - self._lost[rat_idx] / self.max_lost)
            return Detection(
                rat_idx=rat_idx, frame_num=frame_num,
                x=prev.x, y=prev.y, w=prev.w, h=prev.h,
                cx=prev.cx, cy=prev.cy,
                area=prev.area, confidence=round(prev.confidence * fade, 3),
            )
        return None


# -------------------- Dibujo --------------------

def draw_detections(
    frame: np.ndarray,
    detections: list[Optional[Detection]],
    rois: list[tuple[int, int, int, int]],
    waterlines: Optional[list[Optional[int]]] = None,
) -> np.ndarray:
    out = frame.copy()

    for i, (rx, ry, rw, rh) in enumerate(rois):
        cv2.rectangle(out, (rx, ry), (rx + rw, ry + rh), (80, 80, 80), 1)
        if waterlines is not None and i < len(waterlines) and waterlines[i] is not None:
            y = ry + int(waterlines[i])
            cv2.line(out, (rx, y), (rx + rw, y), (0, 255, 255), 1)

    for det in detections:
        if det is None:
            continue
        color = RAT_COLORS[det.rat_idx % len(RAT_COLORS)]
        label = RAT_LABELS[det.rat_idx % len(RAT_LABELS)]
        thick = 2 if det.confidence > 0.3 else 1

        cv2.rectangle(out, (det.x, det.y), (det.x + det.w, det.y + det.h), color, thick)

        font = cv2.FONT_HERSHEY_SIMPLEX
        scale = 0.5
        (tw, th), _ = cv2.getTextSize(label, font, scale, 1)
        ly = max(det.y - 5, th + 5)
        cv2.rectangle(out, (det.x, ly - th - 4), (det.x + tw + 4, ly + 2), color, -1)
        cv2.putText(out, label, (det.x + 2, ly - 2), font, scale, (255, 255, 255), 1, cv2.LINE_AA)
        cv2.circle(out, (int(det.cx), int(det.cy)), 3, color, -1)

    return out


# -------------------- Principal --------------------

def track_video(
    input_path: str,
    output_video: Optional[str] = None,
    output_json: Optional[str] = None,
    layout: str = "auto",
    fps_cap: int = 0,
    show_progress: bool = True,
) -> dict:
    cap = cv2.VideoCapture(input_path)
    if not cap.isOpened():
        raise RuntimeError(f"No se puede abrir: {input_path}")

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    fw = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    fh = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    if show_progress:
        print(f"Video: {fw}x{fh} @ {fps:.1f} FPS, {total_frames} frames ({(total_frames / fps) if fps else 0:.1f}s)")

    if layout == "auto":
        layout = detect_layout(fw, fh)
        if show_progress:
            print(f"Layout detectado: {layout}")

    rois = compute_rois(fw, fh, layout)

    if show_progress:
        print("Construyendo modelo de fondo…")
    bg_models = build_background_model(cap, rois)

    waterlines, global_median = compute_waterlines_from_background(bg_models)
    if show_progress:
        if global_median is None:
            print("No se pudo estimar la línea del agua con confianza (usando recorte fuerte).")
        else:
            print(f"Línea del agua (mediana aprox): y={global_median}px (dentro de cada ROI)")

    writer = None
    if output_video:
        fourcc = cv2.VideoWriter_fourcc(*"mp4v")
        out_fps = fps if fps_cap <= 0 else min(fps, fps_cap)
        writer = cv2.VideoWriter(output_video, fourcc, out_fps, (fw, fh))

    step = max(1, int(fps / fps_cap)) if fps_cap > 0 else 1
    tracker = SmoothTracker(alpha=0.45, max_lost=int(fps * 0.5))

    all_dets: list[dict] = []
    stats = {"total_frames": 0, "detected": [0] * 4, "lost": [0] * 4}

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
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

        dets: list[Optional[Detection]] = []
        for i, (rx, ry, rw, rh) in enumerate(rois):
            raw = detect_rat_in_roi(
                gray[ry: ry + rh, rx: rx + rw],
                bg_models[i],
                (rx, ry),
                rat_idx=i,
                frame_num=frame_num,
                waterline_y=waterlines[i] if i < len(waterlines) else None,
            )
            smoothed = tracker.update(raw, i, frame_num)
            dets.append(smoothed)

            if smoothed is not None:
                stats["detected"][i] += 1
                all_dets.append(asdict(smoothed))
            else:
                stats["lost"][i] += 1

        if writer:
            writer.write(draw_detections(frame, dets, rois, waterlines=waterlines))

        if show_progress and frame_num % max(1, int(fps) * 5) == 0:
            pct = (frame_num / total_frames * 100) if total_frames else 0
            print(f"  Frame {frame_num}/{total_frames} ({pct:.0f}%) — {time.time() - t0:.1f}s")

    cap.release()
    if writer:
        writer.release()
        if show_progress:
            print(f"Video anotado: {output_video}")

    if output_json:
        payload = {
            "video": str(input_path),
            "fps": fps,
            "frame_size": [fw, fh],
            "layout": layout,
            "rois": [
                {"rat_idx": i, "x": r[0], "y": r[1], "w": r[2], "h": r[3], "waterline_y": (waterlines[i] if i < len(waterlines) else None)}
                for i, r in enumerate(rois)
            ],
            "waterline_global_median_y": global_median,
            "total_frames_processed": stats["total_frames"],
            "detections": all_dets,
        }
        with open(output_json, "w") as f:
            json.dump(payload, f, indent=2)
        if show_progress:
            print(f"Tracking JSON: {output_json}")

    if show_progress:
        elapsed = time.time() - t0
        print(f"\nResumen ({elapsed:.1f}s):")
        for i in range(4):
            tot = stats["detected"][i] + stats["lost"][i]
            pct = (stats["detected"][i] / tot * 100) if tot else 0
            print(f"  {RAT_LABELS[i]}: {stats['detected'][i]}/{tot} frames ({pct:.0f}%)")

    return stats


def main():
    import argparse
    p = argparse.ArgumentParser(description="FST Rat Tracker")
    p.add_argument("--input", "-i", required=True)
    p.add_argument("--output", "-o", default=None)
    p.add_argument("--data", "-d", default=None)
    p.add_argument("--layout", "-l", default="auto", choices=["auto", "1x4", "2x2"])
    p.add_argument("--fps-cap", type=int, default=0)
    args = p.parse_args()

    inp = Path(args.input)
    if args.output is None:
        args.output = str(inp.parent / f"{inp.stem}_tracked{inp.suffix}")
    if args.data is None:
        args.data = str(inp.parent / f"{inp.stem}_tracking.json")

    track_video(
        input_path=args.input,
        output_video=args.output,
        output_json=args.data,
        layout=args.layout,
        fps_cap=args.fps_cap,
    )


if __name__ == "__main__":
    main()