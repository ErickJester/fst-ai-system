#!/usr/bin/env python3
"""
Runner standalone para el tracker.

Genera:
  *_tracked.mp4   (overlay)
  *_tracking.json (coords por frame)

Este runner solo pasa argumentos a backend/pipeline/tracker.py
"""

import os
import sys
import argparse
import inspect
from pathlib import Path

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "backend"))
from pipeline.tracker import track_video  # noqa: E402


def _supports_kw(fn, name: str) -> bool:
    try:
        return name in inspect.signature(fn).parameters
    except Exception:
        return False


def main():
    p = argparse.ArgumentParser(description="FST Rat Tracker — runner")
    p.add_argument("video", help="Ruta al video .mp4")
    p.add_argument("--layout", default="auto", choices=["auto", "1x4", "2x2"])
    p.add_argument("--model", default="weights/rat.pt")
    p.add_argument("--tracker", default="bytetrack.yaml")
    p.add_argument("--conf", type=float, default=0.25)
    p.add_argument("--max-freeze", type=int, default=20)

    # NUEVO: saltar frames iniciales (evita que manos/operador siembren falsos positivos)
    p.add_argument("--skip-frames", type=int, default=0, help="No trackear los primeros N frames")
    p.add_argument("--skip-seconds", type=float, default=0.0, help="No trackear los primeros N segundos (usa FPS del video)")

    # Warm-up (después del skip)
    p.add_argument("--warmup-frames", type=int, default=0, help="Frames en modo warm-up (después del skip)")
    p.add_argument("--warmup-conf", type=float, default=0.0, help="Conf SOLO durante warm-up (0=usa --conf)")

    # Filtro para falsos positivos chiquitos (mano/reflejos)
    p.add_argument("--min-area-ratio", type=float, default=0.0, help="Descartar bboxes con área < ratio*áreaROI (0=off)")

    p.add_argument("--stabilize", action="store_true")
    p.add_argument("--fps-cap", type=int, default=0)
    p.add_argument("--device", default="")

    args = p.parse_args()
    if not os.path.isfile(args.video):
        raise SystemExit(f"Error: no se encontró '{args.video}'")

    inp = Path(args.video)
    out_video = str(inp.parent / f"{inp.stem}_tracked.mp4")
    out_json = str(inp.parent / f"{inp.stem}_tracking.json")

    # Calcular skip-frames desde skip-seconds (si aplica)
    skip_frames = max(0, int(args.skip_frames))
    if args.skip_seconds and args.skip_seconds > 0:
        import cv2
        cap = cv2.VideoCapture(str(inp))
        fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
        cap.release()
        skip_frames = max(skip_frames, int(round(float(args.skip_seconds) * float(fps))))

    print("═══════════════════════════════════════════")
    print("  FST Rat Tracker v1.4.3 (runner)")
    print("═══════════════════════════════════════════")
    print(f"Entrada:        {args.video}")
    print(f"Video:          {out_video}")
    print(f"JSON:           {out_json}")
    print(f"Layout:         {args.layout}")
    print(f"Model:          {args.model}")
    print(f"Tracker:        {args.tracker}")
    print(f"Conf:           {args.conf}")
    print(f"Max-freeze:     {args.max_freeze}")
    print(f"Skip-frames:    {skip_frames}")
    print(f"Warmup-frames:  {args.warmup_frames}")
    print(f"Warmup-conf:    {args.warmup_conf if args.warmup_conf > 0 else '(usa --conf)'}")
    print(f"Min-area-ratio: {args.min_area_ratio if args.min_area_ratio > 0 else '(off)'}")
    print(f"Stabilize:      {args.stabilize}")
    print(f"FPS cap:        {args.fps_cap}")
    print("═══════════════════════════════════════════\n")

    kwargs = dict(
        input_path=args.video,
        output_video=out_video,
        output_json=out_json,
        layout=args.layout,
        fps_cap=args.fps_cap,
        show_progress=True,
        model_path=args.model,
        conf=args.conf,
        tracker_yaml=args.tracker,
        max_freeze_frames=args.max_freeze,
        stabilize=args.stabilize,
        device=args.device,
    )

    # pasar nuevos kwargs solo si el tracker los soporta
    if skip_frames > 0 and _supports_kw(track_video, "skip_frames"):
        kwargs["skip_frames"] = skip_frames

    warmup_frames = max(0, int(args.warmup_frames))
    warmup_conf = float(args.warmup_conf) if args.warmup_conf and args.warmup_conf > 0 else None
    if warmup_frames > 0 and _supports_kw(track_video, "warmup_frames"):
        kwargs["warmup_frames"] = warmup_frames
        if warmup_conf is not None and _supports_kw(track_video, "warmup_conf"):
            kwargs["warmup_conf"] = warmup_conf

    if args.min_area_ratio and args.min_area_ratio > 0 and _supports_kw(track_video, "min_area_ratio"):
        kwargs["min_area_ratio"] = float(args.min_area_ratio)

    track_video(**kwargs)
    print("\nListo.")

if __name__ == "__main__":
    main()
