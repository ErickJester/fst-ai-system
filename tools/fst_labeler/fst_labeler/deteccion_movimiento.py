"""Capa 2 (detector de movimiento por umbral) y Capa 0 (compuerta de cordura).

Reimplementacion en Python del enfoque de DBscorer (Nandi et al., eNeuro 2021),
sin MATLAB: se segmenta al especimen por sustraccion de fondo dentro de la
region de interes, y se mide cuanta de esa area cambia entre cuadros. El
promedio por bloque de 5 s decide si el bloque fue inmovil o activo.

Por que sustraccion de fondo y no diferencia directa entre cuadros
------------------------------------------------------------------
Medido sobre material real del laboratorio: la diferencia entre cuadros
consecutivos cambia el 37 % del encuadre por encima de 20 niveles de gris, y
esa senal NO viene del especimen. Viene de la banda de agua agitada, que cruza
los cuatro cilindros, y del temblor de la camara sobre los bordes verticales
de alto contraste. Compensar la traslacion de la camara reduce esa senal solo
un 2.8 %: no alcanza.

La mediana temporal del recorte resuelve el problema. El cilindro, la mesa y
el nivel medio del agua estan en casi todos los cuadros y sobreviven a la
mediana; el especimen esta en un sitio distinto en cada cuadro y desaparece de
ella. Restar esa mediana deja al especimen como un blanco solido. Es ademas lo
que hace el metodo original, que tambien parte de sustraccion de fondo.

Unidad de analisis
------------------
Bloques de 5 s con la conducta predominante, siguiendo a Detke et al. (1995).
Alonso-Fernandez et al. (2015) compararon intervalos de 3, 5 y 10 s en
especimenes Wistar sin encontrar diferencias, asi que la duracion del bloque es un
parametro, no una constante del metodo.

Lo que esta capa NO puede decidir
---------------------------------
Distingue inmovil de activo, dos clases. No separa nado de escalamiento: esa
distincion necesita geometria (Modulo 6) o el modelo preentrenado (Modulo 5).
El Modulo 7 tiene que tratarla como un votante sobre el eje de inmovilidad,
no como un votante de tres clases.
"""
from __future__ import annotations

from dataclasses import dataclass, field

import cv2
import numpy as np

from . import estabilizacion

# --------------------------------------------------------------------------
# Prior de conducta del laboratorio colaborador.
#
# Tomado de la Tabla 4 de la tesis de maestria de la Seccion de Posgrado
# (ENMyH-IPN), sobre especimenes Wistar hembra en el ensayo de 5 min (300 s), seis
# grupos experimentales. En porcentaje del ensayo la inmovilidad va de 23.2 %
# (grupo tratado con electroacupuntura) a 52.4 % (grupo estresado).
#
# ADVERTENCIA: no se usa el prior de ~75 % que circula en la descripcion del
# proyecto. Ese valor no corresponde a este laboratorio; su maximo registrado
# es 52 %. Una compuerta anclada en 75 % marcaria como sospechoso todo
# analisis correcto. La revision de Bogdanova et al. (Physiol Behav, 2017)
# confirma ademas que no existe un prior universal: en especimenes Wistar control la
# inmovilidad publicada cubre todo el rango de 0 a 300 s segun el estudio.
PRIOR_INMOVILIDAD = (23.2, 52.4)

# Razon de varianzas que produce una distribucion normal cortada en su media:
# 2/pi. Sirve de referencia para saber si un corte separa dos grupos de verdad
# o solo parte por la mitad una distribucion de una sola joroba.
REFERENCIA_UNIMODAL = 2.0 / 3.141592653589793
# Por encima de esto se acepta que hay dos grupos. Punto de partida a calibrar.
UMBRAL_SEPARACION = 0.75
# Margen de holgura sobre ese rango antes de marcar el resultado como
# sospechoso. Punto de partida a calibrar.
HOLGURA_PRIOR = 10.0


@dataclass
class Parametros:
    """Parametros del detector. Los valores por omision son PUNTOS DE PARTIDA
    A CALIBRAR con los videos del laboratorio, no resultados experimentales."""

    # Duracion del bloque de analisis. 5 s es el estandar de Detke et al.
    segundos_por_bloque: float = 5.0

    # Cuanto tiene que separarse un pixel del fondo para contar como especimen,
    # en niveles de gris. Medido sobre material real: con 40 el especimen sale
    # como un blanco solido y la mota del fondo queda por debajo.
    umbral_binarizacion: int = 40

    # Fraccion del area de la region que debe cambiar entre cuadros para que
    # el bloque cuente como activo. En None se calcula por el metodo de Otsu
    # sobre la distribucion de bloques del propio video, que no introduce
    # ninguna constante pero supone que la distribucion es bimodal. Calibrar
    # puntuando a mano 3 o 4 videos, como indica el articulo de DBscorer.
    umbral_actividad: float | None = None

    # Cuadros de los que se toma la mediana para construir el fondo.
    muestras_fondo: int = 40

    # Cada cuantos cuadros se vuelve a estimar el movimiento de la camara.
    cadencia_camara: int = 30

    # Compensar el movimiento de la camara arrastrando las esquinas.
    estabilizar: bool = True

    # Lado mayor del recorte rectificado, en pixeles. Acota el costo sin
    # perder detalle util.
    lado_maximo: int = 480

    # Iteraciones de apertura morfologica para quitar mota antes de medir.
    apertura: int = 1

    def cuadros_por_bloque(self, fps: float) -> int:
        return max(1, int(round(self.segundos_por_bloque * fps)))


@dataclass
class Bloque:
    indice: int
    cuadro_inicio: int
    cuadro_fin: int
    segundo_inicio: float
    segundo_fin: float
    cambio_medio: float = 0.0
    area_especimen_media: float = 0.0
    cuadros_medidos: int = 0
    camara_desplazamiento_px: float = 0.0
    clase: str = "sin datos"

    def to_dict(self) -> dict:
        return {
            "bloque": self.indice,
            "cuadro_inicio": self.cuadro_inicio,
            "cuadro_fin": self.cuadro_fin,
            "segundo_inicio": round(self.segundo_inicio, 2),
            "segundo_fin": round(self.segundo_fin, 2),
            "cambio_medio": round(self.cambio_medio, 5),
            "area_especimen_media": round(self.area_especimen_media, 5),
            "cuadros_medidos": self.cuadros_medidos,
            "camara_desplazamiento_px": round(self.camara_desplazamiento_px, 1),
            "clase": self.clase,
        }


@dataclass
class ResultadoRegion:
    region: int
    bloques: list[Bloque] = field(default_factory=list)
    umbral_usado: float = 0.0
    origen_umbral: str = ""
    avisos: list[str] = field(default_factory=list)

    @property
    def porcentaje_inmovilidad(self) -> float:
        medidos = [b for b in self.bloques if b.clase in ("inmovil", "activo")]
        if not medidos:
            return 0.0
        return 100.0 * sum(b.clase == "inmovil" for b in medidos) / len(medidos)

    def to_dict(self) -> dict:
        medidos = [b for b in self.bloques if b.clase in ("inmovil", "activo")]
        return {
            "region": self.region,
            "bloques_totales": len(self.bloques),
            "bloques_medidos": len(medidos),
            "bloques_inmovil": sum(b.clase == "inmovil" for b in medidos),
            "bloques_activo": sum(b.clase == "activo" for b in medidos),
            "porcentaje_inmovilidad": round(self.porcentaje_inmovilidad, 1),
            "umbral_usado": round(self.umbral_usado, 5),
            "origen_umbral": self.origen_umbral,
            "compuerta_cordura": self._compuerta(),
            "avisos": self.avisos,
            "bloques": [b.to_dict() for b in self.bloques],
        }

    def _compuerta(self) -> dict:
        """Capa 0: contrasta el resultado contra el prior del laboratorio.

        No descarta nada. Marca el resultado como sospechoso y explica por
        que, para que quien revise decida.
        """
        pct = self.porcentaje_inmovilidad
        bajo, alto = PRIOR_INMOVILIDAD
        dentro = bajo - HOLGURA_PRIOR <= pct <= alto + HOLGURA_PRIOR
        mensaje = (
            f"{pct:.1f} % de bloques inmóviles. El rango medido por el "
            f"laboratorio en la prueba de 5 min es {bajo}-{alto} %"
            + (
                "; el resultado es compatible."
                if dentro
                else f", y este resultado queda fuera incluso con {HOLGURA_PRIOR} "
                     "puntos de holgura. Revisa la región de interés, el umbral "
                     "de actividad y el movimiento de cámara antes de usarlo."
            )
        )
        return {
            "sospechoso": not dentro,
            "porcentaje_inmovilidad": round(pct, 1),
            "prior_laboratorio": list(PRIOR_INMOVILIDAD),
            "holgura": HOLGURA_PRIOR,
            "mensaje": mensaje,
        }


# --------------------------------------------------------------------- utiles
#
# Publicas a proposito: el Modulo 6 (reglas geometricas) mide sobre la MISMA
# mascara del especimen que esta capa, en lugar de segmentar por su cuenta.
# Dos segmentaciones distintas harian que las capas discreparan por el
# recorte y no por la conducta, y el consenso del Modulo 7 no podria
# distinguir una cosa de la otra.

def _lado(a: dict, b: dict) -> float:
    return float(np.hypot(a["x"] - b["x"], a["y"] - b["y"]))


def tamano_rectificado(esquinas: list[dict], lado_maximo: int) -> tuple[int, int]:
    """Tamano del recorte enderezado, a partir de los lados del cuadrilatero.

    El ancho y el alto salen de los lados reales del cuadrilatero y se escalan
    por igual, asi que el recorte conserva la relacion de aspecto de la camara
    de natacion. De eso depende que el Modulo 6 pueda leer la verticalidad del
    especimen directamente en pixeles del recorte.
    """
    ancho = (_lado(esquinas[0], esquinas[1]) + _lado(esquinas[3], esquinas[2])) / 2
    alto = (_lado(esquinas[1], esquinas[2]) + _lado(esquinas[0], esquinas[3])) / 2
    ancho, alto = max(8.0, ancho), max(8.0, alto)
    escala = min(1.0, lado_maximo / max(ancho, alto))
    return max(8, int(round(ancho * escala))), max(8, int(round(alto * escala)))


def matriz_rectificacion(esquinas: list[dict], tamano: tuple[int, int]) -> np.ndarray:
    """Homografia que lleva el cuadrilatero al recorte enderezado.

    Se expone aparte de `rectificar` porque el Modulo 6 necesita llevar al
    mismo espacio puntos que no son imagen --los dos extremos de la linea de
    agua-- con `cv2.perspectiveTransform`.
    """
    ancho, alto = tamano
    origen = np.float32([[p["x"], p["y"]] for p in esquinas])
    destino = np.float32([[0, 0], [ancho - 1, 0], [ancho - 1, alto - 1], [0, alto - 1]])
    return cv2.getPerspectiveTransform(origen, destino)


def rectificar(gris: np.ndarray, esquinas: list[dict], tamano: tuple[int, int]) -> np.ndarray:
    """Endereza el cuadrilatero a un rectangulo con transformacion de perspectiva.

    Es la misma rectificacion que exige el preprocesamiento del modelo
    preentrenado del Modulo 5, por eso la region se dibuja con cuatro esquinas
    y no como rectangulo alineado a los ejes.
    """
    return cv2.warpPerspective(gris, matriz_rectificacion(esquinas, tamano), tamano)


def mascara_especimen(recorte: np.ndarray, fondo: np.ndarray, p: "Parametros") -> np.ndarray:
    """Segmenta al especimen: lo que se aparta del fondo, sin la mota."""
    binaria = (cv2.absdiff(recorte, fondo) > p.umbral_binarizacion).astype(np.uint8)
    if p.apertura > 0:
        nucleo = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (3, 3))
        binaria = cv2.morphologyEx(binaria, cv2.MORPH_OPEN, nucleo, iterations=p.apertura)
    # El especimen es un cuerpo unico: se conserva el componente conexo mayor
    # y se descarta el resto, que es reflejo, burbuja o ruido de compresion.
    n, etiquetas, estadisticas, _ = cv2.connectedComponentsWithStats(binaria, connectivity=8)
    if n <= 1:
        return binaria
    mayor = 1 + int(np.argmax(estadisticas[1:, cv2.CC_STAT_AREA]))
    return (etiquetas == mayor).astype(np.uint8)


def umbral_otsu(valores: np.ndarray) -> float:
    """Corte de Otsu sobre las puntuaciones de bloque, sin constantes."""
    if valores.size < 2 or float(valores.max()) <= float(valores.min()):
        return float(valores.max()) if valores.size else 0.0
    escala = 255.0 / float(valores.max())
    enteros = np.clip(valores * escala, 0, 255).astype(np.uint8)
    corte, _ = cv2.threshold(enteros, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    return float(corte) / escala


def razon_de_varianzas(valores: np.ndarray, umbral: float) -> float | None:
    """Cuanto separa un corte: varianza entre grupos sobre varianza total.

    Otsu siempre devuelve un corte, incluso sobre una distribucion de una sola
    joroba, y en ese caso el corte no significa nada. Esta razon es el delator:
    una distribucion normal cortada en su media da exactamente 2/pi, y una con
    dos modos separados da bastante mas.

    Devuelve None si el corte dejo todos los valores de un mismo lado.
    """
    bajos, altos = valores[valores <= umbral], valores[valores > umbral]
    if bajos.size == 0 or altos.size == 0:
        return None
    media, total = valores.mean(), float(valores.var())
    if total <= 0:
        return None
    entre = (
        bajos.size * (bajos.mean() - media) ** 2
        + altos.size * (altos.mean() - media) ** 2
    ) / valores.size
    return float(entre / total)


# ------------------------------------------------------------------ analisis

def analizar(
    ruta: str,
    regiones: list[dict],
    fps: float,
    total_cuadros: int,
    cuadro_referencia: int = 0,
    cuadro_inicio: int = 0,
    cuadro_fin: int | None = None,
    parametros: Parametros | None = None,
) -> dict:
    """Corre el detector sobre un video y devuelve el reporte por region.

    `regiones` son las del Modulo 3, con las esquinas en pixeles del archivo
    original y referidas a `cuadro_referencia`, que es el cuadro sobre el que
    se dibujaron.
    """
    p = parametros or Parametros()
    if not regiones:
        raise ValueError("Hace falta al menos una región de interés para analizar.")
    if fps <= 0:
        raise ValueError("Los cuadros por segundo deben ser mayores que cero.")

    fin = total_cuadros if cuadro_fin is None else min(cuadro_fin, total_cuadros)
    if fin - cuadro_inicio < 2:
        raise ValueError("El rango de análisis es demasiado corto.")

    cap = cv2.VideoCapture(ruta)
    if not cap.isOpened():
        raise RuntimeError(f"OpenCV no pudo abrir el video: {ruta}")

    try:
        cap.set(cv2.CAP_PROP_POS_FRAMES, cuadro_referencia)
        ok, cuadro = cap.read()
        if not ok:
            raise RuntimeError(f"No se pudo leer el cuadro de referencia {cuadro_referencia}.")
        gris_ref = cv2.cvtColor(cuadro, cv2.COLOR_BGR2GRAY)
        alto, ancho = gris_ref.shape

        seguidor = None
        aviso_camara = None
        if p.estabilizar:
            mascara = estabilizacion.mascara_fuera_de(regiones, alto, ancho)
            seguidor = estabilizacion.SeguidorCamara(gris_ref, mascara)
            if not seguidor.utilizable:
                seguidor = None
                aviso_camara = (
                    "No hay textura suficiente fuera de las regiones para seguir el "
                    "movimiento de la cámara; el análisis corre sin compensarlo."
                )

        tamanos = [tamano_rectificado(r["esquinas"], p.lado_maximo) for r in regiones]

        fondos = construir_fondos(
            cap, regiones, tamanos, seguidor, p.muestras_fondo, cuadro_inicio, fin
        )
        resultados = _recorrer(
            cap, regiones, tamanos, fondos, seguidor, p, fps, cuadro_inicio, fin
        )
    finally:
        cap.release()

    for r in resultados:
        if aviso_camara:
            r.avisos.insert(0, aviso_camara)

    return {
        "video": ruta,
        "fps": round(fps, 4),
        "cuadro_referencia": cuadro_referencia,
        "cuadro_inicio": cuadro_inicio,
        "cuadro_fin": fin,
        "cuadros_por_bloque": p.cuadros_por_bloque(fps),
        "parametros": {
            "segundos_por_bloque": p.segundos_por_bloque,
            "umbral_binarizacion": p.umbral_binarizacion,
            "umbral_actividad": p.umbral_actividad,
            "muestras_fondo": p.muestras_fondo,
            "cadencia_camara": p.cadencia_camara,
            "estabilizar": p.estabilizar,
            "apertura": p.apertura,
            "nota": "Puntos de partida a calibrar con los videos del laboratorio.",
        },
        "regiones": [r.to_dict() for r in resultados],
    }


def construir_fondos(cap, regiones, tamanos, seguidor, muestras: int, inicio, fin):
    """Mediana temporal del recorte de cada region, en el espacio rectificado.

    El cilindro, la mesa y el nivel medio del agua estan en casi todos los
    cuadros y sobreviven a la mediana; el especimen esta en un sitio distinto
    en cada cuadro y desaparece de ella.
    """
    indices = np.linspace(inicio, max(inicio, fin - 1), muestras).astype(int)
    pilas = [[] for _ in regiones]
    for n in indices:
        cap.set(cv2.CAP_PROP_POS_FRAMES, int(n))
        ok, cuadro = cap.read()
        if not ok:
            continue
        gris = cv2.cvtColor(cuadro, cv2.COLOR_BGR2GRAY)
        matriz = None
        if seguidor is not None:
            matriz, _ = seguidor.matriz(gris)
        for i, region in enumerate(regiones):
            esquinas = estabilizacion.aplicar(matriz, region["esquinas"])
            pilas[i].append(rectificar(gris, esquinas, tamanos[i]))
    fondos = []
    for i, pila in enumerate(pilas):
        if not pila:
            raise RuntimeError("No se pudo leer ningun cuadro para construir el fondo.")
        fondos.append(np.median(np.stack(pila), axis=0).astype(np.uint8))
    return fondos


def _recorrer(cap, regiones, tamanos, fondos, seguidor, p: Parametros, fps, inicio, fin):
    """Pasada secuencial: mide el cambio de area por cuadro y agrupa por bloque."""
    por_bloque = p.cuadros_por_bloque(fps)
    resultados = [ResultadoRegion(region=i + 1) for i in range(len(regiones))]
    for i, resultado in enumerate(resultados):
        for b, arranque in enumerate(range(inicio, fin, por_bloque)):
            cierre = min(arranque + por_bloque, fin)
            resultado.bloques.append(
                Bloque(
                    indice=b + 1,
                    cuadro_inicio=arranque,
                    cuadro_fin=cierre - 1,
                    segundo_inicio=(arranque - inicio) / fps,
                    segundo_fin=(cierre - 1 - inicio) / fps,
                )
            )

    cambios = [[[] for _ in resultados[0].bloques] for _ in regiones]
    areas = [[[] for _ in resultados[0].bloques] for _ in regiones]
    desplazamientos = [[] for _ in resultados[0].bloques]

    cap.set(cv2.CAP_PROP_POS_FRAMES, inicio)
    previas = [None] * len(regiones)
    matriz = None
    desplazamiento = 0.0

    for n in range(inicio, fin):
        ok, cuadro = cap.read()
        if not ok:
            break
        gris = cv2.cvtColor(cuadro, cv2.COLOR_BGR2GRAY)
        b = min((n - inicio) // por_bloque, len(desplazamientos) - 1)

        if seguidor is not None and (n - inicio) % p.cadencia_camara == 0:
            nueva, inliers = seguidor.matriz(gris)
            if nueva is not None and inliers >= estabilizacion.MIN_INLIERS:
                matriz = nueva
                desplazamiento = float(np.hypot(nueva[0, 2], nueva[1, 2]))
        desplazamientos[b].append(desplazamiento)

        for i, region in enumerate(regiones):
            esquinas = estabilizacion.aplicar(matriz, region["esquinas"])
            recorte = rectificar(gris, esquinas, tamanos[i])
            mascara = mascara_especimen(recorte, fondos[i], p)
            areas[i][b].append(float(mascara.mean()))
            if previas[i] is not None:
                cambio = float(np.logical_xor(mascara, previas[i]).mean())
                cambios[i][b].append(cambio)
            previas[i] = mascara

    for i, resultado in enumerate(resultados):
        for b, bloque in enumerate(resultado.bloques):
            if cambios[i][b]:
                bloque.cambio_medio = float(np.mean(cambios[i][b]))
                bloque.area_especimen_media = float(np.mean(areas[i][b]))
                bloque.cuadros_medidos = len(cambios[i][b])
            if desplazamientos[b]:
                bloque.camara_desplazamiento_px = float(np.mean(desplazamientos[b]))

    # Un solo umbral para todo el video, juntando los bloques de las cuatro
    # regiones. El umbral describe las condiciones de grabacion --la misma
    # camara, la misma luz, el mismo cilindro-- no al especimen. Uno por
    # region normalizaria a cada especimen contra si mismo y todos saldrian
    # cerca del 50 % de inmovilidad, borrando justo la diferencia entre
    # especimenes que el experimento busca medir.
    juntos = np.array(
        [b.cambio_medio for r in resultados for b in r.bloques if b.cuadros_medidos],
        dtype=np.float64,
    )
    if juntos.size == 0:
        for resultado in resultados:
            resultado.avisos.append("Ningún bloque pudo medirse.")
        return resultados

    if p.umbral_actividad is not None:
        umbral = float(p.umbral_actividad)
        origen = "fijado a mano"
    else:
        umbral = umbral_otsu(juntos)
        origen = (
            "calculado por el método de Otsu sobre los bloques de todas las regiones "
            "de este video; supone que la distribución es bimodal y debe calibrarse "
            "puntuando a mano 3 o 4 videos, como indica el artículo de DBscorer"
        )

    for resultado in resultados:
        resultado.umbral_usado = umbral
        resultado.origen_umbral = origen
        for bloque in resultado.bloques:
            if bloque.cuadros_medidos:
                bloque.clase = "activo" if bloque.cambio_medio > umbral else "inmovil"
        propios = np.array(
            [b.cambio_medio for b in resultado.bloques if b.cuadros_medidos], dtype=np.float64
        )
        if propios.size:
            _avisos_de_calidad(resultado, propios)

    _aviso_de_separacion(resultados, juntos, umbral)
    return resultados


def _aviso_de_separacion(resultados, valores: np.ndarray, umbral: float) -> None:
    """Avisa si la distribucion de bloques no se separa en dos grupos.

    Otsu siempre devuelve un corte, incluso sobre una distribucion de una sola
    joroba: en ese caso parte por la mitad algo que no tiene dos modos, y el
    porcentaje de inmovilidad que sale de ahi no significa nada.

    El delator es la razon entre la varianza entre grupos y la varianza total.
    La referencia no es arbitraria: una distribucion normal cortada en su media
    da exactamente 2/pi = 0.637. Una distribucion con dos modos separados da
    valores muy por encima. Se avisa por debajo de REFERENCIA_UNIMODAL mas un
    margen, porque cerca de ese valor el corte no es evidencia de dos grupos.
    """
    razon = razon_de_varianzas(valores, umbral)
    if razon is None:
        mensaje = "El umbral dejó todos los bloques de un solo lado."
    else:
        if razon >= UMBRAL_SEPARACION:
            return
        mensaje = (
            f"La distribución de puntuaciones no se separa en dos grupos (razón de "
            f"varianzas {razon:.2f}; una distribución de una sola joroba cortada en "
            f"su media ya da {REFERENCIA_UNIMODAL:.2f}). El corte automático está "
            "partiendo por la mitad algo que no tiene dos modos, así que el "
            "porcentaje de inmovilidad no es interpretable todavía: fija el umbral "
            "a mano tras puntuar 3 o 4 videos."
        )
    for resultado in resultados:
        resultado.avisos.append(mensaje)


def _avisos_de_calidad(resultado: ResultadoRegion, valores: np.ndarray) -> None:
    """Senala condiciones bajo las que el numero no se debe creer."""
    if float(valores.max()) - float(valores.min()) < 1e-6:
        resultado.avisos.append(
            "Todos los bloques dieron la misma puntuación: el detector no está "
            "separando nada. Revisa que la región encierre la cámara de natación."
        )
    desplazados = [b for b in resultado.bloques if b.camara_desplazamiento_px > 50]
    if desplazados:
        resultado.avisos.append(
            f"{len(desplazados)} bloques con la cámara desplazada más de 50 px "
            "respecto al cuadro donde se dibujó la región. Se compensó, pero "
            "conviene verificar que la región sigue encuadrando el cilindro."
        )
    vacios = [b for b in resultado.bloques if b.cuadros_medidos and b.area_especimen_media < 0.005]
    if vacios:
        resultado.avisos.append(
            f"{len(vacios)} bloques con menos del 0.5 % del área segmentada como "
            "espécimen. O el espécimen no está en la región, o el umbral de "
            "binarización es demasiado alto."
        )
