"""Pruebas de vigilancia.py con datos simulados (sin video, sin /proc, sin Docker): python -m unittest test_vigilancia
desde esta carpeta."""

import tempfile
import unittest
from pathlib import Path

import vigilancia as v


class Reloj:
    def __init__(self):
        self.t = 0.0

    def __call__(self):
        return self.t


class PruebasFraccionPorTiempo(unittest.TestCase):
    def test_nunca_llega_a_uno_y_nunca_baja(self):
        anterior = -1.0
        for segundos in range(0, 10000, 7):
            f = v.fraccion_por_tiempo(segundos, 100)
            self.assertLess(f, 1.0)
            self.assertGreaterEqual(f, anterior)
            anterior = f

    def test_sigue_avanzando_despues_de_lo_esperado(self):
        self.assertGreater(v.fraccion_por_tiempo(150, 100), v.fraccion_por_tiempo(120, 100))


class PruebasMarcadores(unittest.TestCase):
    def test_ultimo_paso(self):
        self.assertIsNone(v.ultimo_paso("cargando\n"))
        self.assertEqual(v.ultimo_paso("  1/5 alineando la camara\n  2/5 modelando el fondo\n"), 2)

    def test_segundos_de_ffmpeg(self):
        self.assertEqual(v.segundos_de_ffmpeg(""), 0.0)
        self.assertEqual(v.segundos_de_ffmpeg("out_time_us=1000000\nprogress=continue\nout_time_us=2500000\n"), 2.5)


class PruebasPosicionLectura(unittest.TestCase):
    def _lectura(self, posiciones, reloj):
        with tempfile.NamedTemporaryFile(delete=False) as f:
            f.write(b"x" * 1000)
        cola = iter(posiciones)
        return v.PosicionLectura(f.name, leer_pos=lambda pid: next(cola), reloj=reloj, disponible=True)

    def test_el_fotograma_de_referencia_se_descarta(self):
        # se vio pos 0.49 y luego 0.004: el salto no debe subir la barra
        reloj = Reloj()
        lectura = self._lectura([5, 490, 8, 20], reloj)
        self.assertAlmostEqual(lectura.leer(1), 0.005)
        reloj.t += 1
        self.assertAlmostEqual(lectura.leer(1), 0.005)    # en cuarentena
        reloj.t += 1
        self.assertAlmostEqual(lectura.leer(1), 0.008)    # regresó: se descarta el salto
        reloj.t += 1
        self.assertAlmostEqual(lectura.leer(1), 0.02)

    def test_un_salto_que_se_queda_se_acepta(self):
        reloj = Reloj()
        lectura = self._lectura([5, 500, 520], reloj)
        lectura.leer(1)
        lectura.leer(1)
        reloj.t += v.PosicionLectura.CUARENTENA_S + 1
        self.assertAlmostEqual(lectura.leer(1), 0.52)

    def test_sin_proc_devuelve_none(self):
        with tempfile.NamedTemporaryFile(delete=False) as f:
            f.write(b"x")
        lectura = v.PosicionLectura(f.name, disponible=False)
        self.assertIsNone(lectura.leer(1))


class PruebasVigilantes(unittest.TestCase):
    def test_cilindros_suma_pasos_y_nunca_pasa_de_100(self):
        with tempfile.TemporaryDirectory() as carpeta:
            registro = Path(carpeta) / "pipeline.log"
            video = Path(carpeta) / "v.mp4"
            video.write_bytes(b"x" * 1000)
            avances = []
            reloj = Reloj()
            lectura = v.PosicionLectura(str(video), leer_pos=lambda pid: 150, reloj=reloj, disponible=True)
            vig = v.VigilanteCilindros(avances.append, str(video), registro, 120, lectura, reloj)
            registro.write_text("  1/5 alineando la camara\n")
            vig(1)
            registro.write_text("  1/5 a\n  2/5 b\n")
            vig(1)
            registro.write_text("  1/5 a\n  2/5 b\n  5/5 e\n")
            vig(1)
            self.assertAlmostEqual(avances[0], 7.5)         # 15 % del paso 1 (peso 50)
            self.assertAlmostEqual(avances[1], 50 + 2.25)   # paso 1 completo + 15 % del 2 (peso 15)
            self.assertAlmostEqual(avances[2], 99.0)        # pasos 1 a 4 completos; el 5 solo cuenta por marcador
            self.assertLessEqual(max(avances), 100)
            self.assertEqual(sum(v.PESO_PASO_CILINDROS.values()), 100)

    def test_clasificacion_prediciendo_por_tiempo(self):
        with tempfile.TemporaryDirectory() as carpeta:
            registro = Path(carpeta) / "pipeline.log"
            video = Path(carpeta) / "v.mp4"
            video.write_bytes(b"x" * 1000)
            avances = []
            reloj = Reloj()
            lectura = v.PosicionLectura(str(video), leer_pos=lambda pid: None, reloj=reloj, disponible=True)
            vig = v.VigilanteClasificacion(avances.append, str(video), registro, 120, 2.0, lectura, reloj)
            registro.write_text("recortando 4 tubos de v.mp4\nprediciendo 100 segundos (100 s)\n")
            vig(1)
            reloj.t += 1000       # mucho más de lo esperado (200 s): no debe llegar al tope
            vig(1)
            self.assertAlmostEqual(avances[0], 3.0)
            self.assertLess(avances[1], 88.0)
            self.assertGreater(avances[1], 85.0)
            self.assertEqual(sum(v.PESO_FASE_CLASIFICACION.values()), 100)
            self.assertAlmostEqual(vig.ritmo_medido(), 10.0)


class PruebasRitmo(unittest.TestCase):
    def test_leer_y_guardar(self):
        with tempfile.TemporaryDirectory() as carpeta:
            archivo = Path(carpeta) / "w" / "ritmo.json"
            self.assertEqual(v.leer_ritmo(archivo), v.RITMO_PREDICCION)
            v.guardar_ritmo(archivo, 3.0, 1.0)
            self.assertEqual(v.leer_ritmo(archivo), 2.0)
            v.guardar_ritmo(archivo, 9999, 2.0)           # absurdo: se ignora
            self.assertEqual(v.leer_ritmo(archivo), 2.0)


if __name__ == "__main__":
    unittest.main()
