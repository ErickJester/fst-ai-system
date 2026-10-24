"""Pruebas de la lógica de pausa del worker (§4.3, encargo 32): la racha de «no se ve», la anulación de la conducta,
la regla (a) y la reanudación con correcciones. Sin base de datos ni modelo: se usan tablas de prueba y un conn falso.
Se corren dentro de la imagen del worker: python -m unittest test_pausa -v"""
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import pandas as pd

import worker as w


def cuadros_de(filas):
    """filas: lista de (cilindro, t, cx) -> DataFrame con las columnas que lee el worker."""
    return pd.DataFrame([{"s": s, "t": t, "cx": cx} for s, t, cx in filas])


class SinAnimal(unittest.TestCase):
    def test_un_segundo_sin_fotogramas_con_animal_cuenta_como_sin_animal(self):
        filas = [(1, t / 10, 100.0) for t in range(0, 30)]                  # cilindro 1 con animal
        filas += [(2, t / 10, float("nan")) for t in range(10, 30)]          # cilindro 2 sin animal en t=1.0..2.9 (segs 2 y 3)
        sin = w.segundos_sin_animal(cuadros_de(filas), 3)
        self.assertEqual(sin, {2: {2, 3}})

    def test_racha_de_tres_seguidos_pausa_y_dos_no(self):
        self.assertEqual(w.racha_de_pausa({1: {5, 6, 7}}, 1, 300), (1, 5))
        self.assertIsNone(w.racha_de_pausa({1: {5, 6}}, 1, 300))

    def test_la_racha_mas_temprana_gana_aunque_el_cilindro_sea_mayor(self):
        # cilindro 2 pierde al animal en 100-102 s; cilindro 1 en 200-202 s: la pausa es la del 2 en el segundo 100
        self.assertEqual(w.racha_de_pausa({1: {200, 201, 202}, 2: {100, 101, 102}}, 1, 300), (2, 100))

    def test_en_empate_de_segundo_gana_el_cilindro_de_numero_menor(self):
        self.assertEqual(w.racha_de_pausa({3: {50, 51, 52}, 2: {50, 51, 52}}, 1, 300), (2, 50))

    def test_la_racha_solo_se_busca_en_el_tramo_guardado(self):
        self.assertIsNone(w.racha_de_pausa({1: {2, 3, 4}}, 10, 300))        # antes de los últimos 300 s
        self.assertEqual(w.racha_de_pausa({2: {10, 11, 12}, 1: {50, 51, 52}}, 10, 300), (2, 10))  # la más temprana en el tiempo


class RecuadroDelInstante(unittest.TestCase):
    def test_sale_del_bloque_del_instante_y_no_de_la_mediana(self):
        # dos bloques con cajas distintas (la cámara se movió): en 7.0 s debe salir la del bloque 2 (5-10 s)
        rasgos = pd.DataFrame([
            {"especimen": 1, "t0": 0.0, "t1": 5.0, "bx0": 100, "by0": 200, "bx1": 300, "by1": 400},
            {"especimen": 1, "t0": 5.0, "t1": 10.0, "bx0": 160, "by0": 210, "bx1": 360, "by1": 410},
        ])
        cajas = w.recuadros_en_instante(rasgos, 7.0)
        self.assertEqual(cajas, [{"cilindro": 1, "x0": 160, "y0": 210, "x1": 360, "y1": 410}])
        self.assertNotEqual(cajas[0]["x0"], int(rasgos["bx0"].median()))   # la mediana habría sido 130

    def test_si_no_hay_bloque_en_el_instante_sale_el_mas_cercano(self):
        rasgos = pd.DataFrame([{"especimen": 2, "t0": 0.0, "t1": 5.0, "bx0": 10, "by0": 20, "bx1": 30, "by1": 40}])
        self.assertEqual(w.recuadros_en_instante(rasgos, 12.0)[0]["x0"], 10)


class ReanudarConCorreccion(unittest.TestCase):
    def setUp(self):
        self.carpeta = Path(tempfile.mkdtemp())
        import cola
        self.parches = [mock.patch.object(w, "carpeta_analisis", lambda _id: self.carpeta),
                        mock.patch.object(cola, "carpeta_analisis", lambda _id: self.carpeta)]
        for p in self.parches:
            p.start()
            self.addCleanup(p.stop)

    def test_ajuste_se_acumula_y_borra_la_pausa(self):
        pausa = {"version": 1, "cilindro": 2, "desde_segundo": 142, "ajuste": {"cilindro": 2, "x0": 250, "y0": 480,
                 "x1": 580, "y1": 930, "hora": "2026-10-23T12:00:00-06:00"}}
        (self.carpeta / "pausa.json").write_text(json.dumps(pausa), encoding="utf-8")
        (self.carpeta / "pausa.jpg").write_bytes(b"jpg")
        ruta = w.acumular_correccion(7)
        self.assertEqual(ruta, self.carpeta / "correcciones.json")
        self.assertEqual(json.loads(ruta.read_text(encoding="utf-8")),
                         [{"cilindro": 2, "desde_segundo": 142.0, "x0": 250, "y0": 480, "x1": 580, "y1": 930}])
        self.assertFalse((self.carpeta / "pausa.json").exists())
        self.assertFalse((self.carpeta / "pausa.jpg").exists())

    def test_sin_ajuste_no_toca_nada_y_sin_correcciones_devuelve_none(self):
        self.assertIsNone(w.acumular_correccion(7))
        (self.carpeta / "pausa.json").write_text(json.dumps({"version": 1, "ajuste": None}), encoding="utf-8")
        self.assertIsNone(w.acumular_correccion(7))
        self.assertTrue((self.carpeta / "pausa.json").exists())


class ConductaNula(unittest.TestCase):
    def test_los_segundos_sin_animal_se_anulan_y_el_resto_no(self):
        numeros = [1, 2, 3, 4]
        conductas = ["nado", "inmovilidad", "escalamiento", "nado"]
        vacios = {2, 3}
        resultado = [None if n in vacios else w.traducir(c, 1, 1) for n, c in zip(numeros, conductas)]
        self.assertEqual(resultado, ["nado activo", None, None, "nado activo"])


class RegistroAtomico(unittest.TestCase):
    def test_escribe_json_sin_dejar_temporal(self):
        carpeta = Path(tempfile.mkdtemp())
        w.escribir_atomico(carpeta / "x.json", {"a": 1})
        self.assertEqual(json.loads((carpeta / "x.json").read_text(encoding="utf-8")), {"a": 1})
        self.assertEqual(sorted(p.name for p in carpeta.iterdir()), ["x.json"])


if __name__ == "__main__":
    unittest.main()
