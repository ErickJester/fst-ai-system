"""Piezas que comparten cola.py, worker.py, tareas_programadas.py y los registros de avance y tiempos:
configuración (variables de entorno), conexión a la base, rutas del volumen y avisos."""

import logging
import os
import shutil
import smtplib
from datetime import datetime
from email.message import EmailMessage
from pathlib import Path
from zoneinfo import ZoneInfo

import psycopg2

log = logging.getLogger("worker")


def _entero(nombre, defecto):
    valor = os.environ.get(nombre, "").strip()
    return int(valor) if valor else defecto


# Variables de la guía (sección 2) con sus valores por defecto.
POLL_SECONDS = _entero("WORKER_POLL_SECONDS", 5)
DAILY_HOUR = _entero("WORKER_DAILY_HOUR", 3)
PAUSE_TIMEOUT_HOURS = _entero("PAUSE_TIMEOUT_HOURS", 72)
DISK_ALERT_PERCENT = _entero("DISK_ALERT_PERCENT", 80)
DISK_CLEANUP_PERCENT = _entero("DISK_CLEANUP_PERCENT", 90)
# Solo para pruebas: un porcentaje de disco fingido para disparar la alerta y el borrado sin llenar el disco de verdad.
DISK_SIMULATED_PERCENT = os.environ.get("DISK_SIMULATED_PERCENT", "").strip()

ZONA = ZoneInfo(os.environ.get("WORKER_TZ", "America/Mexico_City"))
# Raíz del volumen videos_reportes. Las rutas que se guardan en la base son relativas a ella.
ARCHIVOS = Path(os.environ.get("ARCHIVOS_DIR", "/data/archivos"))
# Carpeta de los respaldos de la base (D-18). En Docker es una carpeta del equipo montada aparte (./respaldos), FUERA de
# los volúmenes, para que los respaldos sobrevivan aunque alguien borre los volúmenes.
RESPALDOS = Path(os.environ.get("RESPALDOS_DIR", "/data/respaldos"))

ASUNTOS = {
    "analisis completado": "Análisis completado",
    "error del pipeline": "Error en el análisis",
    "analisis en pausa": "Análisis en pausa: ajusta el recuadro",
    "analisis cancelado": "Análisis cancelado",
    "alerta de disco": "Alerta de disco",
    "video borrado": "Videos borrados por falta de disco",
}


def configurar_log():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")


def ahora():
    return datetime.now(ZONA)


def datos_conexion():
    """Los datos de la base, de las variables de entorno. Los usa conectar() y también el respaldo con pg_dump."""
    return {
        "host": os.environ.get("DB_HOST", "localhost"),
        "port": _entero("DB_PORT", 5433),
        "dbname": os.environ.get("POSTGRES_DB", "fst"),
        "user": os.environ.get("POSTGRES_USER", "fst"),
        "password": os.environ.get("POSTGRES_PASSWORD", "fst"),
    }


def conectar():
    return psycopg2.connect(**datos_conexion())


def carpeta_analisis(id_analisis):
    return ARCHIVOS / "analisis" / str(id_analisis)


def relativa(ruta):
    """Ruta relativa a la raíz del volumen, que es como se guarda en la base."""
    return ruta.relative_to(ARCHIVOS).as_posix()


def borrar_archivo(ruta):
    try:
        ruta.unlink()
        return True
    except FileNotFoundError:
        return False


def bloquear_experimento(cur, id_experimento):
    """Bloquea la fila del experimento (FOR UPDATE). Debe ser el PRIMER paso de toda transacción que invalida reportes,
    antes de tocar análisis, SEGUNDO u otra tabla: el mismo orden de candados que el backend (ver ReporteAlmacen)."""
    cur.execute("SELECT idexperimento FROM experimento WHERE idexperimento = %s FOR UPDATE", (id_experimento,))


def invalidar_reportes(cur, id_experimento):
    """Un reporte guardado ya no refleja los datos (C-07): se borran sus filas, dentro de la transacción de quien llama.
    Después del commit, borrar_reportes_del_disco quita la carpeta reportes/{id}/."""
    cur.execute("DELETE FROM reporte WHERE idexperimento = %s", (id_experimento,))


def borrar_reportes_del_disco(id_experimento):
    """Borra reportes/{id}/ del volumen, DESPUÉS del commit. Un fallo es solo un aviso en el log: la fila ya no existe,
    así que la próxima descarga regenera el reporte."""
    carpeta = ARCHIVOS / "reportes" / str(id_experimento)
    try:
        shutil.rmtree(carpeta)
    except FileNotFoundError:
        pass
    except OSError as error:
        log.warning("No se pudo borrar la carpeta de reportes %s: %s", carpeta, error)


def letra_tanda(ordinal):
    return chr(ord("A") + ordinal - 1)


def datos_analisis(cur, id_analisis):
    """Todo lo que el worker necesita de un análisis: su video, la tanda, el experimento y el
    investigador que lo creó (el destinatario de los avisos)."""
    cur.execute(
        """
        SELECT a.idanalisis, a.estado, a.etapa, v.idvideo, v.idtanda, v.archivo, v.duracion,
               g.etiqueta, t.ordinal, e.idexperimento, e.nombre, e.idinstitucional, u.correo
        FROM analisis a
        JOIN video v       ON v.idvideo = a.idvideo
        JOIN tanda t       ON t.idtanda = v.idtanda
        JOIN grupo g       ON g.idgrupo = t.idgrupo
        JOIN experimento e ON e.idexperimento = g.idexperimento
        JOIN usuario u     ON u.idinstitucional = e.idinstitucional
        WHERE a.idanalisis = %s
        """,
        (id_analisis,),
    )
    fila = cur.fetchone()
    if fila is None:
        return None
    datos = dict(zip([c.name for c in cur.description], fila))
    datos["tanda"] = f"{datos['etiqueta']} · Tanda {letra_tanda(datos['ordinal'])}"
    return datos


def notificar(cur, pendientes, id_institucional, correo, tipo, mensaje, id_experimento=None):
    """Crea la NOTIFICACION (campanita) y deja el correo pendiente. El correo se manda después del
    commit con enviar_correos: si la transacción falla, no sale un aviso de algo que no pasó."""
    cur.execute(
        "INSERT INTO notificacion (idinstitucional, idexperimento, tipo, mensaje) VALUES (%s, %s, %s, %s)",
        (id_institucional, id_experimento, tipo, mensaje),
    )
    pendientes.append((correo, ASUNTOS[tipo], mensaje))


def enviar_correos(pendientes):
    """Copia por correo de cada aviso. Si el envío falla, el aviso ya quedó en la campanita y el
    worker sigue (sección 4.4 de la guía)."""
    host = os.environ.get("SMTP_HOST", "localhost").strip()
    if not host:
        # El .env de la versión portable trae SMTP_HOST vacío mientras no se configure el correo: sin servidor no se intenta
        # nada (el aviso ya quedó en la campanita; iniciar.bat avisa que no hay correo).
        pendientes.clear()
        return
    puerto = _entero("SMTP_PORT", 1025)
    usuario = os.environ.get("SMTP_USER", "")
    contrasena = os.environ.get("SMTP_PASSWORD", "")
    starttls = os.environ.get("SMTP_STARTTLS", "false").lower() == "true"
    # SMTP_FROM vacío (el .env lo trae así hasta que se llena) también cae en el remitente por omisión.
    remitente = os.environ.get("SMTP_FROM", "").strip() or "no-reply@fst.local"

    for correo, asunto, texto in pendientes:
        if not correo:
            continue
        mensaje = EmailMessage()
        mensaje["From"] = remitente
        mensaje["To"] = correo
        mensaje["Subject"] = asunto
        mensaje.set_content(texto)
        try:
            with smtplib.SMTP(host, puerto, timeout=10) as smtp:
                if starttls:
                    smtp.starttls()
                if usuario:
                    smtp.login(usuario, contrasena)
                smtp.send_message(mensaje)
        except (OSError, smtplib.SMTPException) as e:
            log.error("No se pudo enviar el correo '%s' a %s: %s", asunto, correo, e)
    pendientes.clear()
