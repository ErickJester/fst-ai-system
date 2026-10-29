"""Pruebas del respaldo de la base (tareas_programadas.respaldar_base, D-18). Sin base de datos ni pg_dump de verdad:
subprocess.run se sustituye por uno falso que escribe el archivo que se le pide. Se corren dentro de la imagen del worker:

  docker compose run --rm --no-deps -v ../worker:/t worker sh -c "cd /t && python -m unittest test_respaldo -v"
"""

import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import tareas_programadas as t

CLAVE = "clave-secreta-prueba-987"


def pg_dump_falso(comando, **kwargs):
    """Hace de pg_dump: escribe el archivo de -f."""
    Path(comando[comando.index("-f") + 1]).write_bytes(b"respaldo falso")
    return subprocess.CompletedProcess(comando, 0, "", "")


class RespaldoDeLaBase(unittest.TestCase):
    def setUp(self):
        self.carpeta = Path(tempfile.mkdtemp())
        parches = [
            mock.patch.object(t, "RESPALDOS", self.carpeta),
            mock.patch.dict(os.environ, {"POSTGRES_PASSWORD": CLAVE, "DB_HOST": "db", "DB_PORT": "5432"}),
        ]
        for p in parches:
            p.start()
            self.addCleanup(p.stop)
        self.hoy = t.ahora().strftime("%Y%m%d")

    def nombres(self):
        return sorted(p.name for p in self.carpeta.iterdir())

    def test_crea_el_respaldo_de_hoy_y_no_deja_temporales(self):
        with mock.patch.object(t.subprocess, "run", side_effect=pg_dump_falso) as run:
            self.assertTrue(t.respaldar_base())
        self.assertEqual(self.nombres(), [f"fst-{self.hoy}.dump"])
        # La contraseña va en el entorno del subproceso, nunca en la línea de comandos.
        comando = run.call_args.args[0]
        self.assertEqual(comando[:2], ["pg_dump", "-Fc"])
        self.assertNotIn(CLAVE, " ".join(comando))
        self.assertEqual(run.call_args.kwargs["env"]["PGPASSWORD"], CLAVE)

    def test_conserva_los_7_mas_nuevos_y_quita_temporales_viejos(self):
        for dia in range(1, 9):
            (self.carpeta / f"fst-202001{dia:02d}.dump").write_text("viejo")
        (self.carpeta / "fst-20200109.dump.tmp").write_text("cortado")
        with mock.patch.object(t.subprocess, "run", side_effect=pg_dump_falso):
            t.respaldar_base()
        esperado = [f"fst-202001{dia:02d}.dump" for dia in range(3, 9)] + [f"fst-{self.hoy}.dump"]
        self.assertEqual(self.nombres(), sorted(esperado))

    def test_si_ya_hay_uno_de_hoy_se_reemplaza(self):
        (self.carpeta / f"fst-{self.hoy}.dump").write_text("de la mañana")
        with mock.patch.object(t.subprocess, "run", side_effect=pg_dump_falso):
            t.respaldar_base()
        self.assertEqual(self.nombres(), [f"fst-{self.hoy}.dump"])
        self.assertEqual((self.carpeta / f"fst-{self.hoy}.dump").read_bytes(), b"respaldo falso")

    def test_si_pg_dump_falla_no_queda_nada_valido_y_el_log_no_trae_la_clave(self):
        def falla(comando, **kwargs):
            Path(comando[comando.index("-f") + 1]).write_bytes(b"a medias")   # alcanzó a escribir algo
            raise subprocess.CalledProcessError(1, comando, "", "pg_dump: error: password authentication failed")

        (self.carpeta / "fst-20200101.dump").write_text("el de ayer")
        with mock.patch.object(t.subprocess, "run", side_effect=falla), self.assertLogs("worker", "ERROR") as registro:
            self.assertFalse(t.respaldar_base())
        self.assertEqual(self.nombres(), ["fst-20200101.dump"])     # sin temporal y sin respaldo de hoy; el de antes sigue
        self.assertNotIn(CLAVE, "\n".join(registro.output))
        self.assertIn("password authentication failed", "\n".join(registro.output))

    def test_sin_pg_dump_solo_se_registra(self):
        with mock.patch.object(t.subprocess, "run", side_effect=FileNotFoundError(2, "No such file", "pg_dump")), \
                self.assertLogs("worker", "ERROR"):
            self.assertFalse(t.respaldar_base())
        self.assertEqual(self.nombres(), [])


if __name__ == "__main__":
    unittest.main()
