"""Endpoints HTTP del servidor de cuadros (Modulos 1 a 3).

Rutas:
  GET /api/indice
  GET /api/salud
  GET /api/preferencias
  GET /api/videos
  GET /api/videos/<video_id>/metadata[?conteo_exacto=1]
  GET /api/videos/<video_id>/frame/<n>[?calidad=1..100][&max_ancho=px]
"""
from __future__ import annotations

import cv2
from flask import Blueprint, Response, current_app, jsonify, request

from . import __version__
from .video_source import (
    CuadroNoDisponible,
    ErrorVideo,
    VideoNoAbierto,
    VideoNoEncontrado,
)


def _verdadero(valor: str | None) -> bool:
    return str(valor).lower() in ("1", "true", "si", "sí", "yes")


def _entero(nombre: str, predeterminado: int | None = None) -> int | None:
    bruto = request.args.get(nombre)
    if bruto is None or bruto == "":
        return predeterminado
    try:
        return int(bruto)
    except ValueError:
        raise ValueError(f"El parametro {nombre} debe ser un numero entero.")


def crear_blueprint(catalogo, cfg) -> Blueprint:
    bp = Blueprint("api", __name__, url_prefix="/api")

    @bp.get("/indice")
    def indice():
        """Indice de la interfaz HTTP, para consultarla sin abrir el visor."""
        return jsonify(
            {
                "herramienta": "Etiquetado semi-automatico FST (nado forzado)",
                "version": __version__,
                "modulo_actual": "3 - regiones de interes y linea de agua",
                "videos_dir": str(catalogo.raiz),
                "endpoints": {
                    "visor": "/",
                    "salud": "/api/salud",
                    "preferencias": "/api/preferencias",
                    "listar_videos": "/api/videos",
                    "metadata": "/api/videos/<video_id>/metadata?conteo_exacto=1",
                    "cuadro": "/api/videos/<video_id>/frame/<n>?calidad=85&max_ancho=960",
                },
            }
        )

    @bp.get("/preferencias")
    def preferencias():
        """Valores por defecto del visor, definidos del lado del servidor."""
        return jsonify(cfg.preferencias_visor())

    @bp.get("/salud")
    def salud():
        return jsonify(
            {
                "estado": "ok",
                "modulo": "3 - regiones de interes y linea de agua",
                "videos_dir": str(catalogo.raiz),
                "videos_dir_existe": catalogo.raiz.is_dir(),
                "opencv": cv2.__version__,
            }
        )

    @bp.get("/videos")
    def listar_videos():
        videos = catalogo.listar()
        return jsonify(
            {
                "videos_dir": str(catalogo.raiz),
                "total": len(videos),
                "videos": videos,
            }
        )

    @bp.get("/videos/<path:video_id>/metadata")
    def metadata(video_id: str):
        lector = catalogo.lector(video_id)
        if _verdadero(request.args.get("conteo_exacto")):
            lector.contar_cuadros_exacto()
        return jsonify(lector.metadatos.to_dict())

    @bp.get("/videos/<path:video_id>/frame/<int:indice>")
    def frame(video_id: str, indice: int):
        calidad = _entero("calidad", cfg.jpeg_quality)
        if not 1 <= calidad <= 100:
            raise ValueError("calidad debe estar entre 1 y 100.")
        max_ancho = _entero("max_ancho")
        if max_ancho is not None and max_ancho < 16:
            raise ValueError("max_ancho debe ser de al menos 16 pixeles.")

        lector = catalogo.lector(video_id)
        meta = lector.metadatos

        # El cuadro N de un video dado no cambia, asi que se puede cachear en
        # el navegador; el mtime invalida el cache si el archivo se reemplaza.
        etag = f'"{video_id}:{indice}:{meta.mtime}:{calidad}:{max_ancho}"'
        if request.headers.get("If-None-Match") == etag:
            return Response(status=304)

        cuadro = lector.leer_cuadro(indice)
        alto_original, ancho_original = cuadro.shape[:2]
        if max_ancho is not None and ancho_original > max_ancho:
            escala = max_ancho / ancho_original
            cuadro = cv2.resize(
                cuadro,
                (max_ancho, max(1, int(round(alto_original * escala)))),
                interpolation=cv2.INTER_AREA,
            )

        ok, buffer = cv2.imencode(
            ".jpg", cuadro, [int(cv2.IMWRITE_JPEG_QUALITY), calidad]
        )
        if not ok:
            raise ErrorVideo(f"No se pudo codificar el cuadro {indice} como JPEG.")

        respuesta = Response(buffer.tobytes(), mimetype="image/jpeg")
        respuesta.headers["ETag"] = etag
        respuesta.headers["Cache-Control"] = "public, max-age=600"
        # Encabezados de verificacion: confirman que se entrego el cuadro pedido.
        respuesta.headers["X-Indice-Cuadro"] = str(indice)
        respuesta.headers["X-Ancho-Original"] = str(ancho_original)
        respuesta.headers["X-Alto-Original"] = str(alto_original)
        return respuesta

    # --------------------------------------------------------------- errores
    @bp.errorhandler(VideoNoEncontrado)
    def _no_encontrado(error):
        return jsonify({"error": str(error), "tipo": "video_no_encontrado"}), 404

    @bp.errorhandler(CuadroNoDisponible)
    def _sin_cuadro(error):
        return jsonify({"error": str(error), "tipo": "cuadro_no_disponible"}), 404

    @bp.errorhandler(VideoNoAbierto)
    def _no_abierto(error):
        return jsonify({"error": str(error), "tipo": "video_no_abierto"}), 500

    @bp.errorhandler(ValueError)
    def _parametro_invalido(error):
        return jsonify({"error": str(error), "tipo": "parametro_invalido"}), 400

    return bp
