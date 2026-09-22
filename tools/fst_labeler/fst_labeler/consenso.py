"""Capa 5: consenso entre capas y cola de discrepancias (Modulo 7).

Esta capa no mira el video. Recibe lo que dijeron las tres anteriores sobre el
mismo material y decide que clips se aceptan sin intervencion humana y cuales
pasan a la cola de revision del Modulo 8, con el contexto completo de por que
entraron.

Que aporta cada capa, y por que no hablan el mismo idioma
---------------------------------------------------------

  Capa  Modulo  Unidad         Salida                 Certidumbre
  2     4       bloque de 5 s  inmovil / activo       distancia al umbral
  1     5       clip de 3 s    las tres conductas     softmax
  3     6       bloque de 5 s  las tres conductas     margen del voto

Hay dos desajustes que resolver antes de poder comparar nada.

1. La unidad. El bloque de 5 s es la unidad de esta prueba desde Detke et al.
   (1995) y es lo que el CSV final tiene que entregar, asi que manda. Los
   clips de 3 s del modelo se reproyectan sobre la rejilla de bloques pesando
   cada clip por los CUADROS que comparte con el bloque --no por segundos: los
   tres reportes traen indices de cuadro absolutos y esos no dependen de si el
   modelo remuestreo a 25 Hz--. El reporte guarda que fraccion del bloque
   quedo cubierta por clips: donde el modelo se quedo corto, por fin de video
   o por tope de clips, la cobertura lo dice y la capa se abstiene en vez de
   opinar a ciegas sobre la parte que no vio.

2. El alfabeto. La Capa 2 no distingue nado de escalamiento: solo separa
   quieto de moviendose. No es un voto de tres clases, y contarlo como si lo
   fuera inflaria el acuerdo. Entra como COMPUERTA: 'inmovil' solo es
   compatible con inmovilidad, y 'activo' solo con nado o escalamiento. Puede
   vetar el acuerdo de las otras dos, pero no puede nombrar una conducta por
   si sola.

Cuando se acepta un clip sin revisarlo
--------------------------------------
Las cuatro condiciones a la vez:

  1. Al menos `minimo_votantes` capas de tres clases opinaron --por omision 2,
     que son todas las que hay-- y nombraron la MISMA conducta.
  2. La Capa 2, si opino, es compatible con esa conducta.
  3. Ninguna opino con poca certidumbre: la confianza del modelo y el margen
     del voto de las reglas superan su umbral.
  4. Las reglas no marcaron empate.

Todo lo demas va a la cola. Cada clip encolado lleva la lista de motivos, en
codigo y en prosa, para que el Modulo 8 pueda ordenar la cola por tipo de
problema y no solo por tiempo.

Que tan independientes son de verdad estas fuentes
---------------------------------------------------
Las Capas 2 y 3 miden sobre la MISMA mascara de sustraccion de fondo. Si la
region esta mal dibujada o el umbral de binarizacion es malo, las dos se
equivocan juntas y el consenso no lo nota: coincidir no es lo mismo que tener
razon. Solo la Capa 1 segmenta por su cuenta, desde los pixeles.

Por eso el reporte publica el acuerdo por pares y la matriz entre las dos
capas de tres clases. Si la Capa 2 y la Capa 3 coinciden mucho mas entre si
que cualquiera de las dos con la Capa 1, lo que se esta midiendo es la
segmentacion compartida y no la conducta. Ese numero hay que leerlo antes de
creerle al porcentaje de aceptacion automatica.

Lo que esta capa NO hace
------------------------
No suaviza en el tiempo --eso es el Modulo 9-- y no exporta el CSV final. No
propone una conducta para los clips en discrepancia: el Modulo 8 muestra lo
que dijo cada capa y la persona decide. Poner ahi una sugerencia del propio
sistema convertiria la revision en confirmar al sistema.
"""
from __future__ import annotations

from dataclasses import dataclass, field

from .deteccion_movimiento import HOLGURA_PRIOR, PRIOR_INMOVILIDAD

# Las tres conductas, en el mismo orden que usa el Modulo 6.
CLASES = ("escalamiento", "inmovilidad", "nado")

# Que conductas admite cada salida de la Capa 2. La compuerta no nombra
# conducta: solo dice con cuales es compatible lo que midio.
COMPATIBLES_MOVIMIENTO = {
    "inmovil": ("inmovilidad",),
    "activo": ("nado", "escalamiento"),
}

# Motivos por los que un clip entra a la cola. El codigo es lo que el Modulo 8
# usa para agrupar; el texto es lo que lee quien revisa.
MOTIVOS = {
    "desacuerdo": "Las capas de tres clases nombraron conductas distintas.",
    "movimiento_incompatible": (
        "La compuerta de movimiento contradice la conducta en la que coincidieron "
        "las otras capas."
    ),
    "pocos_votantes": (
        "No hubo suficientes capas de tres clases con opinion para hablar de consenso."
    ),
    "confianza_baja": "El modelo preentrenado predijo por debajo del umbral de confianza.",
    "margen_bajo": "El voto de las reglas geometricas quedo repartido.",
    "empate": "Las reglas geometricas empataron entre dos conductas.",
    "cobertura_baja": (
        "Los clips del modelo no cubren suficiente parte del bloque como para "
        "reproyectar su prediccion."
    ),
    "sin_datos": "Ninguna capa pudo medir este bloque.",
}


@dataclass
class Parametros:
    """Umbrales del consenso.

    Todos son PUNTOS DE PARTIDA A CALIBRAR. Cada uno mueve el mismo cursor: mas
    exigente acepta menos y manda mas clips a revision humana; mas laxo acepta
    mas y mete mas errores en el conjunto de entrenamiento. Donde ponerlos se
    decide midiendo, sobre el conjunto de prueba etiquetado a mano, cuantos de
    los clips aceptados automaticamente estaban bien. No se puede decidir de
    antemano.
    """

    # Confianza minima del modelo preentrenado para que su voto cuente como
    # firme. Punto de partida: 0.70, el mismo corte con el que el sistema
    # principal detiene el analisis y emite reporte de diagnostico (RN-11).
    confianza_minima_modelo: float = 0.70

    # Fraccion minima de votos que el ganador necesita en el bloque de las
    # reglas geometricas. Con tres clases, el azar esta en 0.33 y un bloque
    # limpio llega cerca de 1.0. Punto de partida: 0.60.
    margen_minimo_reglas: float = 0.60

    # Fraccion minima del bloque que los clips del modelo tienen que cubrir
    # para que su reproyeccion valga. Punto de partida: 0.60, que con clips de
    # 3 s sobre bloques de 5 s exige al menos un clip completo dentro.
    cobertura_minima_modelo: float = 0.60

    # Cuantas capas de TRES CLASES tienen que coincidir para aceptar sin
    # revision. Por omision 2, que son todas las que hay: el modelo y las
    # reglas. Bajarlo a 1 desactiva el consenso --queda una sola opinion sin
    # nadie que la confirme-- y el reporte lo dice en sus avisos.
    minimo_votantes: int = 2

    # Un empate de las reglas manda el bloque a revision aunque el modelo
    # coincida con la conducta que gano el desempate por orden.
    encolar_empates: bool = True

    def to_dict(self) -> dict:
        return {
            "confianza_minima_modelo": self.confianza_minima_modelo,
            "margen_minimo_reglas": self.margen_minimo_reglas,
            "cobertura_minima_modelo": self.cobertura_minima_modelo,
            "minimo_votantes": self.minimo_votantes,
            "encolar_empates": self.encolar_empates,
            "nota": "Puntos de partida a calibrar con los videos del laboratorio.",
        }

    def validar(self) -> None:
        if not 0 <= self.confianza_minima_modelo <= 1:
            raise ValueError("La confianza minima del modelo debe estar entre 0 y 1.")
        if not 0 <= self.margen_minimo_reglas <= 1:
            raise ValueError("El margen minimo de las reglas debe estar entre 0 y 1.")
        if not 0 <= self.cobertura_minima_modelo <= 1:
            raise ValueError("La cobertura minima del modelo debe estar entre 0 y 1.")
        if not 1 <= self.minimo_votantes <= 2:
            raise ValueError("El minimo de votantes debe ser 1 o 2.")


# --------------------------------------------------------------- opiniones

@dataclass
class Opinion:
    """Lo que una capa dijo sobre un bloque, ya en la unidad comun.

    `clase` es None cuando la capa se abstuvo: no midio, no cubrio el bloque o
    no existe en esta corrida. Abstenerse y equivocarse no son lo mismo, y el
    consenso los trata distinto.
    """

    capa: str
    clase: str | None = None
    certidumbre: float | None = None
    nombre_certidumbre: str = ""
    firme: bool = True
    detalle: dict = field(default_factory=dict)

    @property
    def opino(self) -> bool:
        return self.clase is not None

    def to_dict(self) -> dict:
        return {
            "capa": self.capa,
            "clase": self.clase,
            "certidumbre": None if self.certidumbre is None else round(self.certidumbre, 4),
            "nombre_certidumbre": self.nombre_certidumbre,
            "firme": self.firme,
            **self.detalle,
        }


@dataclass
class ClipConsenso:
    """Un bloque de 5 s de una region, con lo que dijo cada capa."""

    clip_id: str
    video: str
    region: int
    bloque: int
    cuadro_inicio: int
    cuadro_fin: int
    segundo_inicio: float
    segundo_fin: float
    estado: str = "sin_datos"          # aceptado | discrepancia | sin_datos
    clase_consenso: str | None = None
    motivos: list[str] = field(default_factory=list)
    opiniones: dict = field(default_factory=dict)

    def to_dict(self) -> dict:
        return {
            "clip_id": self.clip_id,
            "video": self.video,
            "region": self.region,
            "bloque": self.bloque,
            "cuadro_inicio": self.cuadro_inicio,
            "cuadro_fin": self.cuadro_fin,
            "segundo_inicio": round(self.segundo_inicio, 2),
            "segundo_fin": round(self.segundo_fin, 2),
            "estado": self.estado,
            "clase_consenso": self.clase_consenso,
            "motivos": self.motivos,
            "explicacion": [MOTIVOS.get(m, m) for m in self.motivos],
            "capas": {k: v.to_dict() for k, v in self.opiniones.items()},
        }


def clip_id(video: str, region: int, cuadro_inicio: int, cuadro_fin: int) -> str:
    """Identificador estable de un clip, para la base de datos del Modulo 8.

    Lleva el rango de cuadros a proposito. Si se vuelve a correr el pipeline
    con otra duracion de bloque o desde otro cuadro de inicio, la rejilla es
    otra y los identificadores cambian, asi que el Modulo 9 no puede reanudar
    encima de decisiones que se tomaron mirando otro tramo de video. Un
    identificador por numero de bloque si permitiria esa confusion.
    """
    return f"{video}|r{region}|{cuadro_inicio}-{cuadro_fin}"


# ----------------------------------------------------------- reproyeccion

def _solape(a_inicio: int, a_fin: int, b_inicio: int, b_fin: int) -> int:
    """Cuadros compartidos por dos rangos cerrados [inicio, fin]."""
    return max(0, min(a_fin, b_fin) - max(a_inicio, b_inicio) + 1)


def reproyectar_modelo(bloque: dict, clips: list[dict]) -> tuple[dict, float]:
    """Lleva los clips de 3 s del modelo a un bloque de la rejilla comun.

    Promedia las probabilidades de los clips que tocan el bloque, pesando cada
    uno por los cuadros que comparte con el. Se promedian las probabilidades y
    no las clases ganadoras: dos clips con 0.51 y 0.49 repartidos entre nado e
    inmovilidad describen un bloque dudoso, y contarlos como un voto para cada
    una perderia justamente eso.

    Devuelve el vector de probabilidades del bloque y la fraccion del bloque
    cubierta por clips utilizables.
    """
    ancho = bloque["cuadro_fin"] - bloque["cuadro_inicio"] + 1
    if ancho <= 0:
        return {}, 0.0

    acumulado = {c: 0.0 for c in CLASES}
    peso_total = 0
    for clip in clips:
        if clip.get("clase") not in CLASES:
            continue  # clip incompleto: no cubre nada
        peso = _solape(
            bloque["cuadro_inicio"], bloque["cuadro_fin"],
            clip["cuadro_inicio"], clip["cuadro_fin"],
        )
        if peso <= 0:
            continue
        probabilidades = clip.get("probabilidades") or {}
        for c in CLASES:
            acumulado[c] += peso * float(probabilidades.get(c, 0.0))
        peso_total += peso

    if peso_total <= 0:
        return {}, 0.0
    return (
        {c: acumulado[c] / peso_total for c in CLASES},
        min(1.0, peso_total / ancho),
    )


# ------------------------------------------------------------ opiniones

def _opinion_movimiento(bloque: dict | None) -> Opinion:
    """Capa 2: compuerta binaria, no voto de tres clases."""
    o = Opinion(capa="movimiento", nombre_certidumbre="cambio medio")
    if bloque is None:
        o.detalle = {"disponible": False, "salida": None, "compatible_con": []}
        return o
    salida = bloque.get("clase")
    compatibles = list(COMPATIBLES_MOVIMIENTO.get(salida, ()))
    o.detalle = {
        "disponible": True,
        "salida": salida,
        "compatible_con": compatibles,
        "cambio_medio": bloque.get("cambio_medio"),
        "area_especimen_media": bloque.get("area_especimen_media"),
        "cuadros_medidos": bloque.get("cuadros_medidos"),
        "camara_desplazamiento_px": bloque.get("camara_desplazamiento_px"),
        "es_compuerta": True,
    }
    o.certidumbre = bloque.get("cambio_medio")
    # La compuerta no nombra conducta: `clase` se queda en None a proposito y
    # su opinion se ejerce en `compatible_con`.
    return o


def _opinion_modelo(bloque: dict, clips: list[dict], p: Parametros) -> Opinion:
    """Capa 1: reproyeccion de los clips de 3 s sobre el bloque."""
    o = Opinion(capa="modelo", nombre_certidumbre="confianza (softmax)")
    probabilidades, cobertura = reproyectar_modelo(bloque, clips)
    o.detalle = {
        "disponible": bool(probabilidades),
        "cobertura": round(cobertura, 3),
        "clips": [
            {
                "clip": c["clip"],
                "clase": c["clase"],
                "confianza": c.get("confianza"),
                "cuadros_compartidos": _solape(
                    bloque["cuadro_inicio"], bloque["cuadro_fin"],
                    c["cuadro_inicio"], c["cuadro_fin"],
                ),
            }
            for c in clips
            if _solape(
                bloque["cuadro_inicio"], bloque["cuadro_fin"],
                c["cuadro_inicio"], c["cuadro_fin"],
            ) > 0
        ],
    }
    if not probabilidades:
        return o

    o.detalle["probabilidades"] = {c: round(v, 4) for c, v in probabilidades.items()}
    if cobertura < p.cobertura_minima_modelo:
        # Cubre tan poco del bloque que la prediccion no describe el bloque.
        o.detalle["motivo_abstencion"] = "cobertura_baja"
        return o

    clase = max(CLASES, key=lambda c: probabilidades[c])
    o.clase = clase
    o.certidumbre = probabilidades[clase]
    o.firme = probabilidades[clase] >= p.confianza_minima_modelo
    return o


def _opinion_reglas(bloque: dict | None, p: Parametros) -> Opinion:
    """Capa 3: voto mayoritario de los cuadros del bloque."""
    o = Opinion(capa="reglas", nombre_certidumbre="margen del voto")
    if bloque is None:
        o.detalle = {"disponible": False}
        return o
    o.detalle = {
        "disponible": True,
        "votos": bloque.get("votos"),
        "empate": bool(bloque.get("empate")),
        "cuadros_medidos": bloque.get("cuadros_medidos"),
        "cuadros_sin_cuerpo": bloque.get("cuadros_sin_cuerpo"),
        "medianas": bloque.get("medianas"),
    }
    if bloque.get("clase") not in CLASES:
        return o
    o.clase = bloque["clase"]
    o.certidumbre = float(bloque.get("margen_voto") or 0.0)
    o.firme = o.certidumbre >= p.margen_minimo_reglas
    if p.encolar_empates and bloque.get("empate"):
        o.firme = False
    return o


# --------------------------------------------------------------- decision

def _decidir(clip: ClipConsenso, p: Parametros) -> None:
    """Aplica la regla de aceptacion y deja escrito por que."""
    modelo = clip.opiniones["modelo"]
    reglas = clip.opiniones["reglas"]
    movimiento = clip.opiniones["movimiento"]

    votantes = [o for o in (modelo, reglas) if o.opino]
    hay_compuerta = bool(movimiento.detalle.get("salida"))

    if not votantes and not hay_compuerta:
        clip.estado = "sin_datos"
        clip.motivos = ["sin_datos"]
        return

    motivos: list[str] = []

    if len(votantes) < p.minimo_votantes:
        motivos.append("pocos_votantes")
        if modelo.detalle.get("motivo_abstencion") == "cobertura_baja":
            motivos.append("cobertura_baja")

    clases = {o.clase for o in votantes}
    candidata = clases.pop() if len(clases) == 1 else None
    if candidata is None and len(votantes) > 1:
        motivos.append("desacuerdo")

    if candidata is not None and hay_compuerta:
        if candidata not in movimiento.detalle["compatible_con"]:
            motivos.append("movimiento_incompatible")

    for o in votantes:
        if o.firme:
            continue
        if o.capa == "modelo":
            motivos.append("confianza_baja")
        elif o.detalle.get("empate"):
            motivos.append("empate")
        else:
            motivos.append("margen_bajo")

    if motivos:
        clip.estado = "discrepancia"
        # Sin duplicados y en orden estable, para que el Modulo 8 pueda
        # agrupar la cola por motivo.
        clip.motivos = [m for m in MOTIVOS if m in motivos]
        return

    clip.estado = "aceptado"
    clip.clase_consenso = candidata


# ---------------------------------------------------------------- combinar

def combinar(
    movimiento: dict | None,
    modelo: dict | None,
    reglas: dict | None,
    parametros: Parametros | None = None,
    video: str | None = None,
) -> dict:
    """Compara los tres reportes y devuelve el consenso por clip.

    Cualquiera de los tres puede venir en None: la capa no corrio o no estaba
    disponible. El reporte lo dice y el resto sigue funcionando, pero con una
    sola capa de tres clases nada se acepta solo, que es lo correcto.

    La rejilla de bloques la fija el primero de los reportes por bloque que
    exista --las reglas geometricas, si no la deteccion de movimiento--, porque
    el modelo trabaja en clips de 3 s y no define bloques.
    """
    p = parametros or Parametros()
    p.validar()

    avisos: list[str] = []
    rejilla = reglas or movimiento
    if rejilla is None:
        raise ValueError(
            "Hace falta al menos el reporte de la deteccion de movimiento o el de "
            "las reglas geometricas: el modelo preentrenado trabaja en clips de 3 s "
            "y no define la rejilla de bloques."
        )

    nombre_video = video or rejilla.get("video") or ""
    _avisar_desalineacion(movimiento, modelo, reglas, avisos)

    if modelo is None:
        avisos.append(
            "El modelo preentrenado (Capa 1) no entro en esta corrida. Es la unica "
            "capa que segmenta por su cuenta desde los pixeles; sin ella quedan las "
            "dos que comparten mascara y el consenso pierde su fuente independiente."
        )
    if p.minimo_votantes < 2:
        avisos.append(
            "El minimo de votantes esta en 1: se aceptan clips con una sola capa de "
            "tres clases a favor. Eso no es consenso, es una opinion sin confirmar, y "
            "los clips aceptados asi heredan el error de esa capa."
        )

    regiones_salida = []
    todos: list[ClipConsenso] = []
    n_regiones = len(rejilla.get("regiones") or [])
    for i in range(n_regiones):
        numero = i + 1
        bloques_rejilla = _bloques_de(rejilla, numero)
        bloques_mov = _indexar_por_cuadro(_bloques_de(movimiento, numero))
        bloques_reg = _indexar_por_cuadro(_bloques_de(reglas, numero))
        clips_modelo = _clips_de(modelo, numero)

        clips_region: list[ClipConsenso] = []
        for bloque in bloques_rejilla:
            clave = bloque["cuadro_inicio"]
            clip = ClipConsenso(
                clip_id=clip_id(nombre_video, numero, bloque["cuadro_inicio"], bloque["cuadro_fin"]),
                video=nombre_video,
                region=numero,
                bloque=bloque["bloque"],
                cuadro_inicio=bloque["cuadro_inicio"],
                cuadro_fin=bloque["cuadro_fin"],
                segundo_inicio=bloque.get("segundo_inicio", 0.0),
                segundo_fin=bloque.get("segundo_fin", 0.0),
            )
            clip.opiniones = {
                "modelo": (
                    _opinion_modelo(bloque, clips_modelo, p)
                    if modelo is not None
                    else Opinion(capa="modelo", detalle={"disponible": False})
                ),
                "movimiento": _opinion_movimiento(bloques_mov.get(clave)),
                "reglas": _opinion_reglas(bloques_reg.get(clave), p),
            }
            _decidir(clip, p)
            clips_region.append(clip)

        regiones_salida.append(_resumen_region(numero, clips_region))
        todos.extend(clips_region)

    return {
        "video": nombre_video,
        "clases": list(CLASES),
        # La geometria con que corrieron las capas. El Modulo 8 la guarda con
        # la corrida para saber, al revisar, si la persona la corrigio.
        "regiones_dibujadas": _geometria(movimiento, modelo, reglas),
        "cuadro_referencia": rejilla.get("cuadro_referencia"),
        "parametros": p.to_dict(),
        "capas_presentes": {
            "movimiento": movimiento is not None,
            "modelo": modelo is not None,
            "reglas": reglas is not None,
        },
        "rejilla": {
            "origen": "reglas geometricas" if reglas is not None else "deteccion de movimiento",
            "cuadros_por_bloque": rejilla.get("cuadros_por_bloque"),
            "segundos_por_bloque": rejilla.get("segundos_por_bloque")
            or (rejilla.get("parametros") or {}).get("segundos_por_bloque"),
            "fps": rejilla.get("fps"),
        },
        "totales": _totales(todos),
        "acuerdo": _acuerdo(todos),
        "motivos": _conteo_motivos(todos),
        "compuerta_cordura": _compuerta_cordura(todos),
        "avisos": avisos,
        "regiones": regiones_salida,
        "aceptados": [c.to_dict() for c in todos if c.estado == "aceptado"],
        "cola": [c.to_dict() for c in todos if c.estado == "discrepancia"],
        "sin_datos": [c.to_dict() for c in todos if c.estado == "sin_datos"],
    }


def _bloques_de(informe: dict | None, region: int) -> list[dict]:
    if informe is None:
        return []
    for r in informe.get("regiones") or []:
        if r.get("region") == region:
            return r.get("bloques") or []
    return []


def _clips_de(informe: dict | None, region: int) -> list[dict]:
    if informe is None:
        return []
    for r in informe.get("regiones") or []:
        if r.get("region") == region:
            return r.get("detalle") or []
    return []


def _indexar_por_cuadro(bloques: list[dict]) -> dict:
    """Los bloques se emparejan por cuadro de inicio, no por numero.

    Dos corridas con distinto `cuadro_inicio` producen bloques numerados 1, 2,
    3... que cubren tramos distintos del video. Emparejarlos por numero
    mezclaria conductas de momentos distintos sin que nada lo delate.
    """
    return {b["cuadro_inicio"]: b for b in bloques}


def _geometria(movimiento, modelo, reglas) -> list[dict] | None:
    """Regiones con que corrieron las capas; las de las reglas primero.

    Las reglas son la unica capa que usa la linea de agua, asi que su copia es
    la unica que la trae completa.
    """
    for informe in (reglas, movimiento, modelo):
        if informe is not None and informe.get("regiones_dibujadas"):
            return informe["regiones_dibujadas"]
    return None


def _avisar_desalineacion(movimiento, modelo, reglas, avisos: list[str]) -> None:
    """Delata reportes que no describen el mismo material."""
    presentes = [(n, i) for n, i in
                 (("movimiento", movimiento), ("modelo", modelo), ("reglas", reglas))
                 if i is not None]
    videos = {i.get("video") for _, i in presentes if i.get("video")}
    if len(videos) > 1:
        raise ValueError(
            "Los reportes no son del mismo video: " + ", ".join(sorted(str(v) for v in videos))
        )
    fps = {round(float(i["fps"]), 3) for _, i in presentes if i.get("fps")}
    if len(fps) > 1:
        avisos.append(
            "Las capas corrieron con cuadros por segundo distintos (" +
            ", ".join(str(f) for f in sorted(fps)) +
            "). Las fronteras de los bloques no coinciden y el emparejamiento por "
            "cuadro de inicio va a fallar: vuelve a correr las tres con el mismo valor."
        )
    inicios = {i.get("cuadro_inicio") for _, i in presentes}
    if len(inicios) > 1:
        avisos.append(
            "Las capas empezaron en cuadros distintos (" +
            ", ".join(str(c) for c in sorted(inicios, key=lambda x: (x is None, x))) +
            "). Los bloques que no se emparejan quedan con una sola opinion."
        )

    # Cada capa se corre desde su panel, y entre una y otra se puede mover una
    # region o navegar a otro cuadro. Solo se comparan las esquinas: la linea
    # de agua la usan las reglas y ninguna otra capa.
    firmas = {
        n: tuple(
            tuple((round(p["x"], 2), round(p["y"], 2)) for p in r["esquinas"])
            for r in i["regiones_dibujadas"]
        )
        for n, i in presentes if i.get("regiones_dibujadas")
    }
    if len(set(firmas.values())) > 1:
        avisos.append(
            "Las capas no corrieron con las mismas regiones de interes (" +
            ", ".join(sorted(firmas)) + "): alguna region se movio entre una corrida y "
            "otra. Sus bloques describen recortes distintos; vuelve a correr las que "
            "quedaron con la region vieja."
        )
    referencias = {i.get("cuadro_referencia") for _, i in presentes
                   if i.get("cuadro_referencia") is not None}
    if len(referencias) > 1:
        avisos.append(
            "Las capas tomaron cuadros de referencia distintos (" +
            ", ".join(str(c) for c in sorted(referencias)) + "). Las regiones se "
            "dibujaron sobre uno solo: con la camara en movimiento, las demas capas "
            "las colocaron desplazadas."
        )

    if movimiento is not None and reglas is not None:
        b_mov = (movimiento.get("parametros") or {}).get("segundos_por_bloque")
        b_reg = reglas.get("segundos_por_bloque")
        if b_mov and b_reg and abs(float(b_mov) - float(b_reg)) > 1e-6:
            avisos.append(
                f"La deteccion de movimiento uso bloques de {b_mov} s y las reglas "
                f"geometricas de {b_reg} s. Son rejillas distintas y casi ningun "
                "bloque va a emparejarse."
            )


# ---------------------------------------------------------------- resumenes

def _totales(clips: list[ClipConsenso]) -> dict:
    total = len(clips)
    aceptados = sum(c.estado == "aceptado" for c in clips)
    cola = sum(c.estado == "discrepancia" for c in clips)
    sin_datos = sum(c.estado == "sin_datos" for c in clips)
    decidibles = aceptados + cola
    return {
        "clips": total,
        "aceptados": aceptados,
        "en_cola": cola,
        "sin_datos": sin_datos,
        "porcentaje_automatico": round(100.0 * aceptados / decidibles, 1) if decidibles else 0.0,
        "conteo_aceptados": {
            c: sum(k.clase_consenso == c for k in clips) for c in CLASES
        },
    }


def _acuerdo(clips: list[ClipConsenso]) -> dict:
    """Acuerdo por pares y matriz entre las dos capas de tres clases.

    La matriz es lo que dice DONDE discrepan, que es lo accionable: si el
    modelo ve nado donde las reglas ven escalamiento, el sospechoso es el
    umbral de verticalidad, no la conducta.
    """
    pares = {
        "modelo_vs_reglas": [0, 0],
        "movimiento_vs_modelo": [0, 0],
        "movimiento_vs_reglas": [0, 0],
    }
    matriz = {a: {b: 0 for b in CLASES} for a in CLASES}

    for c in clips:
        modelo, reglas, mov = (
            c.opiniones["modelo"], c.opiniones["reglas"], c.opiniones["movimiento"]
        )
        if modelo.opino and reglas.opino:
            pares["modelo_vs_reglas"][1] += 1
            pares["modelo_vs_reglas"][0] += modelo.clase == reglas.clase
            matriz[modelo.clase][reglas.clase] += 1
        compatibles = mov.detalle.get("compatible_con") or []
        if compatibles and modelo.opino:
            pares["movimiento_vs_modelo"][1] += 1
            pares["movimiento_vs_modelo"][0] += modelo.clase in compatibles
        if compatibles and reglas.opino:
            pares["movimiento_vs_reglas"][1] += 1
            pares["movimiento_vs_reglas"][0] += reglas.clase in compatibles

    salida = {
        nombre: {
            "coinciden": n,
            "comparables": d,
            "porcentaje": round(100.0 * n / d, 1) if d else None,
        }
        for nombre, (n, d) in pares.items()
    }
    salida["matriz_modelo_reglas"] = matriz
    salida["nota"] = (
        "El acuerdo con la deteccion de movimiento es por COMPATIBILIDAD: solo "
        "comprueba que quieto o moviendose no contradiga la conducta, asi que es "
        "mas facil de alcanzar y no se compara con el acuerdo entre las otras dos. "
        "Si movimiento y reglas coinciden mucho mas que cualquiera con el modelo, "
        "lo que se esta midiendo es la mascara que ambas comparten."
    )
    return salida


def _conteo_motivos(clips: list[ClipConsenso]) -> list[dict]:
    """Por que se encola lo que se encola, de mayor a menor.

    Es el numero que dice que ajustar: si domina `confianza_baja`, el problema
    es el modelo sobre nuestro montaje; si domina `desacuerdo`, hay dos capas
    leyendo cosas distintas y toca mirar los umbrales.
    """
    conteo = {m: 0 for m in MOTIVOS}
    for c in clips:
        for m in c.motivos:
            conteo[m] = conteo.get(m, 0) + 1
    filas = [
        {"motivo": m, "clips": n, "explicacion": MOTIVOS.get(m, m)}
        for m, n in conteo.items() if n
    ]
    return sorted(filas, key=lambda f: -f["clips"])


def _compuerta_cordura(clips: list[ClipConsenso]) -> dict:
    """Capa 0 sobre lo aceptado automaticamente.

    Se mide solo sobre los clips aceptados, que son los que entrarian al
    conjunto de entrenamiento sin que nadie los mire. Si esa seleccion se
    desvia del rango del laboratorio, el consenso esta aceptando de forma
    sesgada aunque cada clip por separado parezca solido: lo mas probable es
    que este aceptando inmovilidad facil y encolando todo lo demas.
    """
    aceptados = [c for c in clips if c.estado == "aceptado"]
    if not aceptados:
        return {"sospechoso": False, "mensaje": "Ningun clip se acepto automaticamente."}
    pct = 100.0 * sum(c.clase_consenso == "inmovilidad" for c in aceptados) / len(aceptados)
    bajo, alto = PRIOR_INMOVILIDAD
    dentro = bajo - HOLGURA_PRIOR <= pct <= alto + HOLGURA_PRIOR
    mensaje = (
        f"{pct:.1f} % de los clips aceptados son inmovilidad. El rango medido por el "
        f"laboratorio en la prueba de 5 min es {bajo}-{alto} %"
        + (
            "; la seleccion automatica es compatible."
            if dentro
            else f", y esto queda fuera incluso con {HOLGURA_PRIOR} puntos de holgura. "
                 "Revisa si el consenso solo esta aceptando los bloques faciles antes "
                 "de entrenar con este conjunto."
        )
    )
    return {
        "sospechoso": not dentro,
        "porcentaje_inmovilidad": round(pct, 1),
        "prior_laboratorio": list(PRIOR_INMOVILIDAD),
        "holgura": HOLGURA_PRIOR,
        "mensaje": mensaje,
    }


def _resumen_region(numero: int, clips: list[ClipConsenso]) -> dict:
    return {
        "region": numero,
        "totales": _totales(clips),
        "acuerdo": _acuerdo(clips),
        "motivos": _conteo_motivos(clips),
        "compuerta_cordura": _compuerta_cordura(clips),
        "clips": [c.to_dict() for c in clips],
    }


# ------------------------------------------------------------ orquestacion

def correr_capas(
    ruta: str,
    regiones: list[dict],
    fps: float,
    total_cuadros: int,
    cuadro_referencia: int = 0,
    cuadro_inicio: int = 0,
    cuadro_fin: int | None = None,
    p_movimiento=None,
    p_modelo=None,
    p_reglas=None,
    capas: dict | None = None,
) -> tuple[dict | None, dict | None, dict | None, list[str]]:
    """Corre las tres capas sobre el mismo video y devuelve sus tres reportes.

    Cada capa recorre el video entero por su cuenta, asi que esto son tres
    pasadas completas. Cuando los tres reportes ya existen --porque se
    calcularon desde sus paneles-- conviene pasarlos directo a `combinar` y
    ahorrarse las tres.

    Una capa que falla no tumba a las demas: se devuelve None en su lugar y el
    motivo entra en la lista de avisos. Eso importa sobre todo para el modelo
    preentrenado, que necesita TensorFlow y los pesos publicados, y que en una
    instalacion recien hecha puede no estar.
    """
    from . import modelo_3d, reglas_geometricas
    from .deteccion_movimiento import analizar as analizar_movimiento

    activas = {"movimiento": True, "modelo": True, "reglas": True}
    activas.update(capas or {})
    avisos: list[str] = []

    def _intentar(nombre: str, funcion):
        if not activas.get(nombre):
            avisos.append(f"La capa '{nombre}' se desactivo en esta corrida.")
            return None
        try:
            return funcion()
        except modelo_3d.ModeloNoDisponible as error:
            avisos.append(f"La capa '{nombre}' no pudo correr: {error}")
            return None
        except (RuntimeError, ValueError) as error:
            avisos.append(f"La capa '{nombre}' fallo: {error}")
            return None

    movimiento = _intentar("movimiento", lambda: analizar_movimiento(
        ruta=ruta, regiones=regiones, fps=fps, total_cuadros=total_cuadros,
        cuadro_referencia=cuadro_referencia, cuadro_inicio=cuadro_inicio,
        cuadro_fin=cuadro_fin, parametros=p_movimiento,
    ))
    modelo = _intentar("modelo", lambda: modelo_3d.analizar(
        ruta=ruta, regiones=regiones, fps=fps, total_cuadros=total_cuadros,
        cuadro_referencia=cuadro_referencia, cuadro_inicio=cuadro_inicio,
        parametros=p_modelo,
    ))
    reglas = _intentar("reglas", lambda: reglas_geometricas.analizar(
        ruta=ruta, regiones=regiones, fps=fps, total_cuadros=total_cuadros,
        cuadro_referencia=cuadro_referencia, cuadro_inicio=cuadro_inicio,
        cuadro_fin=cuadro_fin, parametros=p_reglas,
    ))
    return movimiento, modelo, reglas, avisos
