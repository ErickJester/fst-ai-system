#!/usr/bin/env python3
"""Verificacion del Modulo 9: suavizado temporal y exportacion final a CSV.

Dos partes independientes:

  A. `suavizar_secuencia` como funcion pura, con secuencias de clases hechas
     a mano: un bloque aislado que se corrige, una transicion real que no se
     toca, un empate que se deja tal cual, una ventana mas ancha que la
     secuencia, la ventana minima (1, que es la identidad) y una secuencia
     vacia. Incluye un caso documentado de dos anomalias contiguas que se
     interfieren entre si -- no es un fallo, es como se comporta un filtro de
     un solo paso que vota sobre la secuencia ORIGINAL y no sobre la ya
     corregida; ver el modulo `exportacion.py`.

  B. La cadena completa sobre una base sqlite temporal: guardar una corrida
     con bloques aceptados por el consenso y otros que una persona decidio,
     exportar el CSV, y comprobar que reabrir la base --con una conexion
     nueva, como pasaria si se cierra la herramienta y se vuelve a abrir--
     entrega exactamente lo mismo. Despues se decide el ultimo bloque
     pendiente y se comprueba que la siguiente exportacion ya lo incluye, sin
     tocar nada de lo que ya estaba resuelto.

No necesita video, TensorFlow ni navegador.

Uso:
    python scripts/verificar_exportacion.py
"""
import csv
import io
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fst_labeler import exportacion
from fst_labeler.revision import BaseRevision

FPS = 30.0
CUADROS_BLOQUE = 150   # 5 s

fallos = []


def check(nombre, obtenido, esperado):
    if obtenido != esperado:
        fallos.append(nombre)
        print(f"  FALLA  {nombre}: esperaba {esperado!r}, obtuvo {obtenido!r}")
    else:
        print(f"  ok     {nombre} = {obtenido!r}")


def falla_con(nombre, excepcion, funcion):
    try:
        funcion()
    except excepcion as error:
        print(f"  ok     {nombre}: {type(error).__name__}: {error}")
        return
    fallos.append(nombre)
    print(f"  FALLA  {nombre}: no lanzo {excepcion.__name__}")


# ===========================================================================
print("== Parte A: suavizar_secuencia como funcion pura ==")
# ===========================================================================

r, cambiados, empatados = exportacion.suavizar_secuencia(
    ["nado", "nado", "escalamiento", "nado", "nado"], 3
)
check("bloque aislado: se corrige", r, ["nado", "nado", "nado", "nado", "nado"])
check("bloque aislado: un solo cambio", cambiados, [2])
check("bloque aislado: sin empates", empatados, [])

r, cambiados, empatados = exportacion.suavizar_secuencia(
    ["nado", "nado", "nado", "inmovilidad", "inmovilidad", "inmovilidad"], 3
)
check("transicion real: no se toca", r,
      ["nado", "nado", "nado", "inmovilidad", "inmovilidad", "inmovilidad"])
check("transicion real: sin cambios", cambiados, [])

r, cambiados, empatados = exportacion.suavizar_secuencia(
    ["nado", "escalamiento", "inmovilidad"], 3
)
check("tres clases distintas: nadie tiene mayoria, no se toca", r,
      ["nado", "escalamiento", "inmovilidad"])
check("tres clases distintas: las tres posiciones empatan", empatados, [0, 1, 2])
check("tres clases distintas: ningun cambio pese al empate", cambiados, [])

r, cambiados, empatados = exportacion.suavizar_secuencia(["nado", "nado", "escalamiento"], 5)
check("ventana mas ancha que la secuencia no revienta, usa lo que hay", r,
      ["nado", "nado", "nado"])
check("ventana ancha: el bloque minoritario se corrige igual", cambiados, [2])

entrada = ["nado", "escalamiento", "inmovilidad"]
r, cambiados, empatados = exportacion.suavizar_secuencia(entrada, 1)
check("ventana 1 es la identidad: cada bloque es su propia mayoria", r, entrada)
check("ventana 1: ningun cambio posible", cambiados, [])

check("secuencia vacia no revienta", exportacion.suavizar_secuencia([], 3), ([], [], []))

# Dos anomalias a un bloque de distancia: la de la izquierda (indice 2) se
# corrige con sus dos vecinos en 'nado'; pero la ventana del indice 3 --que
# ERA un 'nado' legitimo-- todavia ve el valor ORIGINAL sin corregir del
# indice 2 ('escalamiento') mas el de la derecha (indice 4, tambien
# 'escalamiento'), y esos dos ganan 2 a 1 sobre el propio 'nado' del indice 3.
# Es el precio de votar en paralelo sobre la secuencia original -- no en
# cadena sobre lo ya corregido, que introduciria una asimetria izquierda a
# derecha que este proyecto evita en todos los demas modulos (Otsu, el orden
# de las reglas geometricas, el desempate de la Capa 3).
r, cambiados, empatados = exportacion.suavizar_secuencia(
    ["nado", "nado", "escalamiento", "nado", "escalamiento", "inmovilidad"], 3
)
check("dos anomalias contiguas: la primera se corrige", r[2], "nado")
check("dos anomalias contiguas: la SEGUNDA (nado legitimo) se contagia", r[3], "escalamiento")
check("dos anomalias contiguas: los ultimos dos quedan empatados", empatados, [4, 5])

falla_con("ventana par se rechaza", ValueError, lambda: exportacion.suavizar_secuencia(["nado"], 2))
falla_con("ventana cero se rechaza", ValueError, lambda: exportacion.suavizar_secuencia(["nado"], 0))
falla_con("ventana negativa se rechaza", ValueError, lambda: exportacion.suavizar_secuencia(["nado"], -1))


# ===========================================================================
print("\n== Parte B: la cadena completa sobre una base sqlite ==")
# ===========================================================================

REGION_UNICA = [{
    "esquinas": [{"x": 0.0, "y": 0.0}, {"x": 200.0, "y": 0.0},
                 {"x": 200.0, "y": 300.0}, {"x": 0.0, "y": 300.0}],
    "lineaAgua": None,
}]


def bloque_aceptado(numero, clase):
    inicio = (numero - 1) * CUADROS_BLOQUE
    return {
        "clip_id": f"video.mp4|r1|{inicio}-{inicio + CUADROS_BLOQUE - 1}",
        "video": "video.mp4", "region": 1, "bloque": numero,
        "cuadro_inicio": inicio, "cuadro_fin": inicio + CUADROS_BLOQUE - 1,
        "segundo_inicio": inicio / FPS, "segundo_fin": (inicio + CUADROS_BLOQUE - 1) / FPS,
        "estado": "aceptado", "clase_consenso": clase, "motivos": [],
        "capas": {"modelo": {"clase": clase}, "reglas": {"clase": clase}},
    }


def bloque_discrepancia(numero):
    inicio = (numero - 1) * CUADROS_BLOQUE
    return {
        "clip_id": f"video.mp4|r1|{inicio}-{inicio + CUADROS_BLOQUE - 1}",
        "video": "video.mp4", "region": 1, "bloque": numero,
        "cuadro_inicio": inicio, "cuadro_fin": inicio + CUADROS_BLOQUE - 1,
        "segundo_inicio": inicio / FPS, "segundo_fin": (inicio + CUADROS_BLOQUE - 1) / FPS,
        "estado": "discrepancia", "clase_consenso": None, "motivos": ["desacuerdo"],
        "capas": {"modelo": {"clase": "nado"}, "reglas": {"clase": "escalamiento"}},
    }


# b1,b2 nado (aceptados) | b3 escalamiento aislado (decidido a mano) |
# b4,b5 nado (aceptados) | b6 inmovilidad (aceptado) | b7 pendiente |
# b8 inmovilidad (aceptado) | b9 descartado (decidido a mano)
bloques = [
    bloque_aceptado(1, "nado"),
    bloque_aceptado(2, "nado"),
    bloque_discrepancia(3),
    bloque_aceptado(4, "nado"),
    bloque_aceptado(5, "nado"),
    bloque_aceptado(6, "inmovilidad"),
    bloque_discrepancia(7),
    bloque_aceptado(8, "inmovilidad"),
    bloque_discrepancia(9),
]
informe = {
    "video": "video.mp4",
    "regiones_dibujadas": REGION_UNICA,
    "cuadro_referencia": 0,
    "rejilla": {"fps": FPS, "segundos_por_bloque": 5.0},
    "parametros": {}, "capas_presentes": {}, "avisos": [],
    "aceptados": [b for b in bloques if b["estado"] == "aceptado"],
    "cola": [b for b in bloques if b["estado"] == "discrepancia"],
    "sin_datos": [],
}

with tempfile.TemporaryDirectory() as carpeta:
    ruta = Path(carpeta) / "revision.sqlite3"
    base = BaseRevision(ruta)
    guardada = base.guardar_corrida(informe)
    corrida_id = guardada["corrida_id"]

    esquinas = REGION_UNICA[0]["esquinas"]
    base.decidir("video.mp4|r1|300-449", corrida_id, "escalamiento", False, esquinas, None)
    base.decidir("video.mp4|r1|1200-1349", corrida_id, None, True, esquinas, None,
                  notas="cuerpo fuera de la region, oclusion")
    # b7 (900-1049) se deja PENDIENTE a proposito.

    print("\n-- Antes de decidir el pendiente --")
    filas, resumen = exportacion.filas_exportables(base, video=None, ventana=3)
    check("bloques totales", resumen["por_video"]["video.mp4"]["bloques"], 9)
    check("exportables (9 - 1 pendiente - 1 descartado)", resumen["exportados"], 7)
    check("pendientes", resumen["pendientes"], 1)
    check("descartados", resumen["descartados"], 1)
    check("sin_datos", resumen["sin_datos"], 0)
    check("un solo cambio por suavizado (b3)", resumen["cambios_por_suavizado"], 1)
    check("sin empates", resumen["empates_suavizado"], 0)
    check("el video no esta listo: queda 1 pendiente", resumen["por_video"]["video.mp4"]["listo"], False)

    por_bloque = {f["bloque"]: f for f in filas}
    check("b1 origen automatico", por_bloque[1]["origen"], "automatico")
    check("b3 origen humano (se decidio a mano)", por_bloque[3]["origen"], "humano")
    check("b3 quedo suavizado a nado, no escalamiento", por_bloque[3]["clase"], "nado")
    check("b3 guarda la clase de antes de suavizar", por_bloque[3]["clase_antes_de_suavizar"], "escalamiento")
    check("b1 no se suavizo: sin clase_antes_de_suavizar", por_bloque[1]["clase_antes_de_suavizar"], "")
    check("b6 y b8 (transicion real) no se tocan", (por_bloque[6]["clase"], por_bloque[8]["clase"]),
          ("inmovilidad", "inmovilidad"))
    check("b7 (pendiente) no aparece en las filas", 7 in por_bloque, False)
    check("b9 (descartado) no aparece en las filas", 9 in por_bloque, False)

    texto_csv = exportacion.exportar_csv(filas)
    filas_csv = list(csv.DictReader(io.StringIO(texto_csv)))
    check("el CSV tiene una fila por bloque exportable", len(filas_csv), 7)
    check("el encabezado trae los campos declarados", list(filas_csv[0].keys()), exportacion.CAMPOS_CSV)
    fila_b3 = next(f for f in filas_csv if f["bloque"] == "3")
    check("el CSV serializa el booleano de suavizado como texto", fila_b3["suavizado"], "True")
    check("el CSV trae la clase original de b3", fila_b3["clase_antes_de_suavizar"], "escalamiento")

    print("\n-- Reabrir la base con una conexion nueva: mismo resultado --")
    base_reabierta = BaseRevision(ruta)
    filas_2, resumen_2 = exportacion.filas_exportables(base_reabierta, video=None, ventana=3)
    check("mismo numero de filas tras reabrir", len(filas_2), len(filas))
    check("mismo resumen tras reabrir", resumen_2, resumen)
    check("mismas clases finales tras reabrir",
          {f["bloque"]: f["clase"] for f in filas_2}, {f["bloque"]: f["clase"] for f in filas})

    print("\n-- Se decide el pendiente; la siguiente exportacion ya lo incluye --")
    base_reabierta.decidir("video.mp4|r1|900-1049", corrida_id, "inmovilidad", False, esquinas, None)
    filas_3, resumen_3 = exportacion.filas_exportables(BaseRevision(ruta), video=None, ventana=3)
    check("ya no quedan pendientes", resumen_3["pendientes"], 0)
    check("el video queda listo", resumen_3["por_video"]["video.mp4"]["listo"], True)
    check("ahora exporta 8 bloques (7 + el recien decidido)", resumen_3["exportados"], 8)
    por_bloque_3 = {f["bloque"]: f for f in filas_3}
    check("b7 entra con la clase decidida, sin suavizar", por_bloque_3[7]["clase"], "inmovilidad")
    check("b7 origen humano", por_bloque_3[7]["origen"], "humano")
    check("b3 sigue igual que antes: nada se recalculo de mas", por_bloque_3[3]["clase"], "nado")
    check("sigue habiendo un solo cambio por suavizado (el de b3)", resumen_3["cambios_por_suavizado"], 1)

    print("\n-- Un video pedido por nombre que no existe --")
    filas_4, resumen_4 = exportacion.filas_exportables(BaseRevision(ruta), video="otro.mp4", ventana=3)
    check("sin filas para un video sin corridas", filas_4, [])
    check("resumen vacio para un video sin corridas", resumen_4["exportados"], 0)

    falla_con("ventana invalida en filas_exportables", ValueError,
              lambda: exportacion.filas_exportables(BaseRevision(ruta), None, 4))

print()
if fallos:
    print(f"{len(fallos)} comprobaciones fallaron.")
    sys.exit(1)
print("Todas las comprobaciones pasaron.")
