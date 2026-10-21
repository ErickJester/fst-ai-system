"""Piezas que comparten worker_fake.py, tareas_programadas.py y simular_carga.py:
configuración (variables de entorno), conexión a la base, rutas del volumen y avisos."""

import logging
import os
import smtplib
from datetime import datetime
from email.message import EmailMessage
from pathlib import Path
from zoneinfo import ZoneInfo

import psycopg2

log = logging.getLogger("worker_fake")


def _entero(nombre, defecto):
    valor = os.environ.get(nombre, "").strip()
    return int(valor) if valor else defecto


# Variables de la guía (sección 2) con sus valores por defecto.
POLL_SECONDS = _entero("WORKER_POLL_SECONDS", 5)
DAILY_HOUR = _entero("WORKER_DAILY_HOUR", 3)
PAUSE_TIMEOUT_HOURS = _entero("PAUSE_TIMEOUT_HOURS", 72)
DISK_ALERT_PERCENT = _entero("DISK_ALERT_PERCENT", 80)
DISK_CLEANUP_PERCENT = _entero("DISK_CLEANUP_PERCENT", 90)
# Propias del simulador: segundos que tarda cada etapa y, solo para pruebas, un porcentaje de disco
# fingido para disparar la alerta y el borrado sin llenar el disco de verdad.
STAGE_SECONDS = _entero("WORKER_STAGE_SECONDS", 3)
DISK_SIMULATED_PERCENT = os.environ.get("DISK_SIMULATED_PERCENT", "").strip()

ZONA = ZoneInfo(os.environ.get("WORKER_TZ", "America/Mexico_City"))
# Raíz del volumen videos_reportes. Las rutas que se guardan en la base son relativas a ella.
ARCHIVOS = Path(os.environ.get("ARCHIVOS_DIR", "/data/archivos"))

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


def conectar():
    return psycopg2.connect(
        host=os.environ.get("DB_HOST", "localhost"),
        port=_entero("DB_PORT", 5433),
        dbname=os.environ.get("POSTGRES_DB", "fst"),
        user=os.environ.get("POSTGRES_USER", "fst"),
        password=os.environ.get("POSTGRES_PASSWORD", "fst"),
    )


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
    host = os.environ.get("SMTP_HOST", "localhost")
    puerto = _entero("SMTP_PORT", 1025)
    usuario = os.environ.get("SMTP_USER", "")
    contrasena = os.environ.get("SMTP_PASSWORD", "")
    starttls = os.environ.get("SMTP_STARTTLS", "false").lower() == "true"
    remitente = os.environ.get("SMTP_FROM", "no-reply@fst.local")

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
