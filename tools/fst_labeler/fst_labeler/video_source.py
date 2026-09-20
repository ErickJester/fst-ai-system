"""Lectura de videos por numero de cuadro exacto (Modulo 1).

Toda la metadata (FPS, total de cuadros, resolucion) se lee del archivo real
con OpenCV. No se asume ningun valor: si el contenedor no lo declara, se
reporta como desconocido y se marca la falta de confianza.

FPS = cuadros por segundo (frames per second).
ROI = region of interest (region de interes); se usa a partir del Modulo 3.
"""
from __future__ import annotations

import math
import threading
from collections import OrderedDict
from dataclasses import asdict, dataclass, replace
from pathlib import Path

import cv2

from .config import EXTENSIONES_VIDEO


AVISO_FPS = (
    "El FPS declarado por el contenedor no es confiable; confirmalo antes de "
    "convertir cuadros a segundos."
)
AVISO_RESOLUCION = "OpenCV no reporto la resolucion del video."
AVISO_TOTAL_DECLARADO = (
    "El total de cuadros proviene de la metadata del contenedor y puede diferir "
    "del real; usa conteo_exacto=1 para verificarlo."
)
AVISO_TOTAL_AUSENTE = (
    "El contenedor no declara el total de cuadros; pide la metadata con "
    "conteo_exacto=1 para contarlos."
)


class ErrorVideo(Exception):
    """Base de los errores de acceso a video."""


class VideoNoEncontrado(ErrorVideo):
    """El identificador no corresponde a un video dentro de la carpeta raiz."""


class VideoNoAbierto(ErrorVideo):
    """OpenCV no pudo abrir el archivo (codec ausente o archivo corrupto)."""


class CuadroNoDisponible(ErrorVideo):
    """El cuadro pedido no existe o no se pudo decodificar."""


@dataclass(frozen=True)
class MetadatosVideo:
    """Metadata leida del archivo real, sin valores supuestos."""

    video_id: str
    nombre: str
    ancho: int
    alto: int
    fps: float | None
    fps_confiable: bool
    total_cuadros: int | None
    total_cuadros_exacto: bool
    duracion_s: float | None
    codec: str
    tamano_bytes: int
    mtime: float
    aviso: str | None = None

    def to_dict(self) -> dict:
        return asdict(self)


def _duracion(total_cuadros: int | None, fps: float | None) -> float | None:
    if not total_cuadros or not fps or fps <= 0:
        return None
    return round(total_cuadros / fps, 3)


def _fourcc_a_texto(valor: float) -> str:
    try:
        codigo = int(valor)
    except (TypeError, ValueError):
        return "desconocido"
    if codigo <= 0:
        return "desconocido"
    letras = [chr((codigo >> desplazamiento) & 0xFF) for desplazamiento in (0, 8, 16, 24)]
    texto = "".join(letras).strip()
    return texto if texto.isprintable() and texto else "desconocido"


class LectorVideo:
    """Mantiene una VideoCapture abierta y entrega cuadros por indice exacto.

    Un salto (seek) en un contenedor comprimido aterriza en el cuadro clave
    mas cercano, asi que la posicion se lleva en el propio objeto y se avanza
    decodificando hasta el cuadro pedido. De ese modo el cuadro N devuelto es
    siempre el cuadro N del archivo, no un vecino aproximado.
    """

    def __init__(self, ruta: Path, video_id: str, seek_forward_max: int = 60) -> None:
        self.ruta = Path(ruta)
        self.video_id = video_id
        self.seek_forward_max = seek_forward_max
        self._lock = threading.Lock()
        self._cap = cv2.VideoCapture(str(self.ruta))
        if not self._cap.isOpened():
            raise VideoNoAbierto(f"OpenCV no pudo abrir el video: {self.ruta}")
        self._pos = 0  # indice del proximo cuadro a decodificar
        self._total_exacto: int | None = None
        self._meta = self._leer_metadatos()

    # ------------------------------------------------------------------ meta
    def _leer_metadatos(self) -> MetadatosVideo:
        cap = self._cap
        ancho = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
        alto = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)

        fps_bruto = cap.get(cv2.CAP_PROP_FPS)
        fps: float | None
        fps_confiable = True
        if fps_bruto is None or not math.isfinite(fps_bruto) or fps_bruto <= 0:
            fps, fps_confiable = None, False
        else:
            fps = round(float(fps_bruto), 6)
            # Un valor absurdo suele venir de metadata mal escrita en el
            # contenedor; se entrega pero marcado como no confiable.
            if fps > 240:
                fps_confiable = False

        total_bruto = cap.get(cv2.CAP_PROP_FRAME_COUNT)
        if total_bruto is None or not math.isfinite(total_bruto) or total_bruto <= 0:
            total_cuadros = None
        else:
            total_cuadros = int(total_bruto)

        avisos = []
        if not fps_confiable:
            avisos.append(AVISO_FPS)
        avisos.append(AVISO_TOTAL_AUSENTE if total_cuadros is None else AVISO_TOTAL_DECLARADO)
        if ancho <= 0 or alto <= 0:
            avisos.append(AVISO_RESOLUCION)

        estadisticas = self.ruta.stat()
        return MetadatosVideo(
            video_id=self.video_id,
            nombre=self.ruta.name,
            ancho=ancho,
            alto=alto,
            fps=fps,
            fps_confiable=fps_confiable,
            total_cuadros=total_cuadros,
            total_cuadros_exacto=False,
            duracion_s=_duracion(total_cuadros, fps),
            codec=_fourcc_a_texto(cap.get(cv2.CAP_PROP_FOURCC)),
            tamano_bytes=estadisticas.st_size,
            mtime=estadisticas.st_mtime,
            aviso=" ".join(avisos) if avisos else None,
        )

    @property
    def metadatos(self) -> MetadatosVideo:
        return self._meta

    def contar_cuadros_exacto(self) -> int:
        """Cuenta los cuadros decodificables recorriendo el archivo completo.

        Es una pasada completa (cuesta tiempo), por eso se pide de forma
        explicita y el resultado queda en cache. Se usa una VideoCapture
        aparte para no mover la posicion del lector de cuadros.
        """
        if self._total_exacto is not None:
            return self._total_exacto

        cap = cv2.VideoCapture(str(self.ruta))
        if not cap.isOpened():
            raise VideoNoAbierto(f"OpenCV no pudo abrir el video: {self.ruta}")
        contados = 0
        try:
            while cap.grab():
                contados += 1
        finally:
            cap.release()

        avisos = []
        if not self._meta.fps_confiable:
            avisos.append(AVISO_FPS)
        avisos.append(
            f"Total de cuadros verificado decodificando el archivo completo: {contados}."
        )
        if self._meta.ancho <= 0 or self._meta.alto <= 0:
            avisos.append(AVISO_RESOLUCION)

        self._total_exacto = contados
        self._meta = replace(
            self._meta,
            total_cuadros=contados,
            total_cuadros_exacto=True,
            duracion_s=_duracion(contados, self._meta.fps),
            aviso=" ".join(avisos),
        )
        return contados

    # ----------------------------------------------------------------- lectura
    def leer_cuadro(self, indice: int):
        """Devuelve el cuadro `indice` (base 0) como arreglo BGR de NumPy."""
        if indice < 0:
            raise CuadroNoDisponible("El indice de cuadro no puede ser negativo.")
        total = self._total_exacto or self._meta.total_cuadros
        if total is not None and indice >= total and self._meta.total_cuadros_exacto:
            raise CuadroNoDisponible(
                f"El video tiene {total} cuadros (0 a {total - 1}); se pidio {indice}."
            )

        with self._lock:
            self._posicionar(indice)
            ok, cuadro = self._cap.read()
            if not ok or cuadro is None:
                self._pos = -1  # posicion desconocida tras un fallo de decodificacion
                raise CuadroNoDisponible(
                    f"El cuadro {indice} no se pudo decodificar; probablemente "
                    "esta mas alla del final del video."
                )
            self._pos = indice + 1
            return cuadro

    def _posicionar(self, indice: int) -> None:
        pos = self._pos
        if pos != indice:
            necesita_salto = pos < 0 or indice < pos or (indice - pos) > self.seek_forward_max
            if necesita_salto:
                self._saltar(indice)
                pos = self._pos
            while pos < indice:
                if not self._cap.grab():
                    self._pos = -1
                    raise CuadroNoDisponible(
                        f"El video termino antes del cuadro {indice}."
                    )
                pos += 1
                self._pos = pos

    def _saltar(self, indice: int) -> None:
        self._cap.set(cv2.CAP_PROP_POS_FRAMES, indice)
        reportada = self._posicion_reportada()
        if reportada is None or reportada > indice:
            # El contenedor no permite saltar con precision: se reabre el
            # archivo y se avanza desde el inicio. Es lento pero exacto.
            self._reabrir()
            reportada = 0
        self._pos = reportada

    def _posicion_reportada(self) -> int | None:
        valor = self._cap.get(cv2.CAP_PROP_POS_FRAMES)
        if valor is None or not math.isfinite(valor) or valor < 0:
            return None
        return int(round(valor))

    def _reabrir(self) -> None:
        self._cap.release()
        self._cap = cv2.VideoCapture(str(self.ruta))
        if not self._cap.isOpened():
            raise VideoNoAbierto(f"OpenCV no pudo reabrir el video: {self.ruta}")
        self._pos = 0

    def cerrar(self) -> None:
        with self._lock:
            self._cap.release()


class CatalogoVideos:
    """Descubre los videos de una carpeta y reutiliza lectores abiertos."""

    def __init__(self, raiz: Path, seek_forward_max: int = 60, max_lectores: int = 4) -> None:
        self.raiz = Path(raiz).expanduser().resolve()
        self.seek_forward_max = seek_forward_max
        self.max_lectores = max_lectores
        self._lectores: "OrderedDict[str, LectorVideo]" = OrderedDict()
        self._lock = threading.Lock()

    def listar(self) -> list[dict]:
        """Lista los videos disponibles sin abrirlos (no decodifica nada)."""
        if not self.raiz.is_dir():
            return []
        encontrados = []
        for ruta in sorted(self.raiz.rglob("*")):
            if not ruta.is_file() or ruta.suffix.lower() not in EXTENSIONES_VIDEO:
                continue
            estadisticas = ruta.stat()
            encontrados.append(
                {
                    "video_id": ruta.relative_to(self.raiz).as_posix(),
                    "nombre": ruta.name,
                    "tamano_bytes": estadisticas.st_size,
                    "mtime": estadisticas.st_mtime,
                }
            )
        return encontrados

    def ruta_de(self, video_id: str) -> Path:
        """Resuelve un identificador a una ruta dentro de la carpeta raiz."""
        if not video_id or video_id.startswith("/") or "\\" in video_id:
            raise VideoNoEncontrado(f"Identificador de video invalido: {video_id!r}")
        candidata = (self.raiz / video_id).resolve()
        if not candidata.is_relative_to(self.raiz):
            raise VideoNoEncontrado(f"Identificador fuera de la carpeta de videos: {video_id!r}")
        if not candidata.is_file():
            raise VideoNoEncontrado(f"No existe el video {video_id!r} en {self.raiz}")
        if candidata.suffix.lower() not in EXTENSIONES_VIDEO:
            raise VideoNoEncontrado(f"La extension de {video_id!r} no es de video")
        return candidata

    def lector(self, video_id: str) -> LectorVideo:
        ruta = self.ruta_de(video_id)
        with self._lock:
            lector = self._lectores.get(video_id)
            if lector is not None and lector.metadatos.mtime != ruta.stat().st_mtime:
                # El archivo cambio en disco: el lector en cache quedo obsoleto.
                lector.cerrar()
                del self._lectores[video_id]
                lector = None
            if lector is None:
                lector = LectorVideo(ruta, video_id, self.seek_forward_max)
                self._lectores[video_id] = lector
                while len(self._lectores) > self.max_lectores:
                    _, viejo = self._lectores.popitem(last=False)
                    viejo.cerrar()
            else:
                self._lectores.move_to_end(video_id)
            return lector

    def cerrar_todo(self) -> None:
        with self._lock:
            for lector in self._lectores.values():
                lector.cerrar()
            self._lectores.clear()
