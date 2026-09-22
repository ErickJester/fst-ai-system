"""Cola de revision humana y decisiones (Modulo 8).

Primera parte de la herramienta que escribe en disco. Guarda en sqlite tres
tablas, y las tres hacen falta para que el Modulo 9 pueda exportar y reanudar:

  corridas    Cada consenso que se manda a revision: el video, las regiones y
              lineas de agua con las que corrieron las capas, el cuadro de
              referencia y los umbrales del consenso.
  clips       Los bloques de esa corrida con lo que dijo cada capa: los
              aceptados, los de la cola y los que no tuvieron datos. El
              Modulo 9 necesita los aceptados tanto como las decisiones.
  decisiones  Lo que decidio la persona sobre un clip de la cola.

Por que se guarda la geometria de la corrida
--------------------------------------------
Para saber si quien revisa corrigio la region o la linea de agua hay que
compararlas contra las que usaron las capas, no contra lo que haya en
pantalla. Esa comparacion la hace el servidor: el navegador manda la geometria
que tiene y aqui se decide si cambio. Asi la bandera de correccion no depende
de que la interfaz lleve bien la cuenta.

Varias corridas del mismo video
-------------------------------
Mandar otra vez el consenso de un video --tras mover un umbral, por ejemplo--
crea otra corrida, y la cola pasa a mostrar la mas reciente. Las decisiones se
ligan al `clip_id`, que lleva el rango de cuadros, asi que:

  - Con la misma rejilla los identificadores coinciden y las decisiones ya
    tomadas siguen valiendo: la conducta del especimen en ese tramo no cambio
    porque cambiara un umbral del consenso.
  - Con otra rejilla (otro cuadro de inicio, otra duracion de bloque) los
    identificadores son otros y la cola empieza limpia. Las decisiones viejas
    no se borran: quedan ligadas a la corrida en la que se tomaron.

El identificador lleva el numero de region, y las regiones se numeran de
izquierda a derecha. Si entre dos corridas se agrega una region a la
izquierda, `r1` pasa a ser otro cilindro y las decisiones sobre `r1` se
referirian a otro especimen. Al guardar la corrida se comprueba y se avisa.
"""
from __future__ import annotations

import json
import sqlite3
from contextlib import contextmanager
from datetime import datetime
from pathlib import Path

import cv2
import numpy as np

from .consenso import CLASES, MOTIVOS

# Diferencia maxima, en pixeles del archivo original, para dar dos puntos por
# iguales. No es un umbral de conducta ni de tolerancia al pulso: solo absorbe
# el ruido de coma flotante del viaje de ida y vuelta por JSON. Cualquier
# arrastre real de una esquina cuenta como correccion, y la interfaz lo
# muestra en el momento para que un arrastre accidental se pueda deshacer.
TOLERANCIA_PX = 0.01

ESTADOS = ("aceptado", "discrepancia", "sin_datos")


class ClipNoEncontrado(LookupError):
    """El clip no existe en la corrida indicada."""


ESQUEMA = """
CREATE TABLE IF NOT EXISTS corridas (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    video               TEXT NOT NULL,
    creada              TEXT NOT NULL,
    fps                 REAL,
    segundos_por_bloque REAL,
    cuadro_referencia   INTEGER,
    regiones            TEXT NOT NULL,   -- JSON: esquinas y linea de agua usadas
    parametros          TEXT NOT NULL,   -- JSON: umbrales del consenso
    capas_presentes     TEXT NOT NULL,   -- JSON
    avisos              TEXT NOT NULL    -- JSON
);

CREATE TABLE IF NOT EXISTS clips (
    corrida_id      INTEGER NOT NULL REFERENCES corridas(id),
    clip_id         TEXT NOT NULL,
    video           TEXT NOT NULL,
    region          INTEGER NOT NULL,
    bloque          INTEGER NOT NULL,
    cuadro_inicio   INTEGER NOT NULL,
    cuadro_fin      INTEGER NOT NULL,
    segundo_inicio  REAL,
    segundo_fin     REAL,
    estado          TEXT NOT NULL CHECK (estado IN ('aceptado', 'discrepancia', 'sin_datos')),
    clase_consenso  TEXT,
    motivos         TEXT NOT NULL,   -- JSON: codigos de motivo
    capas           TEXT NOT NULL,   -- JSON: lo que dijo cada capa
    PRIMARY KEY (corrida_id, clip_id)
);
CREATE INDEX IF NOT EXISTS clips_por_id ON clips (clip_id);

CREATE TABLE IF NOT EXISTS decisiones (
    clip_id               TEXT PRIMARY KEY,
    corrida_id            INTEGER NOT NULL REFERENCES corridas(id),
    etiqueta_humana       TEXT CHECK (etiqueta_humana IN ('escalamiento', 'inmovilidad', 'nado')),
    descartado            INTEGER NOT NULL DEFAULT 0,
    roi_corregida         INTEGER NOT NULL DEFAULT 0,
    esquinas_nuevas       TEXT,      -- JSON, solo si se corrigio la region
    linea_agua_corregida  INTEGER NOT NULL DEFAULT 0,
    linea_agua_nueva      TEXT,      -- JSON, solo si se corrigio la linea de agua
    notas                 TEXT,
    a_ciegas              INTEGER NOT NULL DEFAULT 0,
    timestamp             TEXT NOT NULL,
    CHECK ((descartado = 1 AND etiqueta_humana IS NULL)
        OR (descartado = 0 AND etiqueta_humana IS NOT NULL))
);
"""


def _ahora() -> str:
    """Hora local con desfase, en ISO 8601: no ambigua al leerla despues."""
    return datetime.now().astimezone().isoformat(timespec="seconds")


# ---------------------------------------------------------------- geometria

def _puntos(crudos, cuantos: int, que: str) -> list[dict]:
    if not isinstance(crudos, list) or len(crudos) != cuantos:
        raise ValueError(f"{que} debe tener {cuantos} puntos.")
    try:
        return [{"x": float(p["x"]), "y": float(p["y"])} for p in crudos]
    except (KeyError, TypeError, ValueError):
        raise ValueError(f"{que} no son coordenadas validas.")


def _mismos_puntos(a: list[dict] | None, b: list[dict] | None) -> bool:
    if a is None or b is None:
        return a is None and b is None
    if len(a) != len(b):
        return False
    return all(
        abs(p["x"] - q["x"]) <= TOLERANCIA_PX and abs(p["y"] - q["y"]) <= TOLERANCIA_PX
        for p, q in zip(a, b)
    )


def _centroide(esquinas: list[dict]) -> tuple[float, float]:
    return (
        sum(p["x"] for p in esquinas) / len(esquinas),
        sum(p["y"] for p in esquinas) / len(esquinas),
    )


def _cambio_de_numeracion(anteriores: list[dict], nuevas: list[dict]) -> list[str]:
    """Delata que `rN` ya no es el mismo cilindro que en la corrida anterior.

    El criterio es que el centro de la region N nueva caiga dentro de la
    region N anterior. No mide cuanto se movio la region --corregirla es
    legitimo--, solo si sigue encima del mismo recipiente.
    """
    avisos = []
    if len(anteriores) != len(nuevas):
        avisos.append(
            f"La corrida anterior de este video tenia {len(anteriores)} regiones y esta "
            f"tiene {len(nuevas)}. Las regiones se numeran de izquierda a derecha, asi "
            "que la numeracion pudo cambiar."
        )
    for i, (vieja, nueva) in enumerate(zip(anteriores, nuevas), start=1):
        poligono = np.array([[p["x"], p["y"]] for p in vieja["esquinas"]], dtype=np.float32)
        if cv2.pointPolygonTest(poligono, _centroide(nueva["esquinas"]), False) < 0:
            avisos.append(
                f"La region {i} de esta corrida no cae sobre la region {i} de la anterior: "
                f"las decisiones ya tomadas sobre 'r{i}' se refieren al especimen de la "
                "corrida anterior, no al de esta."
            )
    return avisos


# --------------------------------------------------------------------- base

class BaseRevision:
    """Acceso a la base sqlite de la revision humana.

    Abre una conexion por operacion: el servidor atiende peticiones en varios
    hilos y una conexion de sqlite no se comparte entre hilos. Abrirla cuesta
    menos que cualquier cosa que haga la herramienta con el video.
    """

    def __init__(self, ruta: Path) -> None:
        self.ruta = Path(ruta)
        self.ruta.parent.mkdir(parents=True, exist_ok=True)
        with self._transaccion() as c:
            c.executescript(ESQUEMA)

    @contextmanager
    def _transaccion(self):
        c = sqlite3.connect(self.ruta, timeout=10)
        c.row_factory = sqlite3.Row
        c.execute("PRAGMA foreign_keys = ON")
        try:
            with c:            # confirma al salir, revierte si hubo excepcion
                yield c
        finally:
            c.close()

    # -------------------------------------------------------------- corridas

    def guardar_corrida(self, informe: dict) -> dict:
        """Guarda un reporte del consenso (Modulo 7) como corrida nueva."""
        video = informe.get("video")
        if not video:
            raise ValueError("El reporte del consenso no dice de que video es.")
        regiones = informe.get("regiones_dibujadas")
        if not regiones:
            raise ValueError(
                "El reporte del consenso no trae las regiones con que corrieron las "
                "capas. Los reportes calculados antes del Modulo 8 no las guardaban: "
                "vuelve a correr las capas y a combinar."
            )
        regiones = [
            {
                "esquinas": _puntos(r.get("esquinas"), 4, f"La region {i}"),
                "lineaAgua": (
                    None if r.get("lineaAgua") is None
                    else _puntos(r["lineaAgua"], 2, f"La linea de agua de la region {i}")
                ),
            }
            for i, r in enumerate(regiones, start=1)
        ]

        clips = [
            *(informe.get("aceptados") or []),
            *(informe.get("cola") or []),
            *(informe.get("sin_datos") or []),
        ]
        if not clips:
            raise ValueError("El reporte del consenso no tiene clips.")
        for k in clips:
            if k.get("estado") not in ESTADOS:
                raise ValueError(f"El clip {k.get('clip_id')} tiene un estado desconocido.")
            if k.get("video") != video:
                raise ValueError(f"El clip {k.get('clip_id')} no es del video {video}.")
            if not 1 <= int(k.get("region", 0)) <= len(regiones):
                raise ValueError(f"El clip {k.get('clip_id')} apunta a una region que no existe.")

        rejilla = informe.get("rejilla") or {}
        with self._transaccion() as c:
            anterior = c.execute(
                "SELECT regiones FROM corridas WHERE video = ? ORDER BY id DESC LIMIT 1",
                (video,),
            ).fetchone()
            avisos = (
                _cambio_de_numeracion(json.loads(anterior["regiones"]), regiones)
                if anterior else []
            )
            corrida_id = c.execute(
                """INSERT INTO corridas (video, creada, fps, segundos_por_bloque,
                       cuadro_referencia, regiones, parametros, capas_presentes, avisos)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (
                    video,
                    _ahora(),
                    rejilla.get("fps"),
                    rejilla.get("segundos_por_bloque"),
                    informe.get("cuadro_referencia"),
                    json.dumps(regiones),
                    json.dumps(informe.get("parametros") or {}),
                    json.dumps(informe.get("capas_presentes") or {}),
                    json.dumps((informe.get("avisos") or []) + avisos),
                ),
            ).lastrowid
            c.executemany(
                """INSERT INTO clips (corrida_id, clip_id, video, region, bloque,
                       cuadro_inicio, cuadro_fin, segundo_inicio, segundo_fin, estado,
                       clase_consenso, motivos, capas)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                [
                    (
                        corrida_id, k["clip_id"], video, int(k["region"]), int(k["bloque"]),
                        int(k["cuadro_inicio"]), int(k["cuadro_fin"]),
                        k.get("segundo_inicio"), k.get("segundo_fin"), k["estado"],
                        k.get("clase_consenso"), json.dumps(k.get("motivos") or []),
                        json.dumps(k.get("capas") or {}),
                    )
                    for k in clips
                ],
            )
            conteo = dict(
                c.execute(
                    "SELECT estado, COUNT(*) FROM clips WHERE corrida_id = ? GROUP BY estado",
                    (corrida_id,),
                ).fetchall()
            )
            ya_decididos = c.execute(
                """SELECT COUNT(*) FROM clips k JOIN decisiones d ON d.clip_id = k.clip_id
                   WHERE k.corrida_id = ? AND k.estado = 'discrepancia'""",
                (corrida_id,),
            ).fetchone()[0]

        return {
            "corrida_id": corrida_id,
            "video": video,
            "clips": len(clips),
            "aceptados": conteo.get("aceptado", 0),
            "en_cola": conteo.get("discrepancia", 0),
            "sin_datos": conteo.get("sin_datos", 0),
            "en_cola_ya_decididos": ya_decididos,
            "avisos": avisos,
        }

    # ------------------------------------------------------------------ cola

    def cola(self, video: str | None = None) -> dict:
        """Clips en discrepancia de la corrida vigente de cada video.

        Ordenados por video, region y tiempo: quien revisa sigue a un
        especimen de principio a fin en lugar de saltar entre cilindros.
        """
        filas, corridas = self._consultar_clips(video, solo_estado="discrepancia")
        clips = [self._fila_a_clip(f) for f in filas]
        decididos = sum(k["decision"] is not None for k in clips)
        return {
            "clips": clips,
            "corridas": corridas,
            "total": len(clips),
            "decididos": decididos,
            "pendientes": len(clips) - decididos,
            "motivos": list(MOTIVOS),
            "clases": list(CLASES),
        }

    def todos_los_clips(self, video: str | None = None) -> dict:
        """Todos los clips (aceptados, en cola y sin datos) de la corrida
        vigente de cada video, con su decision si la tienen.

        A diferencia de `cola`, no filtra por estado: el Modulo 9 necesita ver
        tambien los aceptados automaticamente, que nunca pasan por la cola.
        """
        filas, corridas = self._consultar_clips(video, solo_estado=None)
        clips = [self._fila_a_clip(f) for f in filas]
        return {"clips": clips, "corridas": corridas, "total": len(clips)}

    def _consultar_clips(
        self, video: str | None, solo_estado: str | None
    ) -> tuple[list[sqlite3.Row], dict]:
        """SQL compartida por `cola` y `todos_los_clips`.

        Siempre sobre la corrida vigente (la mas reciente) de cada video, con
        su decision humana a la izquierda por si no la tiene.
        """
        filtro, argumentos = "", []
        if solo_estado:
            filtro += " AND k.estado = ?"
            argumentos.append(solo_estado)
        if video:
            filtro += " AND k.video = ?"
            argumentos.append(video)
        with self._transaccion() as c:
            filas = c.execute(
                f"""SELECT k.*, d.etiqueta_humana, d.descartado, d.roi_corregida,
                           d.esquinas_nuevas, d.linea_agua_corregida, d.linea_agua_nueva,
                           d.notas, d.a_ciegas, d.timestamp,
                           d.corrida_id AS decision_corrida_id
                    FROM clips k
                    LEFT JOIN decisiones d ON d.clip_id = k.clip_id
                    WHERE k.corrida_id IN (SELECT MAX(id) FROM corridas GROUP BY video)
                      {filtro}
                    ORDER BY k.video, k.region, k.cuadro_inicio""",
                argumentos,
            ).fetchall()
            ids = sorted({f["corrida_id"] for f in filas})
            corridas = {
                f["id"]: self._corrida(f)
                for f in c.execute(
                    f"SELECT * FROM corridas WHERE id IN ({','.join('?' * len(ids))})", ids
                ).fetchall()
            } if ids else {}
        return filas, corridas

    @staticmethod
    def _fila_a_clip(f: sqlite3.Row) -> dict:
        return {
            "corrida_id": f["corrida_id"],
            "clip_id": f["clip_id"],
            "video": f["video"],
            "region": f["region"],
            "bloque": f["bloque"],
            "cuadro_inicio": f["cuadro_inicio"],
            "cuadro_fin": f["cuadro_fin"],
            "segundo_inicio": f["segundo_inicio"],
            "segundo_fin": f["segundo_fin"],
            "estado": f["estado"],
            "clase_consenso": f["clase_consenso"],
            "motivos": json.loads(f["motivos"]),
            "explicacion": [MOTIVOS.get(m, m) for m in json.loads(f["motivos"])],
            "capas": json.loads(f["capas"]),
            "decision": BaseRevision._decision(f) if f["timestamp"] else None,
        }

    @staticmethod
    def _corrida(f: sqlite3.Row) -> dict:
        return {
            "id": f["id"],
            "video": f["video"],
            "creada": f["creada"],
            "fps": f["fps"],
            "segundos_por_bloque": f["segundos_por_bloque"],
            "cuadro_referencia": f["cuadro_referencia"],
            "regiones": json.loads(f["regiones"]),
            "parametros": json.loads(f["parametros"]),
            "capas_presentes": json.loads(f["capas_presentes"]),
            "avisos": json.loads(f["avisos"]),
        }

    @staticmethod
    def _decision(f: sqlite3.Row) -> dict:
        claves = f.keys()
        return {
            "clip_id": f["clip_id"],
            "corrida_id": f["decision_corrida_id"] if "decision_corrida_id" in claves else f["corrida_id"],
            "etiqueta_humana": f["etiqueta_humana"],
            "descartado": bool(f["descartado"]),
            "roi_corregida": bool(f["roi_corregida"]),
            "esquinas_nuevas": json.loads(f["esquinas_nuevas"]) if f["esquinas_nuevas"] else None,
            "linea_agua_corregida": bool(f["linea_agua_corregida"]),
            "linea_agua_nueva": json.loads(f["linea_agua_nueva"]) if f["linea_agua_nueva"] else None,
            "notas": f["notas"],
            "a_ciegas": bool(f["a_ciegas"]),
            "timestamp": f["timestamp"],
        }

    # ------------------------------------------------------------ decisiones

    def decidir(
        self,
        clip_id: str,
        corrida_id: int,
        etiqueta: str | None,
        descartado: bool,
        esquinas,
        linea_agua,
        notas: str | None = None,
        a_ciegas: bool = False,
    ) -> dict:
        """Guarda (o reemplaza) la decision humana sobre un clip.

        `esquinas` y `linea_agua` son la geometria que la persona tenia en
        pantalla al decidir. Aqui se comparan contra las de la corrida y solo
        se guardan si cambiaron.
        """
        if descartado and etiqueta:
            raise ValueError("Un clip descartado no lleva etiqueta.")
        if not descartado and etiqueta not in CLASES:
            raise ValueError("La etiqueta debe ser una de: " + ", ".join(CLASES) + ".")
        esquinas = _puntos(esquinas, 4, "La region")
        linea_agua = None if linea_agua is None else _puntos(linea_agua, 2, "La linea de agua")
        notas = (notas or "").strip() or None

        with self._transaccion() as c:
            fila = c.execute(
                """SELECT k.region, c.regiones FROM clips k
                   JOIN corridas c ON c.id = k.corrida_id
                   WHERE k.corrida_id = ? AND k.clip_id = ?""",
                (int(corrida_id), clip_id),
            ).fetchone()
            if fila is None:
                raise ClipNoEncontrado(f"El clip {clip_id} no existe en la corrida {corrida_id}.")
            original = json.loads(fila["regiones"])[fila["region"] - 1]

            roi_corregida = not _mismos_puntos(esquinas, original["esquinas"])
            agua_corregida = not _mismos_puntos(linea_agua, original.get("lineaAgua"))
            c.execute(
                """INSERT INTO decisiones (clip_id, corrida_id, etiqueta_humana, descartado,
                       roi_corregida, esquinas_nuevas, linea_agua_corregida,
                       linea_agua_nueva, notas, a_ciegas, timestamp)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                   ON CONFLICT (clip_id) DO UPDATE SET
                       corrida_id = excluded.corrida_id,
                       etiqueta_humana = excluded.etiqueta_humana,
                       descartado = excluded.descartado,
                       roi_corregida = excluded.roi_corregida,
                       esquinas_nuevas = excluded.esquinas_nuevas,
                       linea_agua_corregida = excluded.linea_agua_corregida,
                       linea_agua_nueva = excluded.linea_agua_nueva,
                       notas = excluded.notas,
                       a_ciegas = excluded.a_ciegas,
                       timestamp = excluded.timestamp""",
                (
                    clip_id, int(corrida_id), None if descartado else etiqueta,
                    int(bool(descartado)), int(roi_corregida),
                    json.dumps(esquinas) if roi_corregida else None,
                    int(agua_corregida),
                    json.dumps(linea_agua) if agua_corregida and linea_agua else None,
                    notas, int(bool(a_ciegas)), _ahora(),
                ),
            )
            guardada = c.execute(
                "SELECT * FROM decisiones WHERE clip_id = ?", (clip_id,)
            ).fetchone()
        return self._decision(guardada)

    def decisiones(self) -> list[dict]:
        """Todas las decisiones, con el tramo de video al que se refieren."""
        with self._transaccion() as c:
            filas = c.execute(
                """SELECT d.*, k.video, k.region, k.cuadro_inicio, k.cuadro_fin
                   FROM decisiones d
                   JOIN clips k ON k.clip_id = d.clip_id AND k.corrida_id = d.corrida_id
                   ORDER BY d.timestamp"""
            ).fetchall()
        return [
            {
                **self._decision(f),
                "video": f["video"],
                "region": f["region"],
                "cuadro_inicio": f["cuadro_inicio"],
                "cuadro_fin": f["cuadro_fin"],
            }
            for f in filas
        ]
