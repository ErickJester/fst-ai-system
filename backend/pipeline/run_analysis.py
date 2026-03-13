"""
Pipeline de análisis FST.

Usa el tracker YOLO v6 para localizar cada rata y clasifica la conducta
(nado, inmovilidad, escape) por ventana temporal de 1 segundo.

Clasificación (por ventana):
  motion_mean < immobile_thr          → Inmovilidad
  motion_mean >= immobile_thr
    AND y_norm < climb_y_thr (arriba) → Escape / escalamiento
  resto                               → Nado
"""

import cv2
import numpy as np
import json
import time
from dataclasses import dataclass, asdict
from typing import Optional, List

from .tracker import (
    YOLOEngine,
    PhysicalGate,
    ROIState,
    StableWaterline,
    FrameStabilizer,
    compute_rois,
    detect_layout,
    build_bg,
    assign_to_rois,
    draw,
    detect_classic_roi,
    _waterline_raw,
    DEFAULT_MODEL,
    DEFAULT_CONF,
    MAX_FREEZE,
    FREEZE_DECAY,
    RECOVERY_CONF,
    RAT_COLORS,
)

# Colores y etiquetas de conducta (BGR)
_BEHAVIOR_COLOR = {
    "swim":      (255, 200,   0),   # azul-cian
    "immobile":  ( 50,  50, 200),   # rojo oscuro
    "escape":    (  0, 200, 255),   # amarillo
    "unknown":   (120, 120, 120),
}
_BEHAVIOR_LABEL = {
    "swim":      "NADO",
    "immobile":  "INMOVIL",
    "escape":    "ESCAPE",
    "unknown":   "?",
}


@dataclass
class RatSummary:
    rat_idx: int
    swim_s: float
    immobile_s: float
    escape_s: float


def _draw_behavior(
    frame: np.ndarray,
    rois,
    behaviors: List[str],
) -> np.ndarray:
    """Superpone la etiqueta de conducta actual sobre cada ROI."""
    font = cv2.FONT_HERSHEY_SIMPLEX
    for i, (rx, ry, rw, rh) in enumerate(rois):
        beh = behaviors[i] if i < len(behaviors) else "unknown"
        color = _BEHAVIOR_COLOR.get(beh, _BEHAVIOR_COLOR["unknown"])
        label = _BEHAVIOR_LABEL.get(beh, "?")

        scale = max(0.5, min(rw / 220, 1.0))
        thick = 2 if scale >= 0.7 else 1
        (tw, th), _ = cv2.getTextSize(label, font, scale, thick)

        # fondo semitransparente en la esquina inferior del ROI
        bx1 = rx + 4
        by2 = ry + rh - 6
        by1 = by2 - th - 6
        bx2 = bx1 + tw + 8

        overlay = frame.copy()
        cv2.rectangle(overlay, (bx1, by1), (bx2, by2), color, -1)
        cv2.addWeighted(overlay, 0.55, frame, 0.45, 0, frame)

        cv2.putText(frame, label, (bx1 + 4, by2 - 4), font, scale,
                    (255, 255, 255), thick, cv2.LINE_AA)
    return frame


def run_analysis(
    video_path: str,
    seconds_per_window: float = 1.0,
    fps_cap: int = 0,
    output_video: Optional[str] = None,
    output_json: Optional[str] = None,
    immobile_thr: float = 5.5,       # px promedio de diff dentro del bbox; < 5.5 = inmóvil
    climb_aspect_thr: float = 1.6,  # h/w del bbox; > 1.6 = bbox vertical = escape (vista lateral)
    # tracker params
    model_path: str = DEFAULT_MODEL,
    conf: float = DEFAULT_CONF,
    tracker_yaml: str = "bytetrack.yaml",
    layout: str = "auto",
    stabilize: bool = False,
    skip_frames: int = 0,
    warmup_frames: int = 0,
    max_freeze_frames: int = MAX_FREEZE,
    device: str = "",
    show_progress: bool = False,
) -> List[RatSummary]:
    """
    Analiza un video FST: tracking YOLO + clasificación de conducta.

    Retorna una lista de RatSummary con segundos de nado, inmovilidad y escape
    por cada rata detectada.

    Video anotado: bounding boxes + etiqueta de conducta de la última ventana.
    JSON: detecciones con campo `behavior` + bloque `behavior_summary`.
    """
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise RuntimeError(f"Cannot open video: {video_path}")

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    fw = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    fh = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    if layout == "auto":
        layout = detect_layout(fw, fh)

    # primer frame para ROIs adaptativas
    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
    ok, first = cap.read()
    if not ok:
        raise RuntimeError("Video vacío")
    first_gray = cv2.cvtColor(first, cv2.COLOR_BGR2GRAY)
    rois = compute_rois(fw, fh, layout, gray=first_gray)
    n_rats = len(rois)

    # YOLO engine
    yolo = YOLOEngine(model_path=model_path, conf=conf, tracker_yaml=tracker_yaml, device=device)
    use_yolo = yolo.available

    # background model + waterlines
    bgs = build_bg(cap, rois)
    wl_trackers: List[StableWaterline] = []
    for i, bg in enumerate(bgs):
        src = bg if bg is not None else first_gray[
            rois[i][1]:rois[i][1] + rois[i][3],
            rois[i][0]:rois[i][0] + rois[i][2],
        ]
        wl_trackers.append(StableWaterline(_waterline_raw(src), alpha=0.12, max_dpx=6))

    gates  = [PhysicalGate(r) for r in rois]
    states = [ROIState(max_freeze=max_freeze_frames, decay=FREEZE_DECAY) for _ in rois]
    stab   = FrameStabilizer() if stabilize else None

    step = max(1, int(fps / fps_cap)) if fps_cap and fps_cap > 0 else 1

    writer = None
    if output_video:
        out_fps = fps if fps_cap <= 0 else min(fps, fps_cap)
        writer = cv2.VideoWriter(
            str(output_video), cv2.VideoWriter_fourcc(*"mp4v"), out_fps, (fw, fh)
        )

    # ── estado de clasificación de conducta ───────────────────────────
    win_target    = max(int(round(seconds_per_window * fps / step)), 1)
    motion_acc    = [0.0] * n_rats
    aspect_acc    = [0.0] * n_rats   # acumula h/w del bbox (vista lateral)
    frames_in_win = 0
    totals        = [{"swim": 0.0, "imm": 0.0, "esc": 0.0} for _ in range(n_rats)]

    # conducta actual por rata (se actualiza al cerrar cada ventana)
    current_behavior: List[str] = ["unknown"] * n_rats

    all_dets: List[dict] = []
    behavior_windows: List[dict] = []   # para el JSON

    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    if show_progress:
        print(f"  Frames totales: {total_frames}  ({total_frames/fps:.1f}s)")
        print(f"  Step:           {step}  (procesando 1 de cada {step} frames)")
        print("═" * 50)

    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
    fnum      = 0
    processed = 0
    prev_gray = first_gray.copy()
    t0 = time.time()
    print_every = max(1, int(fps * 5 / step))   # cada ~5 s de video

    while True:
        ok, frame = cap.read()
        if not ok:
            break
        fnum += 1

        if skip_frames and fnum <= int(skip_frames):
            continue
        if fnum % step != 0:
            continue

        processed += 1
        is_warmup = bool(warmup_frames and processed <= int(warmup_frames))

        if stab is not None:
            frame = stab.stabilize(frame)

        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        diff = cv2.absdiff(gray, prev_gray)
        prev_gray = gray

        # waterlines
        cur_wl = []
        for i, (rx, ry, rw, rh) in enumerate(rois):
            raw = _waterline_raw(gray[ry:ry + rh, rx:rx + rw])
            cur_wl.append(wl_trackers[i].update(raw))

        # YOLO
        use_conf = conf
        if is_warmup:
            use_conf = min(use_conf, 0.10)
        if use_yolo and any(states[i].needs_recovery for i in range(n_rats)):
            use_conf = min(use_conf, RECOVERY_CONF)

        roi_dets = {i: None for i in range(n_rats)}
        if use_yolo:
            boxes    = yolo.track(frame, conf=use_conf, persist=True)
            roi_dets = assign_to_rois(boxes, rois, gates, cur_wl, states, fnum, warmup=is_warmup)

        # fallback clásico
        for i, (rx, ry, rw, rh) in enumerate(rois):
            if roi_dets[i] is not None:
                continue
            cd = detect_classic_roi(
                gray[ry:ry + rh, rx:rx + rw], bgs[i], (rx, ry), i, fnum, cur_wl[i]
            )
            if cd is not None and gates[i].check(cd.x, cd.y, cd.w, cd.h, cur_wl[i], warmup=is_warmup):
                roi_dets[i] = cd

        # freeze / lost
        final_dets: List = []
        for i in range(n_rats):
            det = roi_dets.get(i)
            st  = states[i]
            if det is not None:
                gates[i].commit(det.cx, det.cy)
                st.accept(det)
                final_dets.append(det)
                d = asdict(det)
                d["behavior"] = current_behavior[i]
                all_dets.append(d)
            else:
                frozen = st.freeze_det(fnum, i)
                if frozen is None:
                    final_dets.append(None)
                else:
                    ry_roi = rois[i][1]
                    if frozen.source in ("freeze", "lost") and frozen.cy < (ry_roi + cur_wl[i] + 5):
                        st.mark_gating_fail(limit=5)
                        final_dets.append(None)
                        continue
                    if gates[i].check(frozen.x, frozen.y, frozen.w, frozen.h, cur_wl[i], warmup=is_warmup):
                        final_dets.append(frozen)
                        d = asdict(frozen)
                        d["behavior"] = current_behavior[i]
                        all_dets.append(d)
                    else:
                        st.mark_gating_fail(limit=10)
                        final_dets.append(None)

                if st.freeze_count > int(fps * 2):
                    gates[i].reset_velocity()

        # ── clasificación de conducta (vista lateral) ─────────────────
        for i, (rx, ry, rw, rh) in enumerate(rois):
            det = final_dets[i] if i < len(final_dets) else None

            # motion dentro del bbox de la rata (no del ROI completo)
            # → evita dilución por el área de agua/fondo sin movimiento
            if det is not None and det.w > 5 and det.h > 5:
                bx1 = max(0, det.x)
                by1 = max(0, det.y)
                bx2 = min(diff.shape[1], det.x + det.w)
                by2 = min(diff.shape[0], det.y + det.h)
                bbox_diff = diff[by1:by2, bx1:bx2]
                motion = float(np.mean(bbox_diff)) if bbox_diff.size > 0 else 0.0
            else:
                # sin detección: caer back al ROI completo (señal más débil)
                roi_diff = diff[ry:ry + rh, rx:rx + rw]
                motion   = float(np.mean(roi_diff))
            motion_acc[i] += motion

            # aspect ratio h/w del bbox: >1 = vertical (escalamiento), <1 = horizontal
            if det is not None and det.w > 0:
                aspect = det.h / det.w
            else:
                aspect = 1.0   # neutro si no hay detección
            aspect_acc[i] += aspect

        frames_in_win += 1
        if frames_in_win >= win_target:
            seconds   = frames_in_win * step / fps
            win_start = (processed - frames_in_win) * step / fps
            win_entry: dict = {
                "t_start": round(win_start, 3),
                "t_end":   round(win_start + seconds, 3),
                "rats":    {},
            }
            for i in range(n_rats):
                m  = motion_acc[i] / frames_in_win
                ar = aspect_acc[i] / frames_in_win
                if m < immobile_thr:
                    beh = "immobile"
                    totals[i]["imm"] += seconds
                elif ar > climb_aspect_thr:
                    beh = "escape"
                    totals[i]["esc"] += seconds
                else:
                    beh = "swim"
                    totals[i]["swim"] += seconds

                current_behavior[i] = beh
                win_entry["rats"][str(i)] = {
                    "behavior":     beh,
                    "motion":       round(m, 4),
                    "aspect_ratio": round(ar, 4),   # h/w promedio del bbox en la ventana
                }

            behavior_windows.append(win_entry)
            motion_acc    = [0.0] * n_rats
            aspect_acc    = [0.0] * n_rats
            frames_in_win = 0

        # ── progreso ──────────────────────────────────────────────────
        if show_progress and processed % print_every == 0:
            elapsed = time.time() - t0
            pct     = fnum / total_frames * 100 if total_frames else 0
            speed   = processed / (elapsed or 1)
            eta     = (total_frames - fnum) / (speed * step or 1)
            beh_str = "  ".join(
                f"R{i}:{_BEHAVIOR_LABEL.get(current_behavior[i], '?')}"
                for i in range(n_rats)
            )
            print(f"  {fnum}/{total_frames} ({pct:.0f}%) — {elapsed:.0f}s — ETA {eta:.0f}s — {beh_str}")

        # ── video anotado ──────────────────────────────────────────────
        if writer:
            out_frame = draw(frame, final_dets, rois, cur_wl)
            out_frame = _draw_behavior(out_frame, rois, current_behavior)
            writer.write(out_frame)

    cap.release()
    if writer:
        writer.release()

    if output_json:
        behavior_summary = [
            {
                "rat_idx":     i,
                "swim_s":      round(totals[i]["swim"], 3),
                "immobile_s":  round(totals[i]["imm"],  3),
                "escape_s":    round(totals[i]["esc"],  3),
            }
            for i in range(n_rats)
        ]
        payload = {
            "video":                  str(video_path),
            "fps":                    float(fps),
            "frame_size":             [fw, fh],
            "layout":                 layout,
            "rois": [
                {"rat_idx": i, "x": r[0], "y": r[1], "w": r[2], "h": r[3]}
                for i, r in enumerate(rois)
            ],
            "total_frames_processed": processed,
            "behavior_summary":       behavior_summary,
            "behavior_windows":       behavior_windows,
            "detections":             all_dets,
        }
        with open(str(output_json), "w", encoding="utf-8") as f:
            json.dump(payload, f, indent=2)

    return [
        RatSummary(i, totals[i]["swim"], totals[i]["imm"], totals[i]["esc"])
        for i in range(n_rats)
    ]
