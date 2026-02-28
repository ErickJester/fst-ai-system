# -*- coding: utf-8 -*-
"""
FST Rat Tracker v6 — YOLO track-mode + gating físico + continuidad robusta

Objetivo: 1 bbox por rata (4 ROIs) en (casi) todos los frames, evitando aberraciones
(saltos a paredes/separadores/reflejos) y manteniendo continuidad (freeze/lost).

Estrategia:
  1) YOLO en frame completo usando ultralytics .track() (ByteTrack o BoT-SORT)
  2) Identidad por ROI (centro del bbox cae en ROI i → rat_idx=i)
  3) Gating físico (tamaño, waterline, velocidad máxima)
  4) Freeze policy con decay, y reset si el bbox congelado deja de ser plausible
  5) Re-adquisición: bajar conf cuando un ROI está en recovery
  6) Estabilización opcional (ECC con fallback ORB)

Modelo:
  - Default: weights/rat.pt (1 clase: rat)
  - Fallback: yolov8n.pt (COCO) con WARNING grande (solo debug)
  - Sin ultralytics: fallback clásico (brillo + Otsu + background median)

Dependencias:
  opencv-python, numpy
  (opcional) ultralytics, torch

Va en: backend/pipeline/tracker.py
"""

from __future__ import annotations

import cv2
import numpy as np
import json
import time
from pathlib import Path
from dataclasses import dataclass, asdict
from typing import Optional, Tuple, List, Dict


RAT_COLORS = [(0, 255, 0), (255, 100, 0), (0, 180, 255), (255, 0, 180)]
RAT_LABELS = ["Rata 1", "Rata 2", "Rata 3", "Rata 4"]

DEFAULT_MODEL  = "weights/rat.pt"
FALLBACK_MODEL = "yolov8n.pt"

DEFAULT_CONF   = 0.25
RECOVERY_CONF  = 0.10

MAX_FREEZE     = 20
FREEZE_DECAY   = 0.90


# ─────────────────────────────────────────────────────────────────────
# Data model
# ─────────────────────────────────────────────────────────────────────
@dataclass
class Detection:
    rat_idx:    int
    frame_num:  int
    x:          int
    y:          int
    w:          int
    h:          int
    cx:         float
    cy:         float
    area:       int
    confidence: float
    source:     str      # "yolo" | "track" | "classic" | "freeze" | "lost"
    track_id:   int = -1


# ─────────────────────────────────────────────────────────────────────
# 1 · ROIs adaptativas (separadores de cilindro)
# ─────────────────────────────────────────────────────────────────────
def find_cylinder_separators(gray: np.ndarray) -> List[int]:
    h, w = gray.shape[:2]
    band = gray[int(h * 0.3):int(h * 0.7), :]
    cp = np.mean(band, axis=0)
    sm = np.convolve(cp, np.ones(15) / 15, mode="same")
    md = w // 6
    cands = []
    for x in range(md, w - md):
        left  = sm[max(0, x - md // 2):x]
        right = sm[x + 1:min(w, x + md // 2 + 1)]
        if len(left) and len(right) and sm[x] < np.min(left) and sm[x] < np.min(right):
            cands.append((x, min(np.mean(left) - sm[x], np.mean(right) - sm[x])))
    cands.sort(key=lambda c: c[1], reverse=True)
    seps = sorted(c[0] for c in cands[:3])
    return seps if len(seps) >= 3 else [w // 4, w // 2, 3 * w // 4]


def compute_rois(fw: int, fh: int, layout: str = "1x4", gray: Optional[np.ndarray] = None):
    if layout == "1x4" and gray is not None:
        s = find_cylinder_separators(gray)
        return [
            (0,    0, s[0],          fh),
            (s[0], 0, s[1] - s[0],   fh),
            (s[1], 0, s[2] - s[1],   fh),
            (s[2], 0, fw - s[2],     fh),
        ]
    if layout == "1x4":
        q = fw // 4
        return [(0, 0, q, fh), (q, 0, q, fh), (2*q, 0, q, fh), (3*q, 0, fw - 3*q, fh)]
    if layout == "2x2":
        hw, hh = fw // 2, fh // 2
        return [(0, 0, hw, hh), (hw, 0, fw - hw, hh), (0, hh, hw, fh - hh), (hw, hh, fw - hw, fh - hh)]
    raise ValueError(f"Layout desconocido: {layout}")


def detect_layout(fw: int, fh: int) -> str:
    return "1x4" if fw / fh > 1.2 else "2x2"


# ─────────────────────────────────────────────────────────────────────
# 2 · Waterline estable (bottom-up + EMA + hard clamp)
# ─────────────────────────────────────────────────────────────────────
def _waterline_raw(roi_gray: np.ndarray, band: int = 12, lo: float = 0.20, hi: float = 0.80, min_d: float = 5.0) -> int:
    """Bottom-up: busca superficie del agua sin confundir borde superior."""
    rh, rw = roi_gray.shape[:2]
    if rh < 60 or rw < 40:
        return int(rh * 0.35)

    prof = np.mean(roi_gray, axis=1).astype(np.float64)
    sm = np.convolve(prof, np.ones(11) / 11, mode="same")
    gr = np.diff(sm)
    y0, y1 = int(rh * lo), int(rh * hi)

    best, bs = None, 0.0
    for y in range(y1, y0, -1):
        a0, b1 = max(0, y - band), min(rh, y + band)
        if y - a0 < 3 or b1 - y < 3:
            continue
        d = float(np.mean(sm[a0:y])) - float(np.mean(sm[y:b1]))
        if d < min_d:
            continue
        gs = gr[max(0, y - 3):min(len(gr), y + 3)]
        e = -float(np.min(gs)) if len(gs) else 0.0
        s = d * 0.6 + e * 0.4
        if s > bs:
            bs = s
            best = y

    return int(best) if best is not None else int(rh * 0.35)


class StableWaterline:
    """EMA(α=0.12) sobre delta clamp ±6 px/frame."""
    def __init__(self, init_y: int, alpha: float = 0.12, max_dpx: int = 6):
        self.y = float(init_y)
        self.alpha = float(alpha)
        self.max_dpx = int(max_dpx)

    def update(self, raw: int) -> int:
        d = float(raw) - self.y
        d = max(-self.max_dpx, min(self.max_dpx, d))
        self.y += self.alpha * d
        return int(round(self.y))


# ─────────────────────────────────────────────────────────────────────
# 3 · Gating físico
# ─────────────────────────────────────────────────────────────────────
class PhysicalGate:
    """
    Rechaza bboxes implausibles:
      - Centro fuera de ROI
      - Centro arriba de waterline (margen 8% para cabeza)
      - Tamaño < 0.4% o > 35% del ROI
      - Salto de centroide > 25% de diagonal ROI en 1 frame
    """
    def __init__(self, roi, min_a=0.004, max_a=0.35, max_v=0.25, head_m=0.08):
        rx, ry, rw, rh = roi
        self.rx, self.ry, self.rw, self.rh = int(rx), int(ry), int(rw), int(rh)
        self.roi_area = float(rw * rh)
        self.diag = float((rw**2 + rh**2) ** 0.5)
        self.min_a = float(min_a) * self.roi_area
        self.max_a = float(max_a) * self.roi_area
        self.max_v = float(max_v) * self.diag
        self.head_m = int(head_m * rh)
        self._cx: Optional[float] = None
        self._cy: Optional[float] = None

    def check(self, x: int, y: int, w: int, h: int, wl_y: int) -> bool:
        cx, cy = x + w / 2.0, y + h / 2.0
        if not (self.rx <= cx < self.rx + self.rw and self.ry <= cy < self.ry + self.rh):
            return False
        a = float(w * h)
        if a < self.min_a or a > self.max_a:
            return False
        if cy < self.ry + wl_y - self.head_m:
            return False
        if self._cx is not None:
            d = float(((cx - self._cx)**2 + (cy - self._cy)**2) ** 0.5)
            if d > self.max_v:
                return False
        return True

    def commit(self, cx: float, cy: float):
        self._cx, self._cy = float(cx), float(cy)

    def reset_velocity(self):
        self._cx = self._cy = None


# ─────────────────────────────────────────────────────────────────────
# 4 · ROI state (freeze / lost / recovery)
# ─────────────────────────────────────────────────────────────────────
class ROIState:
    def __init__(self, max_freeze: int = MAX_FREEZE, decay: float = FREEZE_DECAY):
        self.last_det: Optional[Detection] = None
        self.freeze_count: int = 0
        self.max_freeze = int(max_freeze)
        self.decay = float(decay)
        self.gating_fail_streak: int = 0

    @property
    def is_lost(self) -> bool:
        return self.freeze_count > self.max_freeze

    @property
    def needs_recovery(self) -> bool:
        return self.freeze_count > self.max_freeze // 2

    def accept(self, det: Detection):
        self.last_det = det
        self.freeze_count = 0
        self.gating_fail_streak = 0

    def freeze_det(self, frame_num: int, rat_idx: int) -> Optional[Detection]:
        if self.last_det is None:
            return None
        self.freeze_count += 1
        new_conf = self.last_det.confidence * self.decay
        new_conf = float(max(0.01, min(new_conf, 0.99)))
        source = "lost" if self.is_lost else "freeze"
        return Detection(
            rat_idx=rat_idx, frame_num=frame_num,
            x=self.last_det.x, y=self.last_det.y, w=self.last_det.w, h=self.last_det.h,
            cx=self.last_det.cx, cy=self.last_det.cy,
            area=self.last_det.area,
            confidence=round(new_conf, 4),
            source=source,
            track_id=self.last_det.track_id,
        )

    def mark_gating_fail(self, limit: int = 10):
        self.gating_fail_streak += 1
        if self.gating_fail_streak > int(limit):
            # reset total: no congela basura indefinidamente
            self.last_det = None
            self.freeze_count = 0
            self.gating_fail_streak = 0


# ─────────────────────────────────────────────────────────────────────
# 5 · Estabilización opcional (ECC + ORB)
# ─────────────────────────────────────────────────────────────────────
class FrameStabilizer:
    def __init__(self):
        self.prev_gray: Optional[np.ndarray] = None
        self.warp_mode = cv2.MOTION_EUCLIDEAN
        self.criteria = (cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT, 50, 1e-4)

    def stabilize(self, frame: np.ndarray) -> np.ndarray:
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        if self.prev_gray is None:
            self.prev_gray = gray
            return frame

        # ECC
        try:
            warp = np.eye(2, 3, dtype=np.float32)
            _, warp = cv2.findTransformECC(self.prev_gray, gray, warp, self.warp_mode, self.criteria)
            h, w = frame.shape[:2]
            aligned = cv2.warpAffine(frame, warp, (w, h), flags=cv2.INTER_LINEAR + cv2.WARP_INVERSE_MAP)
            self.prev_gray = cv2.cvtColor(aligned, cv2.COLOR_BGR2GRAY)
            return aligned
        except cv2.error:
            pass

        # ORB fallback
        try:
            return self._orb(frame, gray)
        except Exception:
            self.prev_gray = gray
            return frame

    def _orb(self, frame: np.ndarray, gray: np.ndarray) -> np.ndarray:
        orb = cv2.ORB_create(500)
        kp1, d1 = orb.detectAndCompute(self.prev_gray, None)
        kp2, d2 = orb.detectAndCompute(gray, None)
        if d1 is None or d2 is None or len(kp1) < 10 or len(kp2) < 10:
            self.prev_gray = gray
            return frame
        bf = cv2.BFMatcher(cv2.NORM_HAMMING, crossCheck=True)
        matches = sorted(bf.match(d1, d2), key=lambda m: m.distance)[:50]
        if len(matches) < 10:
            self.prev_gray = gray
            return frame
        pts1 = np.float32([kp1[m.queryIdx].pt for m in matches])
        pts2 = np.float32([kp2[m.trainIdx].pt for m in matches])
        M, _ = cv2.estimateAffinePartial2D(pts2, pts1)
        if M is None:
            self.prev_gray = gray
            return frame
        h, w = frame.shape[:2]
        aligned = cv2.warpAffine(frame, M, (w, h))
        self.prev_gray = cv2.cvtColor(aligned, cv2.COLOR_BGR2GRAY)
        return aligned


# ─────────────────────────────────────────────────────────────────────
# 6 · YOLO Engine (ultralytics)
# ─────────────────────────────────────────────────────────────────────
class YOLOEngine:
    """
    Prefer weights/rat.pt; if missing fall back to yolov8n.pt with WARNING.
    If ultralytics is missing → available=False (caller can use classic fallback).
    """
    def __init__(self, model_path: str = DEFAULT_MODEL, conf: float = DEFAULT_CONF,
                 tracker_yaml: str = "bytetrack.yaml", device: str = ""):
        self.available = False
        self.is_coco = False
        self.conf = float(conf)
        self.tracker_yaml = str(tracker_yaml)
        self.device = device
        self._model = None

        try:
            from ultralytics import YOLO  # type: ignore
        except Exception:
            return

        mp = Path(model_path)
        if mp.exists():
            self._model = YOLO(str(mp))
        else:
            self.is_coco = True
            self._warn_coco(model_path)
            self._model = YOLO(FALLBACK_MODEL)

        self.device = self.device or self._pick_device()
        self.available = True

    @staticmethod
    def _pick_device() -> str:
        try:
            import torch  # type: ignore
            return "cuda" if torch.cuda.is_available() else "cpu"
        except Exception:
            return "cpu"

    @staticmethod
    def _warn_coco(missing_path: str):
        print("=" * 76)
        print("⚠⚠⚠  WARNING: MODELO CUSTOM NO ENCONTRADO  ⚠⚠⚠")
        print(f"  No existe: {missing_path}")
        print(f"  Usando {FALLBACK_MODEL} (COCO) SOLO PARA DEBUG. Resultados NO confiables.")
        print("  Entrena rat.pt y colócalo en weights/rat.pt.")
        print("=" * 76)

    def track(self, frame_bgr: np.ndarray, conf: Optional[float] = None, persist: bool = True):
        """
        Usa .track() si es posible. Devuelve lista de:
          (x, y, w, h, conf, track_id, has_id)
        """
        if not self.available:
            return []

        c = float(conf) if conf is not None else self.conf

        try:
            results = self._model.track(
                frame_bgr,
                conf=c,
                persist=persist,
                verbose=False,
                device=self.device,
                tracker=self.tracker_yaml,
            )
        except Exception:
            # fallback a detect
            try:
                results = self._model(frame_bgr, conf=c, verbose=False, device=self.device)
            except Exception:
                return []

        out = []
        for r in results:
            if r.boxes is None:
                continue
            for box in r.boxes:
                x1, y1, x2, y2 = (int(v) for v in box.xyxy[0])
                w, h = x2 - x1, y2 - y1
                if w < 5 or h < 5:
                    continue
                cval = float(box.conf[0])
                tid = int(box.id[0]) if box.id is not None else -1
                has_id = (box.id is not None)
                out.append((x1, y1, w, h, cval, tid, has_id))
        return out


# ─────────────────────────────────────────────────────────────────────
# 7 · Classic fallback detector (ROI)
# ─────────────────────────────────────────────────────────────────────
def _cyl_mask(w: int, h: int, m: float = 0.12) -> np.ndarray:
    mask = np.zeros((h, w), np.uint8)
    cv2.ellipse(mask, (w // 2, h // 2), (int(w * (0.5 - m)), h // 2), 0, 0, 360, 255, -1)
    return mask


def detect_classic_roi(roi_gray: np.ndarray, bg: Optional[np.ndarray], offset: Tuple[int, int],
                       idx: int, fnum: int, wl_y: int) -> Optional[Detection]:
    """
    Brillo + (bg diff Otsu) + percentil + máscara cilíndrica.
    Devuelve Detection en coords globales (sumando offset).
    """
    rh, rw = roi_gray.shape[:2]
    rx, ry = offset

    hm = int(rh * 0.08)
    ct, cb = max(0, wl_y - hm), int(rh * 0.82)
    se = roi_gray[ct:cb, :]
    sh, sw = se.shape[:2]
    if sh < 15 or sw < 15:
        return None
    sa = sw * sh
    wl_loc = wl_y - ct

    bl = cv2.GaussianBlur(se, (5, 5), 0)
    wz = bl[wl_loc:, :]
    tv = max(float(np.percentile(wz, 92)), 55.0) if wz.size > 100 else 80.0

    if bg is not None:
        bs = bg[ct:cb, :]
        if bs.shape == se.shape:
            diff = cv2.absdiff(bl, cv2.GaussianBlur(bs, (5, 5), 0))
            _, m1 = cv2.threshold(diff, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
            _, m2 = cv2.threshold(bl, tv, 255, cv2.THRESH_BINARY)
            mask = cv2.bitwise_or(m1, m2)
        else:
            _, mask = cv2.threshold(bl, tv, 255, cv2.THRESH_BINARY)
    else:
        _, mask = cv2.threshold(bl, tv, 255, cv2.THRESH_BINARY)

    mask = cv2.bitwise_and(mask, _cyl_mask(sw, sh))
    mask[:max(0, wl_loc - int(rh * 0.03)), :] = 0

    k1 = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (7, 7))
    k2 = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, k1, iterations=1)
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN,  k2, iterations=2)

    cs, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not cs:
        return None

    blobs = []
    for c in cs:
        a = cv2.contourArea(c)
        if a < sa * 0.003 or a > sa * 0.35:
            continue
        x, y, bw, bh = cv2.boundingRect(c)
        ba = bw * bh
        if ba <= 0:
            continue
        sol = a / ba
        if sol < 0.20:
            continue
        aspect = bw / bh if bh else 999.0
        if aspect > 5.0:
            continue
        cx, cy = x + bw / 2.0, y + bh / 2.0
        center = 1.0 - abs(cx - sw / 2.0) / (sw / 2.0)
        prox = max(0.0, 1.0 - abs(cy - wl_loc) / (sh * 0.4))
        score = a * (0.2 + 0.3 * center + 0.5 * prox) * sol
        blobs.append((score, x, y, bw, bh, a))

    if not blobs:
        return None

    score, x, y, bw, bh, area = max(blobs, key=lambda t: t[0])

    px, py = int(bw * 0.06), int(bh * 0.06)
    x = max(0, x - px)
    y = max(0, y - py)
    bw = min(sw - x, bw + 2 * px)
    bh = min(sh - y, bh + 2 * py)

    gx = x + rx
    gy = y + ct + ry

    return Detection(
        rat_idx=idx, frame_num=fnum,
        x=int(gx), y=int(gy), w=int(bw), h=int(bh),
        cx=float(gx + bw / 2.0), cy=float(gy + bh / 2.0),
        area=int(area),
        confidence=round(min(1.0, float(area) / (sa * 0.06)), 3),
        source="classic",
        track_id=-1,
    )


def build_bg(cap: cv2.VideoCapture, rois, n: int = 60, step: int = 5):
    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
    samp = [[] for _ in rois]
    cnt, idx = 0, 0
    while cnt < n:
        ok, f = cap.read()
        if not ok:
            break
        idx += 1
        if idx % step:
            continue
        g = cv2.cvtColor(f, cv2.COLOR_BGR2GRAY)
        for i, (rx, ry, rw, rh) in enumerate(rois):
            samp[i].append(g[ry:ry + rh, rx:rx + rw].copy())
        cnt += 1
    out = [np.median(np.stack(s), axis=0).astype(np.uint8) if s else None for s in samp]
    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
    return out


# ─────────────────────────────────────────────────────────────────────
# 8 · Asignación a ROIs + selección estable
# ─────────────────────────────────────────────────────────────────────
def _roi_index(rois, cx: float, cy: float) -> Optional[int]:
    for i, (rx, ry, rw, rh) in enumerate(rois):
        if rx <= cx < rx + rw and ry <= cy < ry + rh:
            return i
    return None


def assign_to_rois(
    boxes: List[Tuple[int, int, int, int, float, int, bool]],
    rois,
    gates: List[PhysicalGate],
    wls: List[int],
    states: List[ROIState],
    frame_num: int,
) -> Dict[int, Optional[Detection]]:
    """
    1 bbox por ROI.
    Selección = conf + bonus por track_id consistente + bonus por cercanía al bbox previo.
    """
    result: Dict[int, Optional[Detection]] = {i: None for i in range(len(rois))}
    best_score: Dict[int, float] = {i: -1e9 for i in range(len(rois))}

    for bx, by, bw, bh, conf, tid, has_id in boxes:
        cx, cy = bx + bw / 2.0, by + bh / 2.0
        i = _roi_index(rois, cx, cy)
        if i is None:
            continue

        if not gates[i].check(bx, by, bw, bh, wls[i]):
            continue

        # score base
        sc = float(conf)

        # bonus por track_id consistente
        prev_tid = states[i].last_det.track_id if states[i].last_det is not None else -1
        if has_id and tid != -1 and tid == prev_tid:
            sc += 0.15

        # bonus por cercanía al bbox previo
        if states[i].last_det is not None:
            pcx, pcy = states[i].last_det.cx, states[i].last_det.cy
            d = float(((cx - pcx)**2 + (cy - pcy)**2) ** 0.5)
            # 0..0.10 aprox
            sc += max(0.0, 0.10 * (1.0 - d / (gates[i].diag + 1e-6)))

        if sc > best_score[i]:
            best_score[i] = sc
            src = "track" if has_id else "yolo"
            result[i] = Detection(
                rat_idx=i, frame_num=frame_num,
                x=bx, y=by, w=bw, h=bh,
                cx=cx, cy=cy,
                area=bw * bh,
                confidence=round(float(conf), 3),
                source=src,
                track_id=int(tid) if has_id else -1,
            )

    return result


# ─────────────────────────────────────────────────────────────────────
# 9 · Drawing
# ─────────────────────────────────────────────────────────────────────
def draw(frame: np.ndarray, dets: List[Optional[Detection]], rois, wls: Optional[List[int]] = None) -> np.ndarray:
    out = frame.copy()

    for i, (rx, ry, rw, rh) in enumerate(rois):
        cv2.rectangle(out, (rx, ry), (rx + rw, ry + rh), (80, 80, 80), 1)
        if wls and i < len(wls):
            cv2.line(out, (rx, ry + int(wls[i])), (rx + rw, ry + int(wls[i])), (0, 255, 255), 1)

    for det in dets:
        if det is None:
            continue

        base = RAT_COLORS[det.rat_idx % 4]
        if det.source == "freeze":
            color = tuple(int(c * 0.55) for c in base)
            thick = 1
        elif det.source == "lost":
            color = (60, 60, 60)
            thick = 1
        else:
            color = base
            thick = 2

        cv2.rectangle(out, (det.x, det.y), (det.x + det.w, det.y + det.h), color, thick)

        src = "" if det.source in ("yolo", "track", "classic") else f" [{det.source}]"
        txt = f"{RAT_LABELS[det.rat_idx]} {det.confidence:.2f}{src}"
        font, sc = cv2.FONT_HERSHEY_SIMPLEX, 0.45
        (tw, th), _ = cv2.getTextSize(txt, font, sc, 1)
        ly = max(det.y - 5, th + 5)
        cv2.rectangle(out, (det.x, ly - th - 4), (det.x + tw + 4, ly + 2), color, -1)
        cv2.putText(out, txt, (det.x + 2, ly - 2), font, sc, (255, 255, 255), 1, cv2.LINE_AA)
        cv2.circle(out, (int(det.cx), int(det.cy)), 3, color, -1)

    return out


# ─────────────────────────────────────────────────────────────────────
# 10 · Main pipeline
# ─────────────────────────────────────────────────────────────────────
def track_video(
    input_path,
    output_video=None,
    output_json=None,
    layout: str = "auto",
    fps_cap: int = 0,
    show_progress: bool = True,
    model_path: str = DEFAULT_MODEL,
    conf: float = DEFAULT_CONF,
    tracker_yaml: str = "bytetrack.yaml",
    max_freeze_frames: int = MAX_FREEZE,
    stabilize: bool = False,
    device: str = "",
):
    cap = cv2.VideoCapture(str(input_path))
    if not cap.isOpened():
        raise RuntimeError(f"No se puede abrir: {input_path}")

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    fw = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    fh = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    if layout == "auto":
        layout = detect_layout(fw, fh)

    # first frame
    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
    ok, first = cap.read()
    if not ok:
        raise RuntimeError("Video vacío")

    first_gray = cv2.cvtColor(first, cv2.COLOR_BGR2GRAY)
    rois = compute_rois(fw, fh, layout, gray=first_gray)

    # YOLO engine
    yolo = YOLOEngine(model_path=model_path, conf=conf, tracker_yaml=tracker_yaml, device=device)
    use_yolo = yolo.available
    if show_progress:
        print("\n" + "═" * 60)
        print("  FST Rat Tracker v6 — YOLO track + gating físico")
        print("═" * 60)
        print(f"  Video:     {fw}x{fh} @ {fps:.1f} FPS, {total} frames ({total / fps:.1f}s)")
        print(f"  Layout:    {layout}")
        print(f"  ROIs:      {[(r[0], r[0] + r[2]) for r in rois]}")
        print(f"  YOLO:      {'✓' if use_yolo else '✗ (fallback clásico)'}")
        print(f"  Model:     {model_path}")
        print(f"  Tracker:   {tracker_yaml}")
        print(f"  Conf:      {conf}")
        print(f"  Stabilize: {stabilize}")
        print("═" * 60)

    # background model
    bgs = build_bg(cap, rois)

    # stable waterlines
    wl_trackers: List[StableWaterline] = []
    for i, bg in enumerate(bgs):
        if bg is not None:
            src = bg
        else:
            rx, ry, rw, rh = rois[i]
            src = first_gray[ry:ry + rh, rx:rx + rw]
        wl_trackers.append(StableWaterline(_waterline_raw(src), alpha=0.12, max_dpx=6))

    # gates & per-roi state
    gates = [PhysicalGate(r) for r in rois]
    states = [ROIState(max_freeze=max_freeze_frames, decay=FREEZE_DECAY) for _ in rois]

    # stabilizer
    stab = FrameStabilizer() if stabilize else None

    # writer
    writer = None
    if output_video:
        out_fps = fps if fps_cap <= 0 else min(fps, fps_cap)
        writer = cv2.VideoWriter(str(output_video), cv2.VideoWriter_fourcc(*"mp4v"), out_fps, (fw, fh))

    step = max(1, int(fps / fps_cap)) if fps_cap and fps_cap > 0 else 1

    all_dets: List[dict] = []
    stats = {"total": 0, "yolo": [0]*4, "track": [0]*4, "classic": [0]*4, "freeze": [0]*4, "lost": [0]*4, "none": [0]*4}

    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
    fnum = 0
    t0 = time.time()

    last_cur_wl = [0, 0, 0, 0]

    while True:
        ok, frame = cap.read()
        if not ok:
            break
        fnum += 1
        if fnum % step != 0:
            continue
        stats["total"] += 1

        if stab is not None:
            frame = stab.stabilize(frame)

        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

        # update waterlines per ROI
        cur_wl = []
        for i, (rx, ry, rw, rh) in enumerate(rois):
            raw = _waterline_raw(gray[ry:ry + rh, rx:rx + rw])
            cur_wl.append(wl_trackers[i].update(raw))
        last_cur_wl = cur_wl

        # choose conf for recovery
        use_conf = conf
        if use_yolo and any(states[i].needs_recovery for i in range(4)):
            use_conf = min(conf, RECOVERY_CONF)

        # YOLO track/detect
        roi_dets: Dict[int, Optional[Detection]] = {i: None for i in range(4)}
        if use_yolo:
            boxes = yolo.track(frame, conf=use_conf, persist=True)
            roi_dets = assign_to_rois(boxes, rois, gates, cur_wl, states, fnum)

        # classic fallback for missing ROIs (or when YOLO missing)
        for i, (rx, ry, rw, rh) in enumerate(rois):
            if roi_dets[i] is not None:
                continue
            cd = detect_classic_roi(gray[ry:ry + rh, rx:rx + rw], bgs[i], (rx, ry), i, fnum, cur_wl[i])
            if cd is not None and gates[i].check(cd.x, cd.y, cd.w, cd.h, cur_wl[i]):
                roi_dets[i] = cd

        # apply freeze/lost policy
        final_dets: List[Optional[Detection]] = []
        for i in range(4):
            det = roi_dets.get(i)
            st = states[i]

            if det is not None:
                gates[i].commit(det.cx, det.cy)
                st.accept(det)
                final_dets.append(det)
                stats[det.source][i] += 1
                all_dets.append(asdict(det))
            else:
                frozen = st.freeze_det(fnum, i)
                if frozen is None:
                    final_dets.append(None)
                    stats["none"][i] += 1
                else:
                    # Si el bbox congelado ya no pasa gating → no lo uses (evitar congelar basura)
                    # Regla estricta para congelados:
                    # el centro debe estar CLARAMENTE debajo de la waterline (evita bbox "flotando")
                    if frozen.source in ("freeze", "lost"):
                        ry = rois[i][1]
                        if frozen.cy < (ry + cur_wl[i] + 5):
                            st.mark_gating_fail(limit=5)   # más agresivo
                            final_dets.append(None)
                            stats["none"][i] += 1
                            continue
                    if gates[i].check(frozen.x, frozen.y, frozen.w, frozen.h, cur_wl[i]):
                        final_dets.append(frozen)
                        stats[frozen.source][i] += 1
                        all_dets.append(asdict(frozen))
                    else:
                        st.mark_gating_fail(limit=10)
                        final_dets.append(None)
                        stats["none"][i] += 1

                # Tras 2s sin detección → reset de velocidad para re-adquisición
                if st.freeze_count > int(fps * 2):
                    gates[i].reset_velocity()

        # write
        if writer:
            writer.write(draw(frame, final_dets, rois, cur_wl))

        if show_progress and fnum % max(1, int(fps) * 5) == 0:
            pct = fnum / total * 100 if total else 0
            elapsed = time.time() - t0
            spd = stats["total"] / (elapsed or 1)
            print(f"  {fnum}/{total} ({pct:.0f}%) — {elapsed:.1f}s — {spd:.1f} fps")

    cap.release()
    if writer:
        writer.release()

    # json
    if output_json:
        payload = {
            "video": str(input_path),
            "fps": float(fps),
            "frame_size": [fw, fh],
            "layout": layout,
            "model": str(model_path),
            "tracker": tracker_yaml,
            "stabilize": bool(stabilize),
            "conf": float(conf),
            "recovery_conf": float(RECOVERY_CONF),
            "rois": [{"rat_idx": i, "x": r[0], "y": r[1], "w": r[2], "h": r[3], "waterline_y": int(last_cur_wl[i])} for i, r in enumerate(rois)],
            "total_frames_processed": int(stats["total"]),
            "detections": all_dets,
            "stats": stats,
        }
        with open(str(output_json), "w", encoding="utf-8") as f:
            json.dump(payload, f, indent=2)

    if show_progress:
        elapsed = time.time() - t0
        n = stats["total"]
        print("\n" + "═" * 60)
        print(f"  Resumen ({elapsed:.1f}s — {n/(elapsed or 1):.1f} fps procesados)")
        print("═" * 60)
        for i in range(4):
            real = stats["yolo"][i] + stats["track"][i] + stats["classic"][i]
            fr   = stats["freeze"][i]
            lo   = stats["lost"][i]
            nn   = stats["none"][i]
            t = real + fr + lo + nn
            if t == 0:
                print(f"  {RAT_LABELS[i]}: sin datos")
                continue
            print(f"  {RAT_LABELS[i]}: real={real}/{t} ({real/t*100:.0f}%)  freeze={fr}  lost={lo}  none={nn}")
        print("═" * 60 + "\n")

    return stats