"""Versión del sistema, calculada con el historial de git y la convención de commits.

    python tools/version.py                     imprime la versión actual
    python tools/version.py --escribir          además la escribe en los archivos de abajo
    python tools/version.py --siguiente feat    imprime la versión que quedará tras un commit "feat" (o "fix")
    python tools/version.py --siguiente feat --escribir    y la escribe: se corre ANTES de hacer ese commit

Reglas (solo cuenta el primer renglón de cada commit, del más viejo al más nuevo):
  - Se empieza en la línea base (BASE_VERSION, abajo) y se cuentan solo los commits posteriores a BASE_COMMIT.
  - "feat" (feat: ..., feat(algo): ...) sube el segundo número y pone el tercero en 0.
  - "fix" sube el tercero.
  - Todo lo demás (docs, chore, wip, refactor, mensajes sueltos) no cuenta.

1.0.0 es la versión que se instaló en producción (la VPS), el commit BASE_COMMIT; ahí se decidió que la versión 1 es
la de producción. Todo lo que se haga después sube desde ahí: 1.0.1, 1.1.0, etc. Antes de eso la cuenta empezaba en
0.0.0 y llegó a 0.93.0.

Dónde escribe la versión (solo con --escribir):
  - VERSION (raíz): una línea, para quien la necesite leer.
  - frontend/package.json: el campo "version" (la web la muestra en el menú de usuario y en la franja de demo).
  - backend-spring/src/main/resources/version.properties: app.version=..., que el backend lee como propiedad
    (application.properties lo importa) y entrega en GET /health. Va dentro del jar: no hace falta pasar nada al build.
"""

import argparse
import re
import subprocess
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
COMMIT = re.compile(r"^(feat|fix)(\([^)]*\))?!?:", re.IGNORECASE)

# Línea base de producción: este commit es la versión 1.0.0. Los commits anteriores ya no cuentan.
BASE_COMMIT = "15489f4d72513f4b61a1f95792bdce69622d2110"
BASE_VERSION = (1, 0, 0)


def asuntos():
    """Primer renglón de cada commit, del más viejo al más nuevo.

    El merge de poc/backend-spring a main trae dos copias (con otro hash) de los commits de main anteriores a la
    reescritura de historia; un asunto feat/fix repetido se cuenta una sola vez para que la versión no suba de más.
    """
    # La base debe estar en el historial de esta rama; si no, la versión que saldría no tendría sentido.
    if subprocess.run(["git", "merge-base", "--is-ancestor", BASE_COMMIT, "HEAD"], cwd=RAIZ).returncode != 0:
        sys.exit("Esta rama no contiene el commit de la versión 1.0.0 (" + BASE_COMMIT[:7] + "): trae main antes de calcular la versión.")
    salida = subprocess.run(["git", "log", "--reverse", "--format=%s", BASE_COMMIT + "..HEAD"], cwd=RAIZ,
                            capture_output=True, text=True, encoding="utf-8", check=True).stdout
    vistos, lineas = set(), []
    for linea in salida.splitlines():
        if COMMIT.match(linea.strip()):
            if linea in vistos:
                continue
            vistos.add(linea)
        lineas.append(linea)
    return lineas


def subir(version, tipo):
    mayor, menor, parche = version
    if tipo == "feat":
        return mayor, menor + 1, 0
    if tipo == "fix":
        return mayor, menor, parche + 1
    return version


def calcular(lineas):
    version = BASE_VERSION
    for linea in lineas:
        m = COMMIT.match(linea.strip())
        if m:
            version = subir(version, m.group(1).lower())
    return version


def texto(version):
    return ".".join(str(n) for n in version)


def escribir(version):
    v = texto(version)
    (RAIZ / "VERSION").write_text(v + "\n", encoding="utf-8")

    paquete = RAIZ / "frontend" / "package.json"
    contenido = paquete.read_text(encoding="utf-8")
    # Solo el primer "version" (el del proyecto): se reemplaza en el texto para no reformatear el archivo.
    nuevo, cambios = re.subn(r'("version"\s*:\s*")[^"]*(")', r"\g<1>" + v + r"\g<2>", contenido, count=1)
    if cambios != 1:
        sys.exit('No se encontró el campo "version" en frontend/package.json')
    paquete.write_text(nuevo, encoding="utf-8")

    propiedades = RAIZ / "backend-spring" / "src" / "main" / "resources" / "version.properties"
    propiedades.write_text("# Lo escribe tools/version.py: no se edita a mano.\napp.version=" + v + "\n", encoding="utf-8")


def main():
    ap = argparse.ArgumentParser(description="Versión del sistema según el historial de git")
    ap.add_argument("--siguiente", choices=["feat", "fix"], help="la versión tras un commit de ese tipo")
    ap.add_argument("--escribir", action="store_true", help="escribe la versión en VERSION, package.json y el backend")
    args = ap.parse_args()

    version = calcular(asuntos())
    if args.siguiente:
        version = subir(version, args.siguiente)
    if args.escribir:
        escribir(version)
    print(texto(version))


if __name__ == "__main__":
    main()
