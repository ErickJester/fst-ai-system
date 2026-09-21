"""Seguimiento del movimiento de la camara (Modulo 4).

Los videos del laboratorio se graban con camara de mano, no con camara fija.
Medido sobre material real (5.3 min, 1280x720): la camara se reencuadra unos
205 px en los primeros ~18 s mientras se acomoda, y despues conserva una
deriva residual de 25 px de mediana y 78 px de maxima, casi toda vertical.

Eso rompe el supuesto de que una region de interes dibujada sobre un cuadro
sirve para todo el video: sobre un cilindro de ~300 px de ancho, 78 px es una
cuarta parte del cilindro. Este modulo estima la transformacion de la camara
respecto al cuadro donde se dibujo la region y permite arrastrar sus esquinas
con ella.

El estimador se apoya en la parte estatica de la escena. Por omision, esa
parte es todo lo que queda FUERA de las regiones de interes: si las regiones
encierran los cilindros, lo de fuera es la mesa, las paredes y el fondo, que
no se mueven. RANSAC descarta como atipicos los pocos puntos moviles que
queden dentro de esa zona.
"""
from __future__ import annotations

from dataclasses import dataclass

import cv2
import numpy as np


# Puntos de partida a calibrar con los videos del laboratorio.
CARACTERISTICAS = 3000      # puntos ORB por cuadro
MIN_EMPAREJAMIENTOS = 15    # por debajo de esto no se intenta la estimacion
MIN_INLIERS = 30            # por debajo de esto la estimacion no es confiable
REPROYECCION_PX = 3.0       # tolerancia de RANSAC, en pixeles


@dataclass(frozen=True)
class Encuadre:
    """Transformacion de la camara entre el cuadro de referencia y otro."""

    cuadro: int
    dx: float
    dy: float
    inliers: int
    confiable: bool

    @property
    def desplazamiento(self) -> float:
        return float(np.hypot(self.dx, self.dy))


class SeguidorCamara:
    """Estima cuanto se ha movido la camara respecto a un cuadro de referencia.

    Solo se modela traslacion y rotacion rigida (`estimateAffinePartial2D`):
    la camara se mueve en la mano del operador, no cambia de lente, asi que no
    hay razon para permitir deformaciones que un estimador mas libre podria
    inventar a partir del ruido.
    """

    def __init__(self, gris_referencia: np.ndarray, mascara: np.ndarray | None = None) -> None:
        self._orb = cv2.ORB_create(CARACTERISTICAS)
        self._bf = cv2.BFMatcher(cv2.NORM_HAMMING, crossCheck=True)
        self._mascara = mascara
        self._kp_ref, self._des_ref = self._orb.detectAndCompute(gris_referencia, mascara)
        self.puntos_referencia = 0 if self._kp_ref is None else len(self._kp_ref)

    @property
    def utilizable(self) -> bool:
        """Falso si el cuadro de referencia no tiene textura suficiente."""
        return self._des_ref is not None and len(self._des_ref) >= MIN_EMPAREJAMIENTOS

    def matriz(self, gris: np.ndarray) -> tuple[np.ndarray | None, int]:
        """Matriz afin 2x3 que lleva del cuadro de referencia a `gris`."""
        if not self.utilizable:
            return None, 0
        kp, des = self._orb.detectAndCompute(gris, self._mascara)
        if des is None or len(des) < MIN_EMPAREJAMIENTOS:
            return None, 0
        pares = self._bf.match(self._des_ref, des)
        if len(pares) < MIN_EMPAREJAMIENTOS:
            return None, 0
        pares = sorted(pares, key=lambda p: p.distance)[:300]
        origen = np.float32([self._kp_ref[p.queryIdx].pt for p in pares])
        destino = np.float32([kp[p.trainIdx].pt for p in pares])
        matriz, inliers = cv2.estimateAffinePartial2D(
            origen, destino, method=cv2.RANSAC, ransacReprojThreshold=REPROYECCION_PX
        )
        if matriz is None or inliers is None:
            return None, 0
        return matriz, int(inliers.sum())

    def encuadre(self, gris: np.ndarray, cuadro: int) -> Encuadre:
        matriz, inliers = self.matriz(gris)
        if matriz is None:
            return Encuadre(cuadro, 0.0, 0.0, inliers, confiable=False)
        return Encuadre(
            cuadro=cuadro,
            dx=float(matriz[0, 2]),
            dy=float(matriz[1, 2]),
            inliers=inliers,
            confiable=inliers >= MIN_INLIERS,
        )


def mascara_fuera_de(regiones: list, alto: int, ancho: int, margen: int = 0) -> np.ndarray:
    """Mascara de la escena estatica: todo lo que queda fuera de las regiones."""
    mascara = np.full((alto, ancho), 255, np.uint8)
    for region in regiones:
        puntos = np.int32([[p["x"], p["y"]] for p in region["esquinas"]])
        if margen:
            centro = puntos.mean(axis=0)
            puntos = np.int32(centro + (puntos - centro) * (1 + margen / 100.0))
        cv2.fillConvexPoly(mascara, puntos, 0)
    return mascara


def aplicar(matriz: np.ndarray | None, puntos: list[dict]) -> list[dict]:
    """Arrastra las esquinas de una region con el movimiento de la camara."""
    if matriz is None:
        return [dict(p) for p in puntos]
    arreglo = np.float32([[[p["x"], p["y"]]] for p in puntos])
    movidos = cv2.transform(arreglo, matriz).reshape(-1, 2)
    return [{"x": float(x), "y": float(y)} for x, y in movidos]


def detectar_inicio_estable(
    ruta: str, regiones: list, cadencia: int, total: int
) -> dict:
    """Busca el cuadro a partir del cual la camara deja de reacomodarse.

    El operador coloca la camara y la ajusta durante los primeros segundos de
    grabacion. Una region dibujada sobre esos cuadros no corresponde al resto
    del video. En vez de fijar un numero de segundos a omitir, se mide: se
    compara cada muestra contra la posicion tipica de la segunda mitad del
    video y se devuelve el primer cuadro que ya esta dentro de la dispersion
    normal de esa segunda mitad.

    Asi el umbral sale de la deriva del propio video, no de una constante.
    """
    cap = cv2.VideoCapture(ruta)
    if not cap.isOpened():
        raise RuntimeError(f"OpenCV no pudo abrir el video: {ruta}")
    try:
        ok, cuadro = cap.read()
        if not ok:
            raise RuntimeError("No se pudo leer el primer cuadro del video.")
        alto, ancho = cuadro.shape[:2]
        mascara = mascara_fuera_de(regiones, alto, ancho)

        # La referencia es la mitad del video: ahi la camara ya esta colocada.
        cap.set(cv2.CAP_PROP_POS_FRAMES, total // 2)
        ok, medio = cap.read()
        if not ok:
            raise RuntimeError("No se pudo leer el cuadro central del video.")
        seguidor = SeguidorCamara(cv2.cvtColor(medio, cv2.COLOR_BGR2GRAY), mascara)
        if not seguidor.utilizable:
            return {
                "cuadro_estable": 0,
                "detectado": False,
                "motivo": "La escena no tiene textura suficiente fuera de las regiones "
                          "para seguir el movimiento de la cámara.",
                "muestras": [],
            }

        muestras = []
        for n in range(0, total, cadencia):
            cap.set(cv2.CAP_PROP_POS_FRAMES, n)
            ok, f = cap.read()
            if not ok:
                break
            e = seguidor.encuadre(cv2.cvtColor(f, cv2.COLOR_BGR2GRAY), n)
            muestras.append(e)
    finally:
        cap.release()

    confiables = [m for m in muestras if m.confiable]
    if len(confiables) < 4:
        return {
            "cuadro_estable": 0,
            "detectado": False,
            "motivo": "No hubo suficientes estimaciones confiables del movimiento de cámara.",
            "muestras": [_a_dict(m) for m in muestras],
        }

    # Dispersion normal: la de la segunda mitad, cuando ya esta colocada.
    segunda = [m.desplazamiento for m in confiables[len(confiables) // 2:]]
    tolerancia = float(np.percentile(segunda, 90)) if segunda else 0.0

    cuadro_estable = 0
    for i, m in enumerate(confiables):
        if m.desplazamiento <= tolerancia and all(
            p.desplazamiento <= tolerancia * 2 for p in confiables[i:i + 3]
        ):
            cuadro_estable = m.cuadro
            break

    return {
        "cuadro_estable": cuadro_estable,
        "detectado": True,
        "tolerancia_px": round(tolerancia, 1),
        "deriva_mediana_px": round(float(np.median(segunda)), 1),
        "deriva_maxima_px": round(float(np.max(segunda)), 1),
        "muestras": [_a_dict(m) for m in muestras],
    }


def _a_dict(e: Encuadre) -> dict:
    return {
        "cuadro": e.cuadro,
        "dx": round(e.dx, 1),
        "dy": round(e.dy, 1),
        "desplazamiento_px": round(e.desplazamiento, 1),
        "inliers": e.inliers,
        "confiable": e.confiable,
    }
