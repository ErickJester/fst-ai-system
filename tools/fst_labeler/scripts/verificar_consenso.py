#!/usr/bin/env python3
"""Verificacion del Modulo 7 con reportes sinteticos de las tres capas.

Construye a mano los tres reportes con la forma exacta que producen los
Modulos 4, 5 y 6, con casos elegidos para que cada rama de la decision se
ejerza al menos una vez, y comprueba el resultado esperado.

No necesita video, ni TensorFlow, ni los pesos del modelo preentrenado: la
logica del consenso es una funcion de tres diccionarios. Justamente por eso se
puede fijar la entrada exacta y saber cual es la salida correcta, que sobre un
video no se puede.

Uso:
    python scripts/verificar_consenso.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fst_labeler import consenso

FPS = 30.0
CUADROS_BLOQUE = 150   # 5 s
CUADROS_CLIP = 90      # 3 s

fallos = []


def check(nombre, obtenido, esperado):
    if obtenido != esperado:
        fallos.append(f"{nombre}: esperaba {esperado!r}, obtuvo {obtenido!r}")
        print(f"  FALLA  {nombre}: esperaba {esperado!r}, obtuvo {obtenido!r}")
    else:
        print(f"  ok     {nombre} = {obtenido!r}")


def bloque_mov(i, clase, cambio=0.01):
    return {
        "bloque": i + 1,
        "cuadro_inicio": i * CUADROS_BLOQUE,
        "cuadro_fin": (i + 1) * CUADROS_BLOQUE - 1,
        "segundo_inicio": i * 5.0,
        "segundo_fin": (i + 1) * 5.0 - 1 / FPS,
        "cambio_medio": cambio,
        "area_especimen_media": 0.03,
        "cuadros_medidos": CUADROS_BLOQUE,
        "camara_desplazamiento_px": 0.0,
        "clase": clase,
    }


def bloque_reglas(i, clase, margen=0.9, empate=False, votos=None):
    return {
        "bloque": i + 1,
        "cuadro_inicio": i * CUADROS_BLOQUE,
        "cuadro_fin": (i + 1) * CUADROS_BLOQUE - 1,
        "segundo_inicio": i * 5.0,
        "segundo_fin": (i + 1) * 5.0 - 1 / FPS,
        "clase": clase,
        "votos": votos or {"escalamiento": 5, "inmovilidad": 5, "nado": 140},
        "cuadros_medidos": 150,
        "cuadros_sin_cuerpo": 0,
        "margen_voto": margen,
        "empate": empate,
        "medianas": {"desplazamiento": 0.3, "verticalidad": 0.5},
    }


def clip_modelo(k, clase, confianza):
    resto = (1.0 - confianza) / 2
    probabilidades = {c: resto for c in consenso.CLASES}
    probabilidades[clase] = confianza
    return {
        "clip": k + 1,
        "region": 1,
        "cuadro_inicio": k * CUADROS_CLIP,
        "cuadro_fin": (k + 1) * CUADROS_CLIP - 1,
        "segundo_inicio": k * 3.0,
        "segundo_fin": (k + 1) * 3.0,
        "clase": clase,
        "confianza": confianza,
        "probabilidades": probabilidades,
        "aviso": None,
    }


def informes(bloques_mov, bloques_reg, clips, video="prueba.mp4"):
    mov = {
        "video": video, "fps": FPS, "cuadro_inicio": 0, "cuadro_fin": 900,
        "cuadros_por_bloque": CUADROS_BLOQUE,
        "parametros": {"segundos_por_bloque": 5.0},
        "regiones": [{"region": 1, "bloques": bloques_mov}],
    }
    reg = {
        "video": video, "fps": FPS, "cuadro_inicio": 0, "cuadro_fin": 900,
        "cuadros_por_bloque": CUADROS_BLOQUE, "segundos_por_bloque": 5.0,
        "regiones": [{"region": 1, "bloques": bloques_reg}],
    }
    mod = {
        "video": video, "fps": FPS, "cuadro_inicio": 0,
        "clips_por_region": len(clips), "segundos_por_clip": 3.0,
        "regiones": [{"region": 1, "detalle": clips}],
    }
    return mov, mod, reg


# ---------------------------------------------------------------------------
# Caso base: seis bloques, uno por cada rama de la decision.
#
#  b1  las tres de acuerdo en nado, con holgura        -> aceptado
#  b2  modelo dice nado, reglas dicen escalamiento     -> desacuerdo
#  b3  modelo y reglas dicen nado, movimiento inmovil  -> movimiento_incompatible
#  b4  de acuerdo en inmovilidad, modelo con 0.55      -> confianza_baja
#  b5  de acuerdo en nado, reglas empatadas            -> empate
#  b6  de acuerdo en nado, margen de reglas 0.45       -> margen_bajo
print("\n== Caso base: una rama de la decision por bloque ==")

bloques_mov = [
    bloque_mov(0, "activo"),
    bloque_mov(1, "activo"),
    bloque_mov(2, "inmovil"),
    bloque_mov(3, "inmovil"),
    bloque_mov(4, "activo"),
    bloque_mov(5, "activo"),
]
bloques_reg = [
    bloque_reglas(0, "nado", 0.95),
    bloque_reglas(1, "escalamiento", 0.90),
    bloque_reglas(2, "nado", 0.90),
    bloque_reglas(3, "inmovilidad", 0.90),
    bloque_reglas(4, "nado", 0.90, empate=True),
    bloque_reglas(5, "nado", 0.45),
]
# Diez clips de 3 s cubren los seis bloques de 5 s, pero las fronteras no
# coinciden y los clips 1, 3, 6 y 8 quedan a caballo entre dos bloques:
#
#   c0 [  0, 89] b1        c5 [450,539] b4
#   c1 [ 90,179] b1 y b2   c6 [540,629] b4 y b5
#   c2 [180,269] b2        c7 [630,719] b5
#   c3 [270,359] b2 y b3   c8 [720,809] b5 y b6
#   c4 [360,449] b3        c9 [810,899] b6
clases_clip = [
    "nado", "nado", "nado", "nado", "nado",
    "inmovilidad", "inmovilidad",       # los dos de b4
    "nado", "nado", "nado",
]
confianzas = [0.95] * 10
confianzas[5] = confianzas[6] = 0.55    # b4 queda por debajo del umbral
clips = [clip_modelo(k, c, confianzas[k]) for k, c in enumerate(clases_clip)]

mov, mod, reg = informes(bloques_mov, bloques_reg, clips)
r = consenso.combinar(mov, mod, reg)

estados = [c["estado"] for c in r["regiones"][0]["clips"]]
motivos = [c["motivos"] for c in r["regiones"][0]["clips"]]
check("b1 estado", estados[0], "aceptado")
check("b1 clase", r["regiones"][0]["clips"][0]["clase_consenso"], "nado")
check("b2 motivos", motivos[1], ["desacuerdo"])
check("b3 motivos", motivos[2], ["movimiento_incompatible"])
check("b4 motivos", motivos[3], ["confianza_baja"])
check("b5 motivos", motivos[4], ["empate"])
check("b6 motivos", motivos[5], ["margen_bajo"])
check("aceptados", r["totales"]["aceptados"], 1)
check("en cola", r["totales"]["en_cola"], 5)
check("% automatico", r["totales"]["porcentaje_automatico"], 16.7)

# El clip_id lleva el rango de cuadros, no el numero de bloque.
check("clip_id", r["regiones"][0]["clips"][0]["clip_id"], "prueba.mp4|r1|0-149")

# ---------------------------------------------------------------------------
print("\n== Reproyeccion de clips de 3 s sobre bloques de 5 s ==")

# Bloque 1 [0,149] lo tocan el clip 1 (90 cuadros) y el clip 2 (60 cuadros).
# Con el clip 1 en nado 0.9 y el clip 2 en inmovilidad 0.8, el promedio pesado
# de nado es (90*0.9 + 60*0.1)/150 = 0.58 y el de inmovilidad
# (90*0.05 + 60*0.8)/150 = 0.35. Gana nado, pero por debajo de 0.70: el
# bloque tiene que encolarse por confianza aunque los dos clips fueran firmes.
c1 = clip_modelo(0, "nado", 0.90)
c2 = clip_modelo(1, "inmovilidad", 0.80)
probabilidades, cobertura = consenso.reproyectar_modelo(
    {"cuadro_inicio": 0, "cuadro_fin": 149}, [c1, c2]
)
check("cobertura", round(cobertura, 3), 1.0)
check("prob nado", round(probabilidades["nado"], 4), 0.58)
check("prob inmovilidad", round(probabilidades["inmovilidad"], 4), 0.35)

r2 = consenso.combinar(*informes([bloque_mov(0, "activo")], [bloque_reglas(0, "nado")], [c1, c2]))
clip0 = r2["regiones"][0]["clips"][0]
check("reproyeccion: estado", clip0["estado"], "discrepancia")
check("reproyeccion: motivos", clip0["motivos"], ["confianza_baja"])
check("reproyeccion: clase del modelo", clip0["capas"]["modelo"]["clase"], "nado")
check("reproyeccion: clips que tocan el bloque", len(clip0["capas"]["modelo"]["clips"]), 2)

# ---------------------------------------------------------------------------
print("\n== Cobertura insuficiente: el modelo se abstiene ==")

# Un solo clip de 3 s sobre un bloque de 5 s cubre 90/150 = 0.6. Con la
# cobertura minima en 0.7 la capa se abstiene y el bloque se queda con una
# sola opinion de tres clases.
p_exigente = consenso.Parametros(cobertura_minima_modelo=0.7)
r3 = consenso.combinar(
    *informes([bloque_mov(0, "activo")], [bloque_reglas(0, "nado")], [clip_modelo(0, "nado", 0.95)]),
    parametros=p_exigente,
)
clip0 = r3["regiones"][0]["clips"][0]
check("cobertura baja: motivos", clip0["motivos"], ["pocos_votantes", "cobertura_baja"])
check("cobertura baja: el modelo no opina", clip0["capas"]["modelo"]["clase"], None)
check("cobertura baja: cobertura medida", clip0["capas"]["modelo"]["cobertura"], 0.6)

# Con la cobertura minima en 0.6 el mismo bloque se acepta.
r4 = consenso.combinar(
    *informes([bloque_mov(0, "activo")], [bloque_reglas(0, "nado")], [clip_modelo(0, "nado", 0.95)]),
    parametros=consenso.Parametros(cobertura_minima_modelo=0.6),
)
check("cobertura al limite: estado", r4["regiones"][0]["clips"][0]["estado"], "aceptado")

# ---------------------------------------------------------------------------
print("\n== Sin el modelo preentrenado: nada se acepta solo ==")

r5 = consenso.combinar(
    mov, None, reg,
)
check("sin modelo: aceptados", r5["totales"]["aceptados"], 0)
check("sin modelo: todos en cola", r5["totales"]["en_cola"], 6)
check("sin modelo: motivo", r5["regiones"][0]["clips"][0]["motivos"], ["pocos_votantes"])
check("sin modelo: capa ausente", r5["capas_presentes"]["modelo"], False)
check("sin modelo: avisa", any("Capa 1" in a for a in r5["avisos"]), True)

# Con minimo_votantes=1 si acepta, y el reporte avisa de lo que eso significa.
r6 = consenso.combinar(mov, None, reg, parametros=consenso.Parametros(minimo_votantes=1))
check("un votante: acepta", r6["totales"]["aceptados"] > 0, True)
check("un votante: avisa", any("no es consenso" in a for a in r6["avisos"]), True)

# ---------------------------------------------------------------------------
print("\n== Acuerdo por pares y matriz ==")

a = r["acuerdo"]
# Seis bloques comparables; el modelo y las reglas discrepan solo en b2.
check("modelo vs reglas: comparables", a["modelo_vs_reglas"]["comparables"], 6)
check("modelo vs reglas: coinciden", a["modelo_vs_reglas"]["coinciden"], 5)
check("modelo vs reglas: %", a["modelo_vs_reglas"]["porcentaje"], 83.3)
# La compuerta contradice al modelo solo en b3 (dice inmovil, el modelo nado).
check("movimiento vs modelo: coinciden", a["movimiento_vs_modelo"]["coinciden"], 5)
check("matriz nado/escalamiento", a["matriz_modelo_reglas"]["nado"]["escalamiento"], 1)
check("matriz nado/nado", a["matriz_modelo_reglas"]["nado"]["nado"], 4)

# ---------------------------------------------------------------------------
print("\n== Rejillas desalineadas ==")

mov_otro, _, reg_otro = informes(bloques_mov, bloques_reg, clips)
mov_otro["parametros"]["segundos_por_bloque"] = 3.0
r7 = consenso.combinar(mov_otro, None, reg_otro)
check("avisa rejillas distintas", any("rejillas distintas" in x for x in r7["avisos"]), True)

# Videos distintos: es un error, no un aviso.
try:
    consenso.combinar(informes(bloques_mov, bloques_reg, clips, video="a.mp4")[0],
                      None,
                      informes(bloques_mov, bloques_reg, clips, video="b.mp4")[2])
    check("videos distintos: rechaza", False, True)
except ValueError as e:
    check("videos distintos: rechaza", "no son del mismo video" in str(e), True)

# Sin ninguna capa que defina la rejilla.
try:
    consenso.combinar(None, mod, None)
    check("sin rejilla: rechaza", False, True)
except ValueError as e:
    check("sin rejilla: rechaza", "rejilla de bloques" in str(e), True)

# ---------------------------------------------------------------------------
print("\n== Emparejamiento por cuadro de inicio, no por numero de bloque ==")

# Las reglas empiezan en el cuadro 0 y el movimiento en el 150. El bloque 1 de
# uno y el bloque 1 del otro cubren tramos distintos y NO deben emparejarse.
mov_corrido = {
    "video": "prueba.mp4", "fps": FPS, "cuadro_inicio": 150,
    "cuadros_por_bloque": CUADROS_BLOQUE,
    "parametros": {"segundos_por_bloque": 5.0},
    "regiones": [{"region": 1, "bloques": [bloque_mov(i, "inmovil") for i in (1, 2, 3)]}],
}
r8 = consenso.combinar(mov_corrido, None, reg)
primero = r8["regiones"][0]["clips"][0]
check("desfase: el primer bloque no tiene compuerta",
      primero["capas"]["movimiento"]["disponible"], False)
segundo = r8["regiones"][0]["clips"][1]
check("desfase: el segundo si", segundo["capas"]["movimiento"]["disponible"], True)
check("desfase: avisa", any("cuadros distintos" in x for x in r8["avisos"]), True)

# ---------------------------------------------------------------------------
print("\n== Compuerta de cordura sobre lo aceptado ==")

# Diez bloques aceptados, todos nado: 0 % de inmovilidad, muy por debajo del
# rango del laboratorio. La compuerta lo tiene que marcar.
muchos_mov = [bloque_mov(i, "activo") for i in range(10)]
muchos_reg = [bloque_reglas(i, "nado", 0.95) for i in range(10)]
muchos_clips = [clip_modelo(k, "nado", 0.95) for k in range(17)]
r9 = consenso.combinar(*informes(muchos_mov, muchos_reg, muchos_clips))
check("cordura: aceptados", r9["totales"]["aceptados"], 10)
check("cordura: sospechoso", r9["compuerta_cordura"]["sospechoso"], True)
check("cordura: % inmovilidad", r9["compuerta_cordura"]["porcentaje_inmovilidad"], 0.0)

# ---------------------------------------------------------------------------
print("\n== Conteo de motivos ==")
conteo = {m["motivo"]: m["clips"] for m in r["motivos"]}
check("motivos contados", conteo,
      {"desacuerdo": 1, "movimiento_incompatible": 1, "confianza_baja": 1,
       "margen_bajo": 1, "empate": 1})

print()
if fallos:
    print(f"{len(fallos)} comprobaciones fallaron.")
    sys.exit(1)
print("Todas las comprobaciones pasaron.")
