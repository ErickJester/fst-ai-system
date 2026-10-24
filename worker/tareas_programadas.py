"""Tareas programadas del worker (sección 8 de INSTRUCCIONES-WEB.md, D-82 y D-86).

Corren una vez al día a la hora WORKER_DAILY_HOUR. Si el worker estaba apagado a esa hora, corren al
arrancar; para no repetirlas el mismo día se guarda la fecha en worker/ultima_corrida_tareas.txt.
Como el worker atiende un análisis a la vez, si hay uno en curso esperan solas a que termine.

Este módulo es el que el worker real puede traer tal cual. Para probarlo a mano:
  python tareas_programadas.py             corre todas ahora (aunque ya hayan corrido hoy)
  python tareas_programadas.py --disco     solo la vigilancia del disco
  python tareas_programadas.py --respaldo  solo el respaldo de la base (pg_dump, D-18)
"""

import os
import shutil
import subprocess
import sys

from comun import (
    ARCHIVOS, DAILY_HOUR, DISK_ALERT_PERCENT, DISK_CLEANUP_PERCENT, DISK_SIMULATED_PERCENT, RESPALDOS,
    ahora, borrar_archivo, carpeta_analisis, configurar_log, conectar, datos_conexion, enviar_correos, log, notificar,
)

ULTIMA_CORRIDA = ARCHIVOS / "worker" / "ultima_corrida_tareas.txt"

CONSERVAR_RESPALDOS = 7          # D-18: los 7 más nuevos
LIMITE_RESPALDO_S = 30 * 60      # un pg_dump que pasa de media hora se da por perdido


def leer_ultima_corrida():
    try:
        return ULTIMA_CORRIDA.read_text(encoding="utf-8").strip()
    except FileNotFoundError:
        return None


def correr_si_toca(conn):
    if ahora().hour < DAILY_HOUR or leer_ultima_corrida() == ahora().date().isoformat():
        return
    correr_todas(conn)


def correr_todas(conn):
    log.info("Tareas programadas: inicio")
    # El respaldo va primero, antes de cualquier borrado. Si falla, lo registra y las demás tareas siguen (D-88).
    respaldar_base()
    borrar_videos_vencidos(conn)
    borrar_pausas_huerfanas(conn)
    avisar_vencimientos(conn)
    vigilar_disco(conn)
    ULTIMA_CORRIDA.parent.mkdir(parents=True, exist_ok=True)
    ULTIMA_CORRIDA.write_text(ahora().date().isoformat(), encoding="utf-8")
    log.info("Tareas programadas: fin")


def borrar_video(cur, id_video, archivo):
    """Borra el video original y la copia web.mp4 de su análisis. La fila de VIDEO se conserva
    (los resultados la referencian) y su archivo queda en nulo, que significa "ya se borró"."""
    borrar_archivo(ARCHIVOS / archivo)
    cur.execute("SELECT idanalisis FROM analisis WHERE idvideo = %s", (id_video,))
    for (id_analisis,) in cur.fetchall():
        borrar_archivo(carpeta_analisis(id_analisis) / "web.mp4")
    cur.execute("UPDATE video SET archivo = NULL WHERE idvideo = %s", (id_video,))


def borrar_videos_vencidos(conn):
    """R-05: 30 días después de un análisis completado. La fecha de borrado no se guarda: se
    deriva de fechaanalisis + 30 días."""
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT v.idvideo, v.archivo
            FROM video v
            JOIN analisis a ON a.idvideo = v.idvideo
            WHERE a.estado = 'completado'
              AND v.archivo IS NOT NULL
              AND a.fechaanalisis + interval '30 days' <= now()
            """
        )
        vencidos = cur.fetchall()
        for id_video, archivo in vencidos:
            borrar_video(cur, id_video, archivo)
    conn.commit()
    log.info("Videos vencidos borrados: %s", len(vencidos))


def borrar_pausas_huerfanas(conn):
    """Regla c de D-89: borra pausa.json y pausa.jpg que ya no sirven. Se conservan los de los
    análisis en pausa y los de los que esperan en la cola (o se procesan) con un ajuste por aplicar."""
    carpeta = ARCHIVOS / "analisis"
    if not carpeta.exists():
        return
    with conn.cursor() as cur:
        cur.execute("SELECT idanalisis FROM analisis WHERE estado IN ('en pausa', 'en cola', 'procesando')")
        vigentes = {fila[0] for fila in cur.fetchall()}
    conn.commit()
    borradas = 0
    for archivo in carpeta.glob("*/pausa.json"):
        nombre = archivo.parent.name
        if nombre.isdigit() and int(nombre) in vigentes:
            continue
        borrar_archivo(archivo)
        borrar_archivo(archivo.parent / "pausa.jpg")
        borradas += 1
    log.info("Pausas huérfanas borradas: %s", borradas)


def avisar_vencimientos(conn):
    """Videos a menos de 7 días del borrado. El dashboard los muestra con un dato que el backend
    calcula al vuelo (avisoVencimiento), sin columna, y la guía no define una notificación para
    esto (D-45 lo deja abierto). Así que esta tarea solo los deja en el log."""
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT e.nombre, v.idvideo, (a.fechaanalisis + interval '30 days')::date
            FROM video v
            JOIN analisis a    ON a.idvideo = v.idvideo
            JOIN tanda t       ON t.idtanda = v.idtanda
            JOIN grupo g       ON g.idgrupo = t.idgrupo
            JOIN experimento e ON e.idexperimento = g.idexperimento
            WHERE a.estado = 'completado'
              AND v.archivo IS NOT NULL
              AND a.fechaanalisis + interval '30 days' <= now() + interval '7 days'
            """
        )
        por_vencer = cur.fetchall()
    conn.commit()
    for nombre, id_video, fecha in por_vencer:
        log.info("Aviso: el video %s del experimento '%s' se borrará el %s", id_video, nombre, fecha)


def respaldar_base():
    """D-18 (sección 8): un respaldo de la BASE por día (no de los videos) con pg_dump, en RESPALDOS, que en Docker es una
    carpeta del equipo fuera de los volúmenes. Se llama fst-AAAAMMDD.dump; si ya hay uno de hoy, se reemplaza. Se
    conservan los CONSERVAR_RESPALDOS más nuevos y se borra el resto. Se escribe primero a un temporal y se renombra al
    final: un respaldo cortado nunca queda como válido. Si algo falla solo se registra (D-88) y las demás tareas siguen.
    Devuelve True si el respaldo de hoy quedó listo."""
    datos = datos_conexion()
    hoy = ahora().strftime("%Y%m%d")
    destino = RESPALDOS / f"fst-{hoy}.dump"
    temporal = RESPALDOS / f"fst-{hoy}.dump.tmp"
    try:
        RESPALDOS.mkdir(parents=True, exist_ok=True)
        for resto in RESPALDOS.glob("fst-*.dump.tmp"):     # lo que dejó un respaldo cortado
            borrar_archivo(resto)
        comando = ["pg_dump", "-Fc", "-h", datos["host"], "-p", str(datos["port"]),
                   "-U", datos["user"], "-d", datos["dbname"], "-f", str(temporal)]
        # La contraseña va solo en el entorno del subproceso: ni en la línea de comandos ni en el log.
        entorno = {**os.environ, "PGPASSWORD": datos["password"]}
        subprocess.run(comando, env=entorno, check=True, capture_output=True, text=True, timeout=LIMITE_RESPALDO_S)
        temporal.replace(destino)
    except subprocess.CalledProcessError as e:
        log.error("Respaldo de la base: pg_dump terminó con error (código %s): %s",
                  e.returncode, (e.stderr or "").strip()[:400])
        borrar_archivo(temporal)
        return False
    except (OSError, subprocess.SubprocessError) as e:      # sin pg_dump, carpeta sin permiso, tiempo agotado...
        log.error("Respaldo de la base: no se pudo hacer: %s", e)
        borrar_archivo(temporal)
        return False

    # El nombre lleva la fecha, así que ordenar por nombre es ordenar por antigüedad.
    for viejo in sorted(RESPALDOS.glob("fst-*.dump"), reverse=True)[CONSERVAR_RESPALDOS:]:
        borrar_archivo(viejo)
    log.info("Respaldo de la base: %s (%.1f MB); se conservan los %s más nuevos",
             destino.name, destino.stat().st_size / (1024 * 1024), CONSERVAR_RESPALDOS)
    return True


def uso_disco():
    if DISK_SIMULATED_PERCENT:
        return float(DISK_SIMULATED_PERCENT)
    ARCHIVOS.mkdir(parents=True, exist_ok=True)
    uso = shutil.disk_usage(ARCHIVOS)
    return uso.used * 100 / uso.total


def vigilar_disco(conn):
    """Al pasar del 80 % avisa a los administradores; al pasar del 90 % borra los videos más
    antiguos (RN-05). Corre en la tarea diaria y antes de cada análisis (D-86)."""
    porcentaje = uso_disco()
    if porcentaje > DISK_ALERT_PERCENT:
        alertar_disco(conn, porcentaje)
    if porcentaje > DISK_CLEANUP_PERCENT:
        liberar_disco(conn)


def alertar_disco(conn, porcentaje):
    """Una alerta por día como máximo: el disco se revisa antes de cada análisis y no tiene caso
    mandar el mismo aviso con cada video."""
    pendientes = []
    with conn.cursor() as cur:
        cur.execute(
            "SELECT EXISTS (SELECT 1 FROM notificacion WHERE tipo = 'alerta de disco' "
            "AND fechacreacion::date = current_date)"
        )
        if cur.fetchone()[0]:
            conn.commit()
            return
        cur.execute(
            "SELECT u.idinstitucional, u.correo FROM administrador a "
            "JOIN usuario u ON u.idinstitucional = a.idinstitucional WHERE u.activo"
        )
        administradores = cur.fetchall()
        if not administradores:
            log.warning("Disco al %.0f %%, pero no hay ningún administrador activo a quien avisar", porcentaje)
        for id_institucional, correo in administradores:
            notificar(cur, pendientes, id_institucional, correo, "alerta de disco",
                      f"El disco de videos y reportes está al {porcentaje:.0f} %. Al pasar del "
                      f"{DISK_CLEANUP_PERCENT} % se borran los videos más antiguos.")
    conn.commit()
    enviar_correos(pendientes)


def liberar_disco(conn):
    """Borra videos del más antiguo al más nuevo hasta bajar del umbral. No toca videos cuyo análisis
    sigue pendiente (en cola, procesando o en pausa). Avisa al creador de cada experimento afectado."""
    simulado = bool(DISK_SIMULATED_PERCENT)
    afectados = {}
    pendientes = []
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT v.idvideo, v.archivo, e.idexperimento, e.nombre, e.idinstitucional, u.correo
            FROM video v
            JOIN tanda t       ON t.idtanda = v.idtanda
            JOIN grupo g       ON g.idgrupo = t.idgrupo
            JOIN experimento e ON e.idexperimento = g.idexperimento
            JOIN usuario u     ON u.idinstitucional = e.idinstitucional
            LEFT JOIN analisis a ON a.idvideo = v.idvideo
            WHERE v.archivo IS NOT NULL
              AND (a.estado IS NULL OR a.estado IN ('completado', 'error', 'cancelado'))
            ORDER BY v.fechacarga, v.idvideo
            """
        )
        for id_video, archivo, id_experimento, nombre, id_institucional, correo in cur.fetchall():
            if not simulado and uso_disco() <= DISK_CLEANUP_PERCENT:
                break
            borrar_video(cur, id_video, archivo)
            log.info("Disco: borrado el video %s del experimento '%s'", id_video, nombre)
            datos = afectados.setdefault(id_experimento, [nombre, id_institucional, correo, 0])
            datos[3] += 1
            if simulado:
                break  # con el porcentaje fingido el uso nunca baja: se borra uno solo por corrida

        for id_experimento, (nombre, id_institucional, correo, cuantos) in afectados.items():
            notificar(cur, pendientes, id_institucional, correo, "video borrado",
                      f"Se borraron {cuantos} video(s) del experimento '{nombre}' porque el disco pasó "
                      f"del {DISK_CLEANUP_PERCENT} %. Los resultados y reportes se conservan.", id_experimento)
    conn.commit()
    enviar_correos(pendientes)


if __name__ == "__main__":
    configurar_log()
    if "--respaldo" in sys.argv:       # no necesita conexión de psycopg2: pg_dump se conecta solo
        sys.exit(0 if respaldar_base() else 1)
    conexion = conectar()
    if "--disco" in sys.argv:
        vigilar_disco(conexion)
    else:
        correr_todas(conexion)
    conexion.close()
