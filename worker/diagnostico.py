"""PDF de diagnóstico de un análisis que terminó en error (sección 4.2): el mensaje técnico y, si el pipeline
la dejó, la imagen de control con los tubos que encontró. Lo usan cola.marcar_error (que lo guarda y avisa) y las
pruebas de test_diagnostico.py."""

import re
import textwrap
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from comun import ahora, carpeta_analisis, log

FUENTE = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
ANCHO_PAGINA, ALTO_PAGINA, MARGEN = 1275, 1650, 100

PIE_DE_CONTROL = ("Imagen de control: amarillo = zona medida de cada tubo encontrado, azul = línea de agua, "
                  "verde = fondo.")


def fuente(tamano):
    """DejaVu (con acentos) si está instalada, como en la imagen de Docker; si no, la de Pillow."""
    try:
        return ImageFont.truetype(FUENTE, tamano)
    except OSError:
        return ImageFont.load_default(size=tamano)


def generar_diagnostico(ruta, d, etapa, mensaje):
    """El PDF de diagnóstico, con el mensaje partido en renglones (trae la cola del registro del pipeline, que es
    larga). Si el error ocurre después de que etiquetar.py dejó su imagen de control (<idVideo>_control.jpg), va en el
    PDF: así se ve desde la página qué tubos se encontraron y cuáles no."""
    paginas = paginas_de_diagnostico(d, etapa, mensaje)
    paginas[0].save(ruta, "PDF", resolution=150, save_all=len(paginas) > 1, append_images=paginas[1:])


def imagen_de_control(d):
    """La imagen de control de este análisis (la deja etiquetar.py en trabajo/, con el idVideo por nombre), o None."""
    if not d.get("archivo"):
        return None
    ruta = carpeta_analisis(d["idanalisis"]) / "trabajo" / f"{Path(d['archivo']).stem}_control.jpg"
    if not ruta.is_file():
        return None
    try:
        with Image.open(ruta) as imagen:
            return imagen.convert("RGB")
    except OSError as e:
        log.warning("No se pudo abrir la imagen de control %s: %s (el diagnóstico sale sin ella)", ruta, e)
        return None


def paginas_de_diagnostico(d, etapa, mensaje):
    """Las páginas del PDF de diagnóstico, como imágenes de Pillow: la primera con el texto y, debajo, la imagen de
    control si cabe; si no cabe, la imagen va sola en una segunda página. Se escala al ancho útil, sin deformarla."""
    pagina = Image.new("RGB", (ANCHO_PAGINA, ALTO_PAGINA), "white")
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
    paginas = [pagina]

    control = imagen_de_control(d)
    if control is None:
        return paginas
    ancho_util = ANCHO_PAGINA - 2 * MARGEN
    ancho, alto = ancho_util, round(control.height * ancho_util / control.width)
    pie = PIE_DE_CONTROL
    if d.get("cilindros_esperados") is not None and d.get("cilindros_encontrados") is not None:
        pie += (f" Se esperaban {d['cilindros_esperados']} cilindros con animal y se encontraron "
                f"{d['cilindros_encontrados']}.")
    renglones_pie = textwrap.wrap(pie, 80)
    alto_pie = len(renglones_pie) * 34 + 20
    y = 200 + len(renglones) * 45 + 20
    destino = pagina
    if y + alto + alto_pie > ALTO_PAGINA - MARGEN:
        # No cabe debajo del texto: segunda página, y si aun sola no cabe por alto, se achica sin deformarla.
        destino = Image.new("RGB", (ANCHO_PAGINA, ALTO_PAGINA), "white")
        paginas.append(destino)
        y = MARGEN
        tope = ALTO_PAGINA - 2 * MARGEN - alto_pie
        if alto > tope:
            ancho, alto = round(control.width * tope / control.height), tope
    destino.paste(control.resize((ancho, alto), Image.LANCZOS), (MARGEN, y))
    dibujo_destino = ImageDraw.Draw(destino)
    for i, renglon in enumerate(renglones_pie):
        dibujo_destino.text((MARGEN, y + alto + 15 + i * 34), renglon, font=fuente(22), fill="black")
    return paginas


def rangos_de_tubos(registro):
    """Los tubos que encontró etiquetar.py, con su rango de columnas en píxeles («1:217-580 2:580-939 …»), del último
    renglón «tubos:» del registro del pipeline; vacío si no lo imprimió."""
    try:
        texto = Path(registro).read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""
    encontrados = re.findall(r"^\s*tubos:\s*(.+)$", texto, re.M)
    return " ".join(encontrados[-1].split()) if encontrados else ""
