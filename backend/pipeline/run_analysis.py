"""
Pipeline de análisis FST.

Usa el tracker YOLO para localizar cada rata y clasifica la conducta
(nado, inmovilidad, escape) por ventana temporal de 1 segundo.

Clasificación (por ventana) — v1.4.1:
  escape_shape (aspect > thr AND motion ≥ thr) → Escape (postura vertical + movimiento)
  [escape_top desactivado: waterline incorrecta en vista lateral genera falsos positivos]
  motion < thr AND disp < thr AND pos_std < thr → Inmovilidad sostenida
  resto                                        → Nado
"""

VERSION = "v1.4.5"

import cv2
import numpy as np
import json
import time
from dataclasses import dataclass, asdict
from typing import Optional, List

try:
    from .classifier import FSTClassifier
    _CNN_AVAILABLE = True
except ImportError:
    _CNN_AVAILABLE = False

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


def _draw_progress_overlay(frame: np.ndarray, fnum: int, total: int,
                           elapsed: float, step: int) -> np.ndarray:
    """Dibuja una barra de progreso en la parte superior del frame."""
    h, w = frame.shape[:2]
    pct = fnum / total if total else 0

    bar_h  = 28
    bar_x1 = 8
    bar_x2 = w - 8
    bar_y1 = 6
    bar_y2 = bar_y1 + bar_h

    # fondo oscuro semitransparente
    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (w, bar_y2 + 8), (0, 0, 0), -1)
    cv2.addWeighted(overlay, 0.55, frame, 0.45, 0, frame)

    # barra de relleno
    fill_x2 = bar_x1 + int((bar_x2 - bar_x1) * pct)
    cv2.rectangle(frame, (bar_x1, bar_y1), (bar_x2, bar_y2), (60, 60, 60), -1)
    if fill_x2 > bar_x1:
        cv2.rectangle(frame, (bar_x1, bar_y1), (fill_x2, bar_y2), (0, 200, 100), -1)
    cv2.rectangle(frame, (bar_x1, bar_y1), (bar_x2, bar_y2), (180, 180, 180), 1)

    # texto izquierda: "frame / total  (pct%)  elapsed  ETA"
    speed = (fnum / step) / (elapsed or 1)
    eta   = (total - fnum) / (speed * step or 1)
    txt   = (f"Frame {fnum}/{total}  ({pct*100:.0f}%)"
             f"  {elapsed:.0f}s  ETA {eta:.0f}s")
    font  = cv2.FONT_HERSHEY_SIMPLEX
    cv2.putText(frame, txt, (bar_x1 + 4, bar_y2 - 6),
                font, 0.45, (255, 255, 255), 1, cv2.LINE_AA)
    # versión en la esquina derecha
    (vw, _), _ = cv2.getTextSize(VERSION, font, 0.4, 1)
    cv2.putText(frame, VERSION, (bar_x2 - vw - 4, bar_y2 - 6),
                font, 0.4, (180, 180, 180), 1, cv2.LINE_AA)
    return frame


def run_analysis(
    video_path: str,
    seconds_per_window: float = 1.0,
    fps_cap: int = 0,
    output_video: Optional[str] = None,
    output_json: Optional[str] = None,
    immobile_thr: float = 6.5,        # px promedio de diff dentro del bbox; < = inmóvil
    climb_aspect_thr: float = 1.6,   # h/w del bbox; > 1.6 = bbox vertical = escape
    disp_thr: float = 8.0,           # px/frame de desplazamiento del centro; < = inmóvil
    escape_top_thr: float = 0.08,    # fracción del ROI; si top del bbox < esta dist del waterline = escape
    swim_width_thr: float = 0.35,    # fracción del ancho del ROI; > = bbox "gordo" = señal de nado
    pos_std_thr: float = 20.0,       # px; dispersión espacial del centro en la ventana; < = inmóvil sostenida
    # CNN classifier params
    use_cnn: bool = True,
    cnn_primary_weights: str = "weights/fst_resnet18.pt",
    cnn_fallback_weights: str = "weights/fst_resnet50.pt",
    cnn_conf_thr: float = 0.65,
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
    show_window: bool = False,
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

    # CNN classifier (opcional — si no hay pesos, recae en heurística)
    clf = None
    if use_cnn and _CNN_AVAILABLE:
        clf = FSTClassifier(
            primary_weights=cnn_primary_weights,
            fallback_weights=cnn_fallback_weights,
            primary_conf_thr=cnn_conf_thr,
            device=device,
        )
        if not clf.is_available:
            clf = None

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

    WIN_NAME = "FST analysis"
    if show_window:
        cv2.namedWindow(WIN_NAME, cv2.WINDOW_NORMAL)
        cv2.resizeWindow(WIN_NAME, min(fw, 1280), min(fh, 720))

    # ── estado de clasificación de conducta ───────────────────────────
    win_target      = max(int(round(seconds_per_window * fps / step)), 1)
    cnn_votes: List[List[str]] = [[] for _ in range(n_rats)]   # votos CNN por ventana
    motion_acc      = [0.0] * n_rats
    aspect_acc      = [0.0] * n_rats   # h/w del bbox
    disp_acc        = [0.0] * n_rats   # desplazamiento del centro (px/frame)
    top_dist_acc    = [0.0] * n_rats   # dist. normalizada del top del bbox al waterline (escape)
    width_acc       = [0.0] * n_rats   # ancho normalizado del bbox (nado)
    depth_from_max_acc = [0.0] * n_rats  # qué tan abajo de la posición más alta está (inmov.)
    # posiciones del centro dentro de la ventana → dispersión espacial para inmovilidad
    cx_win: List[List[float]] = [[] for _ in range(n_rats)]
    cy_win: List[List[float]] = [[] for _ in range(n_rats)]
    frames_in_win   = 0
    totals          = [{"swim": 0.0, "imm": 0.0, "esc": 0.0} for _ in range(n_rats)]

    # centro del bbox en el frame anterior (para calcular desplazamiento)
    prev_cx: List[Optional[float]] = [None] * n_rats
    prev_cy: List[Optional[float]] = [None] * n_rats

    # posición más alta (min det.y) vista por cada rata durante todo el video
    # → referencia para detectar inmovilidad (bbox "baja un poco de su máximo")
    min_det_y: List[Optional[float]] = [None] * n_rats

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

        # ── votos CNN por frame ────────────────────────────────────────
        if clf is not None:
            for i in range(n_rats):
                det = final_dets[i] if i < len(final_dets) else None
                result = clf.classify_frame(frame, det)
                if result.label != "unknown":
                    cnn_votes[i].append(result.label)

        # ── acumulación de features por frame ─────────────────────────
        for i, (rx, ry, rw, rh) in enumerate(rois):
            det = final_dets[i] if i < len(final_dets) else None

            # 1. motion dentro del bbox (evita dilución por área de agua/fondo)
            if det is not None and det.w > 5 and det.h > 5:
                bx1 = max(0, det.x)
                by1 = max(0, det.y)
                bx2 = min(diff.shape[1], det.x + det.w)
                by2 = min(diff.shape[0], det.y + det.h)
                bbox_diff = diff[by1:by2, bx1:bx2]
                motion = float(np.mean(bbox_diff)) if bbox_diff.size > 0 else 0.0
            else:
                roi_diff = diff[ry:ry + rh, rx:rx + rw]
                motion   = float(np.mean(roi_diff))
            motion_acc[i] += motion

            # 2. aspect ratio h/w: >1 = postura vertical, <1 = horizontal
            if det is not None and det.w > 0:
                aspect = det.h / det.w
            else:
                aspect = 1.0
            aspect_acc[i] += aspect

            # 3. desplazamiento del centro entre frames consecutivos (px/frame)
            if det is not None:
                cx, cy = float(det.cx), float(det.cy)
                if prev_cx[i] is not None:
                    disp = ((cx - prev_cx[i]) ** 2 + (cy - prev_cy[i]) ** 2) ** 0.5
                else:
                    disp = 0.0
                prev_cx[i], prev_cy[i] = cx, cy
            else:
                disp = 0.0
            disp_acc[i] += disp

            # 3b. acumular posiciones del centro dentro de la ventana
            #     → se usará para calcular dispersión espacial (inmovilidad sostenida)
            if det is not None:
                cx_win[i].append(float(det.cx))
                cy_win[i].append(float(det.cy))

            # 4. distancia normalizada del borde superior del bbox al waterline
            #    → cercana a 0: el bbox está "pegado" al agua = la rata saca el cuerpo arriba
            #    Modelo entrenado solo desde el agua hacia abajo, así que si sube el cuerpo
            #    el bbox queda cortado justo en el waterline → señal de escape
            wl_abs = ry + cur_wl[i]           # waterline en coordenadas absolutas del frame
            if det is not None:
                top_dist_px   = det.y - wl_abs
                top_dist_norm = top_dist_px / rh
            else:
                top_dist_norm = 1.0           # neutro: sin detección asumimos lejos del tope
            top_dist_acc[i] += top_dist_norm

            # 5. ancho normalizado del bbox relativo al ROI
            #    → bbox "gordo" (> swim_width_thr) indica postura horizontal = nado activo
            if det is not None and rw > 0:
                width_norm = det.w / rw
            else:
                width_norm = 0.0
            width_acc[i] += width_norm

            # 6. profundidad desde la posición más alta vista por esta rata
            #    → rata inmóvil: bbox estable, apenas por debajo de su máximo histórico
            #    → rata nadando: bbox sube/baja activamente, valor variable
            if det is not None:
                # actualizar mínimo de det.y (posición más arriba = y más pequeño)
                if min_det_y[i] is None or det.y < min_det_y[i]:
                    min_det_y[i] = float(det.y)
                depth_from_max = (det.y - min_det_y[i]) / rh  # normalizado por altura del ROI
            else:
                depth_from_max = 0.0
            depth_from_max_acc[i] += depth_from_max

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
                m         = motion_acc[i]         / frames_in_win
                ar        = aspect_acc[i]         / frames_in_win
                disp      = disp_acc[i]           / frames_in_win
                top_dist  = top_dist_acc[i]       / frames_in_win
                width_n   = width_acc[i]          / frames_in_win
                depth_max = depth_from_max_acc[i] / frames_in_win

                # dispersión espacial del centro a lo largo de la ventana
                # mide si el bbox se quedó en el mismo lugar durante todo el segundo
                if len(cx_win[i]) >= 2:
                    pos_std = float(
                        (np.var(cx_win[i]) + np.var(cy_win[i])) ** 0.5
                    )
                else:
                    pos_std = 0.0

                # ── CLASIFICACIÓN ──────────────────────────────────────
                # CNN (si hay pesos): voto mayoritario de los frames de la ventana
                # Heurística: árbol de decisión basado en features agregadas
                if clf is not None and len(cnn_votes[i]) > 0:
                    from collections import Counter
                    beh       = Counter(cnn_votes[i]).most_common(1)[0][0]
                    classifier_source = "cnn"
                else:
                    # escape_top (top_dist_norm < thr) desactivado: la waterline en videos
                    # de vista lateral se detecta incorrectamente (agarra el borde del cilindro),
                    # lo que hace que top_dist_norm sea ~0 para todos los frames → falsos positivos.
                    # top_dist_norm se conserva en el JSON para análisis futuro.
                    escape_shape = ar > climb_aspect_thr and m >= immobile_thr
                    if escape_shape:
                        beh = "escape"
                    elif m < immobile_thr and disp < disp_thr and pos_std < pos_std_thr:
                        beh = "immobile"
                    else:
                        beh = "swim"
                    classifier_source = "heuristic"

                if beh == "escape":
                    totals[i]["esc"] += seconds
                elif beh == "immobile":
                    totals[i]["imm"] += seconds
                else:
                    totals[i]["swim"] += seconds

                current_behavior[i] = beh
                win_entry["rats"][str(i)] = {
                    "behavior":      beh,
                    "classifier":    classifier_source,
                    "motion":        round(m, 4),
                    "aspect_ratio":  round(ar, 4),
                    "displacement":  round(disp, 4),
                    "pos_std":       round(pos_std, 4),    # dispersión espacial; bajo = inmóvil sostenida
                    "top_dist_norm": round(top_dist, 4),   # ~0 = bbox en waterline = escape
                    "width_norm":    round(width_n, 4),    # alto = bbox gordo = nado
                    "depth_from_max":round(depth_max, 4),  # bajo = cerca del tope histórico
                }

            behavior_windows.append(win_entry)
            motion_acc         = [0.0] * n_rats
            aspect_acc         = [0.0] * n_rats
            disp_acc           = [0.0] * n_rats
            top_dist_acc       = [0.0] * n_rats
            width_acc          = [0.0] * n_rats
            depth_from_max_acc = [0.0] * n_rats
            cx_win             = [[] for _ in range(n_rats)]
            cy_win             = [[] for _ in range(n_rats)]
            cnn_votes          = [[] for _ in range(n_rats)]
            frames_in_win      = 0

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

        # ── video anotado / ventana en vivo ───────────────────────────
        if writer or show_window:
            out_frame = draw(frame, final_dets, rois, cur_wl)
            out_frame = _draw_behavior(out_frame, rois, current_behavior)
            if show_window:
                elapsed = time.time() - t0
                disp = _draw_progress_overlay(
                    out_frame.copy(), fnum, total_frames, elapsed, step
                )
                cv2.imshow(WIN_NAME, disp)
                if cv2.waitKey(1) & 0xFF == ord('q'):
                    break
            if writer:
                writer.write(out_frame)

    cap.release()
    if writer:
        writer.release()
    if show_window:
        cv2.destroyWindow(WIN_NAME)

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
            "version":                VERSION,
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
