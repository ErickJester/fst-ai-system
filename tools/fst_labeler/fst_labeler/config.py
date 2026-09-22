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

# Archivos de la interfaz del navegador (HTML, CSS y JavaScript sin
# framework ni paso de compilacion).
DIR_INTERFAZ = DIR_HERRAMIENTA / "static"

# Extensiones de contenedor de video que la herramienta acepta. No se asume
# ningun formato en particular: OpenCV decide si puede abrir el archivo.
EXTENSIONES_VIDEO = (
    ".mp4", ".avi", ".mov", ".mkv", ".mpg", ".mpeg", ".m4v", ".wmv",
)


@dataclass
class Config:
    """Configuracion del servidor de cuadros y del visor."""

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

    # ------------------------------------------------------------- visor
    # Ancho maximo al que se reescala el cuadro para enviarlo al navegador.
    # Solo afecta el transporte: el analisis siempre usa la resolucion real
    # del archivo, y los encabezados X-Ancho-Original / X-Alto-Original
    # conservan las medidas verdaderas.
    # Punto de partida a calibrar: 960 px.
    visor_max_ancho: int = 960

    # Cuadros que el visor pide por adelantado para que la reproduccion no
    # se corte mientras llegan por la red.
    # Punto de partida a calibrar: 8 cuadros.
    visor_prefetch: int = 8

    # Tamano del salto de los botones y atajos de "varios cuadros".
    # Punto de partida a calibrar: 10 cuadros.
    visor_salto_cuadros: int = 10

    # -------------------------------------------- deteccion de movimiento
    # Duracion del bloque de analisis. 5 s es el estandar de Detke et al.
    # (1995); Alonso-Fernandez et al. (2015) no hallaron diferencias entre
    # 3, 5 y 10 s en especimenes Wistar, asi que es un parametro, no una constante.
    deteccion_segundos_bloque: float = 5.0

    # Niveles de gris que un pixel debe apartarse del fondo para contar como
    # especimen. Punto de partida a calibrar: 40, medido sobre material real
    # del laboratorio.
    deteccion_umbral_binarizacion: int = 40

    # Cuadros de los que se toma la mediana para construir el fondo.
    # Punto de partida a calibrar: 40.
    deteccion_muestras_fondo: int = 40

    # Cada cuantos cuadros se reestima el movimiento de la camara.
    # Punto de partida a calibrar: 30 cuadros (~1 s a 30 Hz).
    deteccion_cadencia_camara: int = 30

    # Lado mayor del recorte rectificado, en pixeles.
    # Punto de partida a calibrar: 480.
    deteccion_lado_maximo: int = 480

    # ------------------------------------------------ reglas geometricas
    # Area minima de la mascara para dar un cuadro por medible, en fraccion
    # del area de la region. Es el mismo 0.5 % con el que el detector de
    # movimiento avisa de bloques sin especimen.
    reglas_area_minima: float = 0.005

    # Franja superior de la mascara que hace de cuerpo anterior, en fraccion
    # de la altura del cuerpo. Punto de partida a calibrar: un tercio.
    reglas_fraccion_superior: float = 1.0 / 3.0

    # Los cuatro umbrales de las reglas no viven aqui: en automatico se
    # calculan por Otsu sobre la distribucion del propio video, porque un
    # valor en estas unidades depende del montaje y no es transferible entre
    # grabaciones. Se fijan a mano desde el panel cuando haya videos
    # puntuados con los que calibrarlos.

    # --------------------------------------------------------- consenso
    # Los tres umbrales del Modulo 7 mueven el mismo cursor: mas exigentes
    # aceptan menos clips sin revision y mandan mas a la cola. Donde ponerlos
    # se decide midiendo cuantos de los aceptados estaban bien, contra el
    # conjunto de prueba etiquetado a mano; no se puede fijar de antemano.

    # Confianza minima del modelo preentrenado. Punto de partida: 0.70, el
    # mismo corte con el que el sistema principal detiene el analisis (RN-11).
    consenso_confianza_minima: float = 0.70

    # Fraccion minima de votos del ganador en el bloque de las reglas.
    # Punto de partida a calibrar: 0.60, sobre un azar de 0.33 con tres clases.
    consenso_margen_minimo: float = 0.60

    # Fraccion minima del bloque que los clips de 3 s tienen que cubrir para
    # que la reproyeccion del modelo valga. Punto de partida a calibrar: 0.60.
    consenso_cobertura_minima: float = 0.60

    # Capas de tres clases que tienen que coincidir para aceptar sin revision.
    # 2 son todas las que hay; en 1 el consenso queda desactivado.
    consenso_minimo_votantes: int = 2

    # ------------------------------------------------- revision humana
    # Base sqlite de la cola de revision y de las decisiones (Modulo 8). Vive
    # fuera del control de versiones: son datos, no codigo.
    base_datos: Path = field(default=DIR_HERRAMIENTA / "datos" / "revision.sqlite3")

    # --------------------------------------------- exportacion (Modulo 9)
    # Ventana del voto mayoritario, en bloques. Impar, para que tenga centro.
    # Punto de partida a calibrar: 3, el minimo que corrige un bloque aislado
    # flanqueado por otros dos que concuerdan entre si.
    exportacion_ventana_suavizado: int = 3

    debug: bool = False

    def __post_init__(self) -> None:
        self.videos_dir = Path(self.videos_dir).expanduser().resolve()
        self.base_datos = Path(self.base_datos).expanduser().resolve()
        if not 1 <= self.jpeg_quality <= 100:
            raise ValueError("jpeg_quality debe estar entre 1 y 100")
        if self.seek_forward_max < 0:
            raise ValueError("seek_forward_max no puede ser negativo")
        if self.max_lectores_abiertos < 1:
            raise ValueError("max_lectores_abiertos debe ser al menos 1")
        if self.visor_max_ancho < 16:
            raise ValueError("visor_max_ancho debe ser de al menos 16 pixeles")
        if self.visor_prefetch < 0:
            raise ValueError("visor_prefetch no puede ser negativo")
        if self.visor_salto_cuadros < 1:
            raise ValueError("visor_salto_cuadros debe ser al menos 1")
        if self.deteccion_segundos_bloque <= 0:
            raise ValueError("deteccion_segundos_bloque debe ser mayor que cero")
        if not 1 <= self.deteccion_umbral_binarizacion <= 254:
            raise ValueError("deteccion_umbral_binarizacion debe estar entre 1 y 254")
        if self.deteccion_muestras_fondo < 3:
            raise ValueError("deteccion_muestras_fondo debe ser al menos 3")
        if self.deteccion_cadencia_camara < 1:
            raise ValueError("deteccion_cadencia_camara debe ser al menos 1")
        if self.deteccion_lado_maximo < 16:
            raise ValueError("deteccion_lado_maximo debe ser de al menos 16 pixeles")
        if not 0 <= self.reglas_area_minima < 1:
            raise ValueError("reglas_area_minima debe estar entre 0 y 1")
        if not 0 < self.reglas_fraccion_superior <= 1:
            raise ValueError("reglas_fraccion_superior debe estar entre 0 y 1")
        if not 0 <= self.consenso_confianza_minima <= 1:
            raise ValueError("consenso_confianza_minima debe estar entre 0 y 1")
        if not 0 <= self.consenso_margen_minimo <= 1:
            raise ValueError("consenso_margen_minimo debe estar entre 0 y 1")
        if not 0 <= self.consenso_cobertura_minima <= 1:
            raise ValueError("consenso_cobertura_minima debe estar entre 0 y 1")
        if self.consenso_minimo_votantes not in (1, 2):
            raise ValueError("consenso_minimo_votantes debe ser 1 o 2")
        if self.exportacion_ventana_suavizado < 1 or self.exportacion_ventana_suavizado % 2 == 0:
            raise ValueError("exportacion_ventana_suavizado debe ser un numero impar de al menos 1")

    def preferencias_visor(self) -> dict:
        """Valores por defecto que el visor toma del servidor al arrancar."""
        return {
            "max_ancho": self.visor_max_ancho,
            "calidad": self.jpeg_quality,
            "prefetch": self.visor_prefetch,
            "salto_cuadros": self.visor_salto_cuadros,
            "deteccion": {
                "segundos_por_bloque": self.deteccion_segundos_bloque,
                "umbral_binarizacion": self.deteccion_umbral_binarizacion,
                "muestras_fondo": self.deteccion_muestras_fondo,
                "cadencia_camara": self.deteccion_cadencia_camara,
                "lado_maximo": self.deteccion_lado_maximo,
            },
            "reglas": {
                "area_minima": self.reglas_area_minima,
                "fraccion_superior": self.reglas_fraccion_superior,
            },
            "consenso": {
                "confianza_minima_modelo": self.consenso_confianza_minima,
                "margen_minimo_reglas": self.consenso_margen_minimo,
                "cobertura_minima_modelo": self.consenso_cobertura_minima,
                "minimo_votantes": self.consenso_minimo_votantes,
            },
            "nota": (
                "Valores por defecto: puntos de partida a calibrar con los "
                "videos del laboratorio, no resultados experimentales."
            ),
        }

    @classmethod
    def desde_entorno(cls) -> "Config":
        """Construye la configuracion leyendo variables de entorno FST_*."""
        datos: dict = {}
        if os.environ.get("FST_VIDEOS_DIR"):
            datos["videos_dir"] = Path(os.environ["FST_VIDEOS_DIR"])
        if os.environ.get("FST_BASE_DATOS"):
            datos["base_datos"] = Path(os.environ["FST_BASE_DATOS"])
        if os.environ.get("FST_HOST"):
            datos["host"] = os.environ["FST_HOST"]
        if os.environ.get("FST_PORT"):
            datos["port"] = int(os.environ["FST_PORT"])
        if os.environ.get("FST_JPEG_QUALITY"):
            datos["jpeg_quality"] = int(os.environ["FST_JPEG_QUALITY"])
        if os.environ.get("FST_VISOR_MAX_ANCHO"):
            datos["visor_max_ancho"] = int(os.environ["FST_VISOR_MAX_ANCHO"])
        return cls(**datos)
