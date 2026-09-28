#!/usr/bin/env python3
"""Verificacion de la subida de videos (ampliacion del Modulo 1).

Prueba `CatalogoVideos.guardar_subida` directamente, sin Flask ni navegador:
un video valido que queda listado y se puede leer, el rechazo de un nombre
duplicado, el rechazo de una extension que no es de video, el archivo vacio,
y la limpieza de un archivo que se guarda pero que OpenCV no puede abrir (la
ruta que sigue el endpoint HTTP cuando la subida llego incompleta).

No necesita servidor: `guardar_subida` recibe cualquier objeto con
`.save(ruta)`, que es lo unico que usa de un `FileStorage` de Flask.

Uso:
    python scripts/verificar_subida.py
"""
import shutil
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import cv2
import numpy as np

from fst_labeler.video_source import (
    CatalogoVideos,
    NombreInvalido,
    VideoNoAbierto,
    VideoYaExiste,
)

fallos = []


def check(nombre, obtenido, esperado):
    if obtenido != esperado:
        fallos.append(nombre)
        print(f"  FALLA  {nombre}: esperaba {esperado!r}, obtuvo {obtenido!r}")
    else:
        print(f"  ok     {nombre} = {obtenido!r}")


def falla_con(nombre, excepcion, funcion):
    try:
        funcion()
    except excepcion as error:
        print(f"  ok     {nombre}: {type(error).__name__}: {error}")
        return
    fallos.append(nombre)
    print(f"  FALLA  {nombre}: no lanzo {excepcion.__name__}")


class ArchivoDePrueba:
    """El unico metodo que `guardar_subida` necesita de un `FileStorage`."""

    def __init__(self, origen: Path) -> None:
        self.origen = origen

    def save(self, destino: str) -> None:
        shutil.copyfile(self.origen, destino)


def video_valido(ruta: Path) -> None:
    """Un .mp4 minimo pero real: unos cuadros negros a 10 FPS."""
    escritor = cv2.VideoWriter(
        str(ruta), cv2.VideoWriter_fourcc(*"mp4v"), 10.0, (64, 48)
    )
    for _ in range(5):
        escritor.write(np.zeros((48, 64, 3), dtype=np.uint8))
    escritor.release()


with tempfile.TemporaryDirectory() as carpeta_prueba:
    carpeta_prueba = Path(carpeta_prueba)
    videos_dir = carpeta_prueba / "videos"
    catalogo = CatalogoVideos(raiz=videos_dir)

    print("== Video valido ==")
    origen = carpeta_prueba / "fuente.mp4"
    video_valido(origen)
    video_id = catalogo.guardar_subida("Ensayo día 1.mp4", ArchivoDePrueba(origen))
    check("el nombre con acentos y espacios se conserva", video_id, "Ensayo día 1.mp4")
    check("el archivo quedo en la carpeta", (videos_dir / video_id).is_file(), True)
    check("no quedo ningun temporal '.subiendo-' suelto",
          list(videos_dir.glob("*.subiendo-*")), [])
    check("aparece en el listado", [v["video_id"] for v in catalogo.listar()], [video_id])
    lector = catalogo.lector(video_id)
    check("OpenCV lo puede leer: total de cuadros", lector.contar_cuadros_exacto(), 5)
    check("la resolucion es la que se escribio", (lector.metadatos.ancho, lector.metadatos.alto), (64, 48))

    print("\n== Nombre duplicado ==")
    falla_con("subir el mismo nombre otra vez se rechaza", VideoYaExiste,
              lambda: catalogo.guardar_subida("Ensayo día 1.mp4", ArchivoDePrueba(origen)))
    check("el archivo original no se toco tras el intento", lector.contar_cuadros_exacto(), 5)

    print("\n== Nombres y extensiones invalidas ==")
    falla_con("extension que no es de video", NombreInvalido,
              lambda: catalogo.guardar_subida("notas.txt", ArchivoDePrueba(origen)))
    falla_con("sin extension", NombreInvalido,
              lambda: catalogo.guardar_subida("video_sin_extension", ArchivoDePrueba(origen)))
    falla_con("nombre vacio", NombreInvalido,
              lambda: catalogo.guardar_subida("", ArchivoDePrueba(origen)))
    # Un intento de escapar la carpeta con componentes de ruta: Path(...).name
    # se queda solo con lo de despues de la ultima barra, así que esto sube
    # como 'passwd.mp4' dentro de videos_dir, no fuera de ella.
    video_id_escape = catalogo.guardar_subida("../../passwd.mp4", ArchivoDePrueba(origen))
    check("un nombre con '..' se recorta a un archivo dentro de la carpeta",
          video_id_escape, "passwd.mp4")
    check("el archivo cayo dentro de videos_dir, no afuera",
          (videos_dir / "passwd.mp4").resolve().is_relative_to(videos_dir.resolve()), True)

    print("\n== Archivo vacio ==")
    vacio = carpeta_prueba / "vacio.mp4"
    vacio.touch()
    falla_con("un archivo de cero bytes se rechaza", Exception,
              lambda: catalogo.guardar_subida("vacio.mp4", ArchivoDePrueba(vacio)))
    check("no queda 'vacio.mp4' en la carpeta", (videos_dir / "vacio.mp4").exists(), False)
    check("no queda ningun temporal de la subida vacia",
          list(videos_dir.glob("vacio.mp4.subiendo-*")), [])

    print("\n== Archivo que no es un video valido (lo que hace el endpoint HTTP) ==")
    basura = carpeta_prueba / "basura.mp4"
    basura.write_bytes(b"esto no es un video, son puros bytes de prueba" * 100)
    video_id_basura = catalogo.guardar_subida("basura.mp4", ArchivoDePrueba(basura))
    check("se guarda primero: guardar_subida no valida el contenido", video_id_basura, "basura.mp4")
    falla_con("pero OpenCV no lo puede abrir", VideoNoAbierto,
              lambda: catalogo.lector(video_id_basura))
    # Aqui es donde el endpoint /api/videos llama a catalogo.borrar(...).
    catalogo.borrar(video_id_basura)
    check("tras borrar, ya no esta en la carpeta", (videos_dir / "basura.mp4").exists(), False)
    check("tras borrar, ya no aparece en el listado",
          video_id_basura in [v["video_id"] for v in catalogo.listar()], False)

    # Cierra los lectores de OpenCV antes de que el bloque `with` intente
    # borrar la carpeta temporal: en Windows un archivo con un lector
    # abierto no se puede eliminar.
    catalogo.cerrar_todo()

print()
if fallos:
    print(f"{len(fallos)} comprobaciones fallaron.")
    sys.exit(1)
print("Todas las comprobaciones pasaron.")
