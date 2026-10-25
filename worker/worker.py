"""worker: el worker. La cola, las pausas y las tareas diarias viven en cola.py; aquí está lo que hace cada
análisis: analizar el video con el modelo de fst_auto, que vive tal cual en pipeline/ (ver pipeline/README.md):

  1. preprocesamiento  ffprobe abre el video y lo mide.
  2. cilindros         pipeline/etiquetar.py encuentra los tubos y mide al animal en cada uno.
  3. clasificacion     pipeline/cnn/probar_seg.py clasifica cada segundo con la red fst_cnn_seg.pt.

Al final guarda en SEGUNDO los últimos 300 s de cada espécimen, la copia web.mp4 y cajas.json, y avisa.

Supuestos:
  - El pipeline numera los tubos de izquierda a derecha, de 1 a N. El espécimen k del pipeline es el
    ESPECIMEN de la tanda con numerocilindro k.
  - Este modelo no detecta "no se ve al espécimen", así que la pausa de la sección 4.3 (R-15) no se
    implementa aquí: ningún análisis pasa a 'en pausa'. (cola.revisar_pausas sí atiende las que ya existan.)
  - La carpeta analisis/{id}/trabajo/ (CSV del pipeline, _control.jpg y pipeline.log) se conserva para
    poder revisar a mano un análisis.
"""

import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

import pandas as pd
from psycopg2.extras import execute_values

import cola
import vigilancia
from cola import leer_pausa, marcar_error, poner_etapa
from comun import (
    ARCHIVOS, ahora, borrar_archivo, carpeta_analisis, datos_analisis, enviar_correos, log, notificar,
)
from diagnostico import rangos_de_tubos
from progreso import RegistroProgreso
from tiempos import RegistroTiempos

MODELO = Path(os.environ.get("MODELO_CNN", "/data/modelos/fst_cnn_seg.pt"))
PIPELINE = Path(__file__).resolve().parent / "pipeline"
MAX_SEGUNDOS = 300           # R-13, D-54: solo se guardan los últimos 300 s
LIMITE_PASO_S = 60 * 60      # un paso del pipeline que pasa de una hora se da por perdido
LIMITE_FFMPEG_S = 30 * 60

# Nombre de la conducta en el pipeline -> en la base (dominio tipo_conducta). inmovilidad y escalamiento
# se llaman igual en los dos lados.
CONDUCTAS = {
    "nado": "nado activo",
    "activa": "conducta activa",
    "inmovilidad": "inmovilidad",
    "escalamiento": "escalamiento",
}


# ---------------------------------------------------------------------------
#  Utilidades
# ---------------------------------------------------------------------------

def sondear(ruta):
    """(ancho, alto, duración en s) según ffprobe. Los celulares guardan el video acostado con una marca
    de giro; OpenCV, que es lo que lee el pipeline, ya lo endereza, así que aquí también se intercambian
    ancho y alto para que cajas.json hable de los mismos píxeles que los recuadros del pipeline."""
    salida = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v:0",
         "-show_entries", "stream=width,height:stream_side_data=rotation:stream_tags=rotate:format=duration",
         "-of", "json", str(ruta)],
        capture_output=True, text=True, timeout=60, check=True,
    ).stdout
    info = json.loads(salida)
    flujo = info["streams"][0]
    giro = flujo.get("tags", {}).get("rotate")
    for dato in flujo.get("side_data_list", []):
        if "rotation" in dato:
            giro = dato["rotation"]
    giro = int(float(giro)) if giro is not None else 0
    ancho, alto = int(flujo["width"]), int(flujo["height"])
    if abs(giro) % 180 == 90:
        ancho, alto = alto, ancho
    return ancho, alto, float(info["format"]["duration"])


def correr(comando, registro, al_vigilar=None, limite=LIMITE_PASO_S):
    """Corre un paso del pipeline desde pipeline/ (sus archivos se importan entre sí) y manda toda su
    salida a registro. Devuelve el código de salida, o None si pasó del límite de tiempo.
    Cada vigilancia.PERIODO_S llama a al_vigilar(pid) para medir el avance del paso desde afuera; si el
    vigilante falla, se avisa una vez y el análisis sigue sin barra fina: nunca se rompe por medirlo."""
    entorno = {**os.environ, "PYTHONUNBUFFERED": "1", "PYTHONIOENCODING": "utf-8"}
    with open(registro, "ab") as f:
        f.write(f"\n$ {' '.join(comando)}\n".encode("utf-8"))
        f.flush()
        proceso = subprocess.Popen(comando, cwd=PIPELINE, env=entorno, stdout=f, stderr=subprocess.STDOUT)
        inicio = time.monotonic()
        avisado = False
        while True:
            try:
                return proceso.wait(timeout=vigilancia.PERIODO_S)
            except subprocess.TimeoutExpired:
                pass
            if time.monotonic() - inicio > limite:
                proceso.kill()
                proceso.wait()
                return None
            if al_vigilar is not None:
                try:
                    al_vigilar(proceso.pid)
                except Exception as e:  # noqa: BLE001  (medir el avance no puede romper el análisis)
                    if not avisado:
                        avisado = True
                        log.warning("No se pudo medir el avance de %s: %s", Path(comando[1]).name, e)


def ultimas_lineas(registro, cuantas=4):
    """Las últimas líneas del registro, en una sola línea de texto, para el mensaje de error."""
    try:
        lineas = [l.strip() for l in Path(registro).read_text(encoding="utf-8", errors="replace").splitlines()
                  if l.strip()]
    except OSError:
        return ""
    return " | ".join(lineas[-cuantas:])[:400]


def fallo_del_pipeline(conn, d, etapa, que, codigo, registro):
    """Error de un paso que terminó mal (o pasó del límite): mensaje claro y la cola del registro."""
    if codigo is None:
        causa = f"{que}: pasó de {LIMITE_PASO_S // 60} minutos y se detuvo."
    else:
        causa = f"{que}: el pipeline terminó con error (código {codigo})."
    marcar_error(conn, d, etapa, f"{causa} Últimas líneas del registro: {ultimas_lineas(registro)}")



# ---------------------------------------------------------------------------
#  Un análisis
# ---------------------------------------------------------------------------

def procesar_real(conn, id_analisis):
    with conn.cursor() as cur:
        d = datos_analisis(cur, id_analisis)
        cur.execute(
            "SELECT idespecimen, numerocilindro FROM especimen WHERE idtanda = %s ORDER BY numerocilindro",
            (d["idtanda"],),
        )
        especimenes = cur.fetchall()
    conn.commit()
    log.info("Análisis %s (%s, '%s'): empieza (modelo real)", id_analisis, d["tanda"], d["nombre"])

    # Avance para la pantalla de progreso (analisis/{id}/progreso.json); un análisis reiniciado empieza en 0.
    progreso = RegistroProgreso(id_analisis)
    # Cronómetro del análisis (analisis/{id}/tiempos.json): arranca aquí, cuando el worker lo toma; la espera en la
    # cola no cuenta. Un análisis reiniciado o reencolado empieza de cero.
    tiempos = RegistroTiempos(id_analisis)

    # a. preprocesamiento: el video existe y se puede leer
    poner_etapa(conn, id_analisis, "preprocesamiento")
    tiempos.etapa("preprocesamiento")
    video = ARCHIVOS / d["archivo"] if d["archivo"] else None
    if video is None or not video.exists():
        return marcar_error(conn, d, "preprocesamiento",
                            "No se pudo abrir el video: el archivo no está en el volumen.")
    if not especimenes:
        return marcar_error(conn, d, "preprocesamiento",
                            "La tanda no tiene especímenes registrados: no se sabe cuántos cilindros buscar.")
    if not MODELO.exists():
        return marcar_error(conn, d, "preprocesamiento",
                            f"No se encontró el modelo de clasificación ({MODELO}). Revisa MODELO_CNN y el "
                            "volumen de modelos.")
    try:
        ancho, alto, duracion = sondear(video)
    except (subprocess.SubprocessError, KeyError, IndexError, ValueError) as e:
        return marcar_error(conn, d, "preprocesamiento",
                            f"No se pudo leer el video con ffprobe: el archivo está dañado o no es un video "
                            f"({type(e).__name__}).")
    log.info("Análisis %s: video de %sx%s, %.1f s", id_analisis, ancho, alto, duracion)
    tiempos.video(duracion, ancho, alto, video.stat().st_size / (1024 * 1024))
    progreso.etapa_hecha("preprocesamiento")

    trabajo = carpeta_analisis(id_analisis) / "trabajo"
    trabajo.mkdir(parents=True, exist_ok=True)
    registro = trabajo / "pipeline.log"
    registro.write_bytes(b"")
    nombre = video.stem        # el pipeline nombra sus archivos con el nombre del video: {idVideo}_...

    # b. cilindros: etiquetar.py encuentra los tubos y mide al animal
    poner_etapa(conn, id_analisis, "cilindros")
    tiempos.etapa("cilindros")
    tubos = len(especimenes)
    vigilante_cilindros = vigilancia.VigilanteCilindros(lambda pct: progreso.avance("cilindros", pct),
                                                        video, registro, duracion)
    # §4.3: si el usuario ajustó una pausa, su corrección se acumula en correcciones.json y se pasa a etiquetar.py.
    # Las correcciones acumuladas son todas las del análisis (una por cada pausa atendida).
    correcciones = acumular_correccion(id_analisis)
    argumentos_correccion = ["--correccion", str(correcciones)] if correcciones else []
    codigo = correr([sys.executable, "etiquetar.py", str(video), "--tubos", str(tubos),
                     "--salida", str(trabajo), *argumentos_correccion], registro, vigilante_cilindros)
    for paso, segundos in vigilante_cilindros.duraciones().items():
        tiempos.subpaso("cilindros", paso, segundos)
    if codigo != 0:
        return fallo_del_pipeline(conn, d, "cilindros", "No se pudieron encontrar los cilindros", codigo, registro)
    rasgos = trabajo / f"{nombre}_rasgos.csv"
    if not rasgos.exists():
        return marcar_error(conn, d, "cilindros",
                            "No se pudieron encontrar los cilindros: el pipeline terminó sin generar "
                            f"{rasgos.name}. Últimas líneas del registro: {ultimas_lineas(registro)}")
    tabla = pd.read_csv(rasgos, encoding="utf-8-sig")
    encontrados = {int(e) for e in tabla["especimen"].unique()}
    cilindros = {cilindro for _, cilindro in especimenes}
    if encontrados != cilindros:
        # Estos números van al pie de la imagen de control del PDF de diagnóstico.
        d["cilindros_esperados"], d["cilindros_encontrados"] = tubos, len(encontrados)
        rangos = rangos_de_tubos(registro)
        return marcar_error(conn, d, "cilindros",
                            f"No se encontraron los cilindros esperados: el video tiene {len(encontrados)} y "
                            f"la tanda tiene {tubos} especímenes. En el reporte de diagnóstico está la imagen de "
                            "control con los tubos que se encontraron."
                            + (f" Tubos encontrados (columnas en píxeles): {rangos}." if rangos else ""))

    progreso.etapa_hecha("cilindros")

    # §4.3 (R-15): tres segundos seguidos sin animal en un cilindro pausan el análisis antes de clasificar. Solo se
    # mira el tramo que se guarda (los últimos 300 s, D-54). Si ya se pausó, se suelta el análisis.
    cuadros_cil = pd.read_csv(trabajo / f"{nombre}_cuadros.csv", encoding="utf-8-sig")
    ultimo = int(float(cuadros_cil["t"].max())) + 1
    inicio_guardado = max(1, ultimo - MAX_SEGUNDOS + 1)
    racha = racha_de_pausa(segundos_sin_animal(cuadros_cil, ultimo), inicio_guardado, ultimo)
    if racha is not None:
        cilindro, desde = racha
        pausado = pausar_analisis(conn, d, id_analisis, cilindro, desde, video, tabla, carpeta_analisis(id_analisis))
        if pausado:
            tiempos.cerrar()
            return

    # c. clasificacion: probar_seg.py clasifica cada segundo con la red. Su ritmo se mide en cada corrida y se
    # guarda para que la barra de la siguiente se ajuste a esta computadora.
    poner_etapa(conn, id_analisis, "clasificacion")
    tiempos.etapa("clasificacion")
    archivo_ritmo = ARCHIVOS / "worker" / "ritmo_prediccion.json"
    ritmo = vigilancia.leer_ritmo(archivo_ritmo)
    vigilante = vigilancia.VigilanteClasificacion(lambda pct: progreso.avance("clasificacion", pct),
                                                  video, registro, duracion, ritmo)
    codigo = correr([sys.executable, "cnn/probar_seg.py", str(rasgos), "--red", str(MODELO),
                     "--video", str(video)], registro, vigilante)
    for fase, segundos in vigilante.duraciones().items():
        tiempos.subpaso("clasificacion", fase, segundos)
    if codigo != 0:
        return fallo_del_pipeline(conn, d, "clasificacion", "No se pudo clasificar el video", codigo, registro)
    medido = vigilante.ritmo_medido()
    if medido is not None:
        vigilancia.guardar_ritmo(archivo_ritmo, medido, ritmo)
    clasificados = trabajo / f"{nombre}_cnnseg.csv"
    if not clasificados.exists():
        return marcar_error(conn, d, "clasificacion",
                            "No se pudo clasificar el video: el pipeline terminó sin generar "
                            f"{clasificados.name}. Últimas líneas del registro: {ultimas_lineas(registro)}")

    # d. completar (la copia web.mp4 es la última fase de la etapa clasificacion)
    completar_real(conn, d, especimenes, video, trabajo, nombre, ancho, alto, progreso, registro, tiempos)


RACHA_PAUSA_S = 3   # §4.3: tres segundos seguidos sin animal en un cilindro pausan el análisis


def segundos_sin_animal(cuadros, ultimo_segundo):
    """{cilindro: set de segundos} en los que, para ese cilindro, NINGÚN fotograma medido tiene al animal (cx NaN en
    _cuadros.csv). Los segundos van de 1 en adelante, como en SEGUNDO. Un segundo sin fotogramas no cuenta."""
    cuadros = cuadros.assign(segundo=cuadros["t"].astype(float).apply(lambda t: int(t) + 1))
    sin = {}
    for (cilindro, segundo), grupo in cuadros.groupby(["s", "segundo"]):
        if segundo <= ultimo_segundo and grupo["cx"].isna().all():
            sin.setdefault(int(cilindro), set()).add(int(segundo))
    return sin


def racha_de_pausa(sin_animal, inicio, fin):
    """La racha de RACHA_PAUSA_S segundos seguidos sin animal MÁS TEMPRANA en el tiempo, dentro de [inicio, fin], en
    cualquier cilindro. En empate de segundo gana el cilindro de número menor. Devuelve (cilindro, desde_segundo) o None.
    Solo mira el tramo que se guarda (los últimos 300 s, D-54)."""
    mejor = None
    for cilindro in sorted(sin_animal):
        for s in range(inicio, fin - RACHA_PAUSA_S + 2):
            if all(k in sin_animal[cilindro] for k in range(s, s + RACHA_PAUSA_S)):
                if mejor is None or s < mejor[1]:      # sorted() por cilindro: en empate se queda el de número menor
                    mejor = (cilindro, s)
                break                                 # la primera racha de este cilindro es la suya más temprana
    return mejor


def instante_de_la_pausa(desde_segundo):
    """Centro de la racha. El segundo k va de k-1 a k, así que la racha k, k+1, k+2 cubre de k-1 a k+2 y su centro es
    k+0.5 (con RACHA_PAUSA_S = 3, k - 1 + 1.5)."""
    return desde_segundo - 1 + RACHA_PAUSA_S / 2


def desde_de_la_correccion(desde_segundo):
    """La corrección empieza en k-1: el instante donde empieza el segundo k (que va de k-1 a k)."""
    return desde_segundo - 1


def acumular_correccion(id_analisis):
    """§4.3, al retomar: si pausa.json trae un ajuste, se agrega a correcciones.json (que acumula las del análisis), se
    borran pausa.json y pausa.jpg, y se devuelve la ruta de correcciones.json. Sin ajuste, devuelve la de las que ya
    hay (o None). Así etiquetar.py las aplica todas en orden de desde_segundo."""
    carpeta = carpeta_analisis(id_analisis)
    archivo_corr = carpeta / "correcciones.json"
    lista = []
    if archivo_corr.exists():
        lista = json.loads(archivo_corr.read_text(encoding="utf-8"))
    pausa = leer_pausa(id_analisis)
    if pausa and pausa.get("ajuste"):
        a = pausa["ajuste"]
        lista.append({"cilindro": int(a["cilindro"]),
                      "desde_segundo": desde_de_la_correccion(float(pausa["desde_segundo"])),
                      "x0": a["x0"], "y0": a["y0"], "x1": a["x1"], "y1": a["y1"]})
        escribir_atomico(archivo_corr, lista)
        borrar_archivo(carpeta / "pausa.json")
        borrar_archivo(carpeta / "pausa.jpg")
    return archivo_corr if lista else None


def recuadros_en_instante(rasgos, instante):
    """Por cilindro, el recuadro bx0..by1 del bloque de 5 s que contiene el instante (t0 <= instante < t1). Si para ese
    cilindro no hay bloque en el instante, el del bloque más cercano. Devuelve la lista para pausa.json."""
    recuadros = []
    for cil in sorted(rasgos["especimen"].unique()):
        del_tubo = rasgos[rasgos["especimen"] == cil]
        dentro = del_tubo[(del_tubo["t0"] <= instante) & (instante < del_tubo["t1"])]
        if dentro.empty:
            # el bloque cuyo centro esté más cerca del instante
            centros = (del_tubo["t0"] + del_tubo["t1"]) / 2
            fila = del_tubo.loc[(centros - instante).abs().idxmin()]
        else:
            fila = dentro.iloc[0]
        recuadros.append({"cilindro": int(cil), "x0": int(fila["bx0"]), "y0": int(fila["by0"]),
                          "x1": int(fila["bx1"]), "y1": int(fila["by1"])})
    return recuadros


def escribir_atomico(ruta, datos_o_bytes):
    """Escribe a un temporal y lo renombra: un corte a media escritura nunca deja el archivo a medias (§4.3)."""
    temporal = ruta.with_name(ruta.name + ".tmp")
    if isinstance(datos_o_bytes, bytes):
        temporal.write_bytes(datos_o_bytes)
    else:
        temporal.write_text(json.dumps(datos_o_bytes, indent=2, ensure_ascii=False), encoding="utf-8")
    temporal.replace(ruta)


def pausar_analisis(conn, d, id_analisis, cilindro, desde_segundo, video, rasgos, carpeta):
    """§4.3: fotograma del video ORIGINAL en el centro de la racha (k+0.5), pausa.json con los recuadros de todos los
    cilindros en ese instante, y el análisis a 'en pausa'. Regla (a): primero el archivo y después el estado; si el
    estado ya no es 'procesando' (lo cancelaron o lo borraron a medias), se borran los archivos y no se crea nada."""
    import cv2
    instante = instante_de_la_pausa(desde_segundo)
    # Sin enderezar (ORIENTATION_AUTO = 0): el fotograma es el mismo que lee fst_auto con ROT = 0, y los recuadros salen de ahí.
    # (con set(), no como parámetro de apertura: OpenCV 5 rechaza ese parámetro y la captura queda en None)
    cap = cv2.VideoCapture(str(video))
    cap.set(cv2.CAP_PROP_ORIENTATION_AUTO, 0)
    cap.set(cv2.CAP_PROP_POS_MSEC, instante * 1000)
    ok, fotograma = cap.read()
    cap.release()
    if not ok:
        raise RuntimeError(f"no se pudo leer el fotograma de la pausa en {instante:.1f} s")
    alto, ancho = fotograma.shape[:2]     # el tamaño de la imagen que ve el usuario, no el de ffprobe

    # Los recuadros de TODOS los cilindros en ESE instante (no la mediana del análisis: la cámara se mueve y el
    # usuario ajusta sobre la imagen de ese instante). Se toma el bloque de 5 s que lo contiene.
    recuadros = recuadros_en_instante(rasgos, instante)

    ahora_zona = ahora().isoformat(timespec="seconds")
    pausa = {
        "version": 1,
        "cilindro": int(cilindro),
        "desde_segundo": int(desde_segundo),
        "instante_imagen_s": float(instante),
        "imagen": "pausa.jpg",
        "hora_pausa": ahora_zona,
        "ancho": int(ancho),     # tamaño de la imagen (lo lee el backend para acotar el recuadro que ajusta el usuario)
        "alto": int(alto),
        "recuadros": recuadros,
        "ajuste": None,
    }
    ok, jpg = cv2.imencode(".jpg", fotograma, [cv2.IMWRITE_JPEG_QUALITY, 90])
    escribir_atomico(carpeta / "pausa.jpg", jpg.tobytes())
    escribir_atomico(carpeta / "pausa.json", pausa)

    pendientes = []
    with conn.cursor() as cur:
        cur.execute("UPDATE analisis SET estado = 'en pausa' WHERE idanalisis = %s AND estado = 'procesando'",
                    (id_analisis,))
        cambio = cur.rowcount
        if cambio == 1:
            notificar(cur, pendientes, d["idinstitucional"], d["correo"], "analisis en pausa",
                      f"El análisis de {d['tanda']} del experimento '{d['nombre']}' se pausó: el cilindro {cilindro} "
                      f"no se ve desde el segundo {desde_segundo}. Ajusta el recuadro para continuar.",
                      d["idexperimento"])
    conn.commit()
    if cambio != 1:
        # Alguien lo canceló o lo borró a la mitad: no queda pausa ni notificación.
        borrar_archivo(carpeta / "pausa.json")
        borrar_archivo(carpeta / "pausa.jpg")
        log.warning("Análisis %s: no se pausó (ya no estaba procesando); se borraron los archivos de la pausa", id_analisis)
        return False
    enviar_correos(pendientes)
    log.info("Análisis %s en pausa: cilindro %s desde el segundo %s", id_analisis, cilindro, desde_segundo)
    return True


def traducir(valor, id_analisis, cilindro):
    """Conducta del pipeline -> conducta de la base; cualquier otro valor queda sin conducta (NULL)."""
    conducta = CONDUCTAS.get(valor)
    if conducta is None:
        log.warning("Análisis %s, cilindro %s: la conducta %r no se reconoce; el segundo queda sin conducta",
                    id_analisis, cilindro, valor)
    return conducta


def completar_real(conn, d, especimenes, video, trabajo, nombre, ancho, alto, progreso, registro, tiempos):
    """Cierra el análisis (sección 4.4): por espécimen, una OBSERVACION con sus
    últimos 300 SEGUNDO renumerados de 1 a n. Los archivos se escriben antes y la base se actualiza en una
    sola transacción, con la misma notificación."""
    id_analisis = d["idanalisis"]
    carpeta = carpeta_analisis(id_analisis)
    clasificados = pd.read_csv(trabajo / f"{nombre}_cnnseg.csv", encoding="utf-8-sig")
    rasgos = pd.read_csv(trabajo / f"{nombre}_rasgos.csv", encoding="utf-8-sig")

    # Los últimos 300 s de cada espécimen, ordenados por segundo. Todos miden lo mismo; n_total es el
    # largo que vio el pipeline y de él depende dónde empieza web.mp4 para coincidir con SEGUNDO.
    segundos_de = {}
    numeros_de = {}
    # §4.3: los segundos sin animal de cada cilindro (cx NaN en todos sus fotogramas), para dejarlos con conducta nula.
    cuadros_cil = pd.read_csv(trabajo / f"{nombre}_cuadros.csv", encoding="utf-8-sig")
    sin_animal = segundos_sin_animal(cuadros_cil, int(float(cuadros_cil["t"].max())) + 1)
    for _, cilindro in especimenes:
        propios = clasificados[clasificados["especimen"] == cilindro].sort_values("segundo")
        if propios.empty:
            return marcar_error(conn, d, "clasificacion",
                                f"El modelo no clasificó ningún segundo del cilindro {cilindro}.")
        segundos_de[cilindro] = propios.tail(MAX_SEGUNDOS)["conducta"].tolist()
        numeros_de[cilindro] = propios.tail(MAX_SEGUNDOS)["segundo"].astype(int).tolist()
    n_total = int(clasificados["segundo"].max())
    inicio = max(0, n_total - MAX_SEGUNDOS)

    # web.mp4: la copia para revisar en el navegador, recodificada a H.264 y sin audio (guía 11.16),
    # con los mismos últimos 300 s que se guardan en SEGUNDO (o todo el video si dura menos).
    # -progress escribe en un archivo cuántos segundos de video lleva convertidos (out_time_us): con eso y la
    # duración de la copia se mide esta fase, que pesa el 12 % de la etapa (ver vigilancia.py).
    archivo_progreso = carpeta / "web_progreso.txt"
    archivo_progreso.write_bytes(b"")
    duracion_copia = max(1, min(MAX_SEGUNDOS, n_total))
    comando = ["ffmpeg", "-y", "-v", "error", "-progress", str(archivo_progreso)]
    if inicio > 0:
        comando += ["-ss", str(inicio)]
    comando += ["-i", str(video), "-t", str(MAX_SEGUNDOS), "-an", "-c:v", "libx264", "-preset", "veryfast",
                "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(carpeta / "web.mp4")]
    fase_web = vigilancia.PESO_FASE_CLASIFICACION
    antes_web = 100 - fase_web["web"]

    def vigilar_ffmpeg(_pid):
        hecho = vigilancia.segundos_de_ffmpeg(vigilancia.leer_texto(archivo_progreso)) / duracion_copia
        progreso.avance("clasificacion", antes_web + fase_web["web"] * min(hecho, 0.99))

    inicio_web = time.monotonic()
    codigo = correr(comando, registro, vigilar_ffmpeg, LIMITE_FFMPEG_S)
    tiempos.subpaso("clasificacion", "web.mp4", time.monotonic() - inicio_web)
    borrar_archivo(archivo_progreso)
    if codigo is None:
        return marcar_error(conn, d, "clasificacion",
                            f"No se pudo preparar la copia del video para revisión: ffmpeg pasó de "
                            f"{LIMITE_FFMPEG_S // 60} minutos.")
    if codigo != 0:
        return marcar_error(conn, d, "clasificacion",
                            f"No se pudo preparar la copia del video para revisión (ffmpeg): "
                            f"{ultimas_lineas(registro)}")

    # cajas.json: el recuadro de cada tubo es la mediana de sus recuadros por bloque, en píxeles del
    # video original.
    recuadros = []
    for _, cilindro in especimenes:
        del_tubo = rasgos[rasgos["especimen"] == cilindro]
        x0, y0, x1, y1 = (int(round(del_tubo[c].median())) for c in ("bx0", "by0", "bx1", "by1"))
        recuadros.append({"cilindro": cilindro, "x0": x0, "y0": y0, "x1": x1, "y1": y1})
    cajas = {"version": 1, "ancho": ancho, "alto": alto, "recuadros": recuadros}
    (carpeta / "cajas.json").write_text(json.dumps(cajas, indent=2), encoding="utf-8")

    hay_conducta_activa = False
    pendientes = []
    with conn.cursor() as cur:
        for id_especimen, cilindro in especimenes:
            cur.execute(
                "INSERT INTO observacion (idespecimen, idvideo) VALUES (%s, %s) RETURNING idobservacion",
                (id_especimen, d["idvideo"]),
            )
            id_observacion = cur.fetchone()[0]

            # §4.3: un segundo en que el cilindro no tiene animal en ningún fotograma queda con conducta NULA, aunque
            # la red diga otra cosa. Los segundos van con la misma numeración que SEGUNDO (1 a n, los últimos 300).
            vacios = sin_animal.get(cilindro, set())
            conductas = [
                None if numero in vacios else traducir(c, id_analisis, cilindro)
                for numero, c in zip(numeros_de[cilindro], segundos_de[cilindro])
            ]
            hay_conducta_activa |= "conducta activa" in conductas
            # Los totales por minuto no se guardan: el backend los calcula desde SEGUNDO (D-52).
            execute_values(
                cur,
                "INSERT INTO segundo (idobservacion, segundo, conducta, origen) VALUES %s",
                [(id_observacion, s, c, "maquina") for s, c in enumerate(conductas, start=1)],
            )

        nivel = "agrupado" if hay_conducta_activa else "preciso"
        cur.execute(
            "UPDATE analisis SET estado = 'completado', etapa = 'clasificacion', nivelclasif = %s, "
            "fechaanalisis = now() WHERE idanalisis = %s",
            (nivel, id_analisis),
        )
        notificar(cur, pendientes, d["idinstitucional"], d["correo"], "analisis completado",
                  f"El análisis de {d['tanda']} del experimento '{d['nombre']}' terminó. "
                  f"Ya puedes ver los resultados.", d["idexperimento"])
    conn.commit()
    progreso.terminar()      # hasta aquí el general no llega a 100: el análisis ya quedó completado
    tiempos.cerrar()

    borrar_archivo(carpeta / "pausa.json")
    borrar_archivo(carpeta / "pausa.jpg")
    enviar_correos(pendientes)
    log.info("Análisis %s completado: %s especímenes, %s s, nivel %s", id_analisis, len(especimenes),
             min(MAX_SEGUNDOS, n_total), nivel)


if __name__ == "__main__":
    cola.main(procesar_real)
