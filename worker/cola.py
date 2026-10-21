"""cola: el ciclo del worker (sección 4 de INSTRUCCIONES-WEB.md), sin analizar nada.

Toma un análisis a la vez de la tabla ANALISIS (la cola), revisa las pausas vencidas, corre las tareas diarias,
atrapa los fallos imprevistos y se reconecta si se cae la base. Lo único que cambia de un análisis a otro es la
función que lo procesa: worker.py le pasa procesar_real a main().
"""

import json
import time
from datetime import datetime, timedelta

import psycopg2

import tareas_programadas
from diagnostico import generar_diagnostico
from tiempos import cerrar_archivo
from comun import (
    DAILY_HOUR, PAUSE_TIMEOUT_HOURS, POLL_SECONDS,
    ahora, carpeta_analisis, configurar_log, conectar, datos_analisis, enviar_correos, log, notificar, relativa,
)


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
    """Cancela las pausas vencidas (72 horas sin respuesta) y las que no tienen archivo de pausa.
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
#  Un análisis: lo que comparten todos los caminos
# ---------------------------------------------------------------------------

def poner_etapa(conn, id_analisis, etapa):
    with conn.cursor() as cur:
        cur.execute("UPDATE analisis SET etapa = %s WHERE idanalisis = %s", (etapa, id_analisis))
    conn.commit()


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


def main(procesar_analisis):
    """procesar_analisis es lo que hace el análisis en sí (worker.procesar_real); la cola, las pausas, las tareas
    diarias y la reconexión viven aquí."""
    configurar_log()
    log.info("worker: cola cada %s s, tareas diarias a las %s h, pausa de %s h",
             POLL_SECONDS, DAILY_HOUR, PAUSE_TIMEOUT_HOURS)
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
