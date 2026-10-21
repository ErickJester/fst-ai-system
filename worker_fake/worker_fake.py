"""worker_fake: simula el worker real con datos inventados (sección 4 de INSTRUCCIONES-WEB.md).

Cumple el mismo contrato que el worker real: toma un análisis a la vez de la tabla ANALISIS (la cola),
recorre las tres etapas, escribe los resultados y avisa. No analiza ningún video: inventa las conductas.

Casos de prueba, según una palabra en el nombre del experimento, del grupo o del archivo (el backend
guarda el video como videos/{idVideo}.mp4, así que el nombre original no llega hasta aquí):
  - "falla": termina en error en la etapa de cilindros, con su PDF de diagnóstico.
  - "pausa": se pausa en la clasificación como si no se viera a un espécimen (sección 4.3).
  - cualquier otro: termina en completado.
"""

import json
import random
import shutil
import time
from datetime import datetime, timedelta

import psycopg2
from PIL import Image, ImageDraw, ImageFont
from psycopg2.extras import execute_values

import tareas_programadas
from progreso import RegistroProgreso
from tiempos import RegistroTiempos, cerrar_archivo
from comun import (
    ARCHIVOS, DAILY_HOUR, PAUSE_TIMEOUT_HOURS, POLL_SECONDS, STAGE_SECONDS,
    ahora, borrar_archivo, carpeta_analisis, configurar_log, conectar, datos_analisis,
    enviar_correos, log, notificar, relativa,
)

ANCHO, ALTO = 1280, 720
FUENTE = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"


def fuente(tamano):
    """DejaVu (con acentos) si está instalada, como en la imagen de Docker; si no, la de Pillow."""
    try:
        return ImageFont.truetype(FUENTE, tamano)
    except OSError:
        return ImageFont.load_default(size=tamano)


# ---------------------------------------------------------------------------
#  Al arrancar y en cada vuelta
# ---------------------------------------------------------------------------

def reencolar_huerfanos(conn):
    """D-84: un análisis en 'procesando' al arrancar es de una corrida que se cortó (solo hay un
    worker). Vuelve a la cola desde el principio, conservando su fechaanalisis."""
    with conn.cursor() as cur:
        cur.execute(
            "UPDATE analisis SET estado = 'en cola', etapa = 'preprocesamiento' "
            "WHERE estado = 'procesando' RETURNING idanalisis"
        )
        ids = [fila[0] for fila in cur.fetchall()]
    conn.commit()
    if ids:
        log.info("Reencolados al arrancar (el worker se apagó a media análisis): %s", ids)


def leer_pausa(id_analisis):
    archivo = carpeta_analisis(id_analisis) / "pausa.json"
    if not archivo.exists():
        return None
    return json.loads(archivo.read_text(encoding="utf-8"))


def revisar_pausas(conn):
    """Cancela las pausas vencidas (5 minutos sin respuesta) y las que no tienen archivo de pausa.
    Todo cambio va con la condición "solo si sigue en pausa": si el backend ya la ajustó o canceló,
    el UPDATE no toca nada (regla b de D-89)."""
    pendientes = []
    with conn.cursor() as cur:
        cur.execute("SELECT idanalisis FROM analisis WHERE estado = 'en pausa'")
        for (id_analisis,) in cur.fetchall():
            pausa = leer_pausa(id_analisis)
            if pausa is None:
                cur.execute(
                    "UPDATE analisis SET estado = 'cancelado' WHERE idanalisis = %s AND estado = 'en pausa'",
                    (id_analisis,),
                )
                log.warning("Análisis %s en pausa sin archivo de pausa: cancelado", id_analisis)
                continue
            if pausa.get("ajuste"):
                continue
            vence = datetime.fromisoformat(pausa["hora_pausa"]) + timedelta(hours=PAUSE_TIMEOUT_HOURS)
            if ahora() < vence:
                continue
            cur.execute(
                "UPDATE analisis SET estado = 'cancelado' WHERE idanalisis = %s AND estado = 'en pausa'",
                (id_analisis,),
            )
            if cur.rowcount == 1:
                d = datos_analisis(cur, id_analisis)
                log.info("Análisis %s cancelado: pasaron %s horas sin ajuste", id_analisis, PAUSE_TIMEOUT_HOURS)
                # Solo la cancelación por falta de respuesta crea notificación (sección 4.3).
                notificar(cur, pendientes, d["idinstitucional"], d["correo"], "analisis cancelado",
                          f"El análisis de {d['tanda']} del experimento '{d['nombre']}' se canceló: "
                          f"pasaron {PAUSE_TIMEOUT_HOURS} horas sin que se ajustara el recuadro.",
                          d["idexperimento"])
    conn.commit()
    enviar_correos(pendientes)


def hay_en_cola(conn):
    with conn.cursor() as cur:
        cur.execute("SELECT EXISTS (SELECT 1 FROM analisis WHERE estado = 'en cola')")
        resultado = cur.fetchone()[0]
    conn.commit()
    return resultado


def tomar_siguiente(conn):
    """D-85: el más antiguo de la cola (menor fechaanalisis; si empatan, menor id). Un análisis
    ajustado conserva su fecha, así que queda por delante de los que llegaron después."""
    with conn.cursor() as cur:
        cur.execute(
            "SELECT idanalisis FROM analisis WHERE estado = 'en cola' "
            "ORDER BY fechaanalisis, idanalisis LIMIT 1 FOR UPDATE SKIP LOCKED"
        )
        fila = cur.fetchone()
        if fila is None:
            conn.rollback()
            return None
        cur.execute("UPDATE analisis SET estado = 'procesando' WHERE idanalisis = %s", fila)
    conn.commit()
    return fila[0]


# ---------------------------------------------------------------------------
#  Un análisis
# ---------------------------------------------------------------------------

def poner_etapa(conn, id_analisis, etapa):
    with conn.cursor() as cur:
        cur.execute("UPDATE analisis SET etapa = %s WHERE idanalisis = %s", (etapa, id_analisis))
    conn.commit()


def procesar(conn, id_analisis):
    with conn.cursor() as cur:
        d = datos_analisis(cur, id_analisis)
        cur.execute(
            "SELECT idespecimen, numerocilindro FROM especimen WHERE idtanda = %s ORDER BY numerocilindro",
            (d["idtanda"],),
        )
        especimenes = cur.fetchall()
    conn.commit()

    texto = " ".join(filter(None, [d["archivo"], d["nombre"], d["etiqueta"]])).lower()
    pausa = leer_pausa(id_analisis)
    # Si viene de una pausa ajustada, sigue desde la clasificación (en el worker real, desde el
    # segundo de la pausa, con el recuadro nuevo).
    retoma = bool(pausa and pausa.get("ajuste"))
    etapas = ["clasificacion"] if retoma else ["preprocesamiento", "cilindros", "clasificacion"]
    log.info("Análisis %s (%s, '%s'): %s", id_analisis, d["tanda"], d["nombre"],
             "retoma tras el ajuste" if retoma else "empieza")

    progreso = RegistroProgreso(id_analisis)
    # El cronómetro del análisis arranca aquí, cuando el worker lo toma: la espera en la cola no cuenta.
    tiempos = RegistroTiempos(id_analisis)
    tiempos.video(d["duracion"])
    for etapa in etapas:
        poner_etapa(conn, id_analisis, etapa)
        tiempos.etapa(etapa)
        # En lugar de dormir STAGE_SECONDS de golpe, la etapa avanza de 0 a 100 en ese tiempo, para que las
        # pruebas con el simulador muestren el mismo progreso que el worker real.
        progreso.avanzar_por_tiempo(etapa, STAGE_SECONDS)
        if etapa == "preprocesamiento":
            if not d["archivo"] or not (ARCHIVOS / d["archivo"]).exists():
                return marcar_error(conn, d, etapa, "No se pudo abrir el video: el archivo no está en el volumen.")
        elif etapa == "cilindros":
            if "falla" in texto:
                return marcar_error(conn, d, etapa,
                                    "No se encontraron los cilindros (caso de prueba: el nombre contiene 'falla').")
            if not especimenes:
                return marcar_error(conn, d, etapa,
                                    "No se encontraron los cilindros: la tanda no tiene especímenes registrados.")
        elif etapa == "clasificacion" and "pausa" in texto and not retoma:
            return pausar(conn, d, especimenes)

    completar(conn, d, especimenes, progreso, tiempos)


def recuadros(especimenes):
    """Recuadros inventados: los tubos repartidos a lo ancho del cuadro, en píxeles del video original."""
    n = max(len(especimenes), 1)
    ancho_tubo = (ANCHO - 80) // n
    return [
        {"cilindro": cilindro, "x0": 40 + i * ancho_tubo + 15, "y0": 120,
         "x1": 40 + (i + 1) * ancho_tubo - 15, "y1": 660}
        for i, (_, cilindro) in enumerate(especimenes)
    ]


def marcar_error(conn, d, etapa, mensaje):
    """Camino de error (sección 4.2): PDF de diagnóstico, estado 'error' y notificación. No hay reintento."""
    carpeta = carpeta_analisis(d["idanalisis"])
    carpeta.mkdir(parents=True, exist_ok=True)
    pdf = carpeta / "diagnostico.pdf"
    generar_diagnostico(pdf, d, etapa, mensaje)

    pendientes = []
    with conn.cursor() as cur:
        # rutadiagnostico y estado en el mismo UPDATE: el CHECK de la tabla solo admite la ruta con 'error'.
        cur.execute(
            "UPDATE analisis SET estado = 'error', rutadiagnostico = %s, fechaanalisis = now() WHERE idanalisis = %s",
            (relativa(pdf), d["idanalisis"]),
        )
        notificar(cur, pendientes, d["idinstitucional"], d["correo"], "error del pipeline",
                  f"El análisis de {d['tanda']} del experimento '{d['nombre']}' terminó con error "
                  f"en la etapa '{etapa}': {mensaje}", d["idexperimento"])
    conn.commit()
    cerrar_archivo(d["idanalisis"])     # el cronómetro se detiene donde llegó
    enviar_correos(pendientes)
    log.info("Análisis %s en error (%s): %s", d["idanalisis"], etapa, mensaje)


def generar_diagnostico(ruta, d, etapa, mensaje):
    """PDF de diagnóstico de una página, hecho con Pillow (una imagen guardada como PDF)."""
    pagina = Image.new("RGB", (1275, 1650), "white")
    dibujo = ImageDraw.Draw(pagina)
    titulo = fuente(40)
    texto = fuente(26)
    dibujo.text((100, 100), "Reporte de diagnóstico", font=titulo, fill="black")
    renglones = [
        f"Experimento: {d['nombre']}",
        f"Tanda: {d['tanda']}",
        f"Análisis: {d['idanalisis']}",
        f"Etapa: {etapa}",
        f"Fecha: {ahora().strftime('%Y-%m-%d %H:%M')}",
        "",
        "Mensaje técnico:",
        mensaje,
        "",
        "Generado por worker_fake (simulación, sin análisis real del video).",
    ]
    for i, renglon in enumerate(renglones):
        dibujo.text((100, 200 + i * 45), renglon, font=texto, fill="black")
    pagina.save(ruta, "PDF", resolution=150)


def pausar(conn, d, especimenes):
    """Sección 4.3: tres segundos seguidos sin ver al espécimen. Primero el archivo, después el estado."""
    carpeta = carpeta_analisis(d["idanalisis"])
    carpeta.mkdir(parents=True, exist_ok=True)
    cajas = recuadros(especimenes)
    cilindro = especimenes[1][1] if len(especimenes) > 1 else especimenes[0][1]

    imagen = Image.new("RGB", (ANCHO, ALTO), (128, 128, 128))
    dibujo = ImageDraw.Draw(imagen)
    for caja in cajas:
        color = "red" if caja["cilindro"] == cilindro else "white"
        dibujo.rectangle((caja["x0"], caja["y0"], caja["x1"], caja["y1"]), outline=color, width=4)
        dibujo.text((caja["x0"] + 10, caja["y0"] - 35), f"Cilindro {caja['cilindro']}",
                    font=fuente(24), fill=color)
    dibujo.text((40, 40), "Imagen fija simulada (worker_fake)", font=fuente(28), fill="white")
    imagen.save(carpeta / "pausa.jpg", quality=85)

    pausa = {
        "version": 1,
        "cilindro": cilindro,
        "desde_segundo": 142,
        "instante_imagen_s": 143.5,
        "imagen": "pausa.jpg",
        "hora_pausa": ahora().isoformat(timespec="seconds"),
        "recuadros": cajas,
        "ajuste": None,
    }
    (carpeta / "pausa.json").write_text(json.dumps(pausa, ensure_ascii=False, indent=2), encoding="utf-8")

    pendientes = []
    with conn.cursor() as cur:
        cur.execute(
            "UPDATE analisis SET estado = 'en pausa' WHERE idanalisis = %s AND estado = 'procesando'",
            (d["idanalisis"],),
        )
        notificar(cur, pendientes, d["idinstitucional"], d["correo"], "analisis en pausa",
                  f"El análisis de {d['tanda']} del experimento '{d['nombre']}' está en pausa: no se ve "
                  f"al espécimen del cilindro {cilindro}. Ajusta el recuadro en los próximos "
                  f"{PAUSE_TIMEOUT_HOURS} horas o el análisis se cancelará.", d["idexperimento"])
    conn.commit()
    cerrar_archivo(d["idanalisis"])     # en pausa el worker ya no trabaja en él: el cronómetro se detiene
    enviar_correos(pendientes)
    log.info("Análisis %s en pausa (cilindro %s); el worker sigue con la cola", d["idanalisis"], cilindro)


def inventar_segundos(rng, n):
    """Una conducta por segundo, con rachas (la conducta tiende a mantenerse) y la inmovilidad
    creciendo con el tiempo, como suele pasar en la prueba. Unos pocos segundos quedan sin conducta
    (None: no se ve o no se decidió) o como conducta activa."""
    conducta = "nado activo"
    segundos = []
    for s in range(n):
        r = rng.random()
        if r < 0.02:
            segundos.append(None)
            continue
        if r < 0.05:
            segundos.append("conducta activa")
            continue
        if rng.random() < 0.15:
            p_inmovilidad = 0.25 + 0.5 * s / n
            x = rng.random()
            if x < p_inmovilidad:
                conducta = "inmovilidad"
            elif x < p_inmovilidad + 0.2:
                conducta = "escalamiento"
            else:
                conducta = "nado activo"
        segundos.append(conducta)
    return segundos


def completar(conn, d, especimenes, progreso, tiempos):
    """Sección 4.4: por espécimen, una OBSERVACION, hasta 300 SEGUNDO (los últimos 300 s, ya
    renumerados de 1 a 300). Los totales por minuto no se guardan (D-52). Los archivos se escriben antes y la base se actualiza en una sola transacción."""
    id_analisis = d["idanalisis"]
    carpeta = carpeta_analisis(id_analisis)
    carpeta.mkdir(parents=True, exist_ok=True)

    # Copia para la revisión: el worker real la recodifica a H.264; aquí solo se copia el original.
    shutil.copyfile(ARCHIVOS / d["archivo"], carpeta / "web.mp4")
    cajas = {"version": 1, "ancho": ANCHO, "alto": ALTO, "recuadros": recuadros(especimenes)}
    (carpeta / "cajas.json").write_text(json.dumps(cajas, indent=2), encoding="utf-8")

    n = min(300, int(d["duracion"]))  # máximo 300 s, sin mínimo (D-54)
    hay_conducta_activa = False
    pendientes = []
    with conn.cursor() as cur:
        for id_especimen, cilindro in especimenes:
            cur.execute(
                "INSERT INTO observacion (idespecimen, idvideo) VALUES (%s, %s) RETURNING idobservacion",
                (id_especimen, d["idvideo"]),
            )
            id_observacion = cur.fetchone()[0]

            conductas = inventar_segundos(random.Random(id_analisis * 10 + cilindro), n)
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
    progreso.terminar()
    tiempos.cerrar()

    # Ya no está en pausa: la carpeta de pausa, si la hubo, sobra.
    borrar_archivo(carpeta / "pausa.json")
    borrar_archivo(carpeta / "pausa.jpg")
    enviar_correos(pendientes)
    log.info("Análisis %s completado: %s especímenes, %s s, nivel %s", id_analisis, len(especimenes), n, nivel)


def marcar_error_imprevisto(conn, id_analisis, error):
    """D-88: cualquier fallo inesperado se atrapa, el análisis pasa a error y el worker sigue."""
    with conn.cursor() as cur:
        d = datos_analisis(cur, id_analisis)
    conn.commit()
    if d is not None:
        marcar_error(conn, d, d["etapa"], f"Fallo imprevisto del pipeline: {error!r}")


# ---------------------------------------------------------------------------
#  Bucle principal
# ---------------------------------------------------------------------------

def vuelta(conn, procesar_analisis):
    revisar_pausas(conn)
    tareas_programadas.correr_si_toca(conn)
    if not hay_en_cola(conn):
        time.sleep(POLL_SECONDS)
        return
    tareas_programadas.vigilar_disco(conn)  # D-86: el disco se revisa antes de cada análisis
    id_analisis = tomar_siguiente(conn)
    if id_analisis is None:
        return
    try:
        procesar_analisis(conn, id_analisis)
    except psycopg2.OperationalError:
        raise
    except Exception as e:  # noqa: BLE001 - D-88: ningún fallo del pipeline tumba al worker
        log.exception("Fallo imprevisto en el análisis %s", id_analisis)
        conn.rollback()
        marcar_error_imprevisto(conn, id_analisis, e)


def main(procesar_analisis=procesar):
    """procesar_analisis es lo único que cambia entre el simulador y el worker real (worker/worker.py):
    la cola, las pausas, las tareas diarias y la reconexión son las mismas, así que no se duplican."""
    configurar_log()
    log.info("worker_fake: cola cada %s s, etapas de %s s, tareas diarias a las %s h, pausa de %s h",
             POLL_SECONDS, STAGE_SECONDS, DAILY_HOUR, PAUSE_TIMEOUT_HOURS)
    conn = None
    arrancando = True
    while True:
        try:
            if conn is None:
                conn = conectar()
                if arrancando:
                    reencolar_huerfanos(conn)
                    arrancando = False
            vuelta(conn, procesar_analisis)
        except psycopg2.OperationalError as e:
            log.error("Sin conexión a la base (%s); reintento en 5 s", e)
            try:
                conn.close()
            except Exception:  # noqa: BLE001
                pass
            conn = None
            time.sleep(5)


if __name__ == "__main__":
    main()
