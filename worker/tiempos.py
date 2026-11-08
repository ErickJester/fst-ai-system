"""Cuánto tarda un análisis: analisis/{id}/tiempos.json. Sirve para el cronómetro de la pantalla de progreso, para
comparar entre computadoras y para los reportes. Va en un archivo del volumen y NO en la base (el esquema es el de la
guía y no se toca), con el mismo patrón que progreso.json y pausa.json: el worker lo escribe y el backend lo lee.

    {"inicio": "2026-10-19T12:00:00-06:00", "fin": null, "totalSegundos": null,
     "etapas": [{"etapa": "preprocesamiento", "inicio": "...", "fin": "...", "segundos": 1}, ...],
     "subpasos": {"cilindros": {"1/5 alineando la camara": 54.0, ...}, "clasificacion": {"prediciendo": 903.0, ...}},
     "equipo": {"nombre": "Laptop Erick", "cpu": "...", "nucleos": 8, "memoriaGB": 7.6, "dockerCPU": 8},
     "modelo": {"archivo": "fst_cnn_seg.pt", "version": "5.0.0", "sha256": "...", "tamanoMB": 119.8,
                "modificado": "2026-10-18T13:59:00-06:00", "videosDeEntrenamiento": 12, "clips": 5400},
     "video": {"duracionSegundos": 120.0, "ancho": 1920, "alto": 1080, "tamanoMB": 77.6}}

  - modelo: con qué modelo se hizo el análisis (el archivo, no solo su nombre: el SHA-256 lo identifica aunque
    alguien lo reemplace por otro con el mismo nombre). Para los PDF y los reportes en modo desarrollador. Es null si
    no se pasó la ruta o el archivo no existe.
  - El tiempo que el análisis espera en la cola NO cuenta: inicio es cuando el worker lo toma.
  - Se reescribe al entrar y al salir de cada etapa. Si el análisis termina en error o cancelado (o se pausa), se
    cierran fin y totalSegundos hasta donde llegó (cerrar_archivo). Al reencolar o reiniciar se empieza de cero.
  - Escritura atómica (archivo temporal y os.replace), como progreso.json. Un fallo al escribir se avisa una vez en el
    log y no detiene el análisis.
"""

import hashlib
import json
import os
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

from comun import ahora, carpeta_analisis, log


def modo_desarrollador():
    """MODO_DESARROLLADOR=true prende los tiempos; con false (o sin la variable) NO se escribe tiempos.json. El
    avance (progreso.json) no depende de esto: la barra de 0 a 100 % es para todos. Se lee en cada análisis, no al
    importar, para que las pruebas puedan cambiarla."""
    return os.environ.get("MODO_DESARROLLADOR", "").strip().lower() in ("true", "1", "si", "sí", "yes")


def _leer_cpu():
    """Modelo del procesador según /proc/cpuinfo (dentro de Docker Desktop es el de la máquina virtual de Docker,
    que casi siempre es el del equipo). None si no hay /proc."""
    try:
        with open("/proc/cpuinfo", encoding="utf-8", errors="replace") as f:
            for linea in f:
                if linea.lower().startswith("model name"):
                    return linea.split(":", 1)[1].strip()
    except OSError:
        pass
    return None


def _leer_memoria_gb():
    """Memoria total que ve el contenedor (la de la máquina virtual de Docker), en GB con un decimal."""
    try:
        with open("/proc/meminfo", encoding="utf-8") as f:
            for linea in f:
                if linea.startswith("MemTotal:"):
                    return round(int(linea.split()[1]) / 1024 / 1024, 1)
    except (OSError, ValueError):
        pass
    return None


def _cpus_de_docker():
    """Núcleos que Docker le deja usar a este contenedor: el límite de cpu.max si existe y, si no, los que ve."""
    try:
        cuota, periodo = open("/sys/fs/cgroup/cpu.max", encoding="ascii").read().split()
        if cuota != "max":
            return round(int(cuota) / int(periodo), 1)
    except (OSError, ValueError):
        pass
    try:
        return len(os.sched_getaffinity(0))
    except (AttributeError, OSError):
        return os.cpu_count()


def datos_del_equipo():
    nombre = os.environ.get("EQUIPO_NOMBRE", "").strip() or None
    return {
        "nombre": nombre,
        "cpu": _leer_cpu(),
        "nucleos": os.cpu_count(),
        "memoriaGB": _leer_memoria_gb(),
        "dockerCPU": _cpus_de_docker(),
    }


# Lo que trae el .pt por dentro (la versión de la red y con cuántos videos y clips se entrenó). Lo lee OTRO proceso:
# torch pesa y no se quiere dentro del worker. Es el mismo paquete que abre probar_seg.py (un diccionario con los pesos).
_LEER_PAQUETE = (
    "import json, sys, torch\n"
    "p = torch.load(sys.argv[1], map_location='cpu', weights_only=False)\n"
    "print(json.dumps({'version': p.get('version'), 'videosDeEntrenamiento': len(p.get('videos', [])) or None,"
    " 'clips': p.get('n_clips')}))\n"
)

# (ruta, tamaño, fecha del archivo) → identidad ya calculada. El archivo no cambia de un análisis a otro: el hash y
# la lectura del .pt se hacen una sola vez por proceso y otra vez solo si el archivo cambió.
_MODELOS = {}


def _sha256(ruta):
    resumen = hashlib.sha256()
    with open(ruta, "rb") as f:
        for bloque in iter(lambda: f.read(1024 * 1024), b""):
            resumen.update(bloque)
    return resumen.hexdigest()


def _leer_paquete(ruta):
    """La versión de la red y los datos de entrenamiento que trae el .pt; {} si no se pudo leer (el análisis sigue:
    el SHA-256 ya identifica al modelo)."""
    try:
        salida = subprocess.run([sys.executable, "-c", _LEER_PAQUETE, str(ruta)], capture_output=True, text=True,
                                timeout=120, check=True).stdout
        return json.loads(salida.strip().splitlines()[-1])
    except (OSError, subprocess.SubprocessError, ValueError, IndexError) as e:
        log.warning("No se pudo leer la versión del modelo %s: %s (se registra solo su SHA-256)", ruta, e)
        return {}


def identidad_del_modelo(ruta):
    """Con qué modelo se analiza: el archivo, su tamaño y fecha, su SHA-256 y lo que trae por dentro. None si el
    archivo no existe."""
    ruta = Path(ruta)
    try:
        estado = ruta.stat()
    except OSError:
        return None
    clave = (str(ruta), estado.st_size, estado.st_mtime_ns)
    if clave not in _MODELOS:
        datos = {
            "archivo": ruta.name,
            "version": None,
            "sha256": _sha256(ruta),
            "tamanoMB": round(estado.st_size / (1024 * 1024), 1),
            "modificado": datetime.fromtimestamp(estado.st_mtime).astimezone().isoformat(timespec="seconds"),
            "videosDeEntrenamiento": None,
            "clips": None,
        }
        datos.update(_leer_paquete(ruta))
        _MODELOS[clave] = datos
    return dict(_MODELOS[clave])


def texto_del_modelo(modelo):
    """«fst_cnn_seg.pt · red 5.0.0 · SHA-256 3fa9c1d2e4b5a6f7 · 119.8 MB · archivo del 2026-10-18»."""
    partes = [modelo.get("archivo") or "modelo desconocido"]
    if modelo.get("version"):
        partes.append("red " + str(modelo["version"]))
    if modelo.get("sha256"):
        partes.append("SHA-256 " + modelo["sha256"][:16])
    if modelo.get("tamanoMB") is not None:
        partes.append(f"{modelo['tamanoMB']} MB")
    if modelo.get("modificado"):
        partes.append("archivo del " + str(modelo["modificado"])[:10])
    return " · ".join(partes)


def modelo_del_analisis(id_analisis, carpeta=None):
    """El bloque «modelo» del tiempos.json de un análisis, o None: sin modo desarrollador, sin archivo o de antes de
    que se anotara el modelo."""
    if not modo_desarrollador():
        return None
    archivo = (carpeta or carpeta_analisis(id_analisis)) / "tiempos.json"
    try:
        return json.loads(archivo.read_text(encoding="utf-8")).get("modelo")
    except (OSError, ValueError):
        return None


def _iso(momento):
    return momento.isoformat(timespec="seconds")


def _segundos(inicio, fin):
    return round((datetime.fromisoformat(fin) - datetime.fromisoformat(inicio)).total_seconds(), 1)


def _escribir_archivo(archivo, datos):
    """Escritura atómica. Devuelve el OSError si falló, o None."""
    temporal = archivo.with_suffix(".json.tmp")
    try:
        archivo.parent.mkdir(parents=True, exist_ok=True)
        temporal.write_text(json.dumps(datos, ensure_ascii=False), encoding="utf-8")
        os.replace(temporal, archivo)
    except OSError as e:
        return e
    return None


class RegistroTiempos:
    def __init__(self, id_analisis, carpeta=None, video=None, modelo=None):
        """Empieza un análisis NUEVO: reescribe el archivo desde cero (carpeta solo se cambia en las pruebas). modelo es
        la ruta del .pt con el que se va a analizar; solo se le calcula la identidad en modo desarrollador."""
        self.archivo = (carpeta or carpeta_analisis(id_analisis)) / "tiempos.json"
        self._avisado = False
        self._activo = modo_desarrollador()
        self._datos = {
            "inicio": _iso(ahora()),
            "fin": None,
            "totalSegundos": None,
            "etapas": [],
            "subpasos": {},
            "equipo": datos_del_equipo(),
            "modelo": identidad_del_modelo(modelo) if (self._activo and modelo) else None,
            "video": video,
        }
        self._inicio_monotonico = time.monotonic()
        self._escribir()

    def video(self, duracion_segundos, ancho=None, alto=None, tamano_mb=None):
        self._datos["video"] = {
            "duracionSegundos": round(float(duracion_segundos), 1),
            "ancho": ancho,
            "alto": alto,
            "tamanoMB": None if tamano_mb is None else round(tamano_mb, 1),
        }
        self._escribir()

    def etapa(self, nombre):
        """Entra a una etapa: cierra la que estaba abierta y abre la nueva."""
        self._cerrar_etapa_abierta()
        self._datos["etapas"].append({"etapa": nombre, "inicio": _iso(ahora()), "fin": None, "segundos": None})
        self._escribir()

    def subpaso(self, etapa, nombre, segundos):
        """Un tiempo interno de una etapa (etiquetar paso por paso, recortando, prediciendo, web.mp4)."""
        self._datos["subpasos"].setdefault(etapa, {})[nombre] = round(float(segundos), 1)
        self._escribir()

    def cerrar(self):
        """El análisis terminó (completado): cierra la etapa abierta y el total."""
        self._cerrar_etapa_abierta()
        fin = _iso(ahora())
        self._datos["fin"] = fin
        self._datos["totalSegundos"] = _segundos(self._datos["inicio"], fin)
        self._escribir()

    def _cerrar_etapa_abierta(self):
        if self._datos["etapas"] and self._datos["etapas"][-1]["fin"] is None:
            ultima = self._datos["etapas"][-1]
            ultima["fin"] = _iso(ahora())
            ultima["segundos"] = _segundos(ultima["inicio"], ultima["fin"])

    def _escribir(self):
        if not self._activo:
            return          # sin modo desarrollador no se escribe tiempos.json
        error = _escribir_archivo(self.archivo, self._datos)
        if error is not None and not self._avisado:
            self._avisado = True
            log.warning("No se pudo escribir los tiempos en %s: %s (el análisis sigue)", self.archivo, error)


def cerrar_archivo(id_analisis, carpeta=None):
    """Cierra fin y totalSegundos de un tiempos.json que quedó abierto: lo usan los caminos de error, pausa y
    cancelación, que no tienen el RegistroTiempos a la mano. No hace nada si no hay archivo o si ya estaba cerrado."""
    if not modo_desarrollador():
        return
    archivo = (carpeta or carpeta_analisis(id_analisis)) / "tiempos.json"
    try:
        datos = json.loads(archivo.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return
    if datos.get("fin") is not None:
        return
    fin = _iso(ahora())
    if datos.get("etapas") and datos["etapas"][-1]["fin"] is None:
        ultima = datos["etapas"][-1]
        ultima["fin"] = fin
        ultima["segundos"] = _segundos(ultima["inicio"], fin)
    datos["fin"] = fin
    datos["totalSegundos"] = _segundos(datos["inicio"], fin)
    error = _escribir_archivo(archivo, datos)
    if error is not None:
        log.warning("No se pudo cerrar los tiempos en %s: %s", archivo, error)
