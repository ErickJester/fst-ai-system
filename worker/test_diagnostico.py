"""Pruebas del PDF de diagnóstico con la imagen de control (worker.generar_diagnostico_real). Se corren dentro del
contenedor del worker (necesita sus librerías), desde /app:

    docker cp worker/test_diagnostico.py <worker>:/app/ ; docker cp <control.jpg> <worker>:/tmp/control_ejemplo.jpg
    docker exec <worker> python -m unittest test_diagnostico

CONTROL_EJEMPLO es la imagen de ejemplo (por omisión /tmp/control_ejemplo.jpg: acu-yintang2_control.jpg de fst_auto);
SALIDA_PDFS, una carpeta donde dejar los PDF de prueba para verlos (por omisión, una temporal que se borra)."""

import os
import re
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import comun
import worker

CONTROL = Path(os.environ.get("CONTROL_EJEMPLO", "/tmp/control_ejemplo.jpg"))
MENSAJE = ("No se encontraron los cilindros esperados: el video tiene 3 y la tanda tiene 4 especímenes. En el reporte "
           "de diagnóstico está la imagen de control con los tubos que se encontraron.")


@unittest.skipUnless(CONTROL.is_file(), "falta la imagen de control de ejemplo: " + str(CONTROL))
class PruebasDiagnostico(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.raiz = Path(self._tmp.name)
        parche = mock.patch.object(comun, "ARCHIVOS", self.raiz)
        parche.start()
        self.addCleanup(parche.stop)
        self.d = {"idanalisis": 7, "archivo": "videos/5.mp4", "nombre": "Prueba", "tanda": "Control · Tanda A",
                  "cilindros_esperados": 4, "cilindros_encontrados": 3}

    def poner_control(self):
        trabajo = self.raiz / "analisis" / "7" / "trabajo"
        trabajo.mkdir(parents=True)
        shutil.copyfile(CONTROL, trabajo / "5_control.jpg")    # {idVideo}_control.jpg, como lo nombra el pipeline

    @staticmethod
    def hay_tinta(pagina, y0, y1):
        """¿Hay algo que no sea blanco entre esas filas de la página?"""
        return pagina.crop((0, y0, pagina.width, y1)).convert("L").getextrema()[0] < 250

    def test_con_control_y_texto_corto_la_imagen_va_debajo_en_la_primera_pagina(self):
        self.poner_control()
        paginas = worker.paginas_de_diagnostico(self.d, "cilindros", MENSAJE)
        self.assertEqual(len(paginas), 1)
        self.assertTrue(self.hay_tinta(paginas[0], 800, 1100), "la imagen de control no está debajo del texto")

    def test_sin_control_no_hay_imagen(self):
        paginas = worker.paginas_de_diagnostico(self.d, "cilindros", MENSAJE)
        self.assertEqual(len(paginas), 1)
        self.assertFalse(self.hay_tinta(paginas[0], 800, 1500))

    def test_si_el_texto_no_deja_lugar_va_en_una_segunda_pagina(self):
        self.poner_control()
        largo = "Detalle del registro del pipeline. " * 80          # llena los 22 renglones permitidos
        paginas = worker.paginas_de_diagnostico(self.d, "clasificacion", largo)
        self.assertEqual(len(paginas), 2)
        self.assertTrue(self.hay_tinta(paginas[1], 100, 800))

    def test_el_pdf_trae_las_paginas_y_la_imagen(self):
        self.poner_control()
        salida = Path(os.environ.get("SALIDA_PDFS", self._tmp.name))
        salida.mkdir(parents=True, exist_ok=True)
        for nombre, mensaje in (("diagnostico_cilindros.pdf", MENSAJE),
                                ("diagnostico_texto_largo.pdf", "Detalle del registro del pipeline. " * 80)):
            ruta = salida / nombre
            worker.generar_diagnostico_real(ruta, self.d, "cilindros", mensaje)
            crudo = ruta.read_bytes()
            esperadas = len(worker.paginas_de_diagnostico(self.d, "cilindros", mensaje))
            self.assertTrue(crudo.startswith(b"%PDF"))
            self.assertEqual(len(re.findall(rb"/Type\s*/Page\b", crudo)), esperadas, nombre)
            self.assertGreaterEqual(len(re.findall(rb"/Subtype\s*/Image", crudo)), esperadas, nombre)

    def test_cualquier_error_despues_del_control_la_incluye_y_sin_conteo_no_dice_numeros(self):
        self.poner_control()
        sin_conteo = {k: v for k, v in self.d.items() if not k.startswith("cilindros_")}
        paginas = worker.paginas_de_diagnostico(sin_conteo, "clasificacion", "El pipeline terminó con error.")
        self.assertTrue(self.hay_tinta(paginas[0], 800, 1100))

    def test_rangos_de_los_tubos_salen_del_registro(self):
        registro = self.raiz / "pipeline.log"
        registro.write_text("  3/5 buscando los tubos y la linea de agua\n    tubos: 1:217-580  2:580-939  3:939-1307\n"
                            "  4/5 midiendo al animal\n", encoding="utf-8")
        self.assertEqual(worker.rangos_de_tubos(registro), "1:217-580 2:580-939 3:939-1307")
        self.assertEqual(worker.rangos_de_tubos(self.raiz / "no_existe.log"), "")
        registro.write_text("sin tubos todavia\n", encoding="utf-8")
        self.assertEqual(worker.rangos_de_tubos(registro), "")


if __name__ == "__main__":
    unittest.main()
