"""Pruebas de tiempos.py en una carpeta temporal (no toca analisis/ del volumen): desde esta carpeta,
python -m unittest test_tiempos"""

import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import tiempos as t


class PruebasTiempos(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.carpeta = Path(self._tmp.name)
        self.addCleanup(self._tmp.cleanup)
        # Estas pruebas son del modo desarrollador prendido; la de abajo prueba el apagado.
        entorno = mock.patch.dict(os.environ, {"MODO_DESARROLLADOR": "true"})
        entorno.start()
        self.addCleanup(entorno.stop)

    def test_sin_modo_desarrollador_no_se_escribe_nada(self):
        for valor in ("false", "", "0", "no"):
            with mock.patch.dict(os.environ, {"MODO_DESARROLLADOR": valor}):
                r = t.RegistroTiempos(1, self.carpeta)
                r.video(120, 1920, 1080, 77.6)
                r.etapa("cilindros")
                r.subpaso("cilindros", "1/5 alineando la camara", 54)
                r.cerrar()
                t.cerrar_archivo(1, self.carpeta)
                self.assertFalse((self.carpeta / "tiempos.json").exists(), valor)
        with mock.patch.dict(os.environ):
            os.environ.pop("MODO_DESARROLLADOR", None)    # sin la variable: también apagado
            t.RegistroTiempos(1, self.carpeta).etapa("cilindros")
            self.assertFalse((self.carpeta / "tiempos.json").exists())

    def leer(self):
        return json.loads((self.carpeta / "tiempos.json").read_text(encoding="utf-8"))

    def test_al_empezar_hay_inicio_y_equipo_y_nada_cerrado(self):
        t.RegistroTiempos(1, self.carpeta)
        d = self.leer()
        self.assertIsNotNone(d["inicio"])
        self.assertIsNone(d["fin"])
        self.assertIsNone(d["totalSegundos"])
        self.assertEqual(d["etapas"], [])
        self.assertEqual(set(d["equipo"]), {"nombre", "cpu", "nucleos", "memoriaGB", "dockerCPU"})

    def test_cada_etapa_cierra_la_anterior(self):
        r = t.RegistroTiempos(1, self.carpeta)
        r.etapa("preprocesamiento")
        r.etapa("cilindros")
        d = self.leer()
        self.assertIsNotNone(d["etapas"][0]["fin"])
        self.assertIsNotNone(d["etapas"][0]["segundos"])
        self.assertIsNone(d["etapas"][1]["fin"])      # la que corre
        self.assertIsNone(d["etapas"][1]["segundos"])

    def test_cerrar_deja_fin_total_y_subpasos(self):
        r = t.RegistroTiempos(1, self.carpeta)
        r.video(120, 1920, 1080, 77.64)
        r.etapa("clasificacion")
        r.subpaso("clasificacion", "prediciendo", 903.04)
        r.cerrar()
        d = self.leer()
        self.assertIsNotNone(d["fin"])
        self.assertIsNotNone(d["totalSegundos"])
        self.assertIsNotNone(d["etapas"][-1]["segundos"])
        self.assertEqual(d["subpasos"], {"clasificacion": {"prediciendo": 903.0}})
        self.assertEqual(d["video"], {"duracionSegundos": 120.0, "ancho": 1920, "alto": 1080, "tamanoMB": 77.6})

    def test_error_o_cancelado_cierra_hasta_donde_llego(self):
        r = t.RegistroTiempos(1, self.carpeta)
        r.etapa("cilindros")
        t.cerrar_archivo(1, self.carpeta)
        d = self.leer()
        self.assertIsNotNone(d["fin"])
        self.assertIsNotNone(d["totalSegundos"])
        self.assertIsNotNone(d["etapas"][0]["segundos"])
        fin = d["fin"]
        t.cerrar_archivo(1, self.carpeta)             # ya cerrado: no cambia nada
        self.assertEqual(self.leer()["fin"], fin)

    def test_sin_archivo_no_falla(self):
        t.cerrar_archivo(99, self.carpeta)
        self.assertFalse((self.carpeta / "tiempos.json").exists())

    def test_reiniciar_reescribe_desde_cero(self):
        r = t.RegistroTiempos(1, self.carpeta)
        r.etapa("cilindros")
        r.cerrar()
        t.RegistroTiempos(1, self.carpeta)
        d = self.leer()
        self.assertEqual(d["etapas"], [])
        self.assertIsNone(d["fin"])


if __name__ == "__main__":
    unittest.main()
