#!/usr/bin/env python3
"""Servidor de la herramienta de etiquetado FST (forced swim test).

Modulo 1: sirve cualquier cuadro de un video por numero de cuadro y su
metadata, leida siempre del archivo real con OpenCV.
Modulo 2: visor cuadro a cuadro en el navegador, servido en la raiz.
Modulo 3: dibujo de regiones de interes y linea de agua sobre el cuadro.

Uso:
    pip install -r requirements.txt
    python app.py --videos-dir /ruta/a/mis/videos

La carpeta de videos tambien se puede fijar con la variable de entorno
FST_VIDEOS_DIR. No hay ninguna ruta de laboratorio codificada en el programa.
"""
from __future__ import annotations

import argparse
import sys

from flask import Flask, send_from_directory

from fst_labeler import __version__
from fst_labeler.api import crear_blueprint
from fst_labeler.config import DIR_INTERFAZ, Config
from fst_labeler.video_source import CatalogoVideos


def crear_app(cfg: Config) -> Flask:
    app = Flask(
        __name__,
        static_folder=str(DIR_INTERFAZ),
        static_url_path="/static",
    )
    app.config["FST_CONFIG"] = cfg

    catalogo = CatalogoVideos(
        raiz=cfg.videos_dir,
        seek_forward_max=cfg.seek_forward_max,
        max_lectores=cfg.max_lectores_abiertos,
    )
    app.config["FST_CATALOGO"] = catalogo
    app.register_blueprint(crear_blueprint(catalogo, cfg))

    @app.get("/")
    def visor():
        """Visor y editor de regiones. El indice JSON esta en /api/indice."""
        # max_age=0: la pagina se revalida en cada carga para que al editar la
        # interfaz no haya que limpiar el cache del navegador a mano.
        return send_from_directory(DIR_INTERFAZ, "index.html", max_age=0)

    return app


def parsear_argumentos(argv: list[str]) -> Config:
    base = Config.desde_entorno()
    p = argparse.ArgumentParser(
        description="Servidor y visor de cuadros para el etiquetado de conducta en nado forzado (FST)."
    )
    p.add_argument("--videos-dir", default=str(base.videos_dir),
                   help="Carpeta donde estan los videos a etiquetar.")
    p.add_argument("--host", default=base.host, help="Interfaz de red donde escuchar.")
    p.add_argument("--port", type=int, default=base.port, help="Puerto HTTP.")
    p.add_argument("--jpeg-quality", type=int, default=base.jpeg_quality,
                   help="Calidad JPEG de los cuadros servidos (1-100).")
    p.add_argument("--seek-forward-max", type=int, default=base.seek_forward_max,
                   help="Cuadros que se avanzan decodificando antes de hacer un salto.")
    p.add_argument("--visor-max-ancho", type=int, default=base.visor_max_ancho,
                   help="Ancho maximo del cuadro enviado al navegador (solo transporte).")
    p.add_argument("--visor-prefetch", type=int, default=base.visor_prefetch,
                   help="Cuadros que el visor pide por adelantado.")
    p.add_argument("--visor-salto-cuadros", type=int, default=base.visor_salto_cuadros,
                   help="Tamano del salto de varios cuadros en el visor.")
    p.add_argument("--debug", action="store_true", help="Modo depuracion de Flask.")
    args = p.parse_args(argv)

    return Config(
        videos_dir=args.videos_dir,
        host=args.host,
        port=args.port,
        jpeg_quality=args.jpeg_quality,
        seek_forward_max=args.seek_forward_max,
        visor_max_ancho=args.visor_max_ancho,
        visor_prefetch=args.visor_prefetch,
        visor_salto_cuadros=args.visor_salto_cuadros,
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
        if not encontrados:
            print("       La carpeta esta vacia. Puedes generar un video de prueba con:")
            print("       python scripts/generar_video_prueba.py --fps 30 --segundos 60")

    print(f"\nHerramienta de etiquetado FST v{__version__} (Modulo 3)")
    print(f"Abre en el navegador: http://{cfg.host}:{cfg.port}/")
    app.run(host=cfg.host, port=cfg.port, debug=cfg.debug, threaded=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
