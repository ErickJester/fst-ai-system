"""Endpoints HTTP del servidor de cuadros (Modulos 1 a 3).

Rutas:
  GET /api/indice
  GET /api/salud
  GET /api/preferencias
  GET /api/videos
  GET /api/videos/<video_id>/metadata[?conteo_exacto=1]
  GET /api/videos/<video_id>/frame/<n>[?calidad=1..100][&max_ancho=px]
  POST /api/videos/<video_id>/deteccion-movimiento
  POST /api/videos/<video_id>/estabilidad-camara
"""
from __future__ import annotations

import cv2
from flask import Blueprint, Response, current_app, jsonify, request

from . import __version__, estabilizacion
from .deteccion_movimiento import Parametros, analizar
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
                "modulo_actual": "4 - detector de movimiento por umbral",
                "videos_dir": str(catalogo.raiz),
                "endpoints": {
                    "visor": "/",
                    "salud": "/api/salud",
                    "preferencias": "/api/preferencias",
                    "listar_videos": "/api/videos",
                    "metadata": "/api/videos/<video_id>/metadata?conteo_exacto=1",
                    "cuadro": "/api/videos/<video_id>/frame/<n>?calidad=85&max_ancho=960",
                    "deteccion_movimiento": "POST /api/videos/<video_id>/deteccion-movimiento",
                    "estabilidad_camara": "POST /api/videos/<video_id>/estabilidad-camara",
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
                "modulo": "4 - detector de movimiento por umbral",
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

    # ------------------------------------------------- Modulo 4: deteccion
    #
    # Las regiones viajan en el cuerpo de la peticion, no se guardan en el
    # servidor: el navegador sigue siendo su dueno hasta que el Modulo 8
    # introduzca la base de datos.

    def _regiones_del_cuerpo(cuerpo: dict) -> list:
        regiones = cuerpo.get("regiones")
        if not isinstance(regiones, list) or not regiones:
            raise ValueError("Hace falta al menos una region de interes.")
        limpias = []
        for i, region in enumerate(regiones, start=1):
            esquinas = (region or {}).get("esquinas")
            if not isinstance(esquinas, list) or len(esquinas) != 4:
                raise ValueError(f"La region {i} no tiene cuatro esquinas.")
            try:
                puntos = [{"x": float(p["x"]), "y": float(p["y"])} for p in esquinas]
            except (KeyError, TypeError, ValueError):
                raise ValueError(f"Las esquinas de la region {i} no son coordenadas validas.")
            limpias.append({"esquinas": puntos, "lineaAgua": region.get("lineaAgua")})
        return limpias

    def _fps_efectivo(cuerpo: dict, meta) -> float:
        """El FPS lo manda el navegador solo si el archivo no lo declara bien."""
        crudo = cuerpo.get("fps")
        if crudo is not None:
            valor = float(crudo)
            if valor <= 0:
                raise ValueError("Los cuadros por segundo deben ser mayores que cero.")
            return valor
        if meta.fps and meta.fps_confiable:
            return float(meta.fps)
        raise ValueError(
            "El archivo no declara cuadros por segundo confiables. Indica el valor "
            "real en el visor antes de analizar: de el dependen las fronteras de "
            "los bloques de 5 s."
        )

    @bp.post("/videos/<path:video_id>/estabilidad-camara")
    def estabilidad_camara(video_id: str):
        """Busca a partir de que cuadro la camara deja de reacomodarse."""
        cuerpo = request.get_json(silent=True) or {}
        regiones = _regiones_del_cuerpo(cuerpo)
        lector = catalogo.lector(video_id)
        total = lector.contar_cuadros_exacto()
        cadencia = int(cuerpo.get("cadencia", cfg.deteccion_cadencia_camara))
        if cadencia < 1:
            raise ValueError("La cadencia debe ser de al menos un cuadro.")
        informe = estabilizacion.detectar_inicio_estable(
            str(lector.ruta), regiones, cadencia, total
        )
        informe["total_cuadros"] = total
        return jsonify(informe)

    @bp.post("/videos/<path:video_id>/deteccion-movimiento")
    def deteccion_movimiento(video_id: str):
        """Capa 2 mas compuerta de cordura de la Capa 0, por region."""
        cuerpo = request.get_json(silent=True) or {}
        regiones = _regiones_del_cuerpo(cuerpo)
        lector = catalogo.lector(video_id)
        meta = lector.metadatos
        fps = _fps_efectivo(cuerpo, meta)
        total = lector.contar_cuadros_exacto()

        opciones = cuerpo.get("parametros") or {}
        parametros = Parametros(
            segundos_por_bloque=float(opciones.get("segundos_por_bloque", cfg.deteccion_segundos_bloque)),
            umbral_binarizacion=int(opciones.get("umbral_binarizacion", cfg.deteccion_umbral_binarizacion)),
            umbral_actividad=(
                None if opciones.get("umbral_actividad") in (None, "", "auto")
                else float(opciones["umbral_actividad"])
            ),
            muestras_fondo=int(opciones.get("muestras_fondo", cfg.deteccion_muestras_fondo)),
            cadencia_camara=int(opciones.get("cadencia_camara", cfg.deteccion_cadencia_camara)),
            estabilizar=bool(opciones.get("estabilizar", True)),
            lado_maximo=int(opciones.get("lado_maximo", cfg.deteccion_lado_maximo)),
        )
        if parametros.segundos_por_bloque <= 0:
            raise ValueError("La duracion del bloque debe ser mayor que cero.")
        if not 1 <= parametros.umbral_binarizacion <= 254:
            raise ValueError("El umbral de binarizacion debe estar entre 1 y 254.")

        informe = analizar(
            ruta=str(lector.ruta),
            regiones=regiones,
            fps=fps,
            total_cuadros=total,
            cuadro_referencia=int(cuerpo.get("cuadro_referencia", 0)),
            cuadro_inicio=int(cuerpo.get("cuadro_inicio", 0)),
            cuadro_fin=(None if cuerpo.get("cuadro_fin") in (None, "") else int(cuerpo["cuadro_fin"])),
            parametros=parametros,
        )
        informe["video"] = video_id
        return jsonify(informe)

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

    @bp.errorhandler(RuntimeError)
    def _fallo_analisis(error):
        return jsonify({"error": str(error), "tipo": "fallo_analisis"}), 500

    @bp.errorhandler(ValueError)
    def _parametro_invalido(error):
        return jsonify({"error": str(error), "tipo": "parametro_invalido"}), 400

    return bp
