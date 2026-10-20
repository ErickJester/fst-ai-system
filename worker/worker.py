"""worker: el worker REAL. Hace lo mismo que worker_fake (la misma cola, las mismas etapas, las mismas
pausas y los mismos avisos: reutiliza su código) pero en lugar de inventar las conductas analiza el
video con el modelo de fst_auto, que vive tal cual en pipeline/ (ver pipeline/README.md):

  1. preprocesamiento  ffprobe abre el video y lo mide.
  2. cilindros         pipeline/etiquetar.py encuentra los tubos y mide al animal en cada uno.
  3. clasificacion     pipeline/cnn/probar_seg.py clasifica cada segundo con la red fst_cnn_seg.pt.

Al final guarda en SEGUNDO los últimos 300 s de cada espécimen, la copia web.mp4 y cajas.json, y avisa,
igual que worker_fake.completar.

Supuestos:
  - El pipeline numera los tubos de izquierda a derecha, de 1 a N. El espécimen k del pipeline es el
    ESPECIMEN de la tanda con numerocilindro k.
  - Este modelo no detecta "no se ve al espécimen", así que la pausa de la sección 4.3 (R-15) no se
    implementa aquí: ningún análisis real pasa a 'en pausa'. Tampoco se usan las palabras de prueba
    "falla" y "pausa" de worker_fake.
  - La carpeta analisis/{id}/trabajo/ (CSV del pipeline, _control.jpg y pipeline.log) se conserva para
    poder revisar a mano un análisis.
"""

import json
import os
import subprocess
import sys
import textwrap
import time
from pathlib import Path

import pandas as pd
from PIL import Image, ImageDraw
from psycopg2.extras import execute_values

import vigilancia
import worker_fake
from comun import (
    ARCHIVOS, ahora, borrar_archivo, carpeta_analisis, datos_analisis, enviar_correos, log, notificar,
)
from progreso import RegistroProgreso
from tiempos import RegistroTiempos
from worker_fake import fuente, marcar_error, poner_etapa

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


def generar_diagnostico_real(ruta, d, etapa, mensaje):
    """El PDF de diagnóstico de worker_fake, pero con el mensaje partido en renglones (el de aquí trae
    la cola del registro del pipeline, que es larga) y sin la leyenda de "simulación"."""
    pagina = Image.new("RGB", (1275, 1650), "white")
    dibujo = ImageDraw.Draw(pagina)
    dibujo.text((100, 100), "Reporte de diagnóstico", font=fuente(40), fill="black")
    renglones = [
        f"Experimento: {d['nombre']}",
        f"Tanda: {d['tanda']}",
        f"Análisis: {d['idanalisis']}",
        f"Etapa: {etapa}",
        f"Fecha: {ahora().strftime('%Y-%m-%d %H:%M')}",
        "",
        "Mensaje técnico:",
    ]
    renglones += textwrap.wrap(mensaje, 68)[:22]
    renglones += ["", "Generado por el worker (pipeline fst_auto, modelo fst_cnn_seg)."]
    texto = fuente(26)
    for i, renglon in enumerate(renglones):
        dibujo.text((100, 200 + i * 45), renglon, font=texto, fill="black")
    pagina.save(ruta, "PDF", resolution=150)


# marcar_error (de worker_fake) llama a generar_diagnostico por su nombre global: se cambia por la nuestra
# para que el PDF de un análisis real no diga que es una simulación.
worker_fake.generar_diagnostico = generar_diagnostico_real


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
    codigo = correr([sys.executable, "etiquetar.py", str(video), "--tubos", str(tubos),
                     "--salida", str(trabajo)], registro, vigilante_cilindros)
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
        return marcar_error(conn, d, "cilindros",
                            f"No se encontraron los cilindros esperados: el video tiene {len(encontrados)} y "
                            f"la tanda tiene {tubos} especímenes. Revisa {nombre}_control.jpg en la carpeta "
                            "de trabajo del análisis.")

    progreso.etapa_hecha("cilindros")

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


def traducir(valor, id_analisis, cilindro):
    """Conducta del pipeline -> conducta de la base; cualquier otro valor queda sin conducta (NULL)."""
    conducta = CONDUCTAS.get(valor)
    if conducta is None:
        log.warning("Análisis %s, cilindro %s: la conducta %r no se reconoce; el segundo queda sin conducta",
                    id_analisis, cilindro, valor)
    return conducta


def completar_real(conn, d, especimenes, video, trabajo, nombre, ancho, alto, progreso, registro, tiempos):
    """El equivalente real de worker_fake.completar (sección 4.4): por espécimen, una OBSERVACION con sus
    últimos 300 SEGUNDO renumerados de 1 a n. Los archivos se escriben antes y la base se actualiza en una
    sola transacción, con la misma notificación."""
    id_analisis = d["idanalisis"]
    carpeta = carpeta_analisis(id_analisis)
    clasificados = pd.read_csv(trabajo / f"{nombre}_cnnseg.csv", encoding="utf-8-sig")
    rasgos = pd.read_csv(trabajo / f"{nombre}_rasgos.csv", encoding="utf-8-sig")

    # Los últimos 300 s de cada espécimen, ordenados por segundo. Todos miden lo mismo; n_total es el
    # largo que vio el pipeline y de él depende dónde empieza web.mp4 para coincidir con SEGUNDO.
    segundos_de = {}
    for _, cilindro in especimenes:
        propios = clasificados[clasificados["especimen"] == cilindro].sort_values("segundo")
        if propios.empty:
            return marcar_error(conn, d, "clasificacion",
                                f"El modelo no clasificó ningún segundo del cilindro {cilindro}.")
        segundos_de[cilindro] = propios.tail(MAX_SEGUNDOS)["conducta"].tolist()
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
    # video original (el mismo formato que worker_fake).
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

            conductas = [traducir(c, id_analisis, cilindro) for c in segundos_de[cilindro]]
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
    worker_fake.main(procesar_real)
