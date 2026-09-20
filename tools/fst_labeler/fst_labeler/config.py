"""Parametros configurables de la herramienta de etiquetado FST.

IMPORTANTE: todos los valores por defecto de este archivo son PUNTOS DE
PARTIDA A CALIBRAR con los videos reales del laboratorio. Ninguno se debe
tomar como definitivo ni como resultado experimental.
"""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path

# Raiz de la herramienta: tools/fst_labeler/
DIR_HERRAMIENTA = Path(__file__).resolve().parent.parent

# Extensiones de contenedor de video que la herramienta acepta. No se asume
# ningun formato en particular: OpenCV decide si puede abrir el archivo.
EXTENSIONES_VIDEO = (
    ".mp4", ".avi", ".mov", ".mkv", ".mpg", ".mpeg", ".m4v", ".wmv",
)


@dataclass
class Config:
    """Configuracion del servidor de cuadros (Modulo 1)."""

    # Carpeta donde la herramienta busca videos. Se pasa por --videos-dir o
    # por la variable de entorno FST_VIDEOS_DIR; no hay ninguna ruta de
    # laboratorio codificada en el programa.
    videos_dir: Path = field(default=DIR_HERRAMIENTA / "videos")

    host: str = "127.0.0.1"
    port: int = 5055

    # Calidad de compresion JPEG de los cuadros servidos.
    # Punto de partida a calibrar: 85 conserva detalle suficiente para
    # revisar conducta y mantiene los cuadros ligeros para el navegador.
    jpeg_quality: int = 85

    # Cuantos cuadros conviene avanzar decodificando uno por uno antes de
    # pedirle a OpenCV un salto (seek). Un salto reposiciona al cuadro clave
    # mas cercano y puede ser mas lento que avanzar de corrido.
    # Punto de partida a calibrar: 60 cuadros (~2 s a 30 Hz).
    seek_forward_max: int = 60

    # Cuantos videos se mantienen abiertos al mismo tiempo (cada lector
    # abierto consume memoria del decodificador).
    max_lectores_abiertos: int = 4

    debug: bool = False

    def __post_init__(self) -> None:
        self.videos_dir = Path(self.videos_dir).expanduser().resolve()
        if not 1 <= self.jpeg_quality <= 100:
            raise ValueError("jpeg_quality debe estar entre 1 y 100")
        if self.seek_forward_max < 0:
            raise ValueError("seek_forward_max no puede ser negativo")
        if self.max_lectores_abiertos < 1:
            raise ValueError("max_lectores_abiertos debe ser al menos 1")

    @classmethod
    def desde_entorno(cls) -> "Config":
        """Construye la configuracion leyendo variables de entorno FST_*."""
        datos: dict = {}
        if os.environ.get("FST_VIDEOS_DIR"):
            datos["videos_dir"] = Path(os.environ["FST_VIDEOS_DIR"])
        if os.environ.get("FST_HOST"):
            datos["host"] = os.environ["FST_HOST"]
        if os.environ.get("FST_PORT"):
            datos["port"] = int(os.environ["FST_PORT"])
        if os.environ.get("FST_JPEG_QUALITY"):
            datos["jpeg_quality"] = int(os.environ["FST_JPEG_QUALITY"])
        return cls(**datos)
