"""Hace lo que hará el backend cuando existan la carga de video y la pantalla de pausa (secciones 4.1
y 4.3 de la guía, todavía sin construir en backend-spring), para poder probar el worker_fake.

  python simular_carga.py encolar "Prueba normal" [--cilindros 3] [--duracion 316] [--sesion "Dia 1"]
  python simular_carga.py ajustar ID_ANALISIS
  python simular_carga.py cancelar ID_ANALISIS
  python simular_carga.py estado

Dentro de Docker: docker compose exec worker python simular_carga.py ...
"encolar" crea un experimento con ese nombre (con "falla" o "pausa" en el nombre se prueban esos
casos), un grupo, una tanda con sus especímenes, el video y, si es del Día 2, su análisis en cola.
"""

import argparse
import json
import sys

import psycopg2

from comun import ARCHIVOS, ahora, carpeta_analisis, conectar


def encolar(conn, args):
    with conn.cursor() as cur:
        try:
            cur.execute(
                "INSERT INTO experimento (idinstitucional, nombre, fecha) VALUES (%s, %s, current_date) "
                "RETURNING idexperimento",
                (args.usuario, args.nombre),
            )
        except psycopg2.errors.UniqueViolation:
            print(f"409: ya existe un experimento llamado '{args.nombre}'")
            return 1
        id_experimento = cur.fetchone()[0]
        cur.execute(
            "INSERT INTO grupo (idexperimento, etiqueta, tipo, tratamiento) "
            "VALUES (%s, 'Control', 'control', 'Salina') RETURNING idgrupo",
            (id_experimento,),
        )
        id_grupo = cur.fetchone()[0]
        cur.execute(
            "INSERT INTO tanda (idgrupo, ordinal, ncilindros) VALUES (%s, 1, %s) RETURNING idtanda",
            (id_grupo, args.cilindros),
        )
        id_tanda = cur.fetchone()[0]
        for cilindro in range(1, args.cilindros + 1):
            cur.execute("INSERT INTO especimen (idtanda, numerocilindro) VALUES (%s, %s)", (id_tanda, cilindro))

        # Igual que el backend (sección 4.1): primero el archivo en videos/{idVideo}.mp4, luego la fila.
        cur.execute(
            "INSERT INTO video (idtanda, sesion, duracion) VALUES (%s, %s, %s) RETURNING idvideo",
            (id_tanda, args.sesion, args.duracion),
        )
        id_video = cur.fetchone()[0]
        ruta = f"videos/{id_video}.mp4"
        (ARCHIVOS / "videos").mkdir(parents=True, exist_ok=True)
        (ARCHIVOS / ruta).write_bytes(b"video simulado por simular_carga.py\n")
        cur.execute("UPDATE video SET archivo = %s WHERE idvideo = %s", (ruta, id_video))

        id_analisis = None
        if args.sesion == "Dia 2":
            # Solo el Día 2 se analiza (D-71). estado, etapa, nivelclasif y fechaanalisis toman los
            # valores de arranque de D-79, que son los DEFAULT de la tabla.
            cur.execute("INSERT INTO analisis (idvideo) VALUES (%s) RETURNING idanalisis", (id_video,))
            id_analisis = cur.fetchone()[0]
    conn.commit()
    print(f"experimento {id_experimento}, video {id_video} ({args.sesion}), "
          + (f"análisis {id_analisis} en cola" if id_analisis else "sin análisis (el Día 1 no se analiza)"))
    return 0


def ajustar(conn, args):
    """POST .../pause/adjust: agrega el recuadro nuevo al archivo de pausa y regresa el análisis a la
    cola, solo si sigue en pausa (si no, 409)."""
    with conn.cursor() as cur:
        cur.execute("SELECT estado FROM analisis WHERE idanalisis = %s", (args.id_analisis,))
        fila = cur.fetchone()
        if fila is None or fila[0] != "en pausa":
            print(f"409: el análisis {args.id_analisis} no está en pausa ({fila[0] if fila else 'no existe'})")
            conn.rollback()
            return 1
        archivo = carpeta_analisis(args.id_analisis) / "pausa.json"
        pausa = json.loads(archivo.read_text(encoding="utf-8"))
        caja = next(c for c in pausa["recuadros"] if c["cilindro"] == pausa["cilindro"])
        # El "usuario" mueve el recuadro 10 px a la derecha.
        pausa["ajuste"] = {"cilindro": pausa["cilindro"], "x0": caja["x0"] + 10, "y0": caja["y0"],
                           "x1": caja["x1"] + 10, "y1": caja["y1"], "hora": ahora().isoformat(timespec="seconds")}
        archivo.write_text(json.dumps(pausa, ensure_ascii=False, indent=2), encoding="utf-8")
        cur.execute(
            "UPDATE analisis SET estado = 'en cola' WHERE idanalisis = %s AND estado = 'en pausa'",
            (args.id_analisis,),
        )
        ok = cur.rowcount == 1
    conn.commit()
    print("200: ajustado, de vuelta en la cola" if ok else "409: el análisis ya no estaba en pausa")
    return 0 if ok else 1


def cancelar(conn, args):
    """POST .../cancel: cancela solo si sigue en pausa (si no, 409). No crea notificación: solo la
    cancelación por falta de respuesta la crea."""
    with conn.cursor() as cur:
        cur.execute(
            "UPDATE analisis SET estado = 'cancelado' WHERE idanalisis = %s AND estado = 'en pausa'",
            (args.id_analisis,),
        )
        ok = cur.rowcount == 1
    conn.commit()
    print("200: cancelado" if ok else f"409: el análisis {args.id_analisis} no está en pausa")
    return 0 if ok else 1


def estado(conn, args):
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT a.idanalisis, e.nombre, a.estado, a.etapa, a.nivelclasif,
                   to_char(a.fechaanalisis, 'YYYY-MM-DD HH24:MI:SS'), coalesce(a.rutadiagnostico, '')
            FROM analisis a
            JOIN video v       ON v.idvideo = a.idvideo
            JOIN tanda t       ON t.idtanda = v.idtanda
            JOIN grupo g       ON g.idgrupo = t.idgrupo
            JOIN experimento e ON e.idexperimento = g.idexperimento
            ORDER BY a.idanalisis
            """
        )
        filas = cur.fetchall()
    conn.commit()
    print(f"{'id':>3}  {'experimento':<24} {'estado':<11} {'etapa':<17} {'nivel':<9} {'fechaanalisis':<20} diagnóstico")
    for f in filas:
        print(f"{f[0]:>3}  {f[1][:24]:<24} {f[2]:<11} {f[3]:<17} {f[4]:<9} {f[5]:<20} {f[6]}")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="comando", required=True)

    p = sub.add_parser("encolar", help="sube un video simulado y, si es del Día 2, lo encola")
    p.add_argument("nombre", help="nombre del experimento (con 'falla' o 'pausa' prueba esos casos)")
    p.add_argument("--cilindros", type=int, default=2, choices=[2, 3, 4])
    p.add_argument("--duracion", type=float, default=316, help="segundos del video (por defecto 316)")
    p.add_argument("--sesion", default="Dia 2", choices=["Dia 1", "Dia 2"])
    p.add_argument("--usuario", default="DEV0000001", help="idInstitucional del creador")
    p.set_defaults(funcion=encolar)

    for nombre, funcion in (("ajustar", ajustar), ("cancelar", cancelar)):
        p = sub.add_parser(nombre, help=f"{nombre} un análisis en pausa, como lo hará el backend")
        p.add_argument("id_analisis", type=int)
        p.set_defaults(funcion=funcion)

    sub.add_parser("estado", help="lista los análisis").set_defaults(funcion=estado)

    args = parser.parse_args()
    conn = conectar()
    try:
        return args.funcion(conn, args)
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
