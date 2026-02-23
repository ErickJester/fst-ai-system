"""
FST Rat Tracker v6 (hotfix) — Detección y seguimiento de ratas en nado forzado.

Fix aplicado:
  - OpenCV 4.6.0 (tu build) es quisquilloso con el tipo del boundingBox en tracker.init().
    Ahora se fuerza a tuple(int,int,int,int) y, si falla, se intenta tuple(float,...).
    Si ambas fallan, NO revienta el pipeline: simplemente desactiva el tracker.

Meta:
  - No “perder” la caja ni un frame: segmentación + tracking por correlación (CSRT/KCF/MOSSE si existe),
    y si todo falla, se congela el último bbox con confianza degradada.

Va en: backend/pipeline/tracker.py
Imprime: v 1.3.6
"""

import cv2
import numpy as np
import json
import time
from pathlib import Path
from dataclasses import dataclass, asdict
from typing import Optional, Tuple, List
from collections import deque


RAT_COLORS = [(0, 255, 0), (255, 100, 0), (0, 180, 255), (255, 0, 180)]
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


# ═══════════════════════════════════════════════════════════════════════
# Utils
# ═══════════════════════════════════════════════════════════════════════

def _clamp(v: float, lo: float, hi: float) -> float:
    return lo if v < lo else hi if v > hi else v


def _smooth_1d(x: np.ndarray, win: int = 21) -> np.ndarray:
    if win <= 3:
        return x
    k = np.ones(win, dtype=np.float64) / float(win)
    return np.convolve(x.astype(np.float64), k, mode="same")


def _iou(a: Tuple[int, int, int, int], b: Tuple[int, int, int, int]) -> float:
    ax, ay, aw, ah = a
    bx, by, bw, bh = b
    ax2, ay2 = ax + aw, ay + ah
    bx2, by2 = bx + bw, by + bh
    ix1, iy1 = max(ax, bx), max(ay, by)
    ix2, iy2 = min(ax2, bx2), min(ay2, by2)
    iw, ih = max(0, ix2 - ix1), max(0, iy2 - iy1)
    inter = iw * ih
    if inter <= 0:
        return 0.0
    union = aw * ah + bw * bh - inter
    return float(inter) / float(union) if union > 0 else 0.0


def _bbox_center(b: Tuple[int, int, int, int]) -> Tuple[float, float]:
    x, y, w, h = b
    return x + w / 2.0, y + h / 2.0


def _clamp_bbox_to_roi(b: Tuple[int, int, int, int], roi: Tuple[int, int, int, int]) -> Tuple[int, int, int, int]:
    x, y, w, h = b
    rx, ry, rw, rh = roi
    x1 = int(_clamp(x, rx, rx + rw - 1))
    y1 = int(_clamp(y, ry, ry + rh - 1))
    x2 = int(_clamp(x + w, rx + 1, rx + rw))
    y2 = int(_clamp(y + h, ry + 1, ry + rh))
    return x1, y1, max(2, x2 - x1), max(2, y2 - y1)


# ═══════════════════════════════════════════════════════════════════════
# ROIs adaptativas
# ═══════════════════════════════════════════════════════════════════════

def find_cylinder_separators(gray_frame: np.ndarray) -> List[int]:
    h, w = gray_frame.shape[:2]
    band = gray_frame[int(h * 0.3):int(h * 0.7), :]
    cp = np.mean(band, axis=0)
    sm = np.convolve(cp, np.ones(15) / 15, mode="same")
    md = w // 6
    cands = []
    for x in range(md, w - md):
        left = sm[max(0, x - md // 2):x]
        right = sm[x + 1:min(w, x + md // 2 + 1)]
        if len(left) > 0 and len(right) > 0 and sm[x] < np.min(left) and sm[x] < np.min(right):
            cands.append((x, min(np.mean(left) - sm[x], np.mean(right) - sm[x])))
    cands.sort(key=lambda c: c[1], reverse=True)
    seps = sorted([c[0] for c in cands[:3]])
    return seps if len(seps) >= 3 else [w // 4, w // 2, 3 * w // 4]


def compute_rois(frame_w: int, frame_h: int, layout: str = "1x4", gray_frame: Optional[np.ndarray] = None):
    if layout == "1x4" and gray_frame is not None:
        s = find_cylinder_separators(gray_frame)
        return [
            (0, 0, s[0], frame_h),
            (s[0], 0, s[1] - s[0], frame_h),
            (s[1], 0, s[2] - s[1], frame_h),
            (s[2], 0, frame_w - s[2], frame_h),
        ]
    if layout == "1x4":
        q = frame_w // 4
        return [(0, 0, q, frame_h), (q, 0, q, frame_h),
                (2 * q, 0, q, frame_h), (3 * q, 0, frame_w - 3 * q, frame_h)]
    if layout == "2x2":
        hw, hh = frame_w // 2, frame_h // 2
        return [(0, 0, hw, hh), (hw, 0, frame_w - hw, hh),
                (0, hh, hw, frame_h - hh), (hw, hh, frame_w - hw, frame_h - hh)]
    raise ValueError(f"Layout desconocido: {layout}")


def detect_layout(fw: int, fh: int) -> str:
    return "1x4" if fw / fh > 1.2 else "2x2"


# ═══════════════════════════════════════════════════════════════════════
# Waterline bottom-up + estabilidad
# ═══════════════════════════════════════════════════════════════════════

def find_waterline_bottomup(roi_gray: np.ndarray,
                            band_px: int = 12,
                            min_pct: float = 0.20,
                            max_pct: float = 0.80,
                            min_delta: float = 5.0) -> int:
    rh, rw = roi_gray.shape[:2]
    if rh < 60 or rw < 40:
        return int(rh * 0.35)

    profile = np.mean(roi_gray, axis=1).astype(np.float64)
    sm = np.convolve(profile, np.ones(11) / 11, mode="same")
    grad = np.diff(sm)
    y_lo, y_hi = int(rh * min_pct), int(rh * max_pct)

    best_y, best_s = None, 0.0
    for y in range(y_hi, y_lo, -1):
        a0, b1 = max(0, y - band_px), min(rh, y + band_px)
        if y - a0 < 3 or b1 - y < 3:
            continue
        delta = float(np.mean(sm[a0:y])) - float(np.mean(sm[y:b1]))
        if delta < min_delta:
            continue
        gs = grad[max(0, y - 3):min(len(grad), y + 3)]
        edge = -float(np.min(gs)) if len(gs) > 0 else 0.0
        s = delta * 0.6 + edge * 0.4
        if s > best_s:
            best_s = s
            best_y = y
    return int(best_y) if best_y is not None else int(rh * 0.35)


class WaterlineSmoother:
    def __init__(self, init_y: int, alpha: float = 0.3, max_jump_px: int = 24, win: int = 5):
        self.alpha = float(alpha)
        self.max_jump_px = int(max_jump_px)
        self.hist = deque(maxlen=int(win))
        self.value = int(init_y)
        self.hist.append(int(init_y))

    def update(self, y_new: int) -> int:
        y_new = int(y_new)
        y_clamped = int(_clamp(y_new, self.value - self.max_jump_px, self.value + self.max_jump_px))
        self.hist.append(y_clamped)
        y_med = int(np.median(np.array(self.hist, dtype=np.int32)))
        y_ema = int(self.alpha * y_med + (1.0 - self.alpha) * self.value)
        self.value = y_ema
        return self.value


# ═══════════════════════════════════════════════════════════════════════
# Máscara cilíndrica auto-centrada
# ═══════════════════════════════════════════════════════════════════════

def estimate_cylinder_lr(search_gray: np.ndarray) -> Optional[Tuple[int, int]]:
    sh, sw = search_gray.shape[:2]
    if sh < 30 or sw < 40:
        return None

    y0 = int(sh * 0.25)
    y1 = int(sh * 0.95)
    band = search_gray[y0:y1, :]

    blur = cv2.GaussianBlur(band, (5, 5), 0)
    gx = cv2.Sobel(blur, cv2.CV_32F, 1, 0, ksize=3)
    col = np.mean(np.abs(gx), axis=0)
    col = _smooth_1d(col, win=min(31, max(11, (sw // 20) * 2 + 1)))

    pad = max(10, int(sw * 0.06))
    mid = sw // 2

    left_band = col[pad:mid]
    right_band = col[mid:sw - pad]
    if left_band.size < 10 or right_band.size < 10:
        return None

    l = int(np.argmax(left_band) + pad)
    r = int(np.argmax(right_band) + mid)

    if r - l < int(sw * 0.55):
        return None
    return l, r


def make_cylinder_mask(width: int, height: int, margin_pct: float = 0.12,
                       lr: Optional[Tuple[int, int]] = None) -> np.ndarray:
    mask = np.zeros((height, width), dtype=np.uint8)
    if lr is not None:
        l, r = lr
        l = int(_clamp(l, 0, width - 2))
        r = int(_clamp(r, l + 1, width - 1))
        cx = (l + r) // 2
        rad_x = max(12, int((r - l) * 0.5 * (1.0 - margin_pct)))
    else:
        cx = width // 2
        rad_x = int(width * (0.5 - margin_pct))
    cv2.ellipse(mask, (cx, height // 2), (rad_x, height // 2), 0, 0, 360, 255, -1)
    return mask


# ═══════════════════════════════════════════════════════════════════════
# Segmentación → candidato
# ═══════════════════════════════════════════════════════════════════════

def _best_blob_from_mask(mask: np.ndarray,
                         sw: int,
                         sh: int,
                         wl_local: int,
                         min_area: float,
                         max_area: float,
                         prior_local: Optional[Tuple[float, float]] = None) -> Optional[dict]:
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return None

    best = None
    for c in contours:
        area = float(cv2.contourArea(c))
        if area < min_area or area > max_area:
            continue
        x, y, bw, bh = cv2.boundingRect(c)
        bbox_area = float(bw * bh)
        if bbox_area <= 1.0:
            continue
        solidity = area / bbox_area
        if solidity < 0.20:
            continue

        if bw < int(sw * 0.08) or bh < int(sh * 0.10):
            continue

        aspect = (bw / bh) if bh > 0 else 999.0
        if aspect > 4.8:
            continue

        cx, cy = x + bw / 2.0, y + bh / 2.0

        submerged = (y + bh - wl_local) / float(max(1, bh))
        if submerged < 0.45:
            continue

        center = 1.0 - abs(cx - sw / 2.0) / (sw / 2.0)
        center = max(0.0, center)
        prox = max(0.0, 1.0 - abs(cy - wl_local) / (sh * 0.45))

        prior = 0.0
        if prior_local is not None:
            px, py = prior_local
            d = float(np.hypot(cx - px, cy - py))
            prior = max(0.0, 1.0 - d / (max(sw, sh) * 0.32))

        score = area * (0.20 + 0.15 * center + 0.15 * prox + 0.50 * prior) * (0.70 + 0.30 * solidity)

        blob = {"area": area, "x": x, "y": y, "w": bw, "h": bh, "cx": cx, "cy": cy, "score": score}
        if best is None or blob["score"] > best["score"]:
            best = blob
    return best


def detect_rat_in_roi(roi_gray: np.ndarray,
                      bg_model: Optional[np.ndarray],
                      roi_offset: Tuple[int, int],
                      rat_idx: int,
                      frame_num: int,
                      waterline_y: Optional[int] = None,
                      prior_det: Optional[Detection] = None,
                      min_area_ratio: float = 0.003,
                      max_area_ratio: float = 0.35) -> Optional[Detection]:
    rh, rw = roi_gray.shape[:2]
    rx, ry = roi_offset

    if waterline_y is None:
        waterline_y = find_waterline_bottomup(roi_gray)

    hm = int(rh * 0.10)
    crop_top = max(0, waterline_y - hm)
    crop_bot = min(rh, max(int(rh * 0.92), waterline_y + int(rh * 0.62)))
    if crop_bot - crop_top < 20:
        crop_bot = min(rh, crop_top + 20)

    search = roi_gray[crop_top:crop_bot, :]
    sh, sw = search.shape[:2]
    if sh < 15 or sw < 15:
        return None

    sa = float(sw * sh)
    wl_local = int(waterline_y - crop_top)

    blurred = cv2.GaussianBlur(search, (5, 5), 0)

    edge_kill = max(2, int(sw * 0.04))
    blurred[:, :edge_kill] = 0
    blurred[:, sw - edge_kill:] = 0

    wz0 = int(_clamp(wl_local, 0, sh - 1))
    water_zone = blurred[wz0:, :] if wz0 < sh else blurred
    if water_zone.size > 200:
        thr_hi = float(np.percentile(water_zone, 92))
        thr_lo = float(np.percentile(water_zone, 8))
        if thr_hi - thr_lo < 14:
            thr_hi = thr_lo + 14
        thr_hi = max(thr_hi, 55.0)
        thr_lo = min(thr_lo, 170.0)
    else:
        thr_hi, thr_lo = 85.0, 45.0

    fg = None
    if bg_model is not None:
        bg_s = bg_model[crop_top:crop_bot, :]
        if bg_s.shape == search.shape:
            diff = cv2.absdiff(blurred, cv2.GaussianBlur(bg_s, (5, 5), 0))
            _, fg = cv2.threshold(diff, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)

    _, bright = cv2.threshold(blurred, thr_hi, 255, cv2.THRESH_BINARY)
    _, dark = cv2.threshold(blurred, thr_lo, 255, cv2.THRESH_BINARY_INV)

    if fg is not None:
        mask_bright = cv2.bitwise_or(fg, bright)
        mask_dark = cv2.bitwise_or(fg, dark)
    else:
        mask_bright, mask_dark = bright, dark

    lr = estimate_cylinder_lr(blurred)
    cyl = make_cylinder_mask(sw, sh, margin_pct=0.12, lr=lr)
    mask_bright = cv2.bitwise_and(mask_bright, cyl)
    mask_dark = cv2.bitwise_and(mask_dark, cyl)

    cut = max(0, wl_local - int(rh * 0.03))
    mask_bright[:cut, :] = 0
    mask_dark[:cut, :] = 0

    k_c = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (7, 7))
    k_o = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))

    def _morph(m: np.ndarray) -> np.ndarray:
        m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, k_c, iterations=1)
        m = cv2.morphologyEx(m, cv2.MORPH_OPEN, k_o, iterations=2)
        return m

    mask_bright = _morph(mask_bright)
    mask_dark = _morph(mask_dark)

    prior_local = None
    if prior_det is not None:
        px = float(prior_det.cx - rx)
        py = float(prior_det.cy - (ry + crop_top))
        if -sw * 0.25 <= px <= sw * 1.25 and -sh * 0.25 <= py <= sh * 1.25:
            prior_local = (px, py)

    min_area = float(min_area_ratio * sa)
    max_area = float(max_area_ratio * sa)

    best_b = _best_blob_from_mask(mask_bright, sw, sh, wl_local, min_area, max_area, prior_local)
    best_d = _best_blob_from_mask(mask_dark, sw, sh, wl_local, min_area, max_area, prior_local)

    if best_b is not None and best_d is not None:
        best = best_b if best_b["score"] >= best_d["score"] else best_d
    else:
        best = best_b if best_b is not None else best_d

    if best is None:
        return None

    bx, by, bw, bh = int(best["x"]), int(best["y"]), int(best["w"]), int(best["h"])

    px, py = int(bw * 0.06), int(bh * 0.06)
    bx = max(0, bx - px); by = max(0, by - py)
    bw = min(sw - bx, bw + 2 * px); bh = min(sh - by, bh + 2 * py)

    conf = float(best["area"]) / (sa * 0.06) if sa > 0 else 0.0
    conf = float(_clamp(conf, 0.0, 1.0))

    return Detection(
        rat_idx=rat_idx,
        frame_num=frame_num,
        x=int(bx + rx),
        y=int(by + crop_top + ry),
        w=int(bw),
        h=int(bh),
        cx=float(bx + rx + bw / 2.0),
        cy=float(by + crop_top + ry + bh / 2.0),
        area=int(best["area"]),
        confidence=round(conf, 3),
    )


# ═══════════════════════════════════════════════════════════════════════
# Modelo de fondo
# ═══════════════════════════════════════════════════════════════════════

def build_background_model(cap: cv2.VideoCapture, rois, n_frames: int = 60, sample_step: int = 5):
    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
    samples = [[] for _ in rois]
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
            samples[i].append(gray[ry:ry + rh, rx:rx + rw].copy())
        count += 1
    models = [np.median(np.stack(s), axis=0).astype(np.uint8) if s else None for s in samples]
    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
    return models


# ═══════════════════════════════════════════════════════════════════════
# Tracking por correlación
# ═══════════════════════════════════════════════════════════════════════

def _create_opencv_tracker():
    for name in ("TrackerCSRT_create", "TrackerKCF_create", "TrackerMOSSE_create"):
        if hasattr(cv2, name):
            try:
                return getattr(cv2, name)()
            except Exception:
                pass
    if hasattr(cv2, "legacy"):
        for name in ("TrackerCSRT_create", "TrackerKCF_create", "TrackerMOSSE_create"):
            if hasattr(cv2.legacy, name):
                try:
                    return getattr(cv2.legacy, name)()
                except Exception:
                    pass
    return None


class RatCorrelationTrack:
    def __init__(self):
        self.tracker = None
        self.active = False
        self.bbox = None
        self.last_ok = 0

    def init(self, frame_bgr: np.ndarray, bbox: Tuple[int, int, int, int], frame_num: int):
        tr = _create_opencv_tracker()
        bbox_t = (int(bbox[0]), int(bbox[1]), int(bbox[2]), int(bbox[3]))
        if tr is None:
            self.tracker = None
            self.active = False
            self.bbox = bbox_t
            self.last_ok = frame_num
            return False

        ok = False
        try:
            ok = bool(tr.init(frame_bgr, bbox_t))
        except cv2.error:
            try:
                ok = bool(tr.init(frame_bgr, tuple(map(float, bbox_t))))
            except cv2.error:
                ok = False

        self.tracker = tr
        self.active = ok
        self.bbox = bbox_t
        self.last_ok = frame_num if ok else self.last_ok
        return ok

    def update(self, frame_bgr: np.ndarray, roi: Tuple[int, int, int, int], frame_num: int) -> Optional[Tuple[int, int, int, int]]:
        if not self.active or self.tracker is None:
            return None
        try:
            ok, bb = self.tracker.update(frame_bgr)
        except cv2.error:
            return None
        if not ok:
            return None
        x, y, w, h = [int(round(v)) for v in bb]
        x, y, w, h = _clamp_bbox_to_roi((x, y, w, h), roi)
        if w < 10 or h < 10:
            return None
        self.bbox = (x, y, w, h)
        self.last_ok = frame_num
        return self.bbox


class RatState:
    def __init__(self):
        self.last_det: Optional[Detection] = None
        self.corr = RatCorrelationTrack()
        self.freeze_lost = 0


# ═══════════════════════════════════════════════════════════════════════
# Dibujo
# ═══════════════════════════════════════════════════════════════════════

def draw_detections(frame: np.ndarray, detections, rois, waterlines=None) -> np.ndarray:
    out = frame.copy()
    for i, (rx, ry, rw, rh) in enumerate(rois):
        cv2.rectangle(out, (rx, ry), (rx + rw, ry + rh), (80, 80, 80), 1)
        if waterlines and i < len(waterlines):
            cv2.line(out, (rx, ry + int(waterlines[i])), (rx + rw, ry + int(waterlines[i])), (0, 255, 255), 1)

    for det in detections:
        if det is None:
            continue
        color = RAT_COLORS[det.rat_idx % 4]
        label = RAT_LABELS[det.rat_idx % 4]
        thick = 2 if det.confidence > 0.35 else 1
        cv2.rectangle(out, (det.x, det.y), (det.x + det.w, det.y + det.h), color, thick)
        font, sc = cv2.FONT_HERSHEY_SIMPLEX, 0.5
        (tw, th), _ = cv2.getTextSize(label, font, sc, 1)
        ly = max(det.y - 5, th + 5)
        cv2.rectangle(out, (det.x, ly - th - 4), (det.x + tw + 4, ly + 2), color, -1)
        cv2.putText(out, label, (det.x + 2, ly - 2), font, sc, (255, 255, 255), 1, cv2.LINE_AA)
        cv2.circle(out, (int(det.cx), int(det.cy)), 3, color, -1)
    return out


# ═══════════════════════════════════════════════════════════════════════
# Pipeline de video
# ═══════════════════════════════════════════════════════════════════════

def track_video(input_path, output_video=None, output_json=None,
                layout="auto", fps_cap: int = 0, show_progress: bool = True):
    cap = cv2.VideoCapture(input_path)
    if not cap.isOpened():
        raise RuntimeError(f"No se puede abrir: {input_path}")

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    fw, fh = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)), int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    if show_progress:
        print("v 1.3.7")
        print(f"Video: {fw}x{fh} @ {fps:.1f} FPS, {total} frames ({total / fps:.1f}s)")

    if layout == "auto":
        layout = detect_layout(fw, fh)
        if show_progress:
            print(f"Layout: {layout}")

    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
    ok, first = cap.read()
    if not ok:
        raise RuntimeError("Video vacío")
    first_gray = cv2.cvtColor(first, cv2.COLOR_BGR2GRAY)
    rois = compute_rois(fw, fh, layout, gray_frame=first_gray)
    if show_progress:
        print(f"ROIs: {[(r[0], r[2]) for r in rois]}")

    if show_progress:
        print("Construyendo modelo de fondo…")
    bg_models = build_background_model(cap, rois)

    init_wl = []
    for i, bg in enumerate(bg_models):
        src = bg if bg is not None else first_gray[rois[i][1]:rois[i][1] + rois[i][3],
                                                    rois[i][0]:rois[i][0] + rois[i][2]]
        init_wl.append(find_waterline_bottomup(src))
    if show_progress:
        print(f"Waterlines: {init_wl}")

    wl_smoothers = [WaterlineSmoother(w, alpha=0.3, max_jump_px=24, win=5) for w in init_wl]

    writer = None
    if output_video:
        out_fps = fps if fps_cap <= 0 else min(fps, fps_cap)
        writer = cv2.VideoWriter(output_video, cv2.VideoWriter_fourcc(*"mp4v"), out_fps, (fw, fh))

    step = max(1, int(fps / fps_cap)) if fps_cap > 0 else 1
    states = [RatState() for _ in range(4)]
    all_dets = []
    stats = {"total_frames": 0, "detected": [0] * 4, "lost": [0] * 4, "from_tracker": [0] * 4}

    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
    frame_num, t0 = 0, time.time()

    while True:
        ok, frame = cap.read()
        if not ok:
            break
        frame_num += 1
        if frame_num % step != 0:
            continue
        stats["total_frames"] += 1
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

        current_wl = []
        for i, (rx, ry, rw, rh) in enumerate(rois):
            wl_new = find_waterline_bottomup(gray[ry:ry + rh, rx:rx + rw])
            current_wl.append(wl_smoothers[i].update(wl_new))

        dets_out = []
        for i, roi in enumerate(rois):
            rx, ry, rw, rh = roi
            st = states[i]
            prior = st.last_det

            seg = detect_rat_in_roi(
                gray[ry:ry + rh, rx:rx + rw],
                bg_models[i],
                (rx, ry),
                i,
                frame_num,
                waterline_y=current_wl[i],
                prior_det=prior,
            )

            tr_bbox = st.corr.update(frame, roi, frame_num)

            accept_seg = False
            if seg is not None:
                seg_bbox = (seg.x, seg.y, seg.w, seg.h)
                if st.last_det is None and tr_bbox is None:
                    accept_seg = True
                else:
                    ref = tr_bbox if tr_bbox is not None else (st.last_det.x, st.last_det.y, st.last_det.w, st.last_det.h)
                    iou = _iou(seg_bbox, ref)
                    cx1, cy1 = _bbox_center(seg_bbox)
                    cx2, cy2 = _bbox_center(ref)
                    dist = float(np.hypot(cx1 - cx2, cy1 - cy2))
                    gate = max(ref[2], ref[3]) * 1.6
                    if iou >= 0.08 or dist <= gate:
                        accept_seg = True
                    else:
                        accept_seg = seg.confidence >= 0.60

            final_det = None

            if accept_seg and seg is not None:
                st.corr.init(frame, (seg.x, seg.y, seg.w, seg.h), frame_num)
                st.last_det = seg
                st.freeze_lost = 0
                final_det = seg
                stats["detected"][i] += 1
                all_dets.append(asdict(seg))
            else:
                if tr_bbox is not None:
                    x, y, w, h = tr_bbox
                    conf = 0.28
                    if st.last_det is not None:
                        prev_bbox = (st.last_det.x, st.last_det.y, st.last_det.w, st.last_det.h)
                        conf += 0.30 * _iou(tr_bbox, prev_bbox)
                        conf = float(_clamp(conf, 0.18, 0.55))
                    det = Detection(
                        rat_idx=i, frame_num=frame_num,
                        x=x, y=y, w=w, h=h,
                        cx=x + w / 2.0, cy=y + h / 2.0,
                        area=int(w * h),
                        confidence=round(conf, 3),
                    )
                    st.last_det = det
                    st.freeze_lost = 0
                    final_det = det
                    stats["from_tracker"][i] += 1
                    all_dets.append(asdict(det))
                else:
                    if st.last_det is not None:
                        st.freeze_lost += 1
                        fade = max(0.05, 1.0 - st.freeze_lost / max(1, int(fps * 1.2)))
                        det = Detection(
                            rat_idx=i, frame_num=frame_num,
                            x=st.last_det.x, y=st.last_det.y, w=st.last_det.w, h=st.last_det.h,
                            cx=st.last_det.cx, cy=st.last_det.cy,
                            area=st.last_det.area,
                            confidence=round(float(_clamp(st.last_det.confidence * fade, 0.05, 0.25)), 3),
                        )
                        st.last_det = det
                        final_det = det
                        stats["lost"][i] += 1
                        all_dets.append(asdict(det))
                    else:
                        final_det = None
                        stats["lost"][i] += 1

            dets_out.append(final_det)

        if writer:
            writer.write(draw_detections(frame, dets_out, rois, current_wl))

        if show_progress and frame_num % max(1, int(fps) * 5) == 0:
            pct = frame_num / total * 100 if total else 0
            print(f"  Frame {frame_num}/{total} ({pct:.0f}%) — {time.time() - t0:.1f}s")

    cap.release()
    if writer:
        writer.release()
        if show_progress:
            print(f"Video anotado: {output_video}")

    if output_json:
        payload = {
            "video": str(input_path),
            "fps": float(fps),
            "frame_size": [fw, fh],
            "layout": layout,
            "rois": [
                {"rat_idx": i, "x": r[0], "y": r[1], "w": r[2], "h": r[3], "waterline_y": int(wl_smoothers[i].value)}
                for i, r in enumerate(rois)
            ],
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
            t = stats["detected"][i] + stats["lost"][i] + stats["from_tracker"][i]
            if t:
                print(f"  {RAT_LABELS[i]}: seg={stats['detected'][i]}, trk={stats['from_tracker'][i]}, freeze={stats['lost'][i]} (frames={t})")
    return stats


def main():
    import argparse
    p = argparse.ArgumentParser(description="FST Rat Tracker v6")
    p.add_argument("--input", "-i", required=True)
    p.add_argument("--output", "-o", default=None)
    p.add_argument("--data", "-d", default=None)
    p.add_argument("--layout", "-l", default="auto", choices=["auto", "1x4", "2x2"])
    p.add_argument("--fps-cap", type=int, default=0)
    args = p.parse_args()
    inp = Path(args.input)
    track_video(
        args.input,
        args.output or str(inp.parent / f"{inp.stem}_tracked{inp.suffix}"),
        args.data or str(inp.parent / f"{inp.stem}_tracking.json"),
        args.layout,
        args.fps_cap,
    )


if __name__ == "__main__":
    main()
