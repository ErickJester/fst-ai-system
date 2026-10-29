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
        # la pausa es del segundo 142: la corrección empieza en 141 (k-1), no en 142
        self.assertEqual(json.loads(ruta.read_text(encoding="utf-8")),
                         [{"cilindro": 2, "desde_segundo": 141.0, "x0": 250, "y0": 480, "x1": 580, "y1": 930}])
        self.assertFalse((self.carpeta / "pausa.json").exists())
        self.assertFalse((self.carpeta / "pausa.jpg").exists())

    def test_sin_ajuste_no_toca_nada_y_sin_correcciones_devuelve_none(self):
        self.assertIsNone(w.acumular_correccion(7))
        (self.carpeta / "pausa.json").write_text(json.dumps({"version": 1, "ajuste": None}), encoding="utf-8")
        self.assertIsNone(w.acumular_correccion(7))
        self.assertTrue((self.carpeta / "pausa.json").exists())


class InstanteYTamano(unittest.TestCase):
    def setUp(self):
        self.carpeta = Path(tempfile.mkdtemp())
        self.parche = mock.patch.object(w, "carpeta_analisis", lambda _id: self.carpeta)
        self.parche.start()
        self.addCleanup(self.parche.stop)

    def test_el_instante_es_el_centro_de_la_racha(self):
        # racha k, k+1, k+2 cubre de k-1 a k+2: el centro es k+0.5
        self.assertEqual(w.instante_de_la_pausa(142), 142.5)
        self.assertEqual(w.RACHA_PAUSA_S, 3)

    def test_la_correccion_empieza_en_k_menos_uno(self):
        self.assertEqual(w.desde_de_la_correccion(142), 141.0)

    def test_la_foto_se_lee_sin_enderezar_y_el_tamano_sale_de_ella(self):
        import cv2
        import numpy as np
        foto = np.zeros((900, 500, 3), dtype=np.uint8)      # 500 de ancho y 900 de alto: sin enderezar
        captura = mock.MagicMock()
        captura.read.return_value = (True, foto)
        rasgos = pd.DataFrame([{"especimen": 1, "t0": 140.0, "t1": 145.0, "bx0": 10, "by0": 20, "bx1": 30, "by1": 40}])
        conn = mock.MagicMock()
        conn.cursor.return_value.__enter__.return_value.rowcount = 1
        d = {"idinstitucional": "x", "correo": None, "tanda": "T1", "nombre": "E", "idexperimento": 1}
        with mock.patch("cv2.VideoCapture", return_value=captura) as vc, \
                mock.patch.object(w, "notificar"), mock.patch.object(w, "enviar_correos"):
            w.pausar_analisis(conn, d, 7, 1, 142, Path("video.mp4"), rasgos, self.carpeta)
        # igual que fst_auto (lib.py): no se toca la orientación, solo se busca el instante
        self.assertFalse(any(c.args[0] == cv2.CAP_PROP_ORIENTATION_AUTO for c in captura.set.call_args_list))
        captura.set.assert_called_with(cv2.CAP_PROP_POS_MSEC, 142500.0)
        pausa = json.loads((self.carpeta / "pausa.json").read_text(encoding="utf-8"))
        self.assertEqual((pausa["ancho"], pausa["alto"]), (500, 900))
        self.assertEqual(pausa["instante_imagen_s"], 142.5)


class RachasAceptadas(unittest.TestCase):
    def setUp(self):
        self.carpeta = Path(tempfile.mkdtemp())
        import cola
        self.parches = [mock.patch.object(w, "carpeta_analisis", lambda _id: self.carpeta),
                        mock.patch.object(cola, "carpeta_analisis", lambda _id: self.carpeta)]
        for p in self.parches:
            p.start()
            self.addCleanup(p.stop)

    def test_un_tramo_aceptado_no_pausa_aunque_sea_mas_largo_que_3_s(self):
        sin = {1: {5, 6, 7, 8, 9}}                      # un tramo de 5 s que empieza en 5
        self.assertIsNone(w.racha_de_pausa(sin, 1, 300, {1: {5}}))

    def test_un_tramo_distinto_del_mismo_cilindro_si_pausa(self):
        sin = {1: {5, 6, 7, 8, 9, 20, 21, 22}}          # el tramo de 5 se aceptó; el de 20 no
        self.assertEqual(w.racha_de_pausa(sin, 1, 300, {1: {5}}), (1, 20))

    def test_otro_cilindro_no_se_afecta(self):
        sin = {1: {5, 6, 7}, 2: {5, 6, 7}}              # el cilindro 1 se aceptó en 5; el 2 sigue pausando
        self.assertEqual(w.racha_de_pausa(sin, 1, 300, {1: {5}}), (2, 5))

    def test_aceptar_racha_acumula_y_borra_la_pausa(self):
        (self.carpeta / "pausa.jpg").write_bytes(b"jpg")
        pausa = {"version": 1, "cilindro": 2, "desde_segundo": 142, "ajuste": None,
                 "continuar": {"hora": "2026-10-25T12:00:00-06:00"}}
        (self.carpeta / "pausa.json").write_text(json.dumps(pausa), encoding="utf-8")
        (self.carpeta / "rachas_aceptadas.json").write_text(
            json.dumps([{"cilindro": 1, "desde_segundo": 50}]), encoding="utf-8")
        self.assertTrue(w.aceptar_racha(7))
        lista = json.loads((self.carpeta / "rachas_aceptadas.json").read_text(encoding="utf-8"))
        self.assertEqual(lista, [{"cilindro": 1, "desde_segundo": 50}, {"cilindro": 2, "desde_segundo": 142}])
        self.assertFalse((self.carpeta / "pausa.json").exists())
        self.assertFalse((self.carpeta / "pausa.jpg").exists())
        self.assertEqual(w.rachas_aceptadas(7), {1: {50}, 2: {142}})

    def test_sin_continuar_no_toca_nada(self):
        (self.carpeta / "pausa.json").write_text(json.dumps({"version": 1, "ajuste": None}), encoding="utf-8")
        self.assertFalse(w.aceptar_racha(7))
        self.assertTrue((self.carpeta / "pausa.json").exists())
        self.assertFalse((self.carpeta / "rachas_aceptadas.json").exists())


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
