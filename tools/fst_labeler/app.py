#!/usr/bin/env python3
"""Servidor de la herramienta de etiquetado FST (forced swim test).

Modulo 1: sirve cualquier cuadro de un video por numero de cuadro y su
metadata, leida siempre del archivo real con OpenCV.

Uso:
    pip install -r requirements.txt
    python app.py --videos-dir /ruta/a/mis/videos

La carpeta de videos tambien se puede fijar con la variable de entorno
FST_VIDEOS_DIR. No hay ninguna ruta de laboratorio codificada en el programa.
"""
from __future__ import annotations

import argparse
import sys

from flask import Flask, jsonify

from fst_labeler import __version__
from fst_labeler.api import crear_blueprint
from fst_labeler.config import Config
from fst_labeler.video_source import CatalogoVideos


def crear_app(cfg: Config) -> Flask:
    app = Flask(__name__)
    app.config["FST_CONFIG"] = cfg

    catalogo = CatalogoVideos(
        raiz=cfg.videos_dir,
        seek_forward_max=cfg.seek_forward_max,
        max_lectores=cfg.max_lectores_abiertos,
    )
    app.config["FST_CATALOGO"] = catalogo
    app.register_blueprint(crear_blueprint(catalogo, cfg))

    @app.get("/")
    def indice():
        """Indice de la interfaz HTTP. La interfaz grafica llega en el Modulo 2."""
        return jsonify(
            {
                "herramienta": "Etiquetado semi-automatico FST (nado forzado)",
                "version": __version__,
                "modulo_actual": "1 - servidor de cuadros (sin interfaz todavia)",
                "videos_dir": str(cfg.videos_dir),
                "endpoints": {
                    "salud": "/api/salud",
                    "listar_videos": "/api/videos",
                    "metadata": "/api/videos/<video_id>/metadata?conteo_exacto=1",
                    "cuadro": "/api/videos/<video_id>/frame/<n>?calidad=85&max_ancho=960",
                },
            }
        )

    return app


def parsear_argumentos(argv: list[str]) -> Config:
    base = Config.desde_entorno()
    p = argparse.ArgumentParser(
        description="Servidor de cuadros para el etiquetado de conducta en nado forzado (FST)."
    )
    p.add_argument("--videos-dir", default=str(base.videos_dir),
                   help="Carpeta donde estan los videos a etiquetar.")
    p.add_argument("--host", default=base.host, help="Interfaz de red donde escuchar.")
    p.add_argument("--port", type=int, default=base.port, help="Puerto HTTP.")
    p.add_argument("--jpeg-quality", type=int, default=base.jpeg_quality,
                   help="Calidad JPEG de los cuadros servidos (1-100).")
    p.add_argument("--seek-forward-max", type=int, default=base.seek_forward_max,
                   help="Cuadros que se avanzan decodificando antes de hacer un salto.")
    p.add_argument("--debug", action="store_true", help="Modo depuracion de Flask.")
    args = p.parse_args(argv)

    return Config(
        videos_dir=args.videos_dir,
        host=args.host,
        port=args.port,
        jpeg_quality=args.jpeg_quality,
        seek_forward_max=args.seek_forward_max,
        debug=args.debug,
    )


def main(argv: list[str] | None = None) -> int:
    cfg = parsear_argumentos(argv if argv is not None else sys.argv[1:])
    app = crear_app(cfg)

    if not cfg.videos_dir.is_dir():
        print(f"AVISO: la carpeta de videos no existe todavia: {cfg.videos_dir}")
        print("       Creala y coloca ahi los videos, o usa --videos-dir.")
    else:
        catalogo = app.config["FST_CATALOGO"]
        encontrados = catalogo.listar()
        print(f"Videos detectados en {cfg.videos_dir}: {len(encontrados)}")
        for video in encontrados[:10]:
            print(f"  - {video['video_id']}")

    print(f"\nHerramienta de etiquetado FST v{__version__} (Modulo 1)")
    print(f"Abre en el navegador: http://{cfg.host}:{cfg.port}/")
    app.run(host=cfg.host, port=cfg.port, debug=cfg.debug, threaded=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
