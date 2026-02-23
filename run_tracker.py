#!/usr/bin/env python3
"""
Ejecutar el tracker de forma standalone (sin Docker, sin BD).

Uso:
  python run_tracker.py mi_video.mp4

Salidas (en la misma carpeta del video):
  mi_video_tracked.mp4   → video con bounding boxes
  mi_video_tracking.json → coordenadas por frame

Requisitos:
  pip install opencv-python-headless numpy
"""

import sys
import os

# Agregar backend/ al path para importar pipeline.*
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "backend"))

from pipeline.tracker import track_video
from pathlib import Path


def main():
    if len(sys.argv) < 2:
        print("Uso: python run_tracker.py <video.mp4> [--layout auto|1x4|2x2]")
        print()
        print("Ejemplo:")
        print("  python run_tracker.py videos/fst_dia1.mp4")
        print("  python run_tracker.py videos/fst_dia1.mp4 --layout 1x4")
        sys.exit(1)

    video_path = sys.argv[1]
    if not os.path.isfile(video_path):
        print(f"Error: no se encontró el archivo '{video_path}'")
        sys.exit(1)

    # Parsear --layout si se pasa
    layout = "auto"
    if "--layout" in sys.argv:
        idx = sys.argv.index("--layout")
        if idx + 1 < len(sys.argv):
            layout = sys.argv[idx + 1]

    inp = Path(video_path)
    out_video = str(inp.parent / f"{inp.stem}_tracked.mp4")
    out_json = str(inp.parent / f"{inp.stem}_tracking.json")

    print(f"═══════════════════════════════════════════")
    print(f"  FST Rat Tracker v1.3.7")
    print(f"═══════════════════════════════════════════")
    print(f"  Entrada:  {video_path}")
    print(f"  Video:    {out_video}")
    print(f"  JSON:     {out_json}")
    print(f"  Layout:   {layout}")
    print(f"═══════════════════════════════════════════")
    print()

    stats = track_video(
        input_path=video_path,
        output_video=out_video,
        output_json=out_json,
        layout=layout,
    )

    print()
    print("¡Listo! Revisa los archivos generados.")


if __name__ == "__main__":
    main()
