#!/usr/bin/env python3
"""Runner standalone para run_analysis (clasificación de conducta FST)."""
import sys
import os
import argparse
from pathlib import Path

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "backend"))
from pipeline.run_analysis import run_analysis, VERSION


def main():
    p = argparse.ArgumentParser(description="FST — análisis de conducta")
    p.add_argument("video", help="Ruta al video .mp4")
    p.add_argument("--layout",       default="auto", choices=["auto", "1x4", "1x3", "2x2"])
    p.add_argument("--model",        default="weights/rat.pt")
    p.add_argument("--conf",         type=float, default=0.25)
    p.add_argument("--skip-frames",  type=int,   default=0)
    p.add_argument("--skip-seconds", type=float, default=0.0)
    p.add_argument("--warmup-frames",type=int,   default=0)
    p.add_argument("--fps-cap",      type=int,   default=0)
    p.add_argument("--immobile-thr",    type=float, default=6.5,
                   help="Umbral de motion en píxeles dentro del bbox; por debajo = inmóvil")
    p.add_argument("--climb-aspect-thr", type=float, default=1.6,
                   help="Umbral de h/w del bbox; por encima = escape (vista lateral)")
    p.add_argument("--disp-thr",        type=float, default=8.0,
                   help="Umbral de desplazamiento del centro del bbox (px/frame); por debajo = inmóvil")
    p.add_argument("--escape-top-thr", type=float, default=0.08,
                   help="Fracción del ROI: si top del bbox < esta dist del waterline = escape")
    p.add_argument("--swim-width-thr", type=float, default=0.35,
                   help="Fracción del ancho del ROI: bbox más ancho que esto = señal de nado")
    p.add_argument("--pos-std-thr",   type=float, default=20.0,
                   help="Dispersión espacial del centro del bbox en la ventana (px); por debajo = inmóvil sostenida")
    p.add_argument("--stabilize",    action="store_true")
    p.add_argument("--device",       default="")
    p.add_argument("--no-video",     action="store_true", help="No generar video anotado")
    args = p.parse_args()

    if not os.path.isfile(args.video):
        raise SystemExit(f"Error: no se encontró '{args.video}'")

    inp     = Path(args.video)
    out_dir = inp.parent / inp.stem
    out_dir.mkdir(exist_ok=True)

    skip_frames = max(0, args.skip_frames)
    if args.skip_seconds > 0:
        import cv2
        cap = cv2.VideoCapture(str(inp))
        fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
        cap.release()
        skip_frames = max(skip_frames, int(round(args.skip_seconds * fps)))

    out_video = None if args.no_video else str(out_dir / f"{inp.stem}_analysis_{VERSION}.mp4")
    out_json  = str(out_dir / f"{inp.stem}_analysis_{VERSION}.json")

    print("═" * 50)
    print(f"  FST — Análisis de conducta  {VERSION}")
    print("═" * 50)
    print(f"  Video:       {args.video}")
    print(f"  Layout:      {args.layout}")
    print(f"  Model:       {args.model}")
    print(f"  Skip frames: {skip_frames}")
    print(f"  Output JSON: {out_json}")
    print(f"  Output MP4:  {out_video or '(desactivado)'}")
    print("═" * 50 + "\n")

    summaries = run_analysis(
        video_path=args.video,
        layout=args.layout,
        model_path=args.model,
        conf=args.conf,
        skip_frames=skip_frames,
        warmup_frames=args.warmup_frames,
        fps_cap=args.fps_cap,
        immobile_thr=args.immobile_thr,
        climb_aspect_thr=args.climb_aspect_thr,
        disp_thr=args.disp_thr,
        escape_top_thr=args.escape_top_thr,
        swim_width_thr=args.swim_width_thr,
        pos_std_thr=args.pos_std_thr,
        stabilize=args.stabilize,
        device=args.device,
        output_video=out_video,
        output_json=out_json,
        show_progress=True,
        show_window=not args.no_video,
    )

    print("\n" + "═" * 50)
    print("  Resultados")
    print("═" * 50)
    for s in summaries:
        total = s.swim_s + s.immobile_s + s.escape_s or 1
        print(f"  Rata {s.rat_idx}:  nado={s.swim_s:.1f}s ({s.swim_s/total*100:.0f}%)  "
              f"inmovil={s.immobile_s:.1f}s ({s.immobile_s/total*100:.0f}%)  "
              f"escape={s.escape_s:.1f}s ({s.escape_s/total*100:.0f}%)")
    print("═" * 50)


if __name__ == "__main__":
    main()
