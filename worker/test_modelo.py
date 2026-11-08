"""Pruebas de con qué modelo se hizo un análisis: tiempos.identidad_del_modelo, su registro en tiempos.json y su uso en
el PDF de diagnóstico. Se corren dentro del contenedor del worker (necesita sus librerías), desde /app:

    docker cp worker/test_modelo.py <worker>:/app/ ; docker exec <worker> python -m unittest test_modelo

Leer el paquete de un .pt de verdad necesita torch y un modelo entrenado, así que aquí _leer_paquete se simula; el
SHA-256, el tamaño y la fecha sí se calculan sobre un archivo de prueba."""

import hashlib
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import comun
import diagnostico
import tiempos as t

PAQUETE = {"version": "5.0.0", "videosDeEntrenamiento": 4, "clips": 3323}


class Dibujo:
    """Un ImageDraw que no dibuja: solo anota los textos que se le piden."""

    def __init__(self):
        self.textos = []

    def text(self, posicion, texto, **kwargs):
        self.textos.append(texto)


class PruebasModelo(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.carpeta = Path(self._tmp.name)
        self.modelo = self.carpeta / "fst_cnn_seg.pt"
        self.modelo.write_bytes(b"pesos de prueba")
        t._MODELOS.clear()
        entorno = mock.patch.dict(os.environ, {"MODO_DESARROLLADOR": "true"})
        entorno.start()
        self.addCleanup(entorno.stop)
        paquete = mock.patch.object(t, "_leer_paquete", return_value=dict(PAQUETE))
        self.paquete = paquete.start()
        self.addCleanup(paquete.stop)

    def leer(self):
        return json.loads((self.carpeta / "tiempos.json").read_text(encoding="utf-8"))

    # ------------------------------------------------------------ la identidad del archivo
    def test_la_identidad_trae_el_archivo_su_hash_su_tamano_y_lo_que_trae_dentro(self):
        m = t.identidad_del_modelo(self.modelo)
        self.assertEqual(m["archivo"], "fst_cnn_seg.pt")
        self.assertEqual(m["sha256"], hashlib.sha256(b"pesos de prueba").hexdigest())
        self.assertIsInstance(m["tamanoMB"], float)
        self.assertRegex(m["modificado"], r"^\d{4}-\d{2}-\d{2}T")
        self.assertEqual((m["version"], m["videosDeEntrenamiento"], m["clips"]), ("5.0.0", 4, 3323))

    def test_un_modelo_que_no_existe_es_none(self):
        self.assertIsNone(t.identidad_del_modelo(self.carpeta / "no_existe.pt"))

    def test_si_no_se_puede_leer_el_paquete_queda_el_hash_y_la_version_en_null(self):
        self.paquete.return_value = {}
        m = t.identidad_del_modelo(self.modelo)
        self.assertEqual(m["sha256"], hashlib.sha256(b"pesos de prueba").hexdigest())
        self.assertIsNone(m["version"])

    def test_se_calcula_una_sola_vez_y_otra_vez_si_el_archivo_cambia(self):
        primero = t.identidad_del_modelo(self.modelo)
        t.identidad_del_modelo(self.modelo)
        self.assertEqual(self.paquete.call_count, 1)             # la segunda salió de la memoria
        self.modelo.write_bytes(b"otros pesos, de otro tamano")  # el mismo nombre, otro modelo
        segundo = t.identidad_del_modelo(self.modelo)
        self.assertEqual(self.paquete.call_count, 2)
        self.assertNotEqual(primero["sha256"], segundo["sha256"])

    def test_lo_que_devuelve_no_se_comparte_con_la_memoria(self):
        t.identidad_del_modelo(self.modelo)["version"] = "alterada"
        self.assertEqual(t.identidad_del_modelo(self.modelo)["version"], "5.0.0")

    # ---------------------------------------------------------------- tiempos.json
    def test_tiempos_json_trae_el_modelo(self):
        t.RegistroTiempos(1, self.carpeta, modelo=self.modelo)
        m = self.leer()["modelo"]
        self.assertEqual(m["archivo"], "fst_cnn_seg.pt")
        self.assertEqual(m["sha256"], hashlib.sha256(b"pesos de prueba").hexdigest())
        self.assertEqual(m["version"], "5.0.0")

    def test_sin_la_ruta_del_modelo_va_null(self):
        t.RegistroTiempos(1, self.carpeta)
        self.assertIsNone(self.leer()["modelo"])

    def test_con_un_modelo_que_no_existe_va_null_y_el_analisis_sigue(self):
        t.RegistroTiempos(1, self.carpeta, modelo=self.carpeta / "no_existe.pt")
        self.assertIsNone(self.leer()["modelo"])

    def test_sin_modo_desarrollador_ni_se_calcula_ni_se_escribe(self):
        with mock.patch.dict(os.environ, {"MODO_DESARROLLADOR": "false"}):
            t.RegistroTiempos(1, self.carpeta, modelo=self.modelo)
        self.assertFalse((self.carpeta / "tiempos.json").exists())
        self.paquete.assert_not_called()

    def test_el_modelo_se_conserva_al_cerrar_el_analisis(self):
        r = t.RegistroTiempos(1, self.carpeta, modelo=self.modelo)
        r.etapa("cilindros")
        r.cerrar()
        self.assertEqual(self.leer()["modelo"]["version"], "5.0.0")
        t.cerrar_archivo(1, self.carpeta)
        self.assertEqual(self.leer()["modelo"]["version"], "5.0.0")

    # ------------------------------------------------------------------------- el texto
    def test_el_texto_del_modelo(self):
        m = {"archivo": "fst_cnn_seg.pt", "version": "5.0.0", "sha256": "480c5803998562f59896c4292", "tamanoMB": 119.6,
             "modificado": "2026-10-19T21:27:32-06:00"}
        self.assertEqual(t.texto_del_modelo(m),
                         "fst_cnn_seg.pt · red 5.0.0 · SHA-256 480c5803998562f5 · 119.6 MB · archivo del 2026-10-19")

    def test_el_texto_omite_lo_que_no_se_sabe(self):
        self.assertEqual(t.texto_del_modelo({"archivo": "x.pt"}), "x.pt")
        self.assertEqual(t.texto_del_modelo({}), "modelo desconocido")

    # ------------------------------------------------------- leerlo de un análisis
    def test_modelo_del_analisis_lo_lee_del_tiempos_json(self):
        t.RegistroTiempos(1, self.carpeta, modelo=self.modelo)
        self.assertEqual(t.modelo_del_analisis(1, self.carpeta)["version"], "5.0.0")

    def test_modelo_del_analisis_es_none_sin_modo_desarrollador_sin_archivo_o_de_antes(self):
        t.RegistroTiempos(1, self.carpeta, modelo=self.modelo)
        with mock.patch.dict(os.environ, {"MODO_DESARROLLADOR": "false"}):
            self.assertIsNone(t.modelo_del_analisis(1, self.carpeta))
        self.assertIsNone(t.modelo_del_analisis(99, self.carpeta / "no_hay"))
        (self.carpeta / "tiempos.json").write_text(json.dumps({"inicio": "x"}), encoding="utf-8")   # sin bloque modelo
        self.assertIsNone(t.modelo_del_analisis(1, self.carpeta))


class PruebasModeloEnElDiagnostico(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        parche = mock.patch.object(comun, "ARCHIVOS", Path(self._tmp.name))
        parche.start()
        self.addCleanup(parche.stop)
        self.d = {"idanalisis": 7, "archivo": "videos/5.mp4", "nombre": "Prueba", "tanda": "Control · Tanda A"}

    def textos(self, modelo):
        dibujo = Dibujo()
        with mock.patch.object(diagnostico, "modelo_del_analisis", return_value=modelo), \
                mock.patch.object(diagnostico.ImageDraw, "Draw", return_value=dibujo):
            diagnostico.paginas_de_diagnostico(self.d, "cilindros", "No se encontraron los cilindros.")
        return dibujo.textos

    def test_con_modelo_el_diagnostico_dice_con_cual(self):
        textos = self.textos({"archivo": "fst_cnn_seg.pt", "version": "5.0.0", "sha256": "480c5803998562f59896c4292",
                              "tamanoMB": 119.6, "modificado": "2026-10-19T21:27:32-06:00"})
        unido = " ".join(textos)
        self.assertIn("Modelo del análisis:", unido)
        self.assertIn("fst_cnn_seg.pt", unido)
        self.assertIn("red 5.0.0", unido)
        self.assertIn("480c5803998562f5", unido)

    def test_sin_modelo_el_diagnostico_queda_como_antes(self):
        textos = self.textos(None)
        self.assertNotIn("Modelo del análisis:", " ".join(textos))
        self.assertIn("Generado por el worker (pipeline fst_auto, modelo fst_cnn_seg).", textos)


if __name__ == "__main__":
    unittest.main()
