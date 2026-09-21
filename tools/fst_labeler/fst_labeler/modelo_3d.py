"""Capa 1: modelo 3D preentrenado de Della Valle et al. (2025).

Red residual convolucional 3D que clasifica las tres conductas directamente
desde pixeles, sin estimacion de pose. Los pesos se publicaron junto al
articulo "Machine Learning-Based Model for Behavioral Analysis in Rodents:
Application to the Forced Swim Test".

Procedencia de los pesos
------------------------
Descargar de Zenodo, DOI 10.5281/zenodo.14638257, que declara **CC-BY-4.0**.
El repositorio de GitHub tiene los mismos archivos pero **sin licencia
declarada**, lo que por omision es todos los derechos reservados. Usar la
copia de Zenodo y citar a los autores, como exige CC-BY.

Que tan bien va a funcionar
---------------------------
Los autores reportan 86 +/- 4 % frente a puntuacion manual, y 88.89 % en
validacion. **Esa cifra es sobre SU montaje**: camara a 25 cm del piso y a
120 cm del cilindro, cilindro de plexiglas transparente, 704x576 a 25 Hz,
iluminacion controlada. Ellos mismos advierten que el modelo puede fallar con
iluminacion distinta, posicion de camara distinta y otra relacion de tamano
entre especimen y cilindro. Nuestro montaje difiere en las tres cosas: camara
de mano, 1280x720 a 29.45 Hz, cuatro cilindros por toma y contraluz de
ventana. Hay que esperar exactitud menor hasta calibrar con nuestros videos,
y esta capa entra al consenso del Modulo 7 como un votante mas, no como juez.

Preprocesamiento
----------------
Reproducido del codigo de los autores (`src/functionsDatagenerator.py` y
`Predictor/main.py`), no de la descripcion del articulo, que difiere en
varios puntos:

- La resta del fondo es `cv2.subtract` en BGR --saturada, no valor absoluto--
  y **despues** se pasa a gris.
- El enderezado lleva el cuadrilatero a 64 de ancho por 128 de alto, con las
  esquinas en sentido horario desde la superior izquierda.
- El tensor es (75, 128, 64, 1): **128 de alto y 64 de ancho**. La
  descripcion del proyecto dice "75x64x128"; el modelo real espera lo
  contrario y alimentarlo transpuesto da basura.
- En prediccion **no aplican desenfoque**: el valor por omision de la funcion
  es 5, pero el programa principal pasa 1, y el desenfoque solo se aplica por
  encima de 1.
- La normalizacion es min-max sobre el clip completo, no division entre 255.

Dos adaptaciones necesarias para nuestro material
-------------------------------------------------
1. **Remuestreo a 25 Hz.** El codigo de los autores lee el FPS del archivo y
   acto seguido lo sustituye por 25 fijo, y luego toma 75 cuadros
   **consecutivos**. En sus videos, que son de 25 Hz, eso equivale a 3 s.
   En los nuestros, de 29.45 Hz, 75 cuadros consecutivos son 2.55 s y el
   movimiento entre cuadros se ve mas lento del que el modelo aprendio, lo
   que sesga hacia inmovilidad. Por omision se remuestrea a 25 Hz para que
   cada clip cubra 3 s reales. `remuestrear=False` reproduce el
   comportamiento literal de los autores, util para validar contra sus
   propios videos.

2. **Fondo por mediana temporal.** Los autores usan el primer cuadro del
   archivo, que en su protocolo esta vacio porque empiezan a grabar antes de
   meter al especimen; por eso su `start_time_array` va de 7 a 28 s. En
   nuestro material eso no se cumple: en el primer cuadro hay cilindros que
   ya tienen especimen dentro y otro al que lo estan soltando, y ademas la
   camara se mueve unos 205 px despues. La mediana temporal del recorte
   enderezado estima lo mismo --la escena sin el especimen-- y si existe
   siempre. `fondo="primer_cuadro"` usa el metodo literal de los autores.
"""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path

import cv2
import numpy as np

from . import estabilizacion

# Geometria del tensor que espera el modelo, leida del propio archivo de
# pesos: entrada (None, 75, 128, 64, 1).
CUADROS_CLIP = 75
ALTO = 128
ANCHO = 64
HZ_MODELO = 25.0
SEGUNDOS_CLIP = CUADROS_CLIP / HZ_MODELO   # 3.0 s

# Esquinas de destino del enderezado, en el mismo orden que usan los autores:
# 0 superior izquierda, 1 superior derecha, 2 inferior derecha, 3 inferior
# izquierda. Es el mismo orden en que el Modulo 3 normaliza las esquinas.
DESTINO = np.float32([[0, 0], [ANCHO, 0], [ANCHO, ALTO], [0, ALTO]])

# Correspondencia entre la salida softmax y las tres conductas, tomada de
# `src/Predictors.py` de los autores. No es el orden alfabetico ni el del
# articulo: hay que respetarlo tal cual.
CLASES = {0: "nado", 1: "inmovilidad", 2: "escalamiento"}

RUTA_PESOS = (
    Path(__file__).resolve().parent.parent
    / "modelo_3drcnn/ML_FST/src/TrainedModels/classes3/Model/model.h5"
)

EXACTITUD_AUTORES = (
    "Los autores reportan 86 ± 4 % frente a puntuación manual sobre SU montaje: "
    "cámara fija a 120 cm del cilindro, 704×576 a 25 Hz, iluminación controlada. "
    "Nuestro montaje difiere en cámara, resolución, cadencia y número de cilindros "
    "por toma, así que hay que esperar menos exactitud hasta calibrarlo con videos "
    "propios. Esta capa entra al consenso como un votante más, no como juez."
)


class ModeloNoDisponible(RuntimeError):
    """Faltan los pesos o falta TensorFlow."""


@dataclass
class Parametros:
    """Parametros de la inferencia. Los que no vienen del modelo son PUNTOS
    DE PARTIDA A CALIBRAR."""

    # Remuestrear a 25 Hz para que cada clip cubra 3 s reales. En False se
    # toman 75 cuadros consecutivos, como hace el codigo de los autores.
    remuestrear: bool = True

    # "mediana" o "primer_cuadro".
    fondo: str = "mediana"
    cuadro_fondo: int = 0
    muestras_fondo: int = 40

    # Seguir el movimiento de la camara y arrastrar las esquinas con el.
    estabilizar: bool = True
    cadencia_camara: int = 30

    # Clips por lote en la inferencia.
    lote: int = 8

    # Los autores pasan 1 en prediccion, que desactiva el desenfoque.
    desenfoque: int = 1

    # Cuantos clips analizar como maximo. None = hasta donde alcance el video.
    max_clips: int | None = None


@dataclass
class Clip:
    indice: int
    region: int
    cuadro_inicio: int
    cuadro_fin: int
    segundo_inicio: float
    segundo_fin: float
    clase: str = "sin datos"
    confianza: float = 0.0
    probabilidades: dict = field(default_factory=dict)
    aviso: str | None = None

    def to_dict(self) -> dict:
        return {
            "clip": self.indice,
            "region": self.region,
            "cuadro_inicio": self.cuadro_inicio,
            "cuadro_fin": self.cuadro_fin,
            "segundo_inicio": round(self.segundo_inicio, 2),
            "segundo_fin": round(self.segundo_fin, 2),
            "clase": self.clase,
            "confianza": round(self.confianza, 4),
            "probabilidades": {k: round(v, 4) for k, v in self.probabilidades.items()},
            "aviso": self.aviso,
        }


# ------------------------------------------------------------------- modelo

_modelo = None


def cargar_modelo(ruta: Path | None = None):
    """Carga los pesos una sola vez y los reutiliza.

    TensorFlow se importa aqui dentro, no arriba, para que los Modulos 1 a 4
    sigan funcionando en una instalacion sin TensorFlow.

    Los pesos son Keras 2.6 y TensorFlow 2.16 en adelante trae Keras 3, que no
    los carga. Por eso se usa `tf_keras`, la capa de compatibilidad con
    Keras 2, y se fija TF_USE_LEGACY_KERAS antes de importar.
    """
    global _modelo
    if _modelo is not None:
        return _modelo

    ruta = Path(ruta or RUTA_PESOS)
    if not ruta.is_file():
        raise ModeloNoDisponible(
            f"No se encontraron los pesos en {ruta}. Descárgalos de Zenodo "
            "(DOI 10.5281/zenodo.14638257, CC-BY-4.0); el archivo es "
            "ML_FST/src/TrainedModels/classes3/Model/model.h5."
        )

    os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "3")
    os.environ["TF_USE_LEGACY_KERAS"] = "1"
    try:
        import tf_keras
    except ImportError as e:
        raise ModeloNoDisponible(
            "Falta TensorFlow. Instala 'tensorflow==2.17.1' y 'tf-keras==2.17.0': "
            "los pesos son Keras 2.6 y Keras 3 no los carga."
        ) from e

    modelo = tf_keras.models.load_model(str(ruta), compile=False)
    esperado = (None, CUADROS_CLIP, ALTO, ANCHO, 1)
    if tuple(modelo.input_shape) != esperado:
        raise ModeloNoDisponible(
            f"El modelo espera {modelo.input_shape} y este codigo prepara {esperado}."
        )
    _modelo = modelo
    return _modelo


# ------------------------------------------------------- preprocesamiento

def _enderezar(imagen: np.ndarray, esquinas: list[dict]) -> np.ndarray:
    origen = np.float32([[p["x"], p["y"]] for p in esquinas])
    matriz = cv2.getPerspectiveTransform(origen, DESTINO)
    return cv2.warpPerspective(imagen, matriz, (ANCHO, ALTO))


def _a_gris(bgr: np.ndarray, desenfoque: int) -> np.ndarray:
    gris = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    if desenfoque > 1:
        gris = cv2.GaussianBlur(gris, (desenfoque, desenfoque), 0)
    return gris


def _normalizar(pila: np.ndarray) -> tuple[np.ndarray, str | None]:
    """Min-max sobre el clip completo, como en el codigo de los autores.

    Su version divide entre (max - min) sin comprobar que sean distintos. Un
    clip totalmente uniforme --region fuera del cuadro, video en negro-- le
    daria una division entre cero. Aqui se detecta y se avisa.
    """
    pila = pila.astype(np.uint8)
    mn, mx = int(pila.min()), int(pila.max())
    if mx == mn:
        return np.zeros(pila.shape, np.float32), (
            "El clip es completamente uniforme: la región no está captando nada."
        )
    return ((pila.astype(np.float32) - mn) / (mx - mn)), None


def _indices_del_clip(inicio: int, fps: float, k: int, remuestrear: bool) -> list[int]:
    if not remuestrear:
        base = inicio + k * CUADROS_CLIP
        return list(range(base, base + CUADROS_CLIP))
    t0 = k * SEGUNDOS_CLIP
    return [
        inicio + int(round((t0 + j / HZ_MODELO) * fps)) for j in range(CUADROS_CLIP)
    ]


# ------------------------------------------------------------------ analisis

def analizar(
    ruta: str,
    regiones: list[dict],
    fps: float,
    total_cuadros: int,
    cuadro_referencia: int = 0,
    cuadro_inicio: int = 0,
    parametros: Parametros | None = None,
    ruta_pesos: Path | None = None,
) -> dict:
    """Corre el modelo sobre un video y devuelve la prediccion por clip."""
    p = parametros or Parametros()
    if not regiones:
        raise ValueError("Hace falta al menos una región de interés para analizar.")
    if fps <= 0:
        raise ValueError("Los cuadros por segundo deben ser mayores que cero.")

    modelo = cargar_modelo(ruta_pesos)

    cap = cv2.VideoCapture(ruta)
    if not cap.isOpened():
        raise RuntimeError(f"OpenCV no pudo abrir el video: {ruta}")

    try:
        cap.set(cv2.CAP_PROP_POS_FRAMES, cuadro_referencia)
        ok, cuadro = cap.read()
        if not ok:
            raise RuntimeError(f"No se pudo leer el cuadro de referencia {cuadro_referencia}.")
        alto, ancho = cuadro.shape[:2]

        seguidor = None
        aviso_camara = None
        if p.estabilizar:
            mascara = estabilizacion.mascara_fuera_de(regiones, alto, ancho)
            seguidor = estabilizacion.SeguidorCamara(
                cv2.cvtColor(cuadro, cv2.COLOR_BGR2GRAY), mascara
            )
            if not seguidor.utilizable:
                seguidor = None
                aviso_camara = (
                    "No hay textura suficiente fuera de las regiones para seguir el "
                    "movimiento de la cámara; el análisis corre sin compensarlo."
                )

        # Cuantos clips caben desde el cuadro de inicio.
        if p.remuestrear:
            disponibles = int((total_cuadros - cuadro_inicio) / (SEGUNDOS_CLIP * fps))
        else:
            disponibles = (total_cuadros - cuadro_inicio) // CUADROS_CLIP
        n_clips = disponibles if p.max_clips is None else min(disponibles, p.max_clips)
        if n_clips < 1:
            raise ValueError(
                "El rango es demasiado corto: no cabe ni un clip de "
                f"{SEGUNDOS_CLIP:.0f} s desde el cuadro {cuadro_inicio}."
            )

        fondos = _construir_fondos(
            cap, regiones, seguidor, p, cuadro_inicio,
            cuadro_inicio + int(n_clips * SEGUNDOS_CLIP * fps),
        )
        clips = _recorrer(cap, regiones, fondos, seguidor, p, fps, cuadro_inicio, n_clips, modelo)
    finally:
        cap.release()

    por_region = []
    for i in range(len(regiones)):
        propios = [c for c in clips if c.region == i + 1]
        por_region.append(_resumen_region(i + 1, propios, aviso_camara))

    return {
        "video": ruta,
        "fps": round(fps, 4),
        "cuadro_referencia": cuadro_referencia,
        "cuadro_inicio": cuadro_inicio,
        "clips_por_region": n_clips,
        "segundos_por_clip": SEGUNDOS_CLIP,
        "parametros": {
            "remuestrear_a_25hz": p.remuestrear,
            "fondo": p.fondo,
            "cuadro_fondo": p.cuadro_fondo,
            "estabilizar": p.estabilizar,
            "desenfoque": p.desenfoque,
        },
        "advertencia_exactitud": EXACTITUD_AUTORES,
        "regiones": por_region,
    }


def _resumen_region(numero: int, clips: list[Clip], aviso_camara: str | None) -> dict:
    validos = [c for c in clips if c.clase in CLASES.values()]
    conteo = {nombre: sum(c.clase == nombre for c in validos) for nombre in CLASES.values()}
    total = len(validos) or 1
    avisos = []
    if aviso_camara:
        avisos.append(aviso_camara)
    bajos = [c for c in validos if c.confianza < 0.70]
    if bajos:
        avisos.append(
            f"{len(bajos)} de {len(validos)} clips con confianza por debajo de 0.70, "
            "que es el umbral que el sistema principal usa para detener el análisis "
            "y emitir un reporte de diagnóstico (RN-11)."
        )
    return {
        "region": numero,
        "clips": len(clips),
        "clips_validos": len(validos),
        "conteo": conteo,
        "porcentajes": {k: round(100.0 * v / total, 1) for k, v in conteo.items()},
        "confianza_media": round(
            float(np.mean([c.confianza for c in validos])) if validos else 0.0, 4
        ),
        "avisos": avisos,
        "detalle": [c.to_dict() for c in clips],
    }


def _construir_fondos(cap, regiones, seguidor, p: Parametros, inicio, fin):
    """Fondo por region, ya enderezado a 64x128 y en BGR."""
    if p.fondo == "primer_cuadro":
        cap.set(cv2.CAP_PROP_POS_FRAMES, p.cuadro_fondo)
        ok, cuadro = cap.read()
        if not ok:
            raise RuntimeError(f"No se pudo leer el cuadro de fondo {p.cuadro_fondo}.")
        # Con un solo cuadro de fondo se respeta el orden de los autores: la
        # resta va en el cuadro completo y el enderezado viene despues. Se
        # guarda el cuadro entero y la resta se hace luego.
        return {"modo": "cuadro", "cuadro": cuadro}

    if p.fondo != "mediana":
        raise ValueError(f"Modo de fondo desconocido: {p.fondo!r}")

    indices = np.linspace(inicio, max(inicio, fin - 1), p.muestras_fondo).astype(int)
    pilas = [[] for _ in regiones]
    for n in indices:
        cap.set(cv2.CAP_PROP_POS_FRAMES, int(n))
        ok, cuadro = cap.read()
        if not ok:
            continue
        matriz = None
        if seguidor is not None:
            matriz, _ = seguidor.matriz(cv2.cvtColor(cuadro, cv2.COLOR_BGR2GRAY))
        for i, region in enumerate(regiones):
            esquinas = estabilizacion.aplicar(matriz, region["esquinas"])
            pilas[i].append(_enderezar(cuadro, esquinas))
    if not pilas[0]:
        raise RuntimeError("No se pudo leer ningún cuadro para construir el fondo.")
    return {
        "modo": "mediana",
        "recortes": [np.median(np.stack(pila), axis=0).astype(np.uint8) for pila in pilas],
    }


def _recorrer(cap, regiones, fondos, seguidor, p: Parametros, fps, inicio, n_clips, modelo):
    """Una sola pasada por el video, recogiendo los cuadros que cada clip pide."""
    # Indices que hara falta leer, por clip y region.
    plan = {}
    for k in range(n_clips):
        for j, n in enumerate(_indices_del_clip(inicio, fps, k, p.remuestrear)):
            plan.setdefault(n, []).append((k, j))
    necesarios = sorted(plan)

    pilas = {
        (k, i): [None] * CUADROS_CLIP
        for k in range(n_clips)
        for i in range(len(regiones))
    }

    cap.set(cv2.CAP_PROP_POS_FRAMES, necesarios[0])
    actual = necesarios[0]
    matriz = None
    por_leer = set(necesarios)

    while por_leer:
        ok, cuadro = cap.read()
        if not ok:
            break
        if actual in por_leer:
            por_leer.discard(actual)
            gris_completo = cv2.cvtColor(cuadro, cv2.COLOR_BGR2GRAY)
            if seguidor is not None and (actual - inicio) % p.cadencia_camara == 0:
                nueva, inliers = seguidor.matriz(gris_completo)
                if nueva is not None and inliers >= estabilizacion.MIN_INLIERS:
                    matriz = nueva
            for i, region in enumerate(regiones):
                esquinas = estabilizacion.aplicar(matriz, region["esquinas"])
                if fondos["modo"] == "cuadro":
                    # Orden de los autores: restar en el cuadro completo y
                    # enderezar despues.
                    resta = cv2.subtract(cuadro, fondos["cuadro"])
                    recorte = _enderezar(resta, esquinas)
                else:
                    # Con fondo por mediana la resta va en el espacio ya
                    # enderezado, que es donde la mediana esta alineada.
                    recorte = cv2.subtract(_enderezar(cuadro, esquinas), fondos["recortes"][i])
                gris = _a_gris(recorte, p.desenfoque)
                for (k, j) in plan[actual]:
                    pilas[(k, i)][j] = gris
        actual += 1

    return _predecir(pilas, regiones, p, fps, inicio, n_clips, modelo)


def _predecir(pilas, regiones, p: Parametros, fps, inicio, n_clips, modelo):
    clips: list[Clip] = []
    tensores, refs = [], []

    for k in range(n_clips):
        indices = _indices_del_clip(inicio, fps, k, p.remuestrear)
        for i in range(len(regiones)):
            clip = Clip(
                indice=k + 1,
                region=i + 1,
                cuadro_inicio=indices[0],
                cuadro_fin=indices[-1],
                segundo_inicio=k * SEGUNDOS_CLIP,
                segundo_fin=(k + 1) * SEGUNDOS_CLIP,
            )
            pila = pilas[(k, i)]
            if any(g is None for g in pila):
                clip.aviso = "Faltaron cuadros para completar el clip."
                clips.append(clip)
                continue
            tensor, aviso = _normalizar(np.stack(pila))
            if aviso:
                clip.aviso = aviso
                clips.append(clip)
                continue
            tensores.append(tensor)
            refs.append(clip)
            clips.append(clip)

    for desde in range(0, len(tensores), p.lote):
        lote = np.stack(tensores[desde:desde + p.lote])[..., np.newaxis]
        salida = modelo.predict(lote, verbose=0)
        for fila, clip in zip(salida, refs[desde:desde + p.lote]):
            idx = int(np.argmax(fila))
            clip.clase = CLASES[idx]
            clip.confianza = float(fila[idx])
            clip.probabilidades = {CLASES[j]: float(fila[j]) for j in range(3)}

    return clips
