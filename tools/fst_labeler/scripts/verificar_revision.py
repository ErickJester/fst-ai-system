#!/usr/bin/env python3
"""Verificacion del Modulo 8: base de la revision humana.

Recorre con reportes sinteticos todo lo que la interfaz de revision le pide a
la base: guardar una corrida del consenso, leer la cola, decidir clips con y
sin correccion de geometria, rechazar decisiones mal formadas y conservar las
decisiones al volver a mandar el mismo video. Al final lee el archivo sqlite
directamente, sin pasar por la clase, para comprobar lo que quedo en disco.

No necesita video, navegador ni TensorFlow, y no toca la base real: trabaja
sobre un archivo temporal que se borra al terminar.

Uso:
    python scripts/verificar_revision.py
"""
import copy
import json
import sqlite3
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fst_labeler import consenso
from fst_labeler.revision import BaseRevision, ClipNoEncontrado

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


def region(x0, y0=40.0, ancho=200.0, alto=300.0, agua_y=120.0):
    return {
        "esquinas": [
            {"x": x0, "y": y0}, {"x": x0 + ancho, "y": y0},
            {"x": x0 + ancho, "y": y0 + alto}, {"x": x0, "y": y0 + alto},
        ],
        "lineaAgua": [{"x": x0, "y": agua_y}, {"x": x0 + ancho, "y": agua_y + 4}],
    }


REGIONES = [region(50.0), region(400.0)]


def bloque(i, desfase, **campos):
    inicio = desfase + i * CUADROS_BLOQUE
    return {
        "bloque": i + 1,
        "cuadro_inicio": inicio,
        "cuadro_fin": inicio + CUADROS_BLOQUE - 1,
        "segundo_inicio": inicio / FPS,
        "segundo_fin": (inicio + CUADROS_BLOQUE - 1) / FPS,
        **campos,
    }


def informes(regiones=REGIONES, desfase=0, video="prueba.mp4"):
    """Cuatro bloques por region: el primero de acuerdo, los demas en disputa."""
    reglas_por_bloque = [("nado", 0.9), ("escalamiento", 0.9), ("nado", 0.4), ("inmovilidad", 0.9)]
    mov_por_bloque = ["activo", "activo", "activo", "activo"]
    comun = {"video": video, "fps": FPS, "cuadro_inicio": desfase, "cuadro_referencia": desfase,
             "cuadros_por_bloque": CUADROS_BLOQUE, "regiones_dibujadas": regiones}
    mov = {**comun, "parametros": {"segundos_por_bloque": 5.0}, "regiones": [
        {"region": r + 1, "bloques": [
            bloque(i, desfase, clase=c, cambio_medio=0.05, camara_desplazamiento_px=3.0)
            for i, c in enumerate(mov_por_bloque)
        ]} for r in range(len(regiones))
    ]}
    reg = {**comun, "segundos_por_bloque": 5.0, "regiones": [
        {"region": r + 1, "bloques": [
            bloque(i, desfase, clase=c, margen_voto=m, empate=False,
                   votos={"nado": 100}, medianas={"desplazamiento": 0.3})
            for i, (c, m) in enumerate(reglas_por_bloque)
        ]} for r in range(len(regiones))
    ]}
    # El modelo dice nado con firmeza en todo el tramo: coincide con las
    # reglas en el bloque 1, discrepa en el 2 y el 4, y el 3 cae por margen.
    probabilidades = {"nado": 0.9, "inmovilidad": 0.05, "escalamiento": 0.05}
    mod = {**comun, "regiones": [
        {"region": r + 1, "detalle": [
            {"clip": i + 1, "cuadro_inicio": desfase + i * CUADROS_BLOQUE,
             "cuadro_fin": desfase + (i + 1) * CUADROS_BLOQUE - 1,
             "clase": "nado", "confianza": 0.9, "probabilidades": probabilidades}
            for i in range(4)
        ]} for r in range(len(regiones))
    ]}
    return mov, mod, reg


def consenso_de(**kw):
    mov, mod, reg = informes(**kw)
    return consenso.combinar(mov, mod, reg)


with tempfile.TemporaryDirectory() as carpeta:
    ruta = Path(carpeta) / "revision.sqlite3"
    base = BaseRevision(ruta)

    print("\n== El consenso lleva la geometria con que corrieron las capas ==")
    informe = consenso_de()
    check("regiones_dibujadas viajan en el reporte", informe["regiones_dibujadas"], REGIONES)
    check("cuadro_referencia viaja en el reporte", informe["cuadro_referencia"], 0)
    check("aceptados", informe["totales"]["aceptados"], 2)
    check("en cola", informe["totales"]["en_cola"], 6)

    mov, mod, reg = informes()
    movida = copy.deepcopy(REGIONES)
    movida[0]["esquinas"][0]["x"] += 30
    reg["regiones_dibujadas"] = movida
    mod["cuadro_referencia"] = 900
    avisos = consenso.combinar(mov, mod, reg)["avisos"]
    check("aviso si las capas corrieron con regiones distintas",
          any("mismas regiones" in a for a in avisos), True)
    check("aviso si las capas tomaron cuadros de referencia distintos",
          any("cuadros de referencia distintos" in a for a in avisos), True)

    print("\n== Guardar la corrida ==")
    guardada = base.guardar_corrida(informe)
    check("corrida_id", guardada["corrida_id"], 1)
    check("clips guardados (aceptados + cola)", guardada["clips"], 8)
    check("en cola", guardada["en_cola"], 6)
    check("sin avisos en la primera corrida", guardada["avisos"], [])
    sin_geometria = dict(informe)
    sin_geometria.pop("regiones_dibujadas")
    falla_con("rechaza un reporte sin geometria", ValueError,
              lambda: base.guardar_corrida(sin_geometria))

    print("\n== La cola ==")
    cola = base.cola()
    check("total en cola", cola["total"], 6)
    check("pendientes", cola["pendientes"], 6)
    check("orden por region y tiempo", [(k["region"], k["bloque"]) for k in cola["clips"]],
          [(1, 2), (1, 3), (1, 4), (2, 2), (2, 3), (2, 4)])
    primero = cola["clips"][0]
    check("cada clip lleva lo que dijo cada capa", sorted(primero["capas"]),
          ["modelo", "movimiento", "reglas"])
    check("la corrida lleva sus regiones", cola["corridas"][1]["regiones"], REGIONES)
    a, b, c = cola["clips"][0], cola["clips"][1], cola["clips"][3]

    print("\n== Decidir: sin correccion, con ROI corregida, descartado con linea corregida ==")
    original_a = REGIONES[a["region"] - 1]
    d = base.decidir(a["clip_id"], 1, "nado", False,
                     original_a["esquinas"], original_a["lineaAgua"], notas="  ")
    check("A: etiqueta", d["etiqueta_humana"], "nado")
    check("A: roi_corregida", d["roi_corregida"], False)
    check("A: linea_agua_corregida", d["linea_agua_corregida"], False)
    check("A: sin coordenadas nuevas", (d["esquinas_nuevas"], d["linea_agua_nueva"]), (None, None))
    check("A: notas vacias se guardan como nulas", d["notas"], None)

    esquinas_b = copy.deepcopy(REGIONES[b["region"] - 1]["esquinas"])
    esquinas_b[2]["x"] += 12.0
    d = base.decidir(b["clip_id"], 1, "escalamiento", False, esquinas_b,
                     REGIONES[b["region"] - 1]["lineaAgua"], notas="patas en la pared",
                     a_ciegas=True)
    check("B: roi_corregida", d["roi_corregida"], True)
    check("B: guarda las esquinas nuevas", d["esquinas_nuevas"], esquinas_b)
    check("B: linea de agua intacta", d["linea_agua_corregida"], False)
    check("B: a ciegas", d["a_ciegas"], True)

    linea_c = [{"x": 400.0, "y": 140.0}, {"x": 600.0, "y": 141.0}]
    d = base.decidir(c["clip_id"], 1, None, True, REGIONES[1]["esquinas"], linea_c)
    check("C: descartado", (d["descartado"], d["etiqueta_humana"]), (True, None))
    check("C: roi intacta", d["roi_corregida"], False)
    check("C: linea de agua corregida", d["linea_agua_corregida"], True)
    check("C: guarda la linea nueva", d["linea_agua_nueva"], linea_c)

    print("\n== Decisiones mal formadas ==")
    esq = REGIONES[0]["esquinas"]
    falla_con("etiqueta fuera de las tres clases", ValueError,
              lambda: base.decidir(a["clip_id"], 1, "buceo", False, esq, None))
    falla_con("descartado con etiqueta", ValueError,
              lambda: base.decidir(a["clip_id"], 1, "nado", True, esq, None))
    falla_con("sin etiqueta y sin descartar", ValueError,
              lambda: base.decidir(a["clip_id"], 1, None, False, esq, None))
    falla_con("region con tres esquinas", ValueError,
              lambda: base.decidir(a["clip_id"], 1, "nado", False, esq[:3], None))
    falla_con("clip que no existe", ClipNoEncontrado,
              lambda: base.decidir("otro.mp4|r1|0-149", 1, "nado", False, esq, None))
    falla_con("corrida que no es la del clip", ClipNoEncontrado,
              lambda: base.decidir(a["clip_id"], 99, "nado", False, esq, None))

    print("\n== Cambiar de opinion reemplaza, no duplica ==")
    d = base.decidir(a["clip_id"], 1, "inmovilidad", False,
                     original_a["esquinas"], original_a["lineaAgua"])
    check("A: nueva etiqueta", d["etiqueta_humana"], "inmovilidad")
    check("decisiones totales", len(base.decisiones()), 3)
    check("pendientes tras decidir tres", base.cola()["pendientes"], 3)

    print("\n== Mismo video, misma rejilla, otro umbral del consenso ==")
    mov, mod, reg = informes()
    otra = consenso.combinar(mov, mod, reg, consenso.Parametros(margen_minimo_reglas=0.3))
    guardada = base.guardar_corrida(otra)
    check("corrida nueva", guardada["corrida_id"], 2)
    check("la cola ya trae decisiones de la corrida anterior", guardada["en_cola_ya_decididos"],
          sum(k["clip_id"] in (a["clip_id"], b["clip_id"], c["clip_id"]) for k in otra["cola"]))
    cola = base.cola()
    check("la cola muestra la corrida vigente", {k["corrida_id"] for k in cola["clips"]}, {2})
    check("sin avisos de numeracion con las mismas regiones", guardada["avisos"], [])

    print("\n== Mismo video, regiones renumeradas ==")
    renumeradas = [region(10.0), region(250.0), region(500.0)]
    guardada = base.guardar_corrida(consenso_de(regiones=renumeradas))
    check("avisa del cambio de numero de regiones y de la region 2",
          len(guardada["avisos"]), 2)

    print("\n== Mismo video, otra rejilla ==")
    guardada = base.guardar_corrida(consenso_de(desfase=30))
    check("otra rejilla: ningun clip de la cola trae decision", guardada["en_cola_ya_decididos"], 0)
    check("otra rejilla: la cola vigente empieza limpia", base.cola()["decididos"], 0)
    check("las decisiones viejas no se borran", len(base.decisiones()), 3)

    print("\n== Lo que quedo en disco, leido sin la clase ==")
    con = sqlite3.connect(ruta)
    filas = con.execute(
        """SELECT clip_id, etiqueta_humana, descartado, roi_corregida, esquinas_nuevas,
                  linea_agua_corregida, notas, a_ciegas, timestamp
           FROM decisiones ORDER BY clip_id"""
    ).fetchall()
    con.close()
    for f in filas:
        print("        ", f[0], "|", f[1], "| desc", f[2], "| roi", f[3], "| agua", f[5],
              "| ciegas", f[7], "|", f[8])
    check("tres filas en decisiones", len(filas), 3)
    fila_b = next(f for f in filas if f[0] == b["clip_id"])
    check("B en disco: esquinas nuevas como JSON", json.loads(fila_b[4]), esquinas_b)
    check("B en disco: notas", fila_b[6], "patas en la pared")
    check("todas con timestamp", all(f[8] for f in filas), True)

print()
if fallos:
    print(f"{len(fallos)} comprobaciones fallaron.")
    sys.exit(1)
print("Todas las comprobaciones pasaron.")
