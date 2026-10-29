"""Pruebas de los reportes guardados del worker (encargo 34): invalidar al completar, al marcar error y al borrar las carpetas
huérfanas. Sin base de datos: el cursor es falso y las carpetas son temporales. Se corren dentro de la imagen del worker:
python -m unittest test_reportes -v"""
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import comun
import tareas_programadas


class BorrarDelDisco(unittest.TestCase):
    def setUp(self):
        self.raiz = Path(tempfile.mkdtemp())
        self.parche = mock.patch.object(comun, "ARCHIVOS", self.raiz)
        self.parche.start()
        self.addCleanup(self.parche.stop)

    def test_borra_la_carpeta_del_experimento_con_sus_archivos(self):
        carpeta = self.raiz / "reportes" / "7"
        carpeta.mkdir(parents=True)
        (carpeta / "experimento.pdf").write_bytes(b"%PDF")
        (carpeta / "grupo-3.csv").write_text("x", encoding="utf-8")
        comun.borrar_reportes_del_disco(7)
        self.assertFalse(carpeta.exists())

    def test_sin_carpeta_no_falla(self):
        comun.borrar_reportes_del_disco(99)     # no hay carpeta: no pasa nada

    def test_si_el_borrado_falla_solo_avisa_en_el_log(self):
        carpeta = self.raiz / "reportes" / "7"
        carpeta.mkdir(parents=True)
        with mock.patch.object(comun.shutil, "rmtree", side_effect=OSError("bloqueado")):
            with self.assertLogs("worker", level="WARNING") as aviso:
                comun.borrar_reportes_del_disco(7)     # no lanza excepción
        self.assertIn("No se pudo borrar la carpeta de reportes", aviso.output[0])


class InvalidarEnLaTransaccion(unittest.TestCase):
    def test_borra_las_filas_del_experimento_con_el_cursor_de_quien_llama(self):
        cur = mock.MagicMock()
        comun.invalidar_reportes(cur, 7)
        cur.execute.assert_called_once_with("DELETE FROM reporte WHERE idexperimento = %s", (7,))


class HuerfanosDeReportes(unittest.TestCase):
    def setUp(self):
        self.raiz = Path(tempfile.mkdtemp())
        self.parches = [mock.patch.object(comun, "ARCHIVOS", self.raiz),
                        mock.patch.object(tareas_programadas, "ARCHIVOS", self.raiz)]
        for p in self.parches:
            p.start()
            self.addCleanup(p.stop)

    def test_borra_solo_las_carpetas_de_experimentos_que_ya_no_existen(self):
        for nombre in ("1", "2", "otra"):
            (self.raiz / "reportes" / nombre).mkdir(parents=True)
        conn = mock.MagicMock()
        conn.cursor.return_value.__enter__.return_value.fetchall.return_value = [(1,)]   # solo existe el experimento 1
        borradas = tareas_programadas.borrar_reportes_huerfanos(conn)
        self.assertEqual(borradas, 1)
        self.assertTrue((self.raiz / "reportes" / "1").exists())
        self.assertFalse((self.raiz / "reportes" / "2").exists())
        self.assertTrue((self.raiz / "reportes" / "otra").exists())     # un nombre que no es número no se toca

    def test_sin_carpeta_de_reportes_no_hace_nada(self):
        self.assertEqual(tareas_programadas.borrar_reportes_huerfanos(mock.MagicMock()), 0)


class OrdenDeCandados(unittest.TestCase):
    """El primer execute de la transacción que invalida reportes es el FOR UPDATE del experimento (encargo 34, corrección 1)."""

    def test_la_funcion_bloquea_el_experimento(self):
        cur = mock.MagicMock()
        comun.bloquear_experimento(cur, 7)
        cur.execute.assert_called_once_with(
            "SELECT idexperimento FROM experimento WHERE idexperimento = %s FOR UPDATE", (7,))

    def test_marcar_error_bloquea_el_experimento_antes_de_tocar_el_analisis(self):
        import cola
        carpeta = Path(tempfile.mkdtemp())
        d = {"idanalisis": 9, "idexperimento": 7, "idinstitucional": "2019630871", "correo": None,
             "tanda": "Control · Tanda A", "nombre": "E"}
        conn = mock.MagicMock()
        cur = conn.cursor.return_value.__enter__.return_value
        parches = [mock.patch.object(cola, "carpeta_analisis", lambda _id: carpeta),
                   mock.patch.object(cola, "generar_diagnostico", lambda *a, **k: None),
                   mock.patch.object(cola, "relativa", lambda _p: "x"),
                   mock.patch.object(cola, "cerrar_archivo", lambda _id: None),
                   mock.patch.object(cola, "enviar_correos", lambda _p: None)]
        for p in parches:
            p.start()
            self.addCleanup(p.stop)
        cola.marcar_error(conn, d, "clasificacion", "prueba")
        primera = cur.execute.call_args_list[0].args[0]
        segunda = cur.execute.call_args_list[1].args[0]
        self.assertIn("FROM experimento", primera)
        self.assertIn("FOR UPDATE", primera)
        self.assertIn("UPDATE analisis", segunda)


if __name__ == "__main__":
    unittest.main()
