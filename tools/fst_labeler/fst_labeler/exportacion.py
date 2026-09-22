"""Suavizado temporal y exportacion final a CSV (Modulo 9).

Ultimo eslabon de la cadena. Toma lo que ya existe en la base del Modulo 8 --
los clips que el consenso acepto solo y los que una persona decidio-- y
produce el CSV que alimenta el entrenamiento del clasificador de produccion.
No mide nada nuevo ni corre ninguna capa: es una lectura de la base seguida de
una limpieza sobre la secuencia de clases.

De donde sale la clase final de cada bloque
--------------------------------------------
Por prioridad:

  1. Si hay una decision humana y no es descarte, esa etiqueta manda, siempre
     --incluso sobre un bloque que el consenso habia aceptado solo--. El
     Modulo 8 no ofrece corregir un aceptado desde su cola, pero si alguien
     decidio ese `clip_id` por otra via, la decision pesa mas que el consenso.
  2. Si no hay decision y el bloque quedo `aceptado`, se usa la clase del
     consenso.
  3. Un bloque descartado por una persona queda FUERA del CSV: no describe
     ninguna conducta.
  4. Un bloque `discrepancia` sin decision todavia esta PENDIENTE: no se
     exporta, y el resumen lo cuenta aparte para que se sepa que el CSV es
     parcial.
  5. Un bloque `sin_datos` nunca llega a la cola del Modulo 8 --ninguna capa
     pudo medirlo-- asi que tampoco se puede decidir. Queda fuera del CSV y el
     resumen lo cuenta aparte, en vez de desaparecerlo en silencio.

Suavizado: por que opera sobre la secuencia, no sobre el tiempo
-----------------------------------------------------------------
Un especimen no alterna de conducta en medio segundo: un bloque de 5 s que
discrepa de los que lo rodean por los dos lados es mas probable que sea un
error de alguna capa que un cambio real. El voto mayoritario en una ventana
centrada corrige eso sin que nadie tenga que mirar cada bloque otra vez.

La ventana opera sobre la secuencia de bloques QUE VAN AL CSV, por (video,
region), en el orden en que ocurren -- no sobre el tiempo real del video. Un
bloque pendiente o descartado abre un hueco real en el video, pero aqui
simplemente no esta: sus vecinos en la secuencia son el bloque exportable
anterior y el siguiente, esten a 5 s o a 5 minutos de distancia. Es una
simplificacion deliberada: exigir continuidad temporal estricta dejaria sin
suavizar cualquier bloque junto a uno pendiente, que es exactamente donde mas
factible es un error de una capa.

Un empate en la ventana --por ejemplo dos conductas con el mismo numero de
votos-- no se resuelve por sorteo ni por orden alfabetico: no hay una
conducta "por defecto". El bloque se deja como estaba y se cuenta aparte en el
resumen, igual que las reglas geometricas dejan escrito un empate en vez de
decidirlo a ciegas (Modulo 6).

La ventana es un parametro, no una constante
----------------------------------------------
El valor por omision (3) es el minimo que tiene sentido: un solo bloque
flanqueado por otros dos que concuerdan entre si. Ampliarla suaviza mas fuerte
y puede borrar una transicion real y corta entre dos conductas; para saber
donde ponerla hay que medir, sobre el conjunto de prueba etiquetado a mano,
si el suavizado corrige errores o borra conducta real.
"""
from __future__ import annotations

import csv
import io
from collections import Counter, defaultdict

from .revision import BaseRevision

# Punto de partida a calibrar: ver la nota del modulo. Tiene que ser impar
# para que la ventana tenga un centro sin ambiguedad.
VENTANA_SUAVIZADO_PREDETERMINADA = 3

CAMPOS_CSV = [
    "clip_id", "video", "region", "bloque",
    "cuadro_inicio", "cuadro_fin", "segundo_inicio", "segundo_fin",
    "clase", "origen", "suavizado", "clase_antes_de_suavizar",
    "a_ciegas", "notas",
]


def validar_ventana(ventana: int) -> None:
    if ventana < 1 or ventana % 2 == 0:
        raise ValueError(
            "La ventana de suavizado debe ser un numero impar de al menos 1, para "
            "que tenga un centro sin ambiguedad."
        )


def suavizar_secuencia(
    clases: list[str], ventana: int
) -> tuple[list[str], list[int], list[int]]:
    """Voto mayoritario en ventana centrada sobre una secuencia de clases.

    Devuelve la secuencia suavizada, los indices que cambiaron de clase y los
    indices donde la ventana empato --y por tanto no se toco--. No inventa
    clases nuevas: cada posicion queda con una de las que ya traia la
    secuencia dentro de su ventana.
    """
    validar_ventana(ventana)
    n = len(clases)
    radio = ventana // 2
    resultado = list(clases)
    cambiados: list[int] = []
    empatados: list[int] = []
    for i in range(n):
        vecindario = clases[max(0, i - radio):min(n, i + radio + 1)]
        conteo = Counter(vecindario)
        maximo = max(conteo.values())
        ganadoras = [clase for clase, votos in conteo.items() if votos == maximo]
        if len(ganadoras) > 1:
            empatados.append(i)
            continue
        ganadora = ganadoras[0]
        if ganadora != clases[i]:
            resultado[i] = ganadora
            cambiados.append(i)
    return resultado, cambiados, empatados


def _clase_y_origen(clip: dict) -> tuple[str | None, str | None, str]:
    """Clase final y origen de un clip, o por que se excluye si no tiene.

    El tercer valor solo importa cuando la clase es None: 'pendiente' (en
    cola, sin decidir), 'descartado' (decision humana de excluirlo) o
    'sin_datos' (ninguna capa pudo medirlo).
    """
    decision = clip.get("decision")
    if decision:
        if decision["descartado"]:
            return None, None, "descartado"
        return decision["etiqueta_humana"], "humano", ""
    if clip["estado"] == "aceptado":
        return clip["clase_consenso"], "automatico", ""
    if clip["estado"] == "discrepancia":
        return None, None, "pendiente"
    return None, None, "sin_datos"  # clip["estado"] == "sin_datos"


def filas_exportables(
    base: BaseRevision, video: str | None, ventana: int
) -> tuple[list[dict], dict]:
    """Las filas listas para el CSV, ya suavizadas, mas un resumen.

    Agrupa por (video, region) porque cada region es un especimen distinto:
    suavizar a traves de dos especimenes mezclaria sus secuencias. Dentro de
    cada grupo, los clips ya vienen ordenados por cuadro de inicio (Modulo 8).
    """
    validar_ventana(ventana)
    datos = base.todos_los_clips(video=video)

    por_grupo: dict[tuple[str, int], list[dict]] = defaultdict(list)
    for clip in datos["clips"]:
        por_grupo[(clip["video"], clip["region"])].append(clip)

    resumen = {
        "exportados": 0, "pendientes": 0, "descartados": 0, "sin_datos": 0,
        "cambios_por_suavizado": 0, "empates_suavizado": 0,
        "por_video": {},
    }

    def _video_de(nombre: str) -> dict:
        return resumen["por_video"].setdefault(nombre, {
            "bloques": 0, "exportados": 0, "pendientes": 0,
            "descartados": 0, "sin_datos": 0, "listo": True,
        })

    filas: list[dict] = []
    for (nombre_video, _region), clips in por_grupo.items():
        resueltos: list[tuple[dict, str, str]] = []
        for clip in clips:
            resumen_video = _video_de(nombre_video)
            resumen_video["bloques"] += 1
            clase, origen, motivo = _clase_y_origen(clip)
            if clase is None:
                clave = {"pendiente": "pendientes", "descartado": "descartados",
                         "sin_datos": "sin_datos"}[motivo]
                resumen[clave] += 1
                resumen_video[clave] += 1
                if motivo == "pendiente":
                    resumen_video["listo"] = False
                continue
            resueltos.append((clip, clase, origen))

        secuencia = [clase for _, clase, _ in resueltos]
        suavizada, cambiados, empatados = (
            suavizar_secuencia(secuencia, ventana) if secuencia else ([], [], [])
        )
        cambiados = set(cambiados)
        resumen["empates_suavizado"] += len(empatados)

        for i, (clip, clase_original, origen) in enumerate(resueltos):
            fue_suavizado = i in cambiados
            decision = clip.get("decision")
            filas.append({
                "clip_id": clip["clip_id"],
                "video": clip["video"],
                "region": clip["region"],
                "bloque": clip["bloque"],
                "cuadro_inicio": clip["cuadro_inicio"],
                "cuadro_fin": clip["cuadro_fin"],
                "segundo_inicio": clip["segundo_inicio"],
                "segundo_fin": clip["segundo_fin"],
                "clase": suavizada[i],
                "origen": origen,
                "suavizado": fue_suavizado,
                "clase_antes_de_suavizar": clase_original if fue_suavizado else "",
                "a_ciegas": decision["a_ciegas"] if decision else "",
                "notas": (decision["notas"] or "") if decision else "",
            })
            resumen["exportados"] += 1
            _video_de(clip["video"])["exportados"] += 1
            if fue_suavizado:
                resumen["cambios_por_suavizado"] += 1

    filas.sort(key=lambda f: (f["video"], f["region"], f["cuadro_inicio"]))
    return filas, resumen


def exportar_csv(filas: list[dict]) -> str:
    """Texto CSV de las filas exportables, con encabezado."""
    buffer = io.StringIO()
    escritor = csv.DictWriter(buffer, fieldnames=CAMPOS_CSV, lineterminator="\n")
    escritor.writeheader()
    for fila in filas:
        escritor.writerow({campo: fila.get(campo, "") for campo in CAMPOS_CSV})
    return buffer.getvalue()
