#!/usr/bin/env python3
"""Genera un video sintetico de prueba para verificar la herramienta.

Cada cuadro lleva impreso su propio numero, de modo que pedir el cuadro N por
URL y ver "cuadro N" escrito en la imagen verifica sin ambiguedad que el
servidor entrega el cuadro exacto y no un vecino aproximado.

La escena imita una camara de nado vista de lado: paredes, linea de agua
ligeramente inclinada (la camara puede no estar nivelada) y un especimen
simulado que alterna inmovilidad, nado y escalamiento. Junto al video se
escribe un archivo *_verdad.json con los rangos de cuadros de cada fase, para
comparar despues contra lo que detecten los modulos 4, 5 y 6.

Este video NO sustituye material real de laboratorio: sirve solo para probar
la herramienta.
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

import cv2
import numpy as np

CLASES = ("inmovilidad", "nado", "escalamiento")


def construir_fases(fps: float, segundos: float) -> list[dict]:
    """Secuencia de fases de conducta simulada, en bloques de 5 s."""
    total_cuadros = int(round(fps * segundos))
    cuadros_bloque = max(1, int(round(fps * 5)))
    # Patron de bloques de 5 s: predomina la inmovilidad, como en el prior
    # reportado en la literatura para FST, pero aparecen las tres clases.
    patron = ["nado", "nado", "inmovilidad", "escalamiento", "inmovilidad",
              "inmovilidad", "nado", "inmovilidad", "inmovilidad", "escalamiento",
              "inmovilidad", "inmovilidad"]
    fases = []
    cuadro = 0
    i = 0
    while cuadro < total_cuadros:
        fin = min(cuadro + cuadros_bloque, total_cuadros) - 1
        fases.append({"clase": patron[i % len(patron)], "cuadro_inicio": cuadro, "cuadro_fin": fin})
        cuadro = fin + 1
        i += 1
    return fases


def posicion_especimen(clase: str, t_fase: float, geo: dict, rng) -> tuple[int, int, float]:
    """Centro (x, y) y angulo del especimen simulado para una fase dada."""
    cx = (geo["x0"] + geo["x1"]) / 2
    y_agua = geo["y_agua_centro"]

    if clase == "inmovilidad":
        # Solo los movimientos necesarios para mantener el hocico fuera del agua.
        x = cx + rng.normal(0, 1.2)
        y = y_agua + geo["alto_especimen"] * 0.25 + rng.normal(0, 1.2)
        angulo = 0.0
    elif clase == "nado":
        # Desplazamiento horizontal dentro de la camara.
        amplitud = (geo["x1"] - geo["x0"]) * 0.30
        x = cx + amplitud * math.sin(t_fase * 1.6) + rng.normal(0, 1.0)
        y = y_agua + geo["alto_especimen"] * 0.45 + 6 * math.sin(t_fase * 3.1)
        angulo = 8 * math.sin(t_fase * 1.6)
    else:  # escalamiento: patas anteriores hacia delante sobre la pared
        x = geo["x1"] - geo["ancho_especimen"] * 0.55 + rng.normal(0, 0.8)
        subida = (math.sin(t_fase * 2.2) + 1) / 2
        y = y_agua - geo["alto_especimen"] * (0.2 + 0.9 * subida)
        angulo = -70.0
    return int(round(x)), int(round(y)), angulo


def dibujar_cuadro(indice: int, total: int, fps: float, clase: str, t_fase: float,
                   geo: dict, rng, mostrar_fase: bool) -> np.ndarray:
    ancho, alto = geo["ancho"], geo["alto"]
    img = np.full((alto, ancho, 3), 232, dtype=np.uint8)
    # Textura de fondo suave para que la diferencia entre cuadros no sea nula
    # por razones artificiales.
    img = cv2.add(img, rng.integers(0, 4, (alto, ancho, 3), dtype=np.uint8))

    # Camara de natacion (vista lateral)
    cv2.rectangle(img, (geo["x0"], geo["y0"]), (geo["x1"], geo["y1"]), (150, 150, 150), 3)

    # Linea de agua ligeramente inclinada
    p_izq = (geo["x0"], geo["y_agua_izq"])
    p_der = (geo["x1"], geo["y_agua_der"])
    cv2.line(img, p_izq, p_der, (190, 140, 60), 2)
    agua = img.copy()
    puntos = np.array([p_izq, p_der, (geo["x1"], geo["y1"]), (geo["x0"], geo["y1"])], dtype=np.int32)
    cv2.fillPoly(agua, [puntos], (225, 190, 130))
    cv2.addWeighted(agua, 0.35, img, 0.65, 0, dst=img)

    # Especimen simulado
    x, y, angulo = posicion_especimen(clase, t_fase, geo, rng)
    cv2.ellipse(img, (x, y), (geo["ancho_especimen"] // 2, geo["alto_especimen"] // 2),
                angulo, 0, 360, (58, 58, 62), -1)

    # Numero de cuadro impreso (esto es lo que permite verificar el Modulo 1)
    segundos = indice / fps if fps else 0.0
    cv2.rectangle(img, (0, 0), (ancho, 34), (20, 20, 20), -1)
    cv2.putText(img, f"cuadro {indice} / {total - 1}", (10, 24),
                cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2, cv2.LINE_AA)
    cv2.putText(img, f"t = {segundos:6.2f} s", (ancho - 200, 24),
                cv2.FONT_HERSHEY_SIMPLEX, 0.6, (200, 255, 200), 2, cv2.LINE_AA)
    if mostrar_fase:
        cv2.putText(img, clase, (10, alto - 14), cv2.FONT_HERSHEY_SIMPLEX,
                    0.7, (0, 0, 180), 2, cv2.LINE_AA)
    return img


def main() -> int:
    p = argparse.ArgumentParser(description="Genera un video sintetico de prueba para FST.")
    p.add_argument("--salida", default="videos/prueba_sintetica.mp4", help="Ruta del video a escribir.")
    p.add_argument("--fps", type=float, default=30.0, help="Cuadros por segundo del video generado.")
    p.add_argument("--segundos", type=float, default=60.0, help="Duracion en segundos.")
    p.add_argument("--ancho", type=int, default=640)
    p.add_argument("--alto", type=int, default=480)
    p.add_argument("--semilla", type=int, default=7, help="Semilla del generador aleatorio.")
    p.add_argument("--mostrar-fase", action="store_true",
                   help="Imprime la fase simulada en el cuadro (por omision no se imprime, "
                        "para no sesgar la revision humana).")
    args = p.parse_args()

    salida = Path(args.salida).expanduser()
    salida.parent.mkdir(parents=True, exist_ok=True)

    total = int(round(args.fps * args.segundos))
    if total < 1:
        print("La duracion solicitada no alcanza para un solo cuadro.")
        return 1

    margen_x, margen_y = int(args.ancho * 0.16), int(args.alto * 0.10)
    geo = {
        "ancho": args.ancho, "alto": args.alto,
        "x0": margen_x, "x1": args.ancho - margen_x,
        "y0": margen_y, "y1": args.alto - margen_y,
        # Linea de agua inclinada ~2 grados: la camara puede no estar nivelada.
        "y_agua_izq": int(args.alto * 0.36),
        "y_agua_der": int(args.alto * 0.39),
        "ancho_especimen": int(args.ancho * 0.16),
        "alto_especimen": int(args.alto * 0.11),
    }
    geo["y_agua_centro"] = (geo["y_agua_izq"] + geo["y_agua_der"]) // 2

    fases = construir_fases(args.fps, args.segundos)
    escritor = cv2.VideoWriter(str(salida), cv2.VideoWriter_fourcc(*"mp4v"),
                               args.fps, (args.ancho, args.alto))
    if not escritor.isOpened():
        print(f"OpenCV no pudo crear el archivo de video: {salida}")
        return 1

    rng = np.random.default_rng(args.semilla)
    try:
        for fase in fases:
            for indice in range(fase["cuadro_inicio"], fase["cuadro_fin"] + 1):
                t_fase = (indice - fase["cuadro_inicio"]) / args.fps
                escritor.write(dibujar_cuadro(indice, total, args.fps, fase["clase"],
                                              t_fase, geo, rng, args.mostrar_fase))
    finally:
        escritor.release()

    verdad = {
        "video": salida.name,
        "nota": "Video sintetico de prueba de la herramienta; no es material de laboratorio.",
        "fps": args.fps,
        "ancho": args.ancho,
        "alto": args.alto,
        "total_cuadros": total,
        "roi_sugerido": {"x": geo["x0"], "y": geo["y0"],
                         "ancho": geo["x1"] - geo["x0"], "alto": geo["y1"] - geo["y0"]},
        "linea_agua": {"x1": geo["x0"], "y1": geo["y_agua_izq"],
                       "x2": geo["x1"], "y2": geo["y_agua_der"]},
        "fases": fases,
    }
    ruta_verdad = salida.with_name(salida.stem + "_verdad.json")
    ruta_verdad.write_text(json.dumps(verdad, indent=2, ensure_ascii=False), encoding="utf-8")

    print(f"Video escrito:  {salida}  ({total} cuadros, {args.fps} FPS, {args.ancho}x{args.alto})")
    print(f"Verdad escrita: {ruta_verdad}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
