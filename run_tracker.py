#!/usr/bin/env python3
"""
Ejecutar el tracker de forma standalone (sin Docker, sin BD).

Uso:
  python run_tracker.py mi_video.mp4 [opciones]

Salidas (en la misma carpeta del video):
  mi_video_tracked.mp4   → video con bounding boxes
  mi_video_tracking.json → coordenadas por frame

Nota:
  Este runner solo pasa argumentos a backend/pipeline/tracker.py.
"""

import os
import sys
import argparse
from pathlib import Path

# Agregar backend/ al path para importar pipeline.*
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "backend"))

from pipeline.tracker import track_video  # noqa: E402


def main():
    p = argparse.ArgumentParser(
        description="FST Rat Tracker — runner",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Ejemplos:
  python run_tracker.py data/videos/corto.mp4
  python run_tracker.py data/videos/corto.mp4 --model weights/rat.pt --tracker bytetrack.yaml
  python run_tracker.py data/videos/corto.mp4 --model yolov8n.pt --stabilize --fps-cap 10
  python run_tracker.py data/videos/corto.mp4 --conf 0.20 --max-freeze 30 --tracker botsort.yaml
""",
    )
    p.add_argument("video", help="Ruta al video .mp4")
    p.add_argument("--layout", default="auto", choices=["auto", "1x4", "2x2"], help="Layout de cilindros")
    p.add_argument("--model", default="weights/rat.pt", help="Modelo YOLO .pt (default weights/rat.pt)")
    p.add_argument("--tracker", default="bytetrack.yaml", help="Tracker YAML: bytetrack.yaml | botsort.yaml")
    p.add_argument("--conf", type=float, default=0.25, help="Umbral de confianza base (default 0.25)")
    p.add_argument("--max-freeze", type=int, default=20, help="Frames máximos congelados (default 20)")
    p.add_argument("--stabilize", action="store_true", help="Estabilizar frames (ECC/ORB) para cámara inestable")
    p.add_argument("--fps-cap", type=int, default=0, help="Límite de FPS a procesar (0 = sin límite)")
    p.add_argument("--device", default="", help="Device: cpu | cuda | 0 | etc.")
    args = p.parse_args()

    if not os.path.isfile(args.video):
        print(f"Error: no se encontró el archivo '{args.video}'")
        sys.exit(1)

    inp = Path(args.video)
    out_video = str(inp.parent / f"{inp.stem}_tracked.mp4")
    out_json = str(inp.parent / f"{inp.stem}_tracking.json")

    print("═══════════════════════════════════════════")
    print("  FST Rat Tracker v1.4.1 (runner)")
    print("═══════════════════════════════════════════")
    print(f"  Entrada:    {args.video}")
    print(f"  Video:      {out_video}")
    print(f"  JSON:       {out_json}")
    print(f"  Layout:     {args.layout}")
    print(f"  Model:      {args.model}")
    print(f"  Tracker:    {args.tracker}")
    print(f"  Conf:       {args.conf}")
    print(f"  Max-freeze: {args.max_freeze}")
    print(f"  Stabilize:  {args.stabilize}")
    print(f"  FPS cap:    {args.fps_cap}")
    print("═══════════════════════════════════════════")
    print()

    track_video(
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

    print("\n¡Listo! Revisa los archivos generados.")


if __name__ == "__main__":
    main()
