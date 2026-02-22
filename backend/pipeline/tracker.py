"""
FST Rat Tracker — Detección y seguimiento de ratas en la prueba de nado forzado.

Dado un video con 4 cilindros (1x4 en fila o 2x2), detecta cada rata en su ROI
y dibuja un bounding box sobre ella frame a frame.

Salidas:
  - Video anotado (.mp4) con bounding boxes de colores
  - JSON con coordenadas por frame para cada rata

Uso standalone:
  python -m pipeline.tracker --input video.mp4
"""

import cv2
import numpy as np
import json
import time
from pathlib import Path
from dataclasses import dataclass, asdict
from typing import Optional


# ─── Colores por rata (BGR) ───────────────────────────────────────────
RAT_COLORS = [
    (0, 255, 0),    # Rata 0: verde
    (255, 100, 0),  # Rata 1: azul
    (0, 180, 255),  # Rata 2: naranja
    (255, 0, 180),  # Rata 3: magenta
]

RAT_LABELS = ["Rata 1", "Rata 2", "Rata 3", "Rata 4"]


# ─── Dataclass de detección ──────────────────────────────────────────
@dataclass
class Detection:
    """Bounding box de una rata en un frame."""
    rat_idx: int
    frame_num: int
    x: int
    y: int
    w: int
    h: int
    cx: float   # centro x (coordenadas globales del frame)
    cy: float   # centro y (coordenadas globales del frame)
    area: int
    confidence: float  # 0-1


# ─── Cálculo de ROIs ─────────────────────────────────────────────────
def compute_rois(frame_w: int, frame_h: int, layout: str = "1x4") -> list[tuple[int, int, int, int]]:
    """
    Calcula las ROIs para cada cilindro.

    layout:
      '1x4' — 4 cilindros en fila horizontal (default FST)
      '2x2' — 4 cilindros en cuadrícula 2x2

    Retorna lista de (x, y, w, h) en coordenadas del frame.
    """
    if layout == "1x4":
        qw = frame_w // 4
        return [
            (0, 0, qw, frame_h),
            (qw, 0, qw, frame_h),
            (2 * qw, 0, qw, frame_h),
            (3 * qw, 0, frame_w - 3 * qw, frame_h),
        ]
    elif layout == "2x2":
        hw, hh = frame_w // 2, frame_h // 2
        return [
            (0, 0, hw, hh),
            (hw, 0, frame_w - hw, hh),
            (0, hh, hw, frame_h - hh),
            (hw, hh, frame_w - hw, frame_h - hh),
        ]
    else:
        raise ValueError(f"Layout desconocido: {layout}")


def detect_layout(frame_w: int, frame_h: int) -> str:
    """Auto-detecta si el video es 1x4 o 2x2 por su aspect ratio."""
    aspect = frame_w / frame_h if frame_h > 0 else 1
    return "1x4" if aspect > 1.2 else "2x2"


# ─── Detección de rata en ROI ────────────────────────────────────────
def detect_rat_in_roi(
    roi_gray: np.ndarray,
    bg_model: Optional[np.ndarray],
    roi_offset: tuple[int, int],
    rat_idx: int,
    frame_num: int,
    min_area_ratio: float = 0.005,
    max_area_ratio: float = 0.45,
    water_top_pct: float = 0.30,
) -> Optional[Detection]:
    """
    Detecta la rata dentro de una ROI combinando sustracción de fondo
    y detección por brillo (ratas blancas sobre agua oscura).

    La búsqueda se limita a la zona del agua (parte inferior del ROI),
    descartando el fondo de laboratorio (estantes, etc.).
    """
    full_h, full_w = roi_gray.shape[:2]
    ox, oy = roi_offset

    # Recortar a la región del agua
    crop_top = int(full_h * water_top_pct)
    roi_water = roi_gray[crop_top:, :]
    roi_h, roi_w = roi_water.shape[:2]
    total_area = roi_w * roi_h

    # --- Máscara de foreground ---
    if bg_model is not None:
        bg_water = bg_model[crop_top:, :]
        diff = cv2.absdiff(roi_water, bg_water)
        diff = cv2.GaussianBlur(diff, (5, 5), 0)

        otsu_val, thresh_bg = cv2.threshold(
            diff, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU
        )
        if otsu_val < 15:
            _, thresh_bg = cv2.threshold(diff, 25, 255, cv2.THRESH_BINARY)

        # Señal complementaria por brillo
        blurred = cv2.GaussianBlur(roi_water, (7, 7), 0)
        _, thresh_bright = cv2.threshold(blurred, 100, 255, cv2.THRESH_BINARY)

        thresh = cv2.bitwise_or(thresh_bg, thresh_bright)
    else:
        blurred = cv2.GaussianBlur(roi_water, (7, 7), 0)
        _, thresh = cv2.threshold(blurred, 100, 255, cv2.THRESH_BINARY)

    # Morfología
    k_close = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
    k_open = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (3, 3))
    thresh = cv2.morphologyEx(thresh, cv2.MORPH_CLOSE, k_close, iterations=2)
    thresh = cv2.morphologyEx(thresh, cv2.MORPH_OPEN, k_open, iterations=1)

    # Contornos
    contours, _ = cv2.findContours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return None

    valid = [
        (c, cv2.contourArea(c))
        for c in contours
        if min_area_ratio * total_area <= cv2.contourArea(c) <= max_area_ratio * total_area
    ]

    if not valid:
        min_abs = total_area * min_area_ratio * 0.5
        valid = [(c, cv2.contourArea(c)) for c in contours if cv2.contourArea(c) > min_abs]
        if not valid:
            return None

    best_c, best_area = max(valid, key=lambda x: x[1])
    x, y, w, h = cv2.boundingRect(best_c)

    # Padding 8 %
    px, py = int(w * 0.08), int(h * 0.08)
    x = max(0, x - px)
    y = max(0, y - py)
    w = min(roi_w - x, w + 2 * px)
    h = min(roi_h - y, h + 2 * py)

    confidence = min(1.0, (best_area / total_area) / 0.06)

    return Detection(
        rat_idx=rat_idx,
        frame_num=frame_num,
        x=x + ox,
        y=y + crop_top + oy,
        w=w,
        h=h,
        cx=x + ox + w / 2,
        cy=y + crop_top + oy + h / 2,
        area=best_area,
        confidence=round(confidence, 3),
    )


# ─── Modelo de fondo ─────────────────────────────────────────────────
def build_background_model(
    cap: cv2.VideoCapture,
    rois: list[tuple[int, int, int, int]],
    n_frames: int = 50,
    sample_step: int = 5,
) -> list[np.ndarray]:
    """
    Construye un modelo de fondo por ROI usando la mediana de N frames.
    La mediana es robusta al movimiento de la rata (foreground).
    """
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
            samples[i].append(gray[ry : ry + rh, rx : rx + rw].copy())
        count += 1

    bg_models = []
    for s in samples:
        if s:
            bg_models.append(np.median(np.stack(s), axis=0).astype(np.uint8))
        else:
            bg_models.append(None)

    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
    return bg_models


# ─── Suavizado temporal ──────────────────────────────────────────────
class SmoothTracker:
    """
    Suaviza detecciones con EMA para evitar saltos bruscos.
    Mantiene la última detección si la rata se pierde por pocos frames.
    """

    def __init__(self, alpha: float = 0.4, max_lost: int = 15):
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

        # Sin detección → mantener última posición con fade
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


# ─── Dibujo sobre frame ─────────────────────────────────────────────
def draw_detections(
    frame: np.ndarray,
    detections: list[Optional[Detection]],
    rois: list[tuple[int, int, int, int]],
) -> np.ndarray:
    """Dibuja bounding boxes, etiquetas y centros sobre el frame."""
    out = frame.copy()

    # Bordes de ROI (tenues)
    for rx, ry, rw, rh in rois:
        cv2.rectangle(out, (rx, ry), (rx + rw, ry + rh), (80, 80, 80), 1)

    for det in detections:
        if det is None:
            continue
        color = RAT_COLORS[det.rat_idx % len(RAT_COLORS)]
        label = RAT_LABELS[det.rat_idx % len(RAT_LABELS)]
        thick = 2 if det.confidence > 0.3 else 1

        # Bbox
        cv2.rectangle(out, (det.x, det.y), (det.x + det.w, det.y + det.h), color, thick)

        # Etiqueta con fondo
        font = cv2.FONT_HERSHEY_SIMPLEX
        scale = 0.5
        (tw, th), _ = cv2.getTextSize(label, font, scale, 1)
        ly = max(det.y - 5, th + 5)
        cv2.rectangle(out, (det.x, ly - th - 4), (det.x + tw + 4, ly + 2), color, -1)
        cv2.putText(out, label, (det.x + 2, ly - 2), font, scale, (255, 255, 255), 1, cv2.LINE_AA)

        # Centro
        cv2.circle(out, (int(det.cx), int(det.cy)), 3, color, -1)

    return out


# ─── Función principal de tracking ───────────────────────────────────
def track_video(
    input_path: str,
    output_video: Optional[str] = None,
    output_json: Optional[str] = None,
    layout: str = "auto",
    fps_cap: int = 0,
    show_progress: bool = True,
) -> dict:
    """
    Procesa un video de FST completo.

    Args:
        input_path:   ruta al video de entrada
        output_video: ruta para el video anotado (None = no generar)
        output_json:  ruta para el JSON de tracking (None = no guardar)
        layout:       '1x4', '2x2' o 'auto'
        fps_cap:      limitar FPS de procesamiento (0 = sin límite)

    Returns:
        dict con estadísticas del tracking
    """
    cap = cv2.VideoCapture(input_path)
    if not cap.isOpened():
        raise RuntimeError(f"No se puede abrir: {input_path}")

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    fw = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    fh = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    if show_progress:
        print(f"Video: {fw}x{fh} @ {fps:.1f} FPS, {total_frames} frames ({total_frames / fps:.1f}s)")

    if layout == "auto":
        layout = detect_layout(fw, fh)
        if show_progress:
            print(f"Layout detectado: {layout}")

    rois = compute_rois(fw, fh, layout)

    # Modelo de fondo
    if show_progress:
        print("Construyendo modelo de fondo …")
    bg_models = build_background_model(cap, rois)

    # Writer de video
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
                gray[ry : ry + rh, rx : rx + rw],
                bg_models[i],
                (rx, ry),
                rat_idx=i,
                frame_num=frame_num,
            )
            smoothed = tracker.update(raw, i, frame_num)
            dets.append(smoothed)

            if smoothed is not None:
                stats["detected"][i] += 1
                all_dets.append(asdict(smoothed))
            else:
                stats["lost"][i] += 1

        if writer:
            writer.write(draw_detections(frame, dets, rois))

        if show_progress and frame_num % max(1, int(fps) * 5) == 0:
            pct = frame_num / total_frames * 100 if total_frames else 0
            print(f"  Frame {frame_num}/{total_frames} ({pct:.0f}%) — {time.time() - t0:.1f}s")

    cap.release()
    if writer:
        writer.release()
        if show_progress:
            print(f"Video anotado: {output_video}")

    # JSON
    if output_json:
        payload = {
            "video": str(input_path),
            "fps": fps,
            "frame_size": [fw, fh],
            "layout": layout,
            "rois": [{"rat_idx": i, "x": r[0], "y": r[1], "w": r[2], "h": r[3]} for i, r in enumerate(rois)],
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
            pct = stats["detected"][i] / tot * 100 if tot else 0
            print(f"  {RAT_LABELS[i]}: {stats['detected'][i]}/{tot} frames ({pct:.0f}%)")

    return stats


# ─── CLI ─────────────────────────────────────────────────────────────
def main():
    import argparse

    p = argparse.ArgumentParser(description="FST Rat Tracker")
    p.add_argument("--input", "-i", required=True)
    p.add_argument("--output", "-o", default=None, help="Video anotado")
    p.add_argument("--data", "-d", default=None, help="JSON de tracking")
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
