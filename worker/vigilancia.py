"""Vigilancia del avance de los pasos del pipeline, medido DESDE AFUERA: el código de fst_auto (pipeline/) no se
toca. worker.py lanza cada paso como un proceso y, cada PERIODO_S, llama a un vigilante que calcula cuánto lleva
con tres señales:

  1. Los marcadores que el pipeline ya imprime en su log ("1/5 alineando la camara", "recortando 4 tubos…",
     "prediciendo N segundos"): dicen en qué paso va.
  2. La POSICIÓN DE LECTURA del video: /proc/<pid>/fdinfo/<fd> trae "pos:", el lugar del archivo por donde va
     leyendo el proceso. pos / tamaño del archivo es la fracción del video que lleva. Sirve en los pasos que
     recorren el video completo (1/5, 2/5 y 4/5 de etiquetar.py, y "recortando" de probar_seg.py).
  3. El TIEMPO, para "prediciendo" (la red neuronal), que no da ninguna señal: se estima con los segundos del
     video y un ritmo medido, y la estimación se acerca a su tope sin llegar nunca hasta que el paso termina.

Si /proc no está disponible, se cae a los marcadores y al tiempo, con un aviso en el log (una sola vez), sin
romper nada. Todo lo que se mide aquí se MIDIÓ con un video real (ver las constantes).
"""

import json
import logging
import math
import os
import re
import time
from pathlib import Path

log = logging.getLogger("worker_fake")   # el mismo logger que comun.log

PERIODO_S = 2.0

# ---------------------------------------------------------------------------------------------------------
#  Constantes MEDIDAS el 19-oct-2026 con un clip de 2 minutos de AcuBN1 (120 s, 1920x1080, 4 tubos, H.264,
#  77.6 MB), dentro del contenedor del worker, SOLO (sin otra carga; Docker con 8 CPU), leyendo /proc cada 1 s:
#
#    preprocesamiento (ffprobe)         0.3 s
#    cilindros (etiquetar.py)         116   s   arranque de Python + 1/5: 58 s | 2/5: 17 s | 3/5: 4 s |
#                                               4/5: 36 s | 5/5: 1 s
#    clasificación                   1065   s   probar_seg.py 933.5 s (arranque y carga de la red + recortando:
#                                               30 s | prediciendo: 903 s) + web.mp4 con ffmpeg: 132 s
#
#  De ahí salen los pesos (en %). Todo crece con la duración del video, así que los pesos valen para cualquier
#  largo. El ritmo de "prediciendo" depende de la computadora: por eso se vuelve a medir en cada análisis y se
#  guarda (leer_ritmo / guardar_ritmo); este número solo se usa la primera vez en una PC.
# ---------------------------------------------------------------------------------------------------------

# Peso de cada paso (1/5 a 5/5) dentro de la etapa "cilindros". El arranque de Python cuenta en el paso 1.
PESO_PASO_CILINDROS = {1: 50, 2: 15, 3: 3, 4: 31, 5: 1}

# Peso de cada fase dentro de la etapa "clasificacion". El arranque y la carga de la red cuentan en "recortando".
PESO_FASE_CLASIFICACION = {"recortando": 3, "prediciendo": 85, "web": 12}

# Segundos de trabajo por cada segundo de video, solo para el caso en que /proc no esté disponible:
# paso 1/5 (con el arranque) 58 s / 120 s, paso 2/5 17 / 120, paso 4/5 36 / 120, recortando 30 / 120.
RITMO_PASO_CILINDROS = {1: 0.48, 2: 0.14, 4: 0.30}
RITMO_RECORTANDO = 0.25

# "prediciendo N segundos" (N = segundos del video x tubos): 903 s / 480 = 1.88 s por segundo-tubo.
RITMO_PREDICCION = 1.88

# Copia web.mp4 con ffmpeg (solo si no hay archivo de -progress): 132 s / 120 s.
RITMO_WEB = 1.1


# Nombres de los pasos de etiquetar.py, como los imprime su log, para tiempos.json.
NOMBRE_PASO = {1: "1/5 alineando la camara", 2: "2/5 modelando el fondo", 3: "3/5 buscando los tubos",
               4: "4/5 midiendo al animal", 5: "5/5 agregando en bloques"}


def leer_texto(ruta):
    try:
        return Path(ruta).read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""


_MARCADOR = re.compile(r"^\s*(\d)/5 ", re.M)
_PREDICIENDO = re.compile(r"prediciendo (\d+) segundos")
_OUT_TIME = re.compile(r"^out_time_us=(\d+)", re.M)


def ultimo_paso(texto):
    """El último marcador "N/5" del log de etiquetar.py, o None si todavía no imprime ninguno."""
    pasos = _MARCADOR.findall(texto)
    return int(pasos[-1]) if pasos else None


def segundos_de_ffmpeg(texto):
    """Segundos de video que lleva convertidos ffmpeg, según su archivo de -progress (0 si aún no hay)."""
    tiempos = _OUT_TIME.findall(texto)
    return int(tiempos[-1]) / 1e6 if tiempos else 0.0


def fraccion_por_tiempo(transcurrido, esperado):
    """Avance de 0 a menos de 1 estimado SOLO con el tiempo. Sube parejo hasta el 90 % del tiempo esperado y
    después se acerca despacio al 99 % sin llegar nunca: si el paso tarda más de lo esperado, la barra no se
    pasma ni llega al tope antes de que el paso termine."""
    if esperado <= 0:
        return 0.0
    x = max(0.0, transcurrido / esperado)
    if x <= 0.9:
        return x
    return 0.9 + 0.09 * (1.0 - math.exp(-(x - 0.9) * 4.0))


def proc_disponible():
    return os.path.isdir("/proc/self/fdinfo")


def _posicion_en_proc(pid, ruta):
    """Posición (en bytes) por la que va leyendo el proceso el archivo `ruta`, o None si no lo tiene abierto."""
    base = "/proc/%d" % pid
    destino = str(ruta)
    for fd in os.listdir(base + "/fd"):
        try:
            if os.readlink("%s/fd/%s" % (base, fd)) != destino:
                continue
            with open("%s/fdinfo/%s" % (base, fd), encoding="ascii", errors="replace") as f:
                for linea in f:
                    if linea.startswith("pos:"):
                        return int(linea.split()[1])
        except OSError:
            continue
    return None


class PosicionLectura:
    """Qué fracción (0 a 1) de un video lleva leída un proceso. Nunca baja dentro de un paso (reiniciar() la pone
    en 0 al empezar un paso nuevo, que vuelve a recorrer el video).

    Un salto grande hacia adelante se pone en CUARENTENA: al empezar, el pipeline lee un fotograma de referencia a
    la mitad del video y regresa al principio (se vio pos 0.49 y luego 0.004). Ese salto solo se acepta si la
    posición se queda allá más de CUARENTENA_S; si regresa, se descarta."""

    SALTO_MAX = 0.2
    CUARENTENA_S = 6.0

    def __init__(self, video, leer_pos=None, reloj=time.monotonic, disponible=None):
        try:
            self.tamano = max(1, os.path.getsize(video))
        except OSError:
            self.tamano = 1
        self._leer = leer_pos or (lambda pid: _posicion_en_proc(pid, video))
        self._reloj = reloj
        self.disponible = proc_disponible() if disponible is None else disponible
        if not self.disponible:
            log.warning("/proc no está disponible: el avance se estima con los marcadores del log y el tiempo")
        self.reiniciar()

    def reiniciar(self):
        self.ultima = 0.0
        self.vista = False
        self._candidata = None

    def leer(self, pid):
        """La fracción aceptada hasta ahora, o None si todavía no se ha visto el archivo abierto (o /proc no sirve)."""
        if not self.disponible:
            return None
        try:
            pos = self._leer(pid)
        except OSError:       # el proceso terminó justo ahora
            pos = None
        if pos is None:
            return self.ultima if self.vista else None
        fraccion = min(1.0, pos / self.tamano)
        ahora = self._reloj()
        if fraccion <= self.ultima + self.SALTO_MAX:
            self.vista = True
            self.ultima = max(self.ultima, fraccion)
            self._candidata = None
        elif self._candidata is None:
            self._candidata = (fraccion, ahora)
        elif ahora - self._candidata[1] >= self.CUARENTENA_S and fraccion >= self._candidata[0]:
            self.vista = True
            self.ultima = fraccion
            self._candidata = None
        return self.ultima if self.vista else None


class VigilanteCilindros:
    """etiquetar.py: devuelve el avance de la etapa "cilindros" (0 a 100) mirando su log y su lectura del video."""

    def __init__(self, avance, video, registro, duracion_video, lectura=None, reloj=time.monotonic):
        self._avance = avance            # función(pct_etapa)
        self._registro = registro
        self._duracion = duracion_video
        self._reloj = reloj
        self._lectura = lectura or PosicionLectura(video, reloj=reloj)
        self._paso = None
        self._inicio_paso = reloj()
        self._arranque = self._inicio_paso
        self._inicios = {}               # paso -> cuándo lo vio empezar este vigilante

    def duraciones(self, fin=None):
        """Segundos que tardó cada paso de etiquetar.py, para tiempos.json (comparar entre computadoras). Se miden con
        el mismo vigilante, así que tienen la precisión de PERIODO_S (~2 s); un paso tan corto que el vigilante no lo
        vio empezar (el 5/5 dura menos de 1 s) se suma al anterior. El arranque de Python cuenta en el paso 1."""
        fin = self._reloj() if fin is None else fin
        pasos = sorted(self._inicios)
        return {NOMBRE_PASO[p]: round((self._inicios[pasos[i + 1]] if i + 1 < len(pasos) else fin) - self._inicios[p], 1)
                for i, p in enumerate(pasos)}

    def __call__(self, pid):
        paso = ultimo_paso(leer_texto(self._registro)) or 1    # antes del primer marcador, Python arranca
        if paso != self._paso:
            self._inicios[paso] = self._arranque if self._paso is None else self._reloj()
            self._paso = paso
            self._inicio_paso = self._reloj()
            self._lectura.reiniciar()
        antes = sum(p for k, p in PESO_PASO_CILINDROS.items() if k < paso)
        fraccion = 0.0
        if paso in RITMO_PASO_CILINDROS:      # los pasos que recorren el video completo
            leida = self._lectura.leer(pid)
            if leida is None and not self._lectura.disponible:
                leida = fraccion_por_tiempo(self._reloj() - self._inicio_paso,
                                            self._duracion * RITMO_PASO_CILINDROS[paso])
            fraccion = leida or 0.0
        self._avance(antes + PESO_PASO_CILINDROS.get(paso, 0) * fraccion)


class VigilanteClasificacion:
    """probar_seg.py: avance de la etapa "clasificacion" (0 a 100) hasta antes de la copia web.mp4. Dos fases:
    "recortando" (una lectura del video: se mide con la posición) y "prediciendo" (sin señal: se estima por tiempo)."""

    def __init__(self, avance, video, registro, duracion_video, ritmo_prediccion, lectura=None, reloj=time.monotonic):
        self._avance = avance
        self._registro = registro
        self._duracion = duracion_video
        self._ritmo = ritmo_prediccion
        self._reloj = reloj
        self._lectura = lectura or PosicionLectura(video, reloj=reloj)
        self._inicio = reloj()
        self.inicio_prediccion = None
        self.n_segundos = None

    def __call__(self, pid):
        texto = leer_texto(self._registro)
        recortando = PESO_FASE_CLASIFICACION["recortando"]
        m = _PREDICIENDO.search(texto)
        if m:
            if self.inicio_prediccion is None:
                self.inicio_prediccion = self._reloj()
                self.n_segundos = int(m.group(1))
            esperado = self.n_segundos * self._ritmo
            fraccion = fraccion_por_tiempo(self._reloj() - self.inicio_prediccion, esperado)
            self._avance(recortando + PESO_FASE_CLASIFICACION["prediciendo"] * fraccion)
            return
        leida = self._lectura.leer(pid) if "recortando" in texto else None
        if leida is None and not self._lectura.disponible:
            leida = fraccion_por_tiempo(self._reloj() - self._inicio, self._duracion * RITMO_RECORTANDO)
        self._avance(recortando * (leida or 0.0))

    def duraciones(self, fin=None):
        """Segundos de "recortando" (con el arranque de Python y la carga de la red) y de "prediciendo", para
        tiempos.json. Vacío si no llegó a "prediciendo". Precisión de PERIODO_S (~2 s)."""
        if self.inicio_prediccion is None:
            return {}
        fin = self._reloj() if fin is None else fin
        return {"recortando": round(self.inicio_prediccion - self._inicio, 1),
                "prediciendo": round(fin - self.inicio_prediccion, 1)}

    def ritmo_medido(self):
        """Segundos reales por segundo-tubo de "prediciendo" (hasta ahora), o None si no llegó a esa fase."""
        if self.inicio_prediccion is None or not self.n_segundos:
            return None
        return (self._reloj() - self.inicio_prediccion) / self.n_segundos


def leer_ritmo(archivo, defecto=RITMO_PREDICCION):
    """El ritmo de "prediciendo" medido en la última corrida de esta computadora, o el de la constante."""
    try:
        valor = float(json.loads(Path(archivo).read_text(encoding="utf-8"))["s_por_segundo_tubo"])
    except (OSError, ValueError, KeyError, TypeError):
        return defecto
    return valor if 0.05 <= valor <= 60 else defecto


def guardar_ritmo(archivo, medido, anterior):
    """Guarda el ritmo medido, promediado con el anterior (0.5/0.5) para que una corrida rara no lo desvíe."""
    if not 0.05 <= medido <= 60:
        return
    nuevo = round(0.5 * anterior + 0.5 * medido, 3)
    ruta = Path(archivo)
    try:
        ruta.parent.mkdir(parents=True, exist_ok=True)
        temporal = ruta.with_suffix(".json.tmp")
        temporal.write_text(json.dumps({"s_por_segundo_tubo": nuevo}), encoding="utf-8")
        os.replace(temporal, ruta)
    except OSError as e:
        log.warning("No se pudo guardar el ritmo de la red en %s: %s", ruta, e)
